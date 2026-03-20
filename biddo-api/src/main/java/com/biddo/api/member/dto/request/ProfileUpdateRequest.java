package com.biddo.api.member.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class ProfileUpdateRequest {

    @Size(min = 2, max = 50, message = "닉네임은 2~50자여야 합니다.")
    private String nickname;

    @Size(max = 500, message = "자기소개는 500자 이내여야 합니다.")
    private String introduction;

    private String profileImageUrl;
}