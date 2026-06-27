package com.biddo.infra.scheduler;

import com.biddo.domain.member.service.TrustScoreCalculator;
import com.biddo.infra.redis.SchedulerLockExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrustScoreScheduler {

    private static final String LOCK_KEY = "scheduler:lock:trust-score";
    private static final long LEASE_TIME_SECONDS = 3600;

    private final TrustScoreCalculator trustScoreCalculator;
    private final SchedulerLockExecutor schedulerLockExecutor;

    @Scheduled(cron = "0 0 4 * * *")
    public void recalculateTrustScores() {
        schedulerLockExecutor.tryExecuteWithLock(LOCK_KEY, LEASE_TIME_SECONDS, () -> {
            log.info("Starting daily trust score recalculation");
            trustScoreCalculator.recalculateAll();
            log.info("Daily trust score recalculation completed");
        });
    }
}