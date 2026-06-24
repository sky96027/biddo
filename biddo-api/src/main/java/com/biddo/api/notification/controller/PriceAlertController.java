package com.biddo.api.notification.controller;

import com.biddo.api.common.response.ApiResponse;
import com.biddo.api.common.security.CustomUserDetails;
import com.biddo.api.notification.dto.request.PriceAlertRequest;
import com.biddo.api.notification.dto.request.PriceAlertUpdateRequest;
import com.biddo.api.notification.dto.response.PriceAlertResponse;
import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.auction.exception.AuctionErrorCode;
import com.biddo.domain.notification.model.PriceAlert;
import com.biddo.domain.notification.service.PriceAlertService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "가격 알림")
@RestController
@RequestMapping("/api/v1/price-alerts")
@RequiredArgsConstructor
public class PriceAlertController {

    private final PriceAlertService priceAlertService;
    private final AuctionRepository auctionRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PriceAlertResponse> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PriceAlertRequest request) {
        Auction auction = auctionRepository.findById(request.getAuctionId())
                .orElseThrow(() -> new BusinessException(AuctionErrorCode.AUCTION_NOT_FOUND));

        PriceAlert alert = priceAlertService.create(
                userDetails.getMemberId(),
                request.getAuctionId(),
                request.getThresholdPercent(),
                auction.getCurrentPrice()
        );
        return ApiResponse.success(PriceAlertResponse.from(alert));
    }

    @GetMapping
    public ApiResponse<List<PriceAlertResponse>> getMyAlerts(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<PriceAlertResponse> responses = priceAlertService.findByMemberId(userDetails.getMemberId())
                .stream()
                .map(PriceAlertResponse::from)
                .toList();
        return ApiResponse.success(responses);
    }

    @PutMapping("/{alertId}")
    public ApiResponse<PriceAlertResponse> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long alertId,
            @Valid @RequestBody PriceAlertUpdateRequest request) {
        PriceAlert alert = priceAlertService.update(
                alertId,
                userDetails.getMemberId(),
                request.getThresholdPercent()
        );
        return ApiResponse.success(PriceAlertResponse.from(alert));
    }

    @PatchMapping("/{alertId}/toggle")
    public ApiResponse<Void> toggleActive(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long alertId) {
        priceAlertService.toggleActive(alertId, userDetails.getMemberId());
        return ApiResponse.success();
    }

    @DeleteMapping("/{alertId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long alertId) {
        priceAlertService.delete(alertId, userDetails.getMemberId());
        return ApiResponse.success();
    }
}