package com.biddo.api.admin.dto.response;

import com.biddo.domain.member.entity.BanType;
import com.biddo.domain.member.entity.Member;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BanResponse {

    private Long memberId;
    private String nickname;
    private String email;
    private BanType banType;
    private String banReason;
    private LocalDateTime banEndDate;

    public static BanResponse from(Member member) {
        return BanResponse.builder()
                .memberId(member.getId())
                .nickname(member.getNickname())
                .email(member.getEmail())
                .banType(member.getBanType())
                .banReason(member.getBanReason())
                .banEndDate(member.getBanEndDate())
                .build();
    }
}