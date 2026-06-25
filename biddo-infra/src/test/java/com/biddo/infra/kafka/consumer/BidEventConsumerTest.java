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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
        BidEvent event = bidEvent(10L, 2L, 600_000L);

        Member outbidder = Member.builder().email("other@test.com").password("pw").nickname("other").build();
        setId(outbidder, 3L);

        given(auctionRepository.findByIdIn(List.of(10L))).willReturn(List.of(auction));
        given(bidRepository.findDistinctBidderIdsByAuctionIdIn(List.of(10L)))
                .willReturn(Map.of(10L, new ArrayList<>(List.of(2L, 3L))));
        given(memberRepository.findAllById(anyList())).willReturn(List.of(outbidder));

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
        BidEvent event = bidEvent(10L, 1L, 600_000L); // bidderId=sellerId=1L

        given(auctionRepository.findByIdIn(List.of(10L))).willReturn(List.of(auction));
        given(bidRepository.findDistinctBidderIdsByAuctionIdIn(List.of(10L)))
                .willReturn(Map.of(10L, new ArrayList<>(List.of(1L))));

        // when
        consumer.handleBidEvents(List.of(event));

        // then
        ArgumentCaptor<List<NotificationService.NotificationSpec>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).createAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("BID_PLACED 외 이벤트 타입은 무시 - 아무것도 호출되지 않음")
    void handleBidEvents_unknownEventType_noInteraction() {
        // given
        BidEvent event = BidEvent.builder()
                .eventType("BID_CANCELLED")
                .auctionId(10L).bidderId(2L).bidAmount(600_000L)
                .occurredAt(LocalDateTime.now()).build();

        // when
        consumer.handleBidEvents(List.of(event));

        // then
        verifyNoInteractions(auctionRepository, bidRepository, memberRepository, notificationService);
    }

    @Test
    @DisplayName("경매를 찾을 수 없으면 해당 이벤트 건너뜀")
    void handleBidEvents_auctionNotFound_skipsEvent() {
        // given
        BidEvent event = bidEvent(999L, 2L, 600_000L);

        given(auctionRepository.findByIdIn(List.of(999L))).willReturn(List.of());
        given(bidRepository.findDistinctBidderIdsByAuctionIdIn(List.of(999L))).willReturn(Map.of());

        // when
        consumer.handleBidEvents(List.of(event));

        // then
        ArgumentCaptor<List<NotificationService.NotificationSpec>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).createAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("배치에 여러 이벤트가 있을 때 경매/입찰자 조회는 각 한 번만 수행")
    void handleBidEvents_multipleSameAuction_fetchesOnce() {
        // given
        BidEvent event1 = bidEvent(10L, 2L, 600_000L);
        BidEvent event2 = bidEvent(10L, 2L, 700_000L);

        given(auctionRepository.findByIdIn(List.of(10L))).willReturn(List.of(auction));
        given(bidRepository.findDistinctBidderIdsByAuctionIdIn(List.of(10L)))
                .willReturn(Map.of(10L, new ArrayList<>(List.of(2L))));

        // when
        consumer.handleBidEvents(List.of(event1, event2));

        // then
        verify(auctionRepository, times(1)).findByIdIn(anyList());
        verify(bidRepository, times(1)).findDistinctBidderIdsByAuctionIdIn(anyList());
        verify(notificationService).createAll(any());
    }

    @Test
    @DisplayName("빈 배치 처리 시 아무것도 호출되지 않음")
    void handleBidEvents_emptyBatch_noInteraction() {
        // when
        consumer.handleBidEvents(List.of());

        // then
        verifyNoInteractions(auctionRepository, bidRepository, memberRepository, notificationService);
    }

    private BidEvent bidEvent(Long auctionId, Long bidderId, Long amount) {
        return BidEvent.builder()
                .eventType(BidEvent.BID_PLACED)
                .auctionId(auctionId).bidderId(bidderId).bidAmount(amount)
                .occurredAt(LocalDateTime.now()).build();
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