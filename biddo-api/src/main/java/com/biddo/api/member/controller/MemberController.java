package com.biddo.api.member.controller;

import com.biddo.api.common.response.ApiResponse;
import com.biddo.api.common.security.CustomUserDetails;
import com.biddo.api.member.dto.request.ProfileUpdateRequest;
import com.biddo.api.member.dto.response.MemberResponse;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

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
}