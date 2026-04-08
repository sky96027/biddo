package com.biddo.domain.review.exception;

import com.biddo.domain.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReviewErrorCode implements ErrorCode {

    REVIEW_NOT_FOUND(404, "REVIEW_001", "후기를 찾을 수 없습니다."),
    NOT_WINNER(403, "REVIEW_002", "낙찰자만 후기를 작성할 수 있습니다."),
    ALREADY_REVIEWED(409, "REVIEW_003", "이미 후기를 작성하였습니다."),
    NOT_REVIEWER(403, "REVIEW_004", "후기 작성자만 수정/삭제할 수 있습니다."),
    AUCTION_NOT_ENDED(409, "REVIEW_005", "종료된 경매에만 후기를 작성할 수 있습니다."),
    INVALID_RATING(400, "REVIEW_006", "별점은 1~5점이어야 합니다.");

    private final int status;
    private final String code;
    private final String message;
}