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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionSearchConsumerTest {

    @Mock
    private AuctionJpaRepository auctionJpaRepository;

    @Mock
    private AuctionDocumentRepository auctionDocumentRepository;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @InjectMocks
    private AuctionSearchConsumer consumer;

    // ── handleBidEvent ───────────────────────────────────────────────────────

    @Test
    @DisplayName("BID_PLACED 배치 - DB 1회 조회 후 가격/입찰수 부분 업데이트")
    void handleBidEvent_bidPlacedBatch_partialUpdatesPrice() {
        // given
        List<BidEvent> events = List.of(bidEvent(1L), bidEvent(2L), bidEvent(3L));

        Auction a1 = auctionWithPrice(1L, 10000L, 5);
        Auction a2 = auctionWithPrice(2L, 20000L, 3);
        Auction a3 = auctionWithPrice(3L, 30000L, 7);
        given(auctionJpaRepository.findAllById(anyList())).willReturn(List.of(a1, a2, a3));

        // when
        consumer.handleBidEvent(events);

        // then
        verify(auctionJpaRepository, times(1)).findAllById(anyList());
        verifyNoInteractions(auctionDocumentRepository);

        ArgumentCaptor<List<UpdateQuery>> captor = ArgumentCaptor.forClass(List.class);
        verify(elasticsearchOperations).bulkUpdate(captor.capture(), any(IndexCoordinates.class));
        assertThat(captor.getValue()).hasSize(3);
    }

    @Test
    @DisplayName("같은 경매 중복 이벤트 - 단일 DB 조회 후 단일 부분 업데이트")
    void handleBidEvent_duplicateAuctionId_deduplicates() {
        // given
        List<BidEvent> events = List.of(bidEvent(1L), bidEvent(1L), bidEvent(1L));

        Auction auction = auctionWithPrice(1L, 15000L, 3);
        given(auctionJpaRepository.findAllById(List.of(1L))).willReturn(List.of(auction));

        // when
        consumer.handleBidEvent(events);

        // then
        verify(auctionJpaRepository).findAllById(List.of(1L));
        ArgumentCaptor<List<UpdateQuery>> captor = ArgumentCaptor.forClass(List.class);
        verify(elasticsearchOperations).bulkUpdate(captor.capture(), any(IndexCoordinates.class));
        assertThat(captor.getValue()).hasSize(1);
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
        verifyNoInteractions(auctionJpaRepository, auctionDocumentRepository, elasticsearchOperations);
    }

    @Test
    @DisplayName("빈 배치 - DB/ES 접근 없음")
    void handleBidEvent_emptyList_noInteraction() {
        // when
        consumer.handleBidEvent(List.of());

        // then
        verifyNoInteractions(auctionJpaRepository, auctionDocumentRepository, elasticsearchOperations);
    }

    @Test
    @DisplayName("DB에 없는 경매 - 부분 업데이트 대상에서 제외")
    void handleBidEvent_auctionMissingInDb_skipsUpdate() {
        // given
        List<BidEvent> events = List.of(bidEvent(1L), bidEvent(2L));

        Auction a1 = auctionWithPrice(1L, 10000L, 1);
        given(auctionJpaRepository.findAllById(anyList())).willReturn(List.of(a1)); // 2번 없음

        // when
        consumer.handleBidEvent(events);

        // then
        ArgumentCaptor<List<UpdateQuery>> captor = ArgumentCaptor.forClass(List.class);
        verify(elasticsearchOperations).bulkUpdate(captor.capture(), any(IndexCoordinates.class));
        assertThat(captor.getValue()).hasSize(1);
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
        verifyNoInteractions(elasticsearchOperations);
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
        verifyNoInteractions(auctionJpaRepository, elasticsearchOperations);
    }

    @Test
    @DisplayName("ENDED/SOLD 이벤트 - ES 부분 업데이트로 status만 변경")
    void handleAuctionEvent_statusUpdateEvents_partialUpdatesStatus() {
        // given
        List<AuctionEvent> events = List.of(
                auctionEvent(1L, AuctionEvent.AUCTION_ENDED, "ENDED"),
                auctionEvent(2L, AuctionEvent.AUCTION_SOLD, "SOLD")
        );

        // when
        consumer.handleAuctionEvent(events);

        // then
        verifyNoInteractions(auctionJpaRepository, auctionDocumentRepository);

        ArgumentCaptor<List<UpdateQuery>> captor = ArgumentCaptor.forClass(List.class);
        verify(elasticsearchOperations).bulkUpdate(captor.capture(), any(IndexCoordinates.class));
        assertThat(captor.getValue()).hasSize(2);
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

        // when
        consumer.handleAuctionEvent(events);

        // then
        verify(auctionJpaRepository).findAllByIdWithImages(List.of(1L));
        verify(auctionDocumentRepository).saveAll(anyList());
        verify(auctionDocumentRepository).deleteAllById(List.of(2L));
        verify(elasticsearchOperations).bulkUpdate(anyList(), any(IndexCoordinates.class));
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
