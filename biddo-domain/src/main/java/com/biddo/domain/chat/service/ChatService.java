package com.biddo.domain.chat.service;

import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.chat.entity.*;
import com.biddo.domain.chat.exception.ChatErrorCode;
import com.biddo.domain.chat.repository.ChatMessageRepository;
import com.biddo.domain.chat.repository.ChatRoomRepository;
import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public ChatRoom createRoom(Auction auction) {
        if (chatRoomRepository.existsByAuctionId(auction.getId())) {
            throw new BusinessException(ChatErrorCode.CHAT_ROOM_ALREADY_EXISTS);
        }

        ChatRoom chatRoom = ChatRoom.builder()
                .auction(auction)
                .seller(auction.getSeller())
                .buyer(auction.getWinner())
                .build();
        chatRoom = chatRoomRepository.save(chatRoom);

        ChatMessage systemMessage = ChatMessage.builder()
                .chatRoom(chatRoom)
                .sender(auction.getSeller())
                .content("낙찰이 완료되었습니다. 본 플랫폼은 개발 포트폴리오 시연 목적으로 운영되며, 실제 물품 거래를 중개하지 않습니다.")
                .messageType(MessageType.SYSTEM)
                .build();
        chatMessageRepository.save(systemMessage);

        return chatRoom;
    }

    public List<ChatRoom> findMyRooms(Long memberId) {
        return chatRoomRepository.findByMemberId(memberId);
    }

    public List<ChatMessage> findMessages(Long roomId, Long memberId, Long cursor, int size) {
        ChatRoom chatRoom = getChatRoom(roomId);
        validateParticipant(chatRoom, memberId);

        PageRequest pageRequest = PageRequest.of(0, size);
        if (cursor == null) {
            return chatMessageRepository.findByRoomIdFirstPage(roomId, pageRequest);
        }
        return chatMessageRepository.findByRoomIdWithCursor(roomId, cursor, pageRequest);
    }

    @Transactional
    public ChatMessage sendMessage(Long roomId, Long senderId, String content,
                                   MessageType messageType, String imageUrl, Member sender) {
        ChatRoom chatRoom = getChatRoom(roomId);
        validateParticipant(chatRoom, senderId);
        validateActive(chatRoom);

        ChatMessage message = ChatMessage.builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .content(content)
                .messageType(messageType)
                .imageUrl(imageUrl)
                .build();

        message = chatMessageRepository.save(message);
        chatRoom.updateLastReadMessageId(senderId, message.getId());

        return message;
    }

    @Transactional
    public void markAsRead(Long roomId, Long memberId, Long messageId) {
        ChatRoom chatRoom = getChatRoom(roomId);
        validateParticipant(chatRoom, memberId);
        chatRoom.updateLastReadMessageId(memberId, messageId);
    }

    public ChatMessage getLatestMessage(Long roomId) {
        return chatMessageRepository.findLatestByRoomId(roomId).orElse(null);
    }

    public long getUnreadCount(Long roomId, Long memberId) {
        ChatRoom chatRoom = getChatRoom(roomId);
        Long lastReadId = chatRoom.getLastReadMessageId(memberId);
        if (lastReadId == null) {
            lastReadId = 0L;
        }
        return chatMessageRepository.countByChatRoomIdAndIdGreaterThan(roomId, lastReadId);
    }

    private ChatRoom getChatRoom(Long roomId) {
        return chatRoomRepository.findByIdWithMembers(roomId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    private void validateParticipant(ChatRoom chatRoom, Long memberId) {
        if (!chatRoom.isParticipant(memberId)) {
            throw new BusinessException(ChatErrorCode.NOT_CHAT_PARTICIPANT);
        }
    }

    private void validateActive(ChatRoom chatRoom) {
        if (chatRoom.getStatus() != ChatRoomStatus.ACTIVE) {
            throw new BusinessException(ChatErrorCode.CHAT_ROOM_CLOSED);
        }
    }
}