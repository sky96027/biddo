package com.biddo.domain.chat.service;

import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.model.AuctionStatus;
import com.biddo.domain.auction.model.ItemCondition;
import com.biddo.domain.category.entity.Category;
import com.biddo.domain.chat.entity.*;
import com.biddo.domain.chat.exception.ChatErrorCode;
import com.biddo.domain.chat.repository.ChatMessageRepository;
import com.biddo.domain.chat.repository.ChatRoomRepository;
import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @InjectMocks
    private ChatService chatService;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    private Member seller;
    private Member buyer;
    private Auction auction;
    private ChatRoom chatRoom;

    @BeforeEach
    void setUp() {
        seller = Member.builder().email("seller@test.com").password("encoded").nickname("seller").build();
        setId(seller, 1L);

        buyer = Member.builder().email("buyer@test.com").password("encoded").nickname("buyer").build();
        setId(buyer, 2L);

        Category category = Category.builder().name("스마트폰").depth(1).sortOrder(1).build();
        setId(category, 9L);

        auction = Auction.builder()
                .seller(seller).category(category)
                .title("테스트 경매").description("설명")
                .condition(ItemCondition.LIKE_NEW)
                .startingPrice(500_000L).buyNowPrice(null)
                .startTime(LocalDateTime.now().minusDays(4))
                .endTime(LocalDateTime.now().minusDays(1))
                .build();
        setId(auction, 1L);
        setField(auction, "status", AuctionStatus.SOLD);
        setField(auction, "winner", buyer);

        chatRoom = ChatRoom.builder().auction(auction).seller(seller).buyer(buyer).build();
        setId(chatRoom, 1L);
    }

    @Test
    @DisplayName("채팅방 생성 성공")
    void createRoom_success() {
        given(chatRoomRepository.existsByAuctionId(1L)).willReturn(false);
        given(chatRoomRepository.save(any(ChatRoom.class))).willAnswer(inv -> {
            ChatRoom cr = inv.getArgument(0);
            setId(cr, 1L);
            return cr;
        });
        given(chatMessageRepository.save(any(ChatMessage.class))).willAnswer(inv -> inv.getArgument(0));

        ChatRoom result = chatService.createRoom(auction);

        assertThat(result.getSeller().getId()).isEqualTo(1L);
        assertThat(result.getBuyer().getId()).isEqualTo(2L);
        assertThat(result.getStatus()).isEqualTo(ChatRoomStatus.ACTIVE);
        verify(chatMessageRepository).save(any(ChatMessage.class));
    }

    @Test
    @DisplayName("채팅방 생성 실패 - 이미 존재")
    void createRoom_alreadyExists() {
        given(chatRoomRepository.existsByAuctionId(1L)).willReturn(true);

        assertThatThrownBy(() -> chatService.createRoom(auction))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ChatErrorCode.CHAT_ROOM_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("메시지 전송 성공")
    void sendMessage_success() {
        given(chatRoomRepository.findByIdWithMembers(1L)).willReturn(Optional.of(chatRoom));
        given(chatMessageRepository.save(any(ChatMessage.class))).willAnswer(inv -> {
            ChatMessage msg = inv.getArgument(0);
            setId(msg, 1L);
            return msg;
        });

        ChatMessage result = chatService.sendMessage(1L, 2L, "안녕하세요", MessageType.TEXT, null, buyer);

        assertThat(result.getContent()).isEqualTo("안녕하세요");
        assertThat(result.getMessageType()).isEqualTo(MessageType.TEXT);
    }

    @Test
    @DisplayName("메시지 전송 실패 - 참여자 아님")
    void sendMessage_notParticipant() {
        given(chatRoomRepository.findByIdWithMembers(1L)).willReturn(Optional.of(chatRoom));

        Member stranger = Member.builder().email("stranger@test.com").password("encoded").nickname("stranger").build();
        setId(stranger, 999L);

        assertThatThrownBy(() -> chatService.sendMessage(1L, 999L, "메시지", MessageType.TEXT, null, stranger))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ChatErrorCode.NOT_CHAT_PARTICIPANT));
    }

    @Test
    @DisplayName("메시지 전송 실패 - 채팅방 종료")
    void sendMessage_closed() {
        chatRoom.close();
        given(chatRoomRepository.findByIdWithMembers(1L)).willReturn(Optional.of(chatRoom));

        assertThatThrownBy(() -> chatService.sendMessage(1L, 2L, "메시지", MessageType.TEXT, null, buyer))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ChatErrorCode.CHAT_ROOM_CLOSED));
    }

    @Test
    @DisplayName("읽음 처리 성공")
    void markAsRead_success() {
        given(chatRoomRepository.findByIdWithMembers(1L)).willReturn(Optional.of(chatRoom));

        chatService.markAsRead(1L, 2L, 10L);

        assertThat(chatRoom.getBuyerLastReadMessageId()).isEqualTo(10L);
    }

    private void setId(Object entity, Long id) {
        try {
            java.lang.reflect.Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Object entity, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
