package com.biddo.infra.redis;

import com.biddo.domain.bid.exception.BidErrorCode;
import com.biddo.domain.bid.port.out.AuctionLockPort;
import com.biddo.domain.common.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonAuctionLock implements AuctionLockPort {

    private static final String LOCK_KEY_PREFIX = "auction:lock:";
    private static final long WAIT_TIME = 3;
    private static final long LEASE_TIME = 5;

    private final RedissonClient redissonClient;
    private final MeterRegistry meterRegistry;

    @Override
    public void executeWithLock(Long auctionId, Runnable action) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + auctionId);

        Timer.Sample waitSample = Timer.start(meterRegistry);
        boolean acquired;
        try {
            acquired = lock.tryLock(WAIT_TIME, LEASE_TIME, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BidErrorCode.AUCTION_LOCK_FAILED);
        }

        if (!acquired) {
            waitSample.stop(Timer.builder("auction.lock.wait")
                    .tag("result", "timeout")
                    .register(meterRegistry));
            throw new BusinessException(BidErrorCode.AUCTION_LOCK_FAILED);
        }

        waitSample.stop(Timer.builder("auction.lock.wait")
                .tag("result", "acquired")
                .register(meterRegistry));

        Timer.Sample holdSample = Timer.start(meterRegistry);
        try {
            action.run();
        } finally {
            holdSample.stop(Timer.builder("auction.lock.hold").register(meterRegistry));
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}