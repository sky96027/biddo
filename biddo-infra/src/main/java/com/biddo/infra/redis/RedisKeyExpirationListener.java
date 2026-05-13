package com.biddo.infra.redis;

import com.biddo.domain.auction.service.AuctionService;
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

    public RedisKeyExpirationListener(AuctionService auctionService) {
        this.auctionService = auctionService;
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
            } else if (expiredKey.startsWith(RedisAuctionLifecycle.END_KEY_PREFIX)) {
                Long auctionId = extractAuctionId(expiredKey, RedisAuctionLifecycle.END_KEY_PREFIX);
                log.info("TTL expired - ending auction: auctionId={}", auctionId);
                auctionService.endAuction(auctionId);
            }
        } catch (Exception e) {
            log.error("Failed to handle expired key: {}", expiredKey, e);
        }
    }

    private Long extractAuctionId(String key, String prefix) {
        return Long.parseLong(key.substring(prefix.length()));
    }
}