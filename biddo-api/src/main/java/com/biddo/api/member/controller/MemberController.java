package com.biddo.api.member.controller;

import com.biddo.api.auction.dto.response.AuctionSummaryResponse;
import com.biddo.api.common.response.ApiResponse;
import com.biddo.api.common.response.CursorResponse;
import com.biddo.api.common.security.CustomUserDetails;
import com.biddo.api.member.dto.request.ProfileUpdateRequest;
import com.biddo.api.member.dto.response.MemberResponse;
import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.model.AuctionStatus;
import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private static final int DEFAULT_SIZE = 20;

    private final MemberService memberService;
    private final AuctionRepository auctionRepository;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Member member = memberService.findById(userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(new MemberResponse(member)));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ProfileUpdateRequest request) {
        Member member = memberService.updateProfile(
                userDetails.getMemberId(),
                request.getNickname(),
                request.getIntroduction(),
                request.getProfileImageUrl());
        return ResponseEntity.ok(ApiResponse.success(new MemberResponse(member)));
    }

    @GetMapping("/me/selling")
    public ApiResponse<CursorResponse<AuctionSummaryResponse>> getMySelling(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) AuctionStatus status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        int fetchSize = Math.min(size, DEFAULT_SIZE);
        List<Auction> auctions = auctionRepository.findBySellerId(
                userDetails.getMemberId(), status, cursor, fetchSize + 1);
        return ApiResponse.success(toCursorResponse(auctions, fetchSize));
    }

    @GetMapping("/me/purchases")
    public ApiResponse<CursorResponse<AuctionSummaryResponse>> getMyPurchases(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        int fetchSize = Math.min(size, DEFAULT_SIZE);
        List<Auction> auctions = auctionRepository.findByWinnerId(
                userDetails.getMemberId(), cursor, fetchSize + 1);
        return ApiResponse.success(toCursorResponse(auctions, fetchSize));
    }

    @GetMapping("/me/bids")
    public ApiResponse<CursorResponse<AuctionSummaryResponse>> getMyBids(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        int fetchSize = Math.min(size, DEFAULT_SIZE);
        List<Auction> auctions = auctionRepository.findActiveAuctionsByBidderId(
                userDetails.getMemberId(), cursor, fetchSize + 1);
        return ApiResponse.success(toCursorResponse(auctions, fetchSize));
    }

    private CursorResponse<AuctionSummaryResponse> toCursorResponse(List<Auction> auctions, int fetchSize) {
        boolean hasNext = auctions.size() > fetchSize;
        List<Auction> content = hasNext ? auctions.subList(0, fetchSize) : auctions;
        List<AuctionSummaryResponse> responses = content.stream()
                .map(AuctionSummaryResponse::from).toList();
        String nextCursor = hasNext
                ? String.valueOf(content.get(content.size() - 1).getId())
                : null;
        return CursorResponse.of(responses, nextCursor, hasNext);
    }
}