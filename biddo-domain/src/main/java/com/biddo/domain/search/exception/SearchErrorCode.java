package com.biddo.domain.search.exception;

import com.biddo.domain.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SearchErrorCode implements ErrorCode {

    KEYWORD_ALERT_NOT_FOUND(404, "SEARCH_001", "키워드 알림을 찾을 수 없습니다."),
    NOT_KEYWORD_ALERT_OWNER(403, "SEARCH_002", "키워드 알림 소유자만 수행할 수 있습니다."),
    KEYWORD_ALERT_DUPLICATE(409, "SEARCH_003", "이미 등록된 키워드입니다.");

    private final int status;
    private final String code;
    private final String message;
}