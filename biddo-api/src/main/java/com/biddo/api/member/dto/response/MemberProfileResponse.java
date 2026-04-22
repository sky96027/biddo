package com.biddo.api.member.dto.response;

import com.biddo.domain.member.entity.Member;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class MemberProfileResponse {

    private final Long memberId;
    private final String nickname;
    private final String profileImageUrl;
    private final String introduction;
    private final BigDecimal trustScore;
    private final double averageRating;
    private final long reviewCount;
    private final long completedTradeCount;

    private MemberProfileResponse(Long memberId, String nickname, String profileImageUrl,
                                   String introduction, BigDecimal trustScore,
                                   double averageRating, long reviewCount, long completedTradeCount) {
        this.memberId = memberId;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.introduction = introduction;
        this.trustScore = trustScore;
        this.averageRating = averageRating;
        this.reviewCount = reviewCount;
        this.completedTradeCount = completedTradeCount;
    }

    public static MemberProfileResponse of(Member member, double averageRating,
                                             long reviewCount, long completedTradeCount) {
        return new MemberProfileResponse(
                member.getId(),
                member.getNickname(),
                member.getProfileImageUrl(),
                member.getIntroduction(),
                member.getTrustScore(),
                averageRating,
                reviewCount,
                completedTradeCount
        );
    }
}