package com.biddo.infra.kafka.consumer;

import com.biddo.domain.auction.entity.Auction;
import com.biddo.infra.auction.AuctionJpaRepository;
import com.biddo.infra.elasticsearch.document.AuctionDocument;
import com.biddo.infra.elasticsearch.repository.AuctionDocumentRepository;
import com.biddo.infra.kafka.event.AuctionEvent;
import com.biddo.infra.kafka.event.BidEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionSearchConsumerTest {

    @Mock
    private AuctionJpaRepository auctionJpaRepository;

    @Mock
    private AuctionDocumentRepository auctionDocumentRepository;

    @InjectMocks
    private AuctionSearchConsumer consumer;

    // ── handleBidEvent ───────────────────────────────────────────────────────

    @Test
    @DisplayName("BID_PLACED 배치 - DB/ES 각 1회 조회 후 일괄 저장")
    void handleBidEvent_bidPlacedBatch_bulkFetchAndSave() {
        // given
        List<BidEvent> events = List.of(
                bidEvent(1L), bidEvent(2L), bidEvent(3L)
        );

        Auction a1 = auctionWithPrice(1L, 10000L, 5);
        Auction a2 = auctionWithPrice(2L, 20000L, 3);
        Auction a3 = auctionWithPrice(3L, 30000L, 7);

        AuctionDocument d1 = mock(AuctionDocument.class);
        AuctionDocument d2 = mock(AuctionDocument.class);
        AuctionDocument d3 = mock(AuctionDocument.class);
        given(d1.getId()).willReturn(1L);
        given(d2.getId()).willReturn(2L);
        given(d3.getId()).willReturn(3L);

        given(auctionJpaRepository.findAllById(anyList())).willReturn(List.of(a1, a2, a3));
        given(auctionDocumentRepository.findAllById(anyList())).willReturn(List.of(d1, d2, d3));

        // when
        consumer.handleBidEvent(events);

        // then
        verify(auctionJpaRepository, times(1)).findAllById(anyList());
        verify(auctionDocumentRepository, times(1)).findAllById(anyList());
        verify(d1).updatePrice(10000L, 5);
        verify(d2).updatePrice(20000L, 3);
        verify(d3).updatePrice(30000L, 7);
        verify(auctionDocumentRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("같은 경매 중복 이벤트 - 단일 DB/ES 조회로 처리")
    void handleBidEvent_duplicateAuctionId_deduplicates() {
        // given
        List<BidEvent> events = List.of(bidEvent(1L), bidEvent(1L), bidEvent(1L));

        Auction auction = auctionWithPrice(1L, 15000L, 3);
        AuctionDocument doc = mock(AuctionDocument.class);
        given(doc.getId()).willReturn(1L);

        given(auctionJpaRepository.findAllById(List.of(1L))).willReturn(List.of(auction));
        given(auctionDocumentRepository.findAllById(List.of(1L))).willReturn(List.of(doc));

        // when
        consumer.handleBidEvent(events);

        // then
        verify(auctionJpaRepository).findAllById(List.of(1L));
        verify(doc, times(1)).updatePrice(15000L, 3);
    }

    @Test
    @DisplayName("BID_PLACED 외 이벤트 - DB/ES 접근 없음")
    void handleBidEvent_nonBidPlacedEvent_noInteraction() {
        // given
        List<BidEvent> events = List.of(
                BidEvent.builder().eventType("OTHER_EVENT").auctionId(1L).build()
        );

        // when
        consumer.handleBidEvent(events);

        // then
        verifyNoInteractions(auctionJpaRepository, auctionDocumentRepository);
    }

    @Test
    @DisplayName("빈 배치 - DB/ES 접근 없음")
    void handleBidEvent_emptyList_noInteraction() {
        // when
        consumer.handleBidEvent(List.of());

        // then
        verifyNoInteractions(auctionJpaRepository, auctionDocumentRepository);
    }

    @Test
    @DisplayName("ES에 문서 없는 경매 - saveAll 대상에서 제외")
    void handleBidEvent_documentMissingInEs_skipsUpdate() {
        // given
        List<BidEvent> events = List.of(bidEvent(1L), bidEvent(2L));

        Auction a1 = auctionWithPrice(1L, 10000L, 1);
        Auction a2 = mock(Auction.class);
        given(a2.getId()).willReturn(2L);

        AuctionDocument d1 = mock(AuctionDocument.class);
        given(d1.getId()).willReturn(1L);

        given(auctionJpaRepository.findAllById(anyList())).willReturn(List.of(a1, a2));
        given(auctionDocumentRepository.findAllById(anyList())).willReturn(List.of(d1)); // 2번 없음

        // when
        consumer.handleBidEvent(events);

        // then
        verify(d1).updatePrice(10000L, 1);
        verify(auctionDocumentRepository).saveAll(List.of(d1));
    }

    // ── handleAuctionEvent ───────────────────────────────────────────────────

    @Test
    @DisplayName("CREATED/UPDATED/ACTIVATED 이벤트 - 배치 인덱싱")
    void handleAuctionEvent_indexEvents_bulkIndexes() {
        // given
        List<AuctionEvent> events = List.of(
                auctionEvent(1L, AuctionEvent.AUCTION_CREATED, null),
                auctionEvent(2L, AuctionEvent.AUCTION_UPDATED, null),
                auctionEvent(3L, AuctionEvent.AUCTION_ACTIVATED, null)
        );

        Auction a1 = mockAuction(1L);
        Auction a2 = mockAuction(2L);
        Auction a3 = mockAuction(3L);
        given(auctionJpaRepository.findAllByIdWithImages(anyList())).willReturn(List.of(a1, a2, a3));

        // when
        consumer.handleAuctionEvent(events);

        // then
        verify(auctionJpaRepository).findAllByIdWithImages(List.of(1L, 2L, 3L));
        verify(auctionDocumentRepository).saveAll(anyList());
        verifyNoMoreInteractions(auctionDocumentRepository);
    }

    @Test
    @DisplayName("CANCELLED 이벤트 - 배치 삭제")
    void handleAuctionEvent_cancelledEvents_bulkDeletes() {
        // given
        List<AuctionEvent> events = List.of(
                auctionEvent(1L, AuctionEvent.AUCTION_CANCELLED, null),
                auctionEvent(2L, AuctionEvent.AUCTION_CANCELLED, null)
        );

        // when
        consumer.handleAuctionEvent(events);

        // then
        verify(auctionDocumentRepository).deleteAllById(List.of(1L, 2L));
        verifyNoInteractions(auctionJpaRepository);
    }

    @Test
    @DisplayName("ENDED/SOLD 이벤트 - ES 문서 상태 일괄 업데이트")
    void handleAuctionEvent_statusUpdateEvents_bulkUpdatesStatus() {
        // given
        List<AuctionEvent> events = List.of(
                auctionEvent(1L, AuctionEvent.AUCTION_ENDED, "ENDED"),
                auctionEvent(2L, AuctionEvent.AUCTION_SOLD, "SOLD")
        );

        AuctionDocument d1 = mock(AuctionDocument.class);
        AuctionDocument d2 = mock(AuctionDocument.class);
        given(d1.getId()).willReturn(1L);
        given(d2.getId()).willReturn(2L);
        given(auctionDocumentRepository.findAllById(anyIterable())).willReturn(List.of(d1, d2));

        // when
        consumer.handleAuctionEvent(events);

        // then
        verify(d1).updateStatus("ENDED");
        verify(d2).updateStatus("SOLD");
        verify(auctionDocumentRepository).saveAll(anyList());
        verifyNoInteractions(auctionJpaRepository);
    }

    @Test
    @DisplayName("혼합 이벤트 배치 - 타입별 그룹핑 후 각각 처리")
    void handleAuctionEvent_mixedEvents_processesByGroup() {
        // given
        List<AuctionEvent> events = List.of(
                auctionEvent(1L, AuctionEvent.AUCTION_CREATED, null),
                auctionEvent(2L, AuctionEvent.AUCTION_CANCELLED, null),
                auctionEvent(3L, AuctionEvent.AUCTION_ENDED, "ENDED")
        );

        Auction a1 = mockAuction(1L);
        given(auctionJpaRepository.findAllByIdWithImages(List.of(1L))).willReturn(List.of(a1));

        AuctionDocument d3 = mock(AuctionDocument.class);
        given(d3.getId()).willReturn(3L);
        given(auctionDocumentRepository.findAllById(anyIterable())).willReturn(List.of(d3));

        // when
        consumer.handleAuctionEvent(events);

        // then
        verify(auctionJpaRepository).findAllByIdWithImages(List.of(1L));
        verify(auctionDocumentRepository).deleteAllById(List.of(2L));
        verify(d3).updateStatus("ENDED");
        verify(auctionDocumentRepository, times(2)).saveAll(anyList()); // index + statusUpdate
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private BidEvent bidEvent(Long auctionId) {
        return BidEvent.builder()
                .eventType(BidEvent.BID_PLACED)
                .auctionId(auctionId)
                .build();
    }

    private AuctionEvent auctionEvent(Long auctionId, String eventType, String status) {
        return AuctionEvent.builder()
                .auctionId(auctionId)
                .eventType(eventType)
                .status(status)
                .build();
    }

    private Auction auctionWithPrice(Long id, Long price, int bidCount) {
        Auction auction = mock(Auction.class);
        given(auction.getId()).willReturn(id);
        given(auction.getCurrentPrice()).willReturn(price);
        given(auction.getBidCount()).willReturn(bidCount);
        return auction;
    }

    private Auction mockAuction(Long id) {
        Auction auction = mock(Auction.class);

        com.biddo.domain.category.entity.Category category = mock(com.biddo.domain.category.entity.Category.class);
        com.biddo.domain.member.entity.Member seller = mock(com.biddo.domain.member.entity.Member.class);

        given(auction.getId()).willReturn(id);
        given(auction.getImages()).willReturn(List.of());
        given(auction.getStatus()).willReturn(com.biddo.domain.auction.entity.AuctionStatus.ACTIVE);
        given(auction.getCategory()).willReturn(category);
        given(auction.getSeller()).willReturn(seller);
        return auction;
    }
}