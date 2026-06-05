package com.biddo.infra.scheduler;

import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.auction.service.AuctionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuctionLifecycleScheduler")
class AuctionLifecycleSchedulerTest {

    @InjectMocks
    private AuctionLifecycleScheduler scheduler;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private AuctionService auctionService;

    @Test
    @DisplayName("PENDING 경매를 조회하여 activate를 호출한다")
    void activatesPendingAuctions() {
        // given
        Auction auction1 = mockAuction(1L);
        Auction auction2 = mockAuction(2L);

        given(auctionRepository.findPendingAuctionsToActivate(any())).willReturn(List.of(auction1, auction2));
        given(auctionRepository.findActiveAuctionsToEnd(any())).willReturn(List.of());

        // when
        scheduler.processAuctionLifecycle();

        // then
        verify(auctionService).activateAuction(1L);
        verify(auctionService).activateAuction(2L);
    }

    @Test
    @DisplayName("ACTIVE 경매를 조회하여 end를 호출한다")
    void endsActiveAuctions() {
        // given
        Auction auction1 = mockAuction(10L);
        Auction auction2 = mockAuction(11L);

        given(auctionRepository.findPendingAuctionsToActivate(any())).willReturn(List.of());
        given(auctionRepository.findActiveAuctionsToEnd(any())).willReturn(List.of(auction1, auction2));

        // when
        scheduler.processAuctionLifecycle();

        // then
        verify(auctionService).endAuction(10L);
        verify(auctionService).endAuction(11L);
    }

    @Test
    @DisplayName("activate 중 일부 경매가 실패해도 나머지는 정상 처리된다")
    void activateFailure_doesNotBlockOthers() {
        // given
        Auction auction1 = mockAuction(1L);
        Auction auction2 = mockAuction(2L);
        Auction auction3 = mockAuction(3L);

        given(auctionRepository.findPendingAuctionsToActivate(any())).willReturn(List.of(auction1, auction2, auction3));
        given(auctionRepository.findActiveAuctionsToEnd(any())).willReturn(List.of());

        doThrow(new RuntimeException("DB error")).when(auctionService).activateAuction(2L);

        // when
        scheduler.processAuctionLifecycle();

        // then: 1, 3번은 정상 호출됨
        verify(auctionService).activateAuction(1L);
        verify(auctionService).activateAuction(2L);
        verify(auctionService).activateAuction(3L);
    }

    @Test
    @DisplayName("end 중 일부 경매가 실패해도 나머지는 정상 처리된다")
    void endFailure_doesNotBlockOthers() {
        // given
        Auction auction1 = mockAuction(10L);
        Auction auction2 = mockAuction(11L);
        Auction auction3 = mockAuction(12L);

        given(auctionRepository.findPendingAuctionsToActivate(any())).willReturn(List.of());
        given(auctionRepository.findActiveAuctionsToEnd(any())).willReturn(List.of(auction1, auction2, auction3));

        doThrow(new RuntimeException("Lock timeout")).when(auctionService).endAuction(11L);

        // when
        scheduler.processAuctionLifecycle();

        // then: 10, 12번은 정상 호출됨
        verify(auctionService).endAuction(10L);
        verify(auctionService).endAuction(11L);
        verify(auctionService).endAuction(12L);
    }

    @Test
    @DisplayName("처리할 경매가 없으면 서비스 호출 없이 종료된다")
    void noAuctions_noServiceCalls() {
        // given
        given(auctionRepository.findPendingAuctionsToActivate(any())).willReturn(List.of());
        given(auctionRepository.findActiveAuctionsToEnd(any())).willReturn(List.of());

        // when
        scheduler.processAuctionLifecycle();

        // then
        verify(auctionService, never()).activateAuction(any());
        verify(auctionService, never()).endAuction(any());
    }

    private Auction mockAuction(Long id) {
        Auction auction = mock(Auction.class);
        given(auction.getId()).willReturn(id);
        return auction;
    }
}