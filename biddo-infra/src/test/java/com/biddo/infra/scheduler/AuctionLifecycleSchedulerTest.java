package com.biddo.infra.scheduler;

import com.biddo.domain.auction.entity.Auction;
import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.auction.service.AuctionService;
import com.biddo.infra.redis.SchedulerLockExecutor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuctionLifecycleScheduler")
class AuctionLifecycleSchedulerTest {

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private AuctionService auctionService;

    @Mock
    private SchedulerLockExecutor schedulerLockExecutor;

    private MeterRegistry meterRegistry;
    private AuctionLifecycleScheduler scheduler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        lenient().doAnswer(inv -> { inv.getArgument(2, Runnable.class).run(); return null; })
                .when(schedulerLockExecutor).tryExecuteWithLock(any(), anyLong(), any());
        scheduler = new AuctionLifecycleScheduler(auctionRepository, auctionService, meterRegistry, schedulerLockExecutor);
    }

    @Test
    @DisplayName("PENDING 경매를 조회하여 activate를 호출하고 메트릭을 기록한다")
    void processAuctionLifecycle_pendingAuctionsExist_activatesAllAndRecordsMetric() {
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

        Counter counter = meterRegistry.find("auction.lifecycle.processed")
                .tag("action", "activate")
                .tag("source", "scheduler")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("ACTIVE 경매를 조회하여 end를 호출하고 메트릭을 기록한다")
    void processAuctionLifecycle_activeAuctionsExist_endsAllAndRecordsMetric() {
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

        Counter counter = meterRegistry.find("auction.lifecycle.processed")
                .tag("action", "end")
                .tag("source", "scheduler")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("activate 중 일부 경매가 실패해도 나머지는 정상 처리되고 성공 건만 카운트된다")
    void processAuctionLifecycle_activatePartialFailure_processesRemainingAndCountsSuccessOnly() {
        // given
        Auction auction1 = mockAuction(1L);
        Auction auction2 = mockAuction(2L);
        Auction auction3 = mockAuction(3L);

        given(auctionRepository.findPendingAuctionsToActivate(any())).willReturn(List.of(auction1, auction2, auction3));
        given(auctionRepository.findActiveAuctionsToEnd(any())).willReturn(List.of());

        lenient().doThrow(new RuntimeException("DB error")).when(auctionService).activateAuction(2L);

        // when
        scheduler.processAuctionLifecycle();

        // then: 1, 3번은 정상 호출됨
        verify(auctionService).activateAuction(1L);
        verify(auctionService).activateAuction(2L);
        verify(auctionService).activateAuction(3L);

        Counter counter = meterRegistry.find("auction.lifecycle.processed")
                .tag("action", "activate")
                .tag("source", "scheduler")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("end 중 일부 경매가 실패해도 나머지는 정상 처리되고 성공 건만 카운트된다")
    void processAuctionLifecycle_endPartialFailure_processesRemainingAndCountsSuccessOnly() {
        // given
        Auction auction1 = mockAuction(10L);
        Auction auction2 = mockAuction(11L);
        Auction auction3 = mockAuction(12L);

        given(auctionRepository.findPendingAuctionsToActivate(any())).willReturn(List.of());
        given(auctionRepository.findActiveAuctionsToEnd(any())).willReturn(List.of(auction1, auction2, auction3));

        lenient().doThrow(new RuntimeException("Lock timeout")).when(auctionService).endAuction(11L);

        // when
        scheduler.processAuctionLifecycle();

        // then: 10, 12번은 정상 호출됨
        verify(auctionService).endAuction(10L);
        verify(auctionService).endAuction(11L);
        verify(auctionService).endAuction(12L);

        Counter counter = meterRegistry.find("auction.lifecycle.processed")
                .tag("action", "end")
                .tag("source", "scheduler")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("처리할 경매가 없으면 서비스 호출 없이 종료되고 메트릭 미기록")
    void processAuctionLifecycle_noAuctionsFound_doesNotCallServiceAndNoMetric() {
        // given
        given(auctionRepository.findPendingAuctionsToActivate(any())).willReturn(List.of());
        given(auctionRepository.findActiveAuctionsToEnd(any())).willReturn(List.of());

        // when
        scheduler.processAuctionLifecycle();

        // then
        verify(auctionService, never()).activateAuction(any());
        verify(auctionService, never()).endAuction(any());

        assertThat(meterRegistry.find("auction.lifecycle.processed").counter()).isNull();
    }

    @Test
    @DisplayName("락 획득 실패 시 경매 처리를 건너뛴다")
    void processAuctionLifecycle_lockNotAcquired_skipsProcessing() {
        // given: mock이 액션을 실행하지 않음
        doNothing().when(schedulerLockExecutor).tryExecuteWithLock(any(), anyLong(), any());

        // when
        scheduler.processAuctionLifecycle();

        // then
        verify(auctionRepository, never()).findPendingAuctionsToActivate(any());
        verify(auctionRepository, never()).findActiveAuctionsToEnd(any());
    }

    private Auction mockAuction(Long id) {
        Auction auction = mock(Auction.class);
        given(auction.getId()).willReturn(id);
        return auction;
    }
}