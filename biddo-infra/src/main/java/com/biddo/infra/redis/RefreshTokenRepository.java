package com.biddo.infra.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "member:%s:session";
    private static final long REFRESH_TOKEN_TTL_DAYS = 14;

    private final StringRedisTemplate redisTemplate;

    public void save(Long memberId, String refreshToken) {
        String key = String.format(KEY_PREFIX, memberId);
        redisTemplate.opsForValue().set(key, refreshToken, REFRESH_TOKEN_TTL_DAYS, TimeUnit.DAYS);
    }

    public Optional<String> findByMemberId(Long memberId) {
        String key = String.format(KEY_PREFIX, memberId);
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    public void deleteByMemberId(Long memberId) {
        String key = String.format(KEY_PREFIX, memberId);
        redisTemplate.delete(key);
    }
}
