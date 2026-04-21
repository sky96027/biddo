package com.biddo.domain.search.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AuctionSearchResult {

    private Long auctionId;
    private String title;
    private String status;
    private Long currentPrice;
    private int bidCount;
    private String thumbnailUrl;
    private LocalDateTime endTime;
    private String sellerNickname;
    private String categoryName;
}