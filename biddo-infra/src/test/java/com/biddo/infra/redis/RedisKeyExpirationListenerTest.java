package com.biddo.infra.redis;

import com.biddo.domain.auction.service.AuctionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RedisKeyExpirationListenerTest {

    @Mock
    private AuctionService auctionService;

    private MeterRegistry meterRegistry;
    private RedisKeyExpirationListener listener;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        listener = new RedisKeyExpirationListener(auctionService, meterRegistry);
    }

    @Test
    @DisplayName("TTL 만료 - 경매 활성화 키 처리 및 메트릭 기록")
    void handleExpiredKey_startKey_activatesAuctionAndRecordsMetric() {
        // given
        String expiredKey = "auction:start:1";

        // when
        listener.handleExpiredKey(expiredKey);

        // then
        verify(auctionService).activateAuction(1L);
        verify(auctionService, never()).endAuction(1L);

        Counter counter = meterRegistry.find("auction.lifecycle.processed")
                .tag("action", "activate")
                .tag("source", "ttl")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("TTL 만료 - 경매 종료 키 처리 및 메트릭 기록")
    void handleExpiredKey_endKey_endsAuctionAndRecordsMetric() {
        // given
        String expiredKey = "auction:end:42";

        // when
        listener.handleExpiredKey(expiredKey);

        // then
        verify(auctionService).endAuction(42L);
        verify(auctionService, never()).activateAuction(42L);

        Counter counter = meterRegistry.find("auction.lifecycle.processed")
                .tag("action", "end")
                .tag("source", "ttl")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("TTL 만료 - 관련 없는 키는 무시하고 메트릭 미기록")
    void handleExpiredKey_unrelatedKey_ignoredAndNoMetric() {
        // given
        String expiredKey = "session:abc123";

        // when
        listener.handleExpiredKey(expiredKey);

        // then
        verifyNoInteractions(auctionService);

        Counter counter = meterRegistry.find("auction.lifecycle.processed").counter();
        assertThat(counter).isNull();
    }

    @Test
    @DisplayName("TTL 만료 - 예외 발생 시 전파하지 않고 메트릭 미기록")
    void handleExpiredKey_exceptionThrown_doesNotPropagateAndNoMetric() {
        // given
        String expiredKey = "auction:end:invalid";

        // when - NumberFormatException 발생하지만 catch됨
        listener.handleExpiredKey(expiredKey);

        // then - 예외가 전파되지 않으면 테스트 통과
        Counter counter = meterRegistry.find("auction.lifecycle.processed").counter();
        assertThat(counter).isNull();
    }
}