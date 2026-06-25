package com.biddo.infra.kafka.consumer;

import com.biddo.domain.auction.entity.Auction;
import com.biddo.domain.auction.entity.ItemCondition;
import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.bid.port.out.BidRepository;
import com.biddo.domain.category.entity.Category;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.repository.MemberRepository;
import com.biddo.domain.notification.entity.NotificationType;
import com.biddo.domain.notification.service.NotificationService;
import com.biddo.infra.kafka.event.BidEvent;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BidEventConsumerTest {

    @InjectMocks
    private BidEventConsumer consumer;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private NotificationService notificationService;

    private Member seller;
    private Member bidder;
    private Auction auction;

    @BeforeEach
    void setUp() {
        seller = Member.builder().email("seller@test.com").password("pw").nickname("seller").build();
        setId(seller, 1L);

        bidder = Member.builder().email("bidder@test.com").password("pw").nickname("bidder").build();
        setId(bidder, 2L);

        Category category = Category.builder().name("전자기기").depth(1).sortOrder(1).build();

        auction = Auction.builder()
                .seller(seller).category(category)
                .title("아이폰 15 Pro").description("상태 좋음")
                .condition(ItemCondition.LIKE_NEW)
                .startingPrice(500_000L).buyNowPrice(null)
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusDays(3))
                .build();
        setId(auction, 10L);
    }

    @Test
    @DisplayName("BID_PLACED 이벤트 - 판매자 및 기존 입찰자에게 알림 생성")
    void handleBidEvents_bidPlaced_createsNotificationsForSellerAndOutbidders() {
        // given
        BidEvent event = BidEvent.builder()
                .eventType(BidEvent.BID_PLACED)
                .auctionId(10L)
                .bidId(100L)
                .bidderId(2L)
                .bidAmount(600_000L)
                .occurredAt(LocalDateTime.now())
                .build();

        Member outbidder = Member.builder().email("other@test.com").password("pw").nickname("other").build();
        setId(outbidder, 3L);

        given(auctionRepository.findByIdIn(List.of(10L))).willReturn(List.of(auction));
        given(bidRepository.findDistinctBidderIdsByAuctionId(10L)).willReturn(new ArrayList<>(List.of(2L, 3L)));
        given(memberRepository.findAllById(List.of(3L))).willReturn(List.of(outbidder));

        // when
        consumer.handleBidEvents(List.of(event));

        // then
        ArgumentCaptor<List<NotificationService.NotificationSpec>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).createAll(captor.capture());

        List<NotificationService.NotificationSpec> specs = captor.getValue();
        assertThat(specs).hasSize(2);
        assertThat(specs).anyMatch(s -> s.receiver().equals(seller) && s.type() == NotificationType.BID);
        assertThat(specs).anyMatch(s -> s.receiver().equals(outbidder) && s.type() == NotificationType.OUTBID);
    }

    @Test
    @DisplayName("BID_PLACED 이벤트 - 판매자가 직접 입찰 시 판매자 알림 제외")
    void handleBidEvents_sellerBidsSelf_noSellerNotification() {
        // given
        BidEvent event = BidEvent.builder()
                .eventType(BidEvent.BID_PLACED)
                .auctionId(10L)
                .bidId(100L)
                .bidderId(1L)  // seller bids on own auction
                .bidAmount(600_000L)
                .occurredAt(LocalDateTime.now())
                .build();

        given(auctionRepository.findByIdIn(List.of(10L))).willReturn(List.of(auction));
        given(bidRepository.findDistinctBidderIdsByAuctionId(10L)).willReturn(new ArrayList<>(List.of(1L)));

        // when
        consumer.handleBidEvents(List.of(event));

        // then
        ArgumentCaptor<List<NotificationService.NotificationSpec>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).createAll(captor.capture());

        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("BID_PLACED 외 이벤트 타입은 무시")
    void handleBidEvents_unknownEventType_skipped() {
        // given
        BidEvent event = BidEvent.builder()
                .eventType("BID_CANCELLED")
                .auctionId(10L)
                .bidderId(2L)
                .bidAmount(600_000L)
                .occurredAt(LocalDateTime.now())
                .build();

        given(auctionRepository.findByIdIn(List.of(10L))).willReturn(List.of(auction));

        // when
        consumer.handleBidEvents(List.of(event));

        // then
        ArgumentCaptor<List<NotificationService.NotificationSpec>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).createAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("경매를 찾을 수 없으면 해당 이벤트 건너뜀")
    void handleBidEvents_auctionNotFound_skipsEvent() {
        // given
        BidEvent event = BidEvent.builder()
                .eventType(BidEvent.BID_PLACED)
                .auctionId(999L)
                .bidderId(2L)
                .bidAmount(600_000L)
                .occurredAt(LocalDateTime.now())
                .build();

        given(auctionRepository.findByIdIn(List.of(999L))).willReturn(List.of());

        // when
        consumer.handleBidEvents(List.of(event));

        // then
        ArgumentCaptor<List<NotificationService.NotificationSpec>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).createAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("배치에 여러 이벤트가 있을 때 경매 조회는 한 번만 수행")
    void handleBidEvents_multipleSameAuction_fetchesAuctionOnce() {
        // given
        BidEvent event1 = BidEvent.builder()
                .eventType(BidEvent.BID_PLACED).auctionId(10L)
                .bidderId(2L).bidAmount(600_000L).occurredAt(LocalDateTime.now()).build();
        BidEvent event2 = BidEvent.builder()
                .eventType(BidEvent.BID_PLACED).auctionId(10L)
                .bidderId(2L).bidAmount(700_000L).occurredAt(LocalDateTime.now()).build();

        given(auctionRepository.findByIdIn(List.of(10L))).willReturn(List.of(auction));
        given(bidRepository.findDistinctBidderIdsByAuctionId(10L)).willReturn(new ArrayList<>(List.of(1L)));

        // when
        consumer.handleBidEvents(List.of(event1, event2));

        // then
        verify(auctionRepository).findByIdIn(List.of(10L));
        verify(notificationService).createAll(any());
    }

    @Test
    @DisplayName("빈 배치 처리 시 알림 생성 없음")
    void handleBidEvents_emptyBatch_noNotifications() {
        // when
        consumer.handleBidEvents(List.of());

        // then
        ArgumentCaptor<List<NotificationService.NotificationSpec>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).createAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
        verify(auctionRepository).findByIdIn(List.of());
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