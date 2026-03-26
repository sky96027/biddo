package com.biddo.api.bid.dto.response;

import com.biddo.domain.bid.model.AutoBid;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class AutoBidResponse {

    private final Long autoBidId;
    private final Long auctionId;
    private final Long maxAmount;
    private final boolean isActive;
    private final LocalDateTime createdAt;

    private AutoBidResponse(Long autoBidId, Long auctionId, Long maxAmount,
                            boolean isActive, LocalDateTime createdAt) {
        this.autoBidId = autoBidId;
        this.auctionId = auctionId;
        this.maxAmount = maxAmount;
        this.isActive = isActive;
        this.createdAt = createdAt;
    }

    public static AutoBidResponse from(AutoBid autoBid) {
        return new AutoBidResponse(
                autoBid.getId(),
                autoBid.getAuction().getId(),
                autoBid.getMaxAmount(),
                autoBid.isActive(),
                autoBid.getCreatedAt()
        );
    }
}