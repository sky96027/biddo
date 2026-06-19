package com.biddo.infra.redis;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final RedisConnectionFactory connectionFactory;

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @PostConstruct
    public void enableKeyspaceNotifications() {
        try {
            connectionFactory.getConnection().serverCommands()
                    .setConfig("notify-keyspace-events", "Ex");
            log.info("Redis keyspace notifications enabled (notify-keyspace-events=Ex)");
        } catch (Exception e) {
            log.warn("Failed to enable Redis keyspace notifications - ensure Redis is configured with notify-keyspace-events=Ex", e);
        }
    }
}