package com.biddo.infra.scheduler;

import com.biddo.domain.member.service.TrustScoreCalculator;
import com.biddo.infra.redis.SchedulerLockExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrustScoreScheduler")
class TrustScoreSchedulerTest {

    @Mock
    private TrustScoreCalculator trustScoreCalculator;

    @Mock
    private SchedulerLockExecutor schedulerLockExecutor;

    private TrustScoreScheduler scheduler;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(inv -> { inv.getArgument(2, Runnable.class).run(); return null; })
                .when(schedulerLockExecutor).tryExecuteWithLock(any(), anyLong(), any());
        scheduler = new TrustScoreScheduler(trustScoreCalculator, schedulerLockExecutor);
    }

    @Test
    @DisplayName("락 획득 성공 시 신뢰 점수를 재계산한다")
    void recalculateTrustScores_lockAcquired_callsCalculator() {
        // when
        scheduler.recalculateTrustScores();

        // then
        verify(trustScoreCalculator).recalculateAll();
    }

    @Test
    @DisplayName("락 획득 실패 시 신뢰 점수 재계산을 건너뛴다")
    void recalculateTrustScores_lockNotAcquired_skipsCalculation() {
        // given
        doNothing().when(schedulerLockExecutor).tryExecuteWithLock(any(), anyLong(), any());

        // when
        scheduler.recalculateTrustScores();

        // then
        verify(trustScoreCalculator, never()).recalculateAll();
    }
}