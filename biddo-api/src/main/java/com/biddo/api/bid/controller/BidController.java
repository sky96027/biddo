package com.biddo.api.bid.controller;

import com.biddo.api.bid.dto.request.AutoBidRequest;
import com.biddo.api.bid.dto.request.BidRequest;
import com.biddo.api.bid.dto.response.AutoBidResponse;
import com.biddo.api.bid.dto.response.BidResponse;
import com.biddo.api.common.response.ApiResponse;
import com.biddo.api.common.security.CustomUserDetails;
import com.biddo.domain.bid.model.AutoBid;
import com.biddo.domain.bid.model.Bid;
import com.biddo.domain.bid.service.BidService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auctions/{auctionId}")
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;

    @PostMapping("/bids")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BidResponse> placeBid(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long auctionId,
            @Valid @RequestBody BidRequest request) {
        Bid bid = bidService.placeBid(auctionId, userDetails.getMemberId(), request.getBidAmount());
        return ApiResponse.success(BidResponse.from(bid));
    }

    @PostMapping("/buy-now")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BidResponse> buyNow(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long auctionId) {
        Bid bid = bidService.buyNow(auctionId, userDetails.getMemberId());
        return ApiResponse.success(BidResponse.from(bid));
    }

    @PostMapping("/auto-bids")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AutoBidResponse> setAutoBid(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long auctionId,
            @Valid @RequestBody AutoBidRequest request) {
        AutoBid autoBid = bidService.setAutoBid(auctionId, userDetails.getMemberId(), request.getMaxAmount());
        return ApiResponse.success(AutoBidResponse.from(autoBid));
    }

    @DeleteMapping("/auto-bids")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> cancelAutoBid(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long auctionId) {
        bidService.cancelAutoBid(auctionId, userDetails.getMemberId());
        return ApiResponse.success();
    }

    @GetMapping("/bids")
    public ApiResponse<List<BidResponse>> getBidHistory(@PathVariable Long auctionId) {
        List<Bid> bids = bidService.getBidHistory(auctionId);
        return ApiResponse.success(bids.stream().map(BidResponse::from).toList());
    }
}