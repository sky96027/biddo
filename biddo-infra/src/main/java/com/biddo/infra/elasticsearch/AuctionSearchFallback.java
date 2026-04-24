package com.biddo.infra.elasticsearch;

import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.search.dto.AuctionSearchCondition;
import com.biddo.domain.search.dto.AuctionSearchResult;
import com.biddo.infra.auction.AuctionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuctionSearchFallback {

    private final AuctionJpaRepository auctionJpaRepository;

    public List<AuctionSearchResult> search(AuctionSearchCondition condition) {
        int size = Math.min(condition.getSize(), 100);
        PageRequest pageRequest = PageRequest.of(0, size);

        LocalDateTime endBefore = null;
        if (condition.getEndWithin() != null) {
            endBefore = switch (condition.getEndWithin()) {
                case "1h" -> LocalDateTime.now().plusHours(1);
                case "24h" -> LocalDateTime.now().plusHours(24);
                case "3d" -> LocalDateTime.now().plusDays(3);
                default -> null;
            };
        }

        List<Auction> auctions = auctionJpaRepository.searchAuctions(
                condition.getKeyword(),
                condition.getCategoryId(),
                condition.getMinPrice(),
                condition.getMaxPrice(),
                endBefore,
                condition.getCursor(),
                pageRequest
        );

        return auctions.stream()
                .map(this::toSearchResult)
                .toList();
    }

    public List<AuctionSearchResult> findSimilarByCategory(Long auctionId, int size) {
        PageRequest pageRequest = PageRequest.of(0, size + 1);
        List<Auction> auctions = auctionJpaRepository.findSimilarByCategory(auctionId, pageRequest);
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