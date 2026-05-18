package com.biddo.infra.scheduler;

import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.auction.service.AuctionService;
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

    private final AuctionRepository auctionRepository;
    private final AuctionService auctionService;

    @Scheduled(fixedRate = 60_000)
    public void processAuctionLifecycle() {
        LocalDateTime now = LocalDateTime.now();

        List<Auction> toActivate = auctionRepository.findPendingAuctionsToActivate(now);
        for (Auction auction : toActivate) {
            try {
                auctionService.activateAuction(auction.getId());
                log.info("Scheduler activated auction: auctionId={}", auction.getId());
            } catch (Exception e) {
                log.error("Scheduler failed to activate auction: auctionId={}", auction.getId(), e);
            }
        }

        List<Auction> toEnd = auctionRepository.findActiveAuctionsToEnd(now);
        for (Auction auction : toEnd) {
            try {
                auctionService.endAuction(auction.getId());
                log.info("Scheduler ended auction: auctionId={}", auction.getId());
            } catch (Exception e) {
                log.error("Scheduler failed to end auction: auctionId={}", auction.getId(), e);
            }
        }

        if (!toActivate.isEmpty() || !toEnd.isEmpty()) {
            log.info("Scheduler processed: activated={}, ended={}", toActivate.size(), toEnd.size());
        }
    }
}