package com.biddo.infra.elasticsearch;

import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.search.dto.AuctionSearchCondition;
import com.biddo.domain.search.dto.AuctionSearchResult;
import com.biddo.domain.auction.port.out.AuctionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuctionSearchFallback {

    private final AuctionRepository auctionRepository;
    private final EntityManager entityManager;

    public List<AuctionSearchResult> search(AuctionSearchCondition condition) {
        int size = Math.min(condition.getSize(), 100);

        StringBuilder jpql = new StringBuilder("""
                SELECT a FROM Auction a
                JOIN FETCH a.seller JOIN FETCH a.category LEFT JOIN FETCH a.images
                WHERE a.status = 'ACTIVE'
                """);

        Map<String, Object> params = new HashMap<>();

        if (condition.getKeyword() != null && !condition.getKeyword().isBlank()) {
            jpql.append(" AND a.title LIKE :keyword");
            params.put("keyword", "%" + condition.getKeyword() + "%");
        }

        if (condition.getCategoryId() != null) {
            jpql.append(" AND a.category.id = :categoryId");
            params.put("categoryId", condition.getCategoryId());
        }

        if (condition.getMinPrice() != null) {
            jpql.append(" AND a.currentPrice >= :minPrice");
            params.put("minPrice", condition.getMinPrice());
        }

        if (condition.getMaxPrice() != null) {
            jpql.append(" AND a.currentPrice <= :maxPrice");
            params.put("maxPrice", condition.getMaxPrice());
        }

        if (condition.getEndWithin() != null) {
            LocalDateTime endBefore = switch (condition.getEndWithin()) {
                case "1h" -> LocalDateTime.now().plusHours(1);
                case "24h" -> LocalDateTime.now().plusHours(24);
                case "3d" -> LocalDateTime.now().plusDays(3);
                default -> null;
            };
            if (endBefore != null) {
                jpql.append(" AND a.endTime <= :endBefore");
                params.put("endBefore", endBefore);
            }
        }

        if (condition.getCursor() != null) {
            jpql.append(" AND a.id < :cursor");
            params.put("cursor", condition.getCursor());
        }

        jpql.append(" ORDER BY a.id DESC");

        TypedQuery<Auction> query = entityManager.createQuery(jpql.toString(), Auction.class);
        params.forEach(query::setParameter);
        query.setMaxResults(size);

        return query.getResultList().stream()
                .map(this::toSearchResult)
                .toList();
    }

    public List<AuctionSearchResult> findSimilarByCategory(Long auctionId, int size) {
        List<Auction> auctions = auctionRepository.findSimilarByCategory(auctionId, size + 1);
        return auctions.stream()
                .map(this::toSearchResult)
                .toList();
    }

    private AuctionSearchResult toSearchResult(Auction auction) {
        String thumbnail = auction.getImages().isEmpty()
                ? null
                : auction.getImages().get(0).getImageUrl();

        return AuctionSearchResult.builder()
                .auctionId(auction.getId())
                .title(auction.getTitle())
                .status(auction.getStatus().name())
                .currentPrice(auction.getCurrentPrice())
                .bidCount(auction.getBidCount())
                .thumbnailUrl(thumbnail)
                .endTime(auction.getEndTime())
                .sellerNickname(auction.getSeller().getNickname())
                .categoryName(auction.getCategory().getName())
                .build();
    }
}