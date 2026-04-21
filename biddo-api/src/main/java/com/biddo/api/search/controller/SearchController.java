package com.biddo.api.search.controller;

import com.biddo.api.common.response.ApiResponse;
import com.biddo.api.common.response.CursorResponse;
import com.biddo.api.search.dto.response.AuctionSearchResponse;
import com.biddo.domain.search.dto.AuctionSearchCondition;
import com.biddo.domain.search.dto.AuctionSearchResult;
import com.biddo.domain.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/auctions")
    public ApiResponse<CursorResponse<AuctionSearchResponse>> searchAuctions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(required = false) String endWithin,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {

        AuctionSearchCondition condition = AuctionSearchCondition.builder()
                .keyword(keyword)
                .categoryId(categoryId)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .endWithin(endWithin)
                .sort(sort)
                .cursor(cursor)
                .size(size + 1)
                .build();

        List<AuctionSearchResult> results = searchService.search(condition);

        boolean hasNext = results.size() > size;
        List<AuctionSearchResponse> content = results.stream()
                .limit(size)
                .map(AuctionSearchResponse::from)
                .toList();

        String nextCursor = hasNext
                ? String.valueOf(content.get(content.size() - 1).getAuctionId())
                : null;

        return ApiResponse.success(CursorResponse.of(content, nextCursor, hasNext));
    }
}