package com.biddo.api.member.dto.request;

import lombok.Getter;

@Getter
public class ProfileUpdateRequest {

    private String nickname;

    private String introduction;

    private String profileImageUrl;
}