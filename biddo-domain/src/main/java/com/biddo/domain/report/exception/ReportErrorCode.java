package com.biddo.domain.report.exception;

import com.biddo.domain.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements ErrorCode {

    REPORT_NOT_FOUND(404, "REPORT_001", "신고를 찾을 수 없습니다."),
    SELF_REPORT_NOT_ALLOWED(400, "REPORT_002", "본인을 신고할 수 없습니다.");

    private final int status;
    private final String code;
    private final String message;
}