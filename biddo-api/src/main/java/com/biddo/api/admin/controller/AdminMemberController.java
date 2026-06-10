package com.biddo.api.admin.controller;

import com.biddo.api.admin.dto.request.BanRequest;
import com.biddo.api.admin.dto.response.BanResponse;
import com.biddo.api.common.response.ApiResponse;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.service.MemberService;
import com.biddo.domain.member.service.TrustScoreCalculator;
import io.swagger.v3.oas.annotations.Operation;
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
    private final TrustScoreCalculator trustScoreCalculator;

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

    @Operation(summary = "신뢰도 점수 수동 재계산",
            description = "전체 회원의 신뢰도 점수를 즉시 재계산합니다. "
                    + "매일 새벽 4시 자동 배치가 실행되지만, 긴급 반영이 필요할 때 수동으로 호출합니다.")
    @PostMapping("/trust-score/recalculate")
    public ApiResponse<Void> recalculateTrustScores() {
        trustScoreCalculator.recalculateAll();
        return ApiResponse.success(null);
    }
}