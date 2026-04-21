package com.biddo.domain.search.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuctionSearchCondition {

    private String keyword;
    private Long categoryId;
    private Long minPrice;
    private Long maxPrice;
    private String endWithin;
    private String sort;
    private Long cursor;
    private int size;
}