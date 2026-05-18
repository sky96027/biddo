package com.biddo.api.search.controller;

import com.biddo.api.common.response.ApiResponse;
import com.biddo.api.common.response.CursorResponse;
import com.biddo.api.common.security.CustomUserDetails;
import com.biddo.api.search.dto.response.AuctionSearchResponse;
import com.biddo.domain.search.dto.AuctionSearchCondition;
import com.biddo.domain.search.dto.AuctionSearchResult;
import com.biddo.domain.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/auctions")
    public ApiResponse<CursorResponse<AuctionSearchResponse>> searchAuctions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
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

        Long memberId = userDetails != null ? userDetails.getMemberId() : null;
        List<AuctionSearchResult> results = searchService.search(condition, memberId);

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

    @GetMapping("/recent")
    public ApiResponse<List<String>> getRecentSearches(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<String> keywords = searchService.getRecentSearches(userDetails.getMemberId());
        return ApiResponse.success(keywords);
    }

    @DeleteMapping("/recent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteAllRecentSearches(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        searchService.deleteAllRecentSearches(userDetails.getMemberId());
        return ApiResponse.success();
    }

    @DeleteMapping("/recent/{keyword}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteRecentSearch(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String keyword) {
        searchService.deleteRecentSearch(userDetails.getMemberId(), keyword);
        return ApiResponse.success();
    }
}