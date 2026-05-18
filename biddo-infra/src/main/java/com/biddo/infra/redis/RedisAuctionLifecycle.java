package com.biddo.infra.redis;

import com.biddo.domain.auction.port.out.AuctionLifecyclePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisAuctionLifecycle implements AuctionLifecyclePort {

    static final String START_KEY_PREFIX = "auction:start:";
    static final String END_KEY_PREFIX = "auction:end:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void scheduleActivation(Long auctionId, LocalDateTime startTime) {
        Duration ttl = Duration.between(LocalDateTime.now(), startTime);
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofSeconds(1);
        }
        String key = START_KEY_PREFIX + auctionId;
        redisTemplate.opsForValue().set(key, auctionId.toString(), ttl);
        log.info("Scheduled activation: auctionId={}, ttl={}s", auctionId, ttl.getSeconds());
    }

    @Override
    public void scheduleEnd(Long auctionId, LocalDateTime endTime) {
        Duration ttl = Duration.between(LocalDateTime.now(), endTime);
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofSeconds(1);
        }
        String key = END_KEY_PREFIX + auctionId;
        redisTemplate.opsForValue().set(key, auctionId.toString(), ttl);
        log.info("Scheduled end: auctionId={}, ttl={}s", auctionId, ttl.getSeconds());
    }

    @Override
    public void cancelSchedule(Long auctionId) {
        redisTemplate.delete(START_KEY_PREFIX + auctionId);
        redisTemplate.delete(END_KEY_PREFIX + auctionId);
        log.info("Cancelled schedule: auctionId={}", auctionId);
    }
}