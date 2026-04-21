package com.biddo.infra.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RecentSearchRepository {

    private static final String KEY_PREFIX = "search:recent:%s";
    private static final int MAX_SIZE = 10;
    private static final long TTL_DAYS = 30;

    private final StringRedisTemplate redisTemplate;

    public void save(Long memberId, String keyword) {
        String key = String.format(KEY_PREFIX, memberId);
        // 중복 제거 후 맨 앞에 추가
        redisTemplate.opsForList().remove(key, 0, keyword);
        redisTemplate.opsForList().leftPush(key, keyword);
        redisTemplate.opsForList().trim(key, 0, MAX_SIZE - 1);
        redisTemplate.expire(key, TTL_DAYS, TimeUnit.DAYS);
    }

    public List<String> findByMemberId(Long memberId) {
        String key = String.format(KEY_PREFIX, memberId);
        return redisTemplate.opsForList().range(key, 0, MAX_SIZE - 1);
    }

    public void delete(Long memberId, String keyword) {
        String key = String.format(KEY_PREFIX, memberId);
        redisTemplate.opsForList().remove(key, 0, keyword);
    }

    public void deleteAll(Long memberId) {
        String key = String.format(KEY_PREFIX, memberId);
        redisTemplate.delete(key);
    }
}