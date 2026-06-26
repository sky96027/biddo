package com.biddo.infra.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Arrays;

@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.sentinel.master:}")
    private String sentinelMaster;

    @Value("${spring.data.redis.sentinel.nodes:}")
    private String sentinelNodes;

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        if (StringUtils.hasText(sentinelMaster)) {
            String[] addresses = Arrays.stream(sentinelNodes.split(","))
                    .map(node -> "redis://" + node.trim())
                    .toArray(String[]::new);
            config.useSentinelServers()
                    .setMasterName(sentinelMaster)
                    .addSentinelAddress(addresses);
        } else {
            config.useSingleServer()
                    .setAddress("redis://" + host + ":" + port);
        }
        return Redisson.create(config);
    }
}