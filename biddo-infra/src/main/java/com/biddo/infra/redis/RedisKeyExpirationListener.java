package com.biddo.infra.redis;

import com.biddo.domain.auction.service.AuctionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RedisKeyExpirationListener {

    private final AuctionService auctionService;
    private final MeterRegistry meterRegistry;

    public RedisKeyExpirationListener(AuctionService auctionService, MeterRegistry meterRegistry) {
        this.auctionService = auctionService;
        this.meterRegistry = meterRegistry;
    }

    @Configuration
    static class RedisListenerConfig {

        @Bean
        public RedisMessageListenerContainer redisMessageListenerContainer(
                RedisConnectionFactory connectionFactory,
                RedisKeyExpirationListener listener) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            container.addMessageListener((message, pattern) -> {
                String expiredKey = new String(message.getBody());
                listener.handleExpiredKey(expiredKey);
            }, new PatternTopic("__keyevent@0__:expired"));
            return container;
        }
    }

    void handleExpiredKey(String expiredKey) {
        try {
            if (expiredKey.startsWith(RedisAuctionLifecycle.START_KEY_PREFIX)) {
                Long auctionId = extractAuctionId(expiredKey, RedisAuctionLifecycle.START_KEY_PREFIX);
                log.info("TTL expired - activating auction: auctionId={}", auctionId);
                auctionService.activateAuction(auctionId);
                lifecycle("activate", "ttl").increment();
            } else if (expiredKey.startsWith(RedisAuctionLifecycle.END_KEY_PREFIX)) {
                Long auctionId = extractAuctionId(expiredKey, RedisAuctionLifecycle.END_KEY_PREFIX);
                log.info("TTL expired - ending auction: auctionId={}", auctionId);
                auctionService.endAuction(auctionId);
                lifecycle("end", "ttl").increment();
            }
        } catch (Exception e) {
            log.error("Failed to handle expired key: {}", expiredKey, e);
        }
    }

    private Counter lifecycle(String action, String source) {
        return Counter.builder("auction.lifecycle.processed")
                .tag("action", action)
                .tag("source", source)
                .register(meterRegistry);
    }

    private Long extractAuctionId(String key, String prefix) {
        return Long.parseLong(key.substring(prefix.length()));
    }
}