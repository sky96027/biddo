package com.biddo.api.chat.dto.response;

import com.biddo.domain.chat.entity.ChatMessage;
import com.biddo.domain.chat.entity.ChatRoom;
import com.biddo.domain.chat.entity.ChatRoomStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ChatRoomResponse {

    private final Long roomId;
    private final Long auctionId;
    private final String auctionTitle;
    private final String opponentNickname;
    private final String lastMessage;
    private final LocalDateTime lastMessageAt;
    private final long unreadCount;
    private final ChatRoomStatus status;

    private ChatRoomResponse(Long roomId, Long auctionId, String auctionTitle,
                             String opponentNickname, String lastMessage,
                             LocalDateTime lastMessageAt, long unreadCount,
                             ChatRoomStatus status) {
        this.roomId = roomId;
        this.auctionId = auctionId;
        this.auctionTitle = auctionTitle;
        this.opponentNickname = opponentNickname;
        this.lastMessage = lastMessage;
        this.lastMessageAt = lastMessageAt;
        this.unreadCount = unreadCount;
        this.status = status;
    }

    public static ChatRoomResponse of(ChatRoom chatRoom, Long memberId,
                                       ChatMessage lastMsg, long unreadCount) {
        String opponent = chatRoom.isSeller(memberId)
                ? chatRoom.getBuyer().getNickname()
                : chatRoom.getSeller().getNickname();

        return new ChatRoomResponse(
                chatRoom.getId(),
                chatRoom.getAuction().getId(),
                chatRoom.getAuction().getTitle(),
                opponent,
                lastMsg != null ? lastMsg.getContent() : null,
                lastMsg != null ? lastMsg.getCreatedAt() : null,
                unreadCount,
                chatRoom.getStatus()
        );
    }
}