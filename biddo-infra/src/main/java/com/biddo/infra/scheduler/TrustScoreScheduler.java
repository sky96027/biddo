package com.biddo.infra.scheduler;

import com.biddo.domain.member.service.TrustScoreCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrustScoreScheduler {

    private final TrustScoreCalculator trustScoreCalculator;

    @Scheduled(cron = "0 0 4 * * *")
    public void recalculateTrustScores() {
        log.info("Starting daily trust score recalculation");
        trustScoreCalculator.recalculateAll();
        log.info("Daily trust score recalculation completed");
    }
}