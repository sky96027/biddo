package com.biddo.infra.scheduler;

import com.biddo.domain.auction.entity.Auction;
import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.auction.service.AuctionService;
import com.biddo.infra.redis.SchedulerLockExecutor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionLifecycleScheduler {

    private static final String LOCK_KEY = "scheduler:lock:auction-lifecycle";
    private static final long LEASE_TIME_SECONDS = 55;

    private final AuctionRepository auctionRepository;
    private final AuctionService auctionService;
    private final MeterRegistry meterRegistry;
    private final SchedulerLockExecutor schedulerLockExecutor;

    @Scheduled(fixedRate = 60_000)
    public void processAuctionLifecycle() {
        schedulerLockExecutor.tryExecuteWithLock(LOCK_KEY, LEASE_TIME_SECONDS, this::doProcess);
    }

    private void doProcess() {
        LocalDateTime now = LocalDateTime.now();

        List<Auction> toActivate = auctionRepository.findPendingAuctionsToActivate(now);
        for (Auction auction : toActivate) {
            try {
                auctionService.activateAuction(auction.getId());
                lifecycle("activate", "scheduler").increment();
                log.info("Scheduler activated auction: auctionId={}", auction.getId());
            } catch (Exception e) {
                log.error("Scheduler failed to activate auction: auctionId={}", auction.getId(), e);
            }
        }

        List<Auction> toEnd = auctionRepository.findActiveAuctionsToEnd(now);
        for (Auction auction : toEnd) {
            try {
                auctionService.endAuction(auction.getId());
                lifecycle("end", "scheduler").increment();
                log.info("Scheduler ended auction: auctionId={}", auction.getId());
            } catch (Exception e) {
                log.error("Scheduler failed to end auction: auctionId={}", auction.getId(), e);
            }
        }

        if (!toActivate.isEmpty() || !toEnd.isEmpty()) {
            log.info("Scheduler processed: activated={}, ended={}", toActivate.size(), toEnd.size());
        }
    }

    private Counter lifecycle(String action, String source) {
        return Counter.builder("auction.lifecycle.processed")
                .tag("action", action)
                .tag("source", source)
                .register(meterRegistry);
    }
}