package com.biddo.infra.redis;

import com.biddo.domain.common.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedissonAuctionLockTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    private MeterRegistry meterRegistry;
    private RedissonAuctionLock auctionLock;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        auctionLock = new RedissonAuctionLock(redissonClient, meterRegistry);
        given(redissonClient.getLock(anyString())).willReturn(rLock);
    }

    @Test
    @DisplayName("락 획득 성공 - action 실행 후 wait/hold 타이머 기록")
    void executeWithLock_acquired_recordsWaitAndHoldTimers() throws InterruptedException {
        // given
        given(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);

        // when
        auctionLock.executeWithLock(1L, () -> {});

        // then
        Timer waitTimer = meterRegistry.find("auction.lock.wait").tag("result", "acquired").timer();
        Timer holdTimer = meterRegistry.find("auction.lock.hold").timer();

        assertThat(waitTimer).isNotNull();
        assertThat(waitTimer.count()).isEqualTo(1);
        assertThat(holdTimer).isNotNull();
        assertThat(holdTimer.count()).isEqualTo(1);
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("락 획득 실패(타임아웃) - timeout 태그로 wait 타이머 기록 후 예외 발생")
    void executeWithLock_timeout_recordsTimeoutTimerAndThrows() throws InterruptedException {
        // given
        given(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).willReturn(false);

        // when & then
        assertThatThrownBy(() -> auctionLock.executeWithLock(1L, () -> {}))
                .isInstanceOf(BusinessException.class);

        Timer waitTimer = meterRegistry.find("auction.lock.wait").tag("result", "timeout").timer();
        assertThat(waitTimer).isNotNull();
        assertThat(waitTimer.count()).isEqualTo(1);

        Timer holdTimer = meterRegistry.find("auction.lock.hold").timer();
        assertThat(holdTimer).isNull();
        verify(rLock, never()).unlock();
    }

    @Test
    @DisplayName("action 실행 중 예외 발생 - hold 타이머 기록 후 락 해제")
    void executeWithLock_actionThrows_recordsHoldTimerAndUnlocks() throws InterruptedException {
        // given
        given(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);

        // when & then
        assertThatThrownBy(() -> auctionLock.executeWithLock(1L, () -> {
            throw new RuntimeException("action failed");
        })).isInstanceOf(RuntimeException.class);

        Timer holdTimer = meterRegistry.find("auction.lock.hold").timer();
        assertThat(holdTimer).isNotNull();
        assertThat(holdTimer.count()).isEqualTo(1);
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("스레드 인터럽트 - 예외 발생 및 인터럽트 상태 복원")
    void executeWithLock_interrupted_throwsAndRestoresInterruptFlag() throws InterruptedException {
        // given
        given(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .willThrow(new InterruptedException());

        // when & then
        assertThatThrownBy(() -> auctionLock.executeWithLock(1L, () -> {}))
                .isInstanceOf(BusinessException.class);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted(); // 테스트 후 인터럽트 상태 초기화
    }
}