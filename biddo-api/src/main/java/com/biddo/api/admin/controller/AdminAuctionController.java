package com.biddo.api.admin.controller;

import com.biddo.api.common.response.ApiResponse;
import com.biddo.domain.auction.service.AuctionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - 경매 관리")
@RestController
@RequestMapping("/api/v1/admin/auctions")
@RequiredArgsConstructor
public class AdminAuctionController {

    private final AuctionService auctionService;

    @DeleteMapping("/{auctionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> forceCancel(@PathVariable Long auctionId) {
        auctionService.forceCancel(auctionId);
        return ApiResponse.success();
    }
}