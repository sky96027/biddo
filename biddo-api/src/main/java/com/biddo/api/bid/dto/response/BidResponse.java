package com.biddo.api.bid.dto.response;

import com.biddo.domain.bid.model.Bid;
import com.biddo.domain.bid.model.BidType;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class BidResponse {

    private final Long bidId;
    private final Long auctionId;
    private final String bidderNickname;
    private final Long bidAmount;
    private final BidType bidType;
    private final boolean isWinning;
    private final LocalDateTime createdAt;

    private BidResponse(Long bidId, Long auctionId, String bidderNickname, Long bidAmount,
                        BidType bidType, boolean isWinning, LocalDateTime createdAt) {
        this.bidId = bidId;
        this.auctionId = auctionId;
        this.bidderNickname = bidderNickname;
        this.bidAmount = bidAmount;
        this.bidType = bidType;
        this.isWinning = isWinning;
        this.createdAt = createdAt;
    }

    public static BidResponse from(Bid bid) {
        return new BidResponse(
                bid.getId(),
                bid.getAuction().getId(),
                bid.getBidder().getNickname(),
                bid.getBidAmount(),
                bid.getBidType(),
                bid.isWinning(),
                bid.getCreatedAt()
        );
    }
}