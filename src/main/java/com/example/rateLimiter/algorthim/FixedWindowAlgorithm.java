package com.example.rateLimiter.algorthim;


import java.time.Instant;

import com.example.rateLimiter.model.RateLimitResult;
import com.example.rateLimiter.model.RateLimitRule;
import com.example.rateLimiter.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("FIXED_WINDOW")
@RequiredArgsConstructor
public class FixedWindowAlgorithm implements RateLimitAlgorithm {

    private final RedisUtil redisUtil;
    

    @Override
    public RateLimitResult allowRequest(String key, RateLimitRule rule) {
        // Get current count

        long count = increment(key, rule.getWindowSeconds());
        boolean allowed = count <= rule.getLimit();
        long remaining = Math.max(0, rule.getLimit() - count);
        long ttlSeconds = redisUtil.getTTL(key);
        long resetTimestamp = Instant.now().toEpochMilli() + ttlSeconds * 1000L;

        return new RateLimitResult(allowed, rule.getLimit(), remaining, resetTimestamp, ttlSeconds);
    }

    private long increment(String key, int windowSeconds) {
        // Increment counter atomically
        long count = redisUtil.increment(key);

        // If first request, set expiry for this fixed window
        if (count == 1L) { //TODO use lua script when moving to phase 2 to ensure atomicity and solve race conditions
            redisUtil.expire(key, windowSeconds);
        }
        return count;
    }
}

