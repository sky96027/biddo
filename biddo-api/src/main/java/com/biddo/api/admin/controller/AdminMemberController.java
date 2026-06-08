package com.biddo.api.admin.controller;

import com.biddo.api.admin.dto.request.BanRequest;
import com.biddo.api.admin.dto.response.BanResponse;
import com.biddo.api.common.response.ApiResponse;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.service.MemberService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - 회원 관리")
@RestController
@RequestMapping("/api/v1/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final MemberService memberService;

    @PatchMapping("/{memberId}/ban")
    public ApiResponse<BanResponse> ban(
            @PathVariable Long memberId,
            @Valid @RequestBody BanRequest request) {
        Member member = memberService.ban(
                memberId, request.getBanType(), request.getReason(), request.getDurationDays());
        return ApiResponse.success(BanResponse.from(member));
    }

    @DeleteMapping("/{memberId}/ban")
    public ApiResponse<BanResponse> unban(@PathVariable Long memberId) {
        Member member = memberService.unban(memberId);
        return ApiResponse.success(BanResponse.from(member));
    }
}