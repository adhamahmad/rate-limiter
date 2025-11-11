package com.example.rateLimiter.repo;

import java.util.Map;

import com.example.rateLimiter.model.TokenBucketState;
import com.example.rateLimiter.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class TokenBucketRepository {
    private final RedisUtil redisUtil;

    public TokenBucketState load(String key, long defaultTokens, long nowMillis) {
        Map<String, String> data = redisUtil.getHash(key);
        if (data.isEmpty()) {
            return new TokenBucketState(defaultTokens, nowMillis);
        }

        double tokens = safeParseDouble(data.get("tokens"), defaultTokens);
        long lastRefill = safeParseLong(data.get("lastRefill"), nowMillis);
        return new TokenBucketState(tokens, lastRefill);
    }

    public void save(String key, TokenBucketState state, long ttlSeconds) {
        Map<String, String> data = Map.of(
                "tokens", String.valueOf(state.getTokens()),
                "lastRefill", String.valueOf(state.getLastRefillTimestamp())
        );
        redisUtil.setHash(key, data);
        redisUtil.expire(key, ttlSeconds);
    }

    private double safeParseDouble(String value, double fallback) {
        if (value == null) return fallback;
        try { return Double.parseDouble(value); } catch (NumberFormatException e) { return fallback; }
    }

    private long safeParseLong(String value, long fallback) {
        if (value == null) return fallback;
        try { return Long.parseLong(value); } catch (NumberFormatException e) { return fallback; }
    }
}

