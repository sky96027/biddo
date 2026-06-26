package com.biddo.infra.elasticsearch;

import com.biddo.domain.search.dto.AuctionSearchCondition;
import com.biddo.domain.search.dto.AuctionSearchResult;
import com.biddo.domain.search.port.AuctionSearchPort;
import com.biddo.infra.elasticsearch.document.AuctionDocument;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.query_dsl.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionSearchAdapter implements AuctionSearchPort {

    private final ElasticsearchOperations elasticsearchOperations;
    private final AuctionSearchFallback auctionSearchFallback;

    @Override
    @CircuitBreaker(name = "elasticsearch", fallbackMethod = "searchFallback")
    public List<AuctionSearchResult> search(AuctionSearchCondition condition) {
        NativeQuery query = buildQuery(condition);
        SearchHits<AuctionDocument> searchHits = elasticsearchOperations.search(query, AuctionDocument.class);

        return searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(this::toSearchResult)
                .toList();
    }

    private List<AuctionSearchResult> searchFallback(AuctionSearchCondition condition, Throwable t) {
        log.warn("Elasticsearch 장애 발생, DB fallback 실행: {}", t.getMessage());
        return auctionSearchFallback.search(condition);
    }

    @Override
    @CircuitBreaker(name = "elasticsearch", fallbackMethod = "findSimilarFallback")
    public List<AuctionSearchResult> findSimilar(Long auctionId, int size) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .must(m -> m.moreLikeThis(mlt -> mlt
                                .fields("title", "description")
                                .like(l -> l.document(d -> d.index("auctions").id(String.valueOf(auctionId))))
                                .minTermFreq(1)
                                .minDocFreq(1)
                                .maxQueryTerms(12)
                        ))
                        .filter(f -> f.term(t -> t.field("status").value("ACTIVE")))
                ))
                .withPageable(PageRequest.of(0, size))
                .build();

        SearchHits<AuctionDocument> searchHits = elasticsearchOperations.search(query, AuctionDocument.class);

        return searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .filter(doc -> !doc.getId().equals(auctionId))
                .map(this::toSearchResult)
                .toList();
    }

    private List<AuctionSearchResult> findSimilarFallback(Long auctionId, int size, Throwable t) {
        log.warn("Elasticsearch 장애 발생, 유사 상품 DB fallback 실행: {}", t.getMessage());
        return auctionSearchFallback.findSimilarByCategory(auctionId, size);
    }

    private NativeQuery buildQuery(AuctionSearchCondition condition) {
        BoolQuery.Builder boolQuery = QueryBuilders.bool();
        boolean hasKeyword = condition.getKeyword() != null && !condition.getKeyword().isBlank();

        // ACTIVE 상태만 검색
        boolQuery.filter(f -> f.term(t -> t.field("status").value("ACTIVE")));

        // 키워드 검색
        if (hasKeyword) {
            boolQuery.must(m -> m.multiMatch(mm -> mm
                    .query(condition.getKeyword())
                    .fields("title^2", "description")
            ));
        }

        // 카테고리 필터
        if (condition.getCategoryId() != null) {
            boolQuery.filter(f -> f.term(t -> t.field("categoryId").value(condition.getCategoryId())));
        }

        // 가격 범위 필터
        if (condition.getMinPrice() != null || condition.getMaxPrice() != null) {
            boolQuery.filter(f -> f.range(r -> {
                var range = r.number(n -> {
                    var nr = n.field("currentPrice");
                    if (condition.getMinPrice() != null) {
                        nr.gte(condition.getMinPrice().doubleValue());
                    }
                    if (condition.getMaxPrice() != null) {
                        nr.lte(condition.getMaxPrice().doubleValue());
                    }
                    return nr;
                });
                return range;
            }));
        }

        // 종료 임박 필터
        if (condition.getEndWithin() != null) {
            LocalDateTime deadline = calculateDeadline(condition.getEndWithin());
            if (deadline != null) {
                boolQuery.filter(f -> f.range(r -> r.date(d -> d
                        .field("endTime")
                        .lte(deadline.toString())
                )));
            }
        }

        // 페이지네이션 (cursor = auctionId 기반)
        int size = Math.min(condition.getSize(), 100);
        if (condition.getCursor() != null) {
            boolQuery.filter(f -> f.range(r -> r.number(n -> n
                    .field("id")
                    .lt(condition.getCursor().doubleValue())
            )));
        }

        // 인기도(bidCount log1p)와 마감임박(gauss endTime) 가중치를 bool 쿼리에 적용
        Query boolQueryObj = Query.of(q -> q.bool(boolQuery.build()));
        Query scoringQuery = Query.of(q -> q.functionScore(fs -> fs
                .query(boolQueryObj)
                .functions(List.of(
                        FunctionScore.of(f -> f.fieldValueFactor(fvf -> fvf
                                .field("bidCount")
                                .factor(1.2)
                                .modifier(FieldValueFactorModifier.Log1p)
                                .missing(0.0)
                        )),
                        FunctionScore.of(f -> f.gauss(g -> g
                                .date(d -> d
                                        .field("endTime")
                                        .placement(p -> p
                                                .origin("now")
                                                .scale(Time.of(t -> t.time("3d")))
                                        )
                                )
                        ))
                ))
                .scoreMode(FunctionScoreMode.Sum)
                .boostMode(FunctionBoostMode.Multiply)
        ));

        NativeQueryBuilder queryBuilder = NativeQuery.builder();
        queryBuilder.withQuery(scoringQuery);
        queryBuilder.withSort(resolveSort(condition.getSort(), hasKeyword));
        queryBuilder.withPageable(PageRequest.of(0, size));

        return queryBuilder.build();
    }

    private Sort resolveSort(String sortParam, boolean hasKeyword) {
        if (sortParam != null) {
            return switch (sortParam) {
                case "BID_COUNT" -> Sort.by(Sort.Direction.DESC, "bidCount").and(Sort.by(Sort.Direction.DESC, "id"));
                case "END_TIME" -> Sort.by(Sort.Direction.ASC, "endTime").and(Sort.by(Sort.Direction.DESC, "id"));
                case "PRICE" -> Sort.by(Sort.Direction.ASC, "currentPrice").and(Sort.by(Sort.Direction.DESC, "id"));
                default -> Sort.by(Sort.Direction.DESC, "id");
            };
        }
        // 키워드 검색 시 relevance 정렬, 브라우징 시 최신순
        return hasKeyword ? Sort.by(Sort.Direction.DESC, "_score") : Sort.by(Sort.Direction.DESC, "id");
    }

    private LocalDateTime calculateDeadline(String endWithin) {
        return switch (endWithin) {
            case "1h" -> LocalDateTime.now().plusHours(1);
            case "24h" -> LocalDateTime.now().plusHours(24);
            case "3d" -> LocalDateTime.now().plusDays(3);
            default -> null;
        };
    }

    private AuctionSearchResult toSearchResult(AuctionDocument doc) {
        return AuctionSearchResult.builder()
                .auctionId(doc.getId())
                .title(doc.getTitle())
                .status(doc.getStatus())
                .currentPrice(doc.getCurrentPrice())
                .bidCount(doc.getBidCount())
                .thumbnailUrl(doc.getThumbnailUrl())
                .endTime(doc.getEndTime())
                .sellerNickname(doc.getSellerNickname())
                .categoryName(doc.getCategoryName())
                .build();
    }
}