package com.biddo.api.auction.dto.response;

import com.biddo.domain.auction.entity.Auction;
import com.biddo.domain.auction.entity.AuctionStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class AuctionSummaryResponse {

    private final Long auctionId;
    private final String title;
    private final AuctionStatus status;
    private final Long currentPrice;
    private final int bidCount;
    private final String thumbnailUrl;
    private final LocalDateTime endTime;

    private AuctionSummaryResponse(Long auctionId, String title, AuctionStatus status,
                                    Long currentPrice, int bidCount, String thumbnailUrl,
                                    LocalDateTime endTime) {
        this.auctionId = auctionId;
        this.title = title;
        this.status = status;
        this.currentPrice = currentPrice;
        this.bidCount = bidCount;
        this.thumbnailUrl = thumbnailUrl;
        this.endTime = endTime;
    }

    public static AuctionSummaryResponse from(Auction auction) {
        String thumbnail = auction.getImages().isEmpty()
                ? null
                : auction.getImages().get(0).getImageUrl();

        return new AuctionSummaryResponse(
                auction.getId(),
                auction.getTitle(),
                auction.getStatus(),
                auction.getCurrentPrice(),
                auction.getBidCount(),
                thumbnail,
                auction.getEndTime()
        );
    }
}