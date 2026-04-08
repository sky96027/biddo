package com.biddo.domain.member.exception;

import com.biddo.domain.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {

    MEMBER_NOT_FOUND(404, "MEMBER_001", "회원을 찾을 수 없습니다."),
    DUPLICATE_EMAIL(400, "MEMBER_002", "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(400, "MEMBER_003", "이미 사용 중인 닉네임입니다."),
    INVALID_PASSWORD(400, "MEMBER_004", "현재 비밀번호가 일치하지 않습니다."),
    INVALID_CREDENTIALS(401, "MEMBER_005", "이메일 또는 비밀번호가 일치하지 않습니다."),
    INVALID_REFRESH_TOKEN(401, "MEMBER_006", "유효하지 않은 Refresh Token입니다."),
    INVALID_PASSWORD_LENGTH(400, "MEMBER_007", "비밀번호는 8~20자여야 합니다."),
    INVALID_NICKNAME_LENGTH(400, "MEMBER_008", "닉네임은 2~50자여야 합니다."),
    INVALID_INTRODUCTION_LENGTH(400, "MEMBER_009", "자기소개는 500자 이내여야 합니다.");

    private final int status;
    private final String code;
    private final String message;
}