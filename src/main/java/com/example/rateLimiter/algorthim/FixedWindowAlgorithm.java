package com.example.rateLimiter.algorthim;


import java.time.Instant;
import java.util.List;

import com.example.rateLimiter.model.RateLimitResult;
import com.example.rateLimiter.model.RateLimitRule;
import com.example.rateLimiter.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component("FIXED_WINDOW")
@RequiredArgsConstructor
public class FixedWindowAlgorithm implements RateLimitAlgorithm {

    private final RedisUtil redisUtil;
    private final RedisScript<List> fixedWindowScript;
    

    @Override
    public RateLimitResult allowRequest(String key, RateLimitRule rule) {
        // Get current count
        long[] result = redisUtil.executeLuaScript(fixedWindowScript, key, rule.getWindowSeconds());

        long count = result[0];
        long ttlSeconds = result[1];

        boolean allowed = count <= rule.getLimit();
        long remaining = Math.max(0, rule.getLimit() - count);
        long resetTimestamp = Instant.now().toEpochMilli() + ttlSeconds * 1000L;

        return new RateLimitResult(allowed, rule.getLimit(), remaining, resetTimestamp, ttlSeconds);
    }

    private long increment(String key, int windowSeconds) {
        // Increment counter atomically
        long count = redisUtil.increment(key);

        // If first request, set expiry for this fixed window
        if (count == 1L) {
            redisUtil.expire(key, windowSeconds);
        }
        return count;
    }
}

