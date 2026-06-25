package com.biddo.infra.kafka.consumer;

import com.biddo.domain.bid.port.out.BidRepository;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.repository.MemberRepository;
import com.biddo.domain.notification.entity.NotificationType;
import com.biddo.domain.notification.service.NotificationService;
import com.biddo.infra.kafka.event.AuctionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuctionEventConsumerTest {

    @InjectMocks
    private AuctionEventConsumer consumer;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private NotificationService notificationService;

    private Member seller;
    private Member winner;
    private Member otherBidder;

    @BeforeEach
    void setUp() {
        seller = Member.builder().email("seller@test.com").password("pw").nickname("seller").build();
        setId(seller, 1L);

        winner = Member.builder().email("winner@test.com").password("pw").nickname("winner").build();
        setId(winner, 2L);

        otherBidder = Member.builder().email("other@test.com").password("pw").nickname("other").build();
        setId(otherBidder, 3L);
    }

    @Test
    @DisplayName("AUCTION_SOLD - 낙찰자, 판매자, 기타 입찰자에게 알림 생성")
    void handleAuctionEvents_sold_createsNotificationsForAllParties() {
        // given
        AuctionEvent event = AuctionEvent.builder()
                .eventType(AuctionEvent.AUCTION_SOLD)
                .auctionId(10L)
                .sellerId(1L)
                .currentPrice(800_000L)
                .winnerId(2L)
                .occurredAt(LocalDateTime.now())
                .build();

        given(memberRepository.findById(1L)).willReturn(java.util.Optional.of(seller));
        given(memberRepository.findById(2L)).willReturn(java.util.Optional.of(winner));
        given(bidRepository.findDistinctBidderIdsByAuctionId(10L)).willReturn(new ArrayList<>(List.of(2L, 3L)));
        given(memberRepository.findAllById(List.of(3L))).willReturn(List.of(otherBidder));

        // when
        consumer.handleAuctionEvents(List.of(event));

        // then
        ArgumentCaptor<List<NotificationService.NotificationSpec>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).createAll(captor.capture());

        List<NotificationService.NotificationSpec> specs = captor.getValue();
        assertThat(specs).hasSize(3);
        assertThat(specs).anyMatch(s -> s.receiver().equals(winner) && s.type() == NotificationType.WON);
        assertThat(specs).anyMatch(s -> s.receiver().equals(seller) && s.type() == NotificationType.AUCTION_END);
        assertThat(specs).anyMatch(s -> s.receiver().equals(otherBidder) && s.type() == NotificationType.AUCTION_END);
    }

    @Test
    @DisplayName("AUCTION_ENDED (낙찰) - 낙찰자, 판매자, 기타 입찰자에게 알림 생성")
    void handleAuctionEvents_endedWithWinner_createsNotificationsForAllParties() {
        // given
        AuctionEvent event = AuctionEvent.builder()
                .eventType(AuctionEvent.AUCTION_ENDED)
                .auctionId(10L)
                .sellerId(1L)
                .currentPrice(700_000L)
                .winnerId(2L)
                .occurredAt(LocalDateTime.now())
                .build();

        given(memberRepository.findById(1L)).willReturn(java.util.Optional.of(seller));
        given(memberRepository.findById(2L)).willReturn(java.util.Optional.of(winner));
        given(bidRepository.findDistinctBidderIdsByAuctionId(10L)).willReturn(new ArrayList<>(List.of(2L, 3L)));
        given(memberRepository.findAllById(List.of(3L))).willReturn(List.of(otherBidder));

        // when
        consumer.handleAuctionEvents(List.of(event));

        // then
        ArgumentCaptor<List<NotificationService.NotificationSpec>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).createAll(captor.capture());

        List<NotificationService.NotificationSpec> specs = captor.getValue();
        assertThat(specs).hasSize(3);
        assertThat(specs).anyMatch(s -> s.receiver().equals(winner) && s.type() == NotificationType.WON);
        assertThat(specs).anyMatch(s -> s.receiver().equals(seller) && s.type() == NotificationType.AUCTION_END);
        assertThat(specs).anyMatch(s -> s.receiver().equals(otherBidder) && s.type() == NotificationType.AUCTION_END);
    }

    @Test
    @DisplayName("AUCTION_ENDED (유찰) - 판매자에게만 알림 생성")
    void handleAuctionEvents_endedWithoutWinner_notifiesSellerOnly() {
        // given
        AuctionEvent event = AuctionEvent.builder()
                .eventType(AuctionEvent.AUCTION_ENDED)
                .auctionId(10L)
                .sellerId(1L)
                .currentPrice(500_000L)
                .winnerId(null)
                .occurredAt(LocalDateTime.now())
                .build();

        given(memberRepository.findById(1L)).willReturn(java.util.Optional.of(seller));

        // when
        consumer.handleAuctionEvents(List.of(event));

        // then
        ArgumentCaptor<List<NotificationService.NotificationSpec>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).createAll(captor.capture());

        List<NotificationService.NotificationSpec> specs = captor.getValue();
        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).receiver()).isEqualTo(seller);
        assertThat(specs.get(0).type()).isEqualTo(NotificationType.AUCTION_END);
        assertThat(specs.get(0).message()).contains("유찰");
    }

    @Test
    @DisplayName("AUCTION_CANCELLED - 기존 입찰자에게 취소 알림 생성")
    void handleAuctionEvents_cancelled_notifiesBidders() {
        // given
        AuctionEvent event = AuctionEvent.builder()
                .eventType(AuctionEvent.AUCTION_CANCELLED)
                .auctionId(10L)
                .sellerId(1L)
                .occurredAt(LocalDateTime.now())
                .build();

        given(bidRepository.findDistinctBidderIdsByAuctionId(10L)).willReturn(new ArrayList<>(List.of(2L, 3L)));
        given(memberRepository.findAllById(List.of(2L, 3L))).willReturn(List.of(winner, otherBidder));

        // when
        consumer.handleAuctionEvents(List.of(event));

        // then
        ArgumentCaptor<List<NotificationService.NotificationSpec>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).createAll(captor.capture());

        List<NotificationService.NotificationSpec> specs = captor.getValue();
        assertThat(specs).hasSize(2);
        assertThat(specs).allMatch(s -> s.type() == NotificationType.AUCTION_END);
        assertThat(specs).anyMatch(s -> s.receiver().equals(winner));
        assertThat(specs).anyMatch(s -> s.receiver().equals(otherBidder));
    }

    @Test
    @DisplayName("AUCTION_CANCELLED - 입찰자 없으면 알림 생성 없음")
    void handleAuctionEvents_cancelledNoBidders_noNotifications() {
        // given
        AuctionEvent event = AuctionEvent.builder()
                .eventType(AuctionEvent.AUCTION_CANCELLED)
                .auctionId(10L)
                .sellerId(1L)
                .occurredAt(LocalDateTime.now())
                .build();

        given(bidRepository.findDistinctBidderIdsByAuctionId(10L)).willReturn(List.of());

        // when
        consumer.handleAuctionEvents(List.of(event));

        // then
        ArgumentCaptor<List<NotificationService.NotificationSpec>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).createAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("처리 대상 외 이벤트 타입은 무시")
    void handleAuctionEvents_unknownEventType_skipped() {
        // given
        AuctionEvent event = AuctionEvent.builder()
                .eventType(AuctionEvent.AUCTION_CREATED)
                .auctionId(10L)
                .sellerId(1L)
                .occurredAt(LocalDateTime.now())
                .build();

        // when
        consumer.handleAuctionEvents(List.of(event));

        // then
        ArgumentCaptor<List<NotificationService.NotificationSpec>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).createAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("배치 내 이벤트 하나 처리 실패 시 나머지 이벤트는 계속 처리")
    void handleAuctionEvents_oneEventFails_othersProcessed() {
        // given
        AuctionEvent failingEvent = AuctionEvent.builder()
                .eventType(AuctionEvent.AUCTION_ENDED)
                .auctionId(99L)
                .sellerId(999L)  // 존재하지 않는 판매자
                .currentPrice(500_000L)
                .winnerId(null)
                .occurredAt(LocalDateTime.now())
                .build();

        AuctionEvent validEvent = AuctionEvent.builder()
                .eventType(AuctionEvent.AUCTION_ENDED)
                .auctionId(10L)
                .sellerId(1L)
                .currentPrice(500_000L)
                .winnerId(null)
                .occurredAt(LocalDateTime.now())
                .build();

        given(memberRepository.findById(999L)).willReturn(java.util.Optional.empty());
        given(memberRepository.findById(1L)).willReturn(java.util.Optional.of(seller));

        // when
        consumer.handleAuctionEvents(List.of(failingEvent, validEvent));

        // then
        ArgumentCaptor<List<NotificationService.NotificationSpec>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).createAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).receiver()).isEqualTo(seller);
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
}