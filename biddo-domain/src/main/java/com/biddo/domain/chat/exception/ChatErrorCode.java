package com.biddo.domain.chat.exception;

import com.biddo.domain.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements ErrorCode {

    CHAT_ROOM_NOT_FOUND(404, "CHAT_001", "채팅방을 찾을 수 없습니다."),
    NOT_CHAT_PARTICIPANT(403, "CHAT_002", "채팅방 참여자만 접근할 수 있습니다."),
    CHAT_ROOM_CLOSED(409, "CHAT_003", "종료된 채팅방에는 메시지를 보낼 수 없습니다."),
    CHAT_ROOM_ALREADY_EXISTS(409, "CHAT_004", "이미 해당 경매의 채팅방이 존재합니다.");

    private final int status;
    private final String code;
    private final String message;
}