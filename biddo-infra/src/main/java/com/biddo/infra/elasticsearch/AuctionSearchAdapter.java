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

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;

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

        // ACTIVE 상태만 검색
        boolQuery.filter(f -> f.term(t -> t.field("status").value("ACTIVE")));

        // 키워드 검색
        if (condition.getKeyword() != null && !condition.getKeyword().isBlank()) {
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

        NativeQueryBuilder queryBuilder = NativeQuery.builder()
                .withQuery(q -> q.bool(boolQuery.build()));

        // 정렬
        Sort sort = resolveSort(condition.getSort());
        queryBuilder.withSort(sort);

        // 페이지네이션 (cursor = auctionId 기반)
        int size = Math.min(condition.getSize(), 100);
        if (condition.getCursor() != null) {
            // search_after를 위해 size+1로 조회하지 않고, cursor 이후 필터
            boolQuery.filter(f -> f.range(r -> r.number(n -> n
                    .field("id")
                    .lt(condition.getCursor().doubleValue())
            )));
        }

        queryBuilder.withQuery(q -> q.bool(boolQuery.build()));
        queryBuilder.withPageable(PageRequest.of(0, size));

        return queryBuilder.build();
    }

    private Sort resolveSort(String sortParam) {
        if (sortParam == null) {
            return Sort.by(Sort.Direction.DESC, "id");
        }
        return switch (sortParam) {
            case "BID_COUNT" -> Sort.by(Sort.Direction.DESC, "bidCount").and(Sort.by(Sort.Direction.DESC, "id"));
            case "END_TIME" -> Sort.by(Sort.Direction.ASC, "endTime").and(Sort.by(Sort.Direction.DESC, "id"));
            case "PRICE" -> Sort.by(Sort.Direction.ASC, "currentPrice").and(Sort.by(Sort.Direction.DESC, "id"));
            default -> Sort.by(Sort.Direction.DESC, "id");
        };
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