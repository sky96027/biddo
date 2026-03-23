package com.biddo.api.auction.controller;

import com.biddo.api.auction.dto.request.AuctionCreateRequest;
import com.biddo.api.auction.dto.request.AuctionUpdateRequest;
import com.biddo.api.auction.dto.response.AuctionDetailResponse;
import com.biddo.api.auction.dto.response.AuctionResponse;
import com.biddo.api.common.response.ApiResponse;
import com.biddo.api.common.security.CustomUserDetails;
import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.service.AuctionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;

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

    @GetMapping("/{auctionId}")
    public ApiResponse<AuctionDetailResponse> getAuction(@PathVariable Long auctionId) {
        Auction auction = auctionService.findById(auctionId);
        return ApiResponse.success(AuctionDetailResponse.from(auction));
    }
}
