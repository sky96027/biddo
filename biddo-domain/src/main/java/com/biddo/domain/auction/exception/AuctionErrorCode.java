package com.biddo.domain.auction.exception;

import com.biddo.domain.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuctionErrorCode implements ErrorCode {

    AUCTION_NOT_FOUND(404, "AUCTION_001", "경매를 찾을 수 없습니다."),
    AUCTION_NOT_PENDING(409, "AUCTION_002", "대기 중인 경매만 수정/취소할 수 있습니다."),
    NOT_AUCTION_SELLER(403, "AUCTION_003", "경매 판매자만 수행할 수 있습니다."),
    CATEGORY_NOT_FOUND(404, "AUCTION_004", "카테고리를 찾을 수 없습니다."),
    INVALID_AUCTION_TIME(400, "AUCTION_005", "경매 종료 시간은 시작 시간 이후여야 합니다."),
    INVALID_AUCTION_DURATION(400, "AUCTION_006", "경매 기간은 최소 1시간, 최대 7일이어야 합니다."),
    INVALID_BUY_NOW_PRICE(400, "AUCTION_007", "즉시구매가는 시작가보다 높아야 합니다.");

    private final int status;
    private final String code;
    private final String message;
}
