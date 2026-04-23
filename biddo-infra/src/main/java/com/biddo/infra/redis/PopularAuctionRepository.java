package com.biddo.infra.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class PopularAuctionRepository {

    private static final String KEY = "auction:popular";
    private static final long TTL_HOURS = 1;

    private final StringRedisTemplate redisTemplate;

    public void incrementScore(Long auctionId) {
        redisTemplate.opsForZSet().incrementScore(KEY, String.valueOf(auctionId), 1);
        redisTemplate.expire(KEY, TTL_HOURS, TimeUnit.HOURS);
    }

    public List<Long> getTopAuctionIds(int size) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(KEY, 0, size - 1);
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }
        return tuples.stream()
                .map(tuple -> Long.parseLong(tuple.getValue()))
                .toList();
    }

    public void remove(Long auctionId) {
        redisTemplate.opsForZSet().remove(KEY, String.valueOf(auctionId));
    }
}
