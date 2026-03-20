package com.biddo.api.member.dto.response;

import com.biddo.domain.member.entity.Member;
import lombok.Getter;

@Getter
public class SignupResponse {

    private final Long memberId;
    private final String email;
    private final String nickname;

    public SignupResponse(Member member) {
        this.memberId = member.getId();
        this.email = member.getEmail();
        this.nickname = member.getNickname();
    }
}