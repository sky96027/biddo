package com.biddo.api.member.dto.response;

import com.biddo.domain.member.entity.Member;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class MemberResponse {

    private final Long memberId;
    private final String email;
    private final String nickname;
    private final String profileImageUrl;
    private final String introduction;
    private final BigDecimal trustScore;

    public MemberResponse(Member member) {
        this.memberId = member.getId();
        this.email = member.getEmail();
        this.nickname = member.getNickname();
        this.profileImageUrl = member.getProfileImageUrl();
        this.introduction = member.getIntroduction();
        this.trustScore = member.getTrustScore();
    }
}