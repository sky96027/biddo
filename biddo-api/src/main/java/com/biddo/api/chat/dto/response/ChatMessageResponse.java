package com.biddo.api.chat.dto.response;

import com.biddo.domain.chat.entity.ChatMessage;
import com.biddo.domain.chat.entity.MessageType;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ChatMessageResponse {

    private final Long messageId;
    private final Long senderId;
    private final String senderNickname;
    private final String content;
    private final MessageType messageType;
    private final String imageUrl;
    private final LocalDateTime createdAt;

    private ChatMessageResponse(Long messageId, Long senderId, String senderNickname,
                                String content, MessageType messageType,
                                String imageUrl, LocalDateTime createdAt) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.senderNickname = senderNickname;
        this.content = content;
        this.messageType = messageType;
        this.imageUrl = imageUrl;
        this.createdAt = createdAt;
    }

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSender().getId(),
                message.getSender().getNickname(),
                message.getContent(),
                message.getMessageType(),
                message.getImageUrl(),
                message.getCreatedAt()
        );
    }
}