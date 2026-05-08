package com.biddo.domain.bid.exception;

import com.biddo.domain.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BidErrorCode implements ErrorCode {

    AUCTION_NOT_ACTIVE(409, "BID_001", "진행 중인 경매에만 입찰할 수 있습니다."),
    SELF_BID_NOT_ALLOWED(403, "BID_002", "본인 경매에는 입찰할 수 없습니다."),
    BID_AMOUNT_TOO_LOW(400, "BID_003", "최소 입찰 금액 이상으로 입찰해야 합니다."),
    BUY_NOW_NOT_AVAILABLE(400, "BID_004", "즉시 구매가가 설정되지 않은 경매입니다."),
    AUTO_BID_MAX_TOO_LOW(400, "BID_005", "자동 입찰 최대 금액은 최소 입찰 금액 이상이어야 합니다."),
    AUTO_BID_ALREADY_EXISTS(409, "BID_006", "이미 자동 입찰이 설정되어 있습니다."),
    AUCTION_LOCK_FAILED(409, "BID_007", "다른 입찰이 처리 중입니다. 잠시 후 다시 시도해주세요.");

    private final int status;
    private final String code;
    private final String message;
}