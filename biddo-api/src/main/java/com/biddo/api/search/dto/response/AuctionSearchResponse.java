package com.biddo.api.search.dto.response;

import com.biddo.domain.search.dto.AuctionSearchResult;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class AuctionSearchResponse {

    private final Long auctionId;
    private final String title;
    private final String status;
    private final Long currentPrice;
    private final int bidCount;
    private final String thumbnailUrl;
    private final LocalDateTime endTime;
    private final String sellerNickname;
    private final String categoryName;

    private AuctionSearchResponse(Long auctionId, String title, String status, Long currentPrice,
                                   int bidCount, String thumbnailUrl, LocalDateTime endTime,
                                   String sellerNickname, String categoryName) {
        this.auctionId = auctionId;
        this.title = title;
        this.status = status;
        this.currentPrice = currentPrice;
        this.bidCount = bidCount;
        this.thumbnailUrl = thumbnailUrl;
        this.endTime = endTime;
        this.sellerNickname = sellerNickname;
        this.categoryName = categoryName;
    }

    public static AuctionSearchResponse from(AuctionSearchResult result) {
        return new AuctionSearchResponse(
                result.getAuctionId(),
                result.getTitle(),
                result.getStatus(),
                result.getCurrentPrice(),
                result.getBidCount(),
                result.getThumbnailUrl(),
                result.getEndTime(),
                result.getSellerNickname(),
                result.getCategoryName()
        );
    }
}