package com.biddo.api.auction.controller;

import com.biddo.api.auction.dto.request.AuctionCreateRequest;
import com.biddo.api.auction.dto.request.AuctionUpdateRequest;
import com.biddo.api.auction.dto.response.AuctionDetailResponse;
import com.biddo.api.auction.dto.response.AuctionResponse;
import com.biddo.api.auction.dto.response.AuctionSummaryResponse;
import com.biddo.api.common.response.ApiResponse;
import com.biddo.api.common.security.CustomUserDetails;
import com.biddo.api.search.dto.response.AuctionSearchResponse;
import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.auction.service.AuctionService;
import com.biddo.domain.search.dto.AuctionSearchResult;
import com.biddo.domain.search.port.AuctionSearchPort;
import com.biddo.infra.redis.PopularAuctionRepository;
import com.biddo.infra.sse.AuctionSseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private static final int POPULAR_SIZE = 10;
    private static final int SIMILAR_SIZE = 6;

    private final AuctionService auctionService;
    private final AuctionRepository auctionRepository;
    private final AuctionSearchPort auctionSearchPort;
    private final PopularAuctionRepository popularAuctionRepository;
    private final AuctionSseService auctionSseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuctionResponse> createAuction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AuctionCreateRequest request) {
        Auction auction = auctionService.create(
                userDetails.getMemberId(),
                request.getCategoryId(),
                request.getTitle(),
                request.getDescription(),
                request.getCondition(),
                request.getStartingPrice(),
                request.getBuyNowPrice(),
                request.getStartTime(),
                request.getEndTime(),
                request.getImageUrls()
        );
        return ApiResponse.success(AuctionResponse.from(auction));
    }

    @PutMapping("/{auctionId}")
    public ApiResponse<AuctionResponse> updateAuction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long auctionId,
            @Valid @RequestBody AuctionUpdateRequest request) {
        Auction auction = auctionService.update(
                auctionId,
                userDetails.getMemberId(),
                request.getCategoryId(),
                request.getTitle(),
                request.getDescription(),
                request.getCondition(),
                request.getStartingPrice(),
                request.getBuyNowPrice(),
                request.getEndTime(),
                request.getImageUrls()
        );
        return ApiResponse.success(AuctionResponse.from(auction));
    }

    @DeleteMapping("/{auctionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> cancelAuction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long auctionId) {
        auctionService.cancel(auctionId, userDetails.getMemberId());
        return ApiResponse.success();
    }

    @GetMapping("/popular")
    public ApiResponse<List<AuctionSummaryResponse>> getPopularAuctions(
            @RequestParam(defaultValue = "10") int size) {
        int fetchSize = Math.min(size, POPULAR_SIZE);
        List<Long> popularIds = popularAuctionRepository.getTopAuctionIds(fetchSize);
        if (popularIds.isEmpty()) {
            return ApiResponse.success(List.of());
        }
        List<Auction> auctions = auctionRepository.findByIdIn(popularIds);
        // Redis 순서(인기순) 유지
        Map<Long, Auction> auctionMap = auctions.stream()
                .collect(Collectors.toMap(Auction::getId, Function.identity()));
        List<AuctionSummaryResponse> responses = popularIds.stream()
                .filter(auctionMap::containsKey)
                .map(id -> AuctionSummaryResponse.from(auctionMap.get(id)))
                .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{auctionId}")
    public ApiResponse<AuctionDetailResponse> getAuction(@PathVariable Long auctionId) {
        Auction auction = auctionService.findById(auctionId);
        return ApiResponse.success(AuctionDetailResponse.from(auction));
    }

    @GetMapping("/{auctionId}/similar")
    public ApiResponse<List<AuctionSearchResponse>> getSimilarAuctions(
            @PathVariable Long auctionId,
            @RequestParam(defaultValue = "6") int size) {
        int fetchSize = Math.min(size, SIMILAR_SIZE);
        List<AuctionSearchResult> results = auctionSearchPort.findSimilar(auctionId, fetchSize);
        List<AuctionSearchResponse> responses = results.stream()
                .map(AuctionSearchResponse::from)
                .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping(value = "/{auctionId}/countdown", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeCountdown(@PathVariable Long auctionId) {
        Auction auction = auctionService.findById(auctionId);
        return auctionSseService.subscribe(auctionId, auction.getEndTime());
    }
}
