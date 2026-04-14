package com.biddo.api.admin.controller;

import com.biddo.domain.auction.service.AuctionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/auctions")
@RequiredArgsConstructor
public class AdminAuctionController {

    private final AuctionService auctionService;

    @DeleteMapping("/{auctionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forceCancel(@PathVariable Long auctionId) {
        auctionService.forceCancel(auctionId);
    }
}