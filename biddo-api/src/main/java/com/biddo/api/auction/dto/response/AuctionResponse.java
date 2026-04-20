package com.biddo.api.auction.dto.response;

import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.model.AuctionStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class AuctionResponse {

    private final Long auctionId;
    private final String title;
    private final AuctionStatus status;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;

    private AuctionResponse(Long auctionId, String title, AuctionStatus status,
                            LocalDateTime startTime, LocalDateTime endTime) {
        this.auctionId = auctionId;
        this.title = title;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public static AuctionResponse from(Auction auction) {
        return new AuctionResponse(
                auction.getId(),
                auction.getTitle(),
                auction.getStatus(),
                auction.getStartTime(),
                auction.getEndTime()
        );
    }
}