package com.example.rateLimiter.algorthim;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.rateLimiter.model.RateLimitResult;
import com.example.rateLimiter.model.RateLimitRule;
import com.example.rateLimiter.model.TokenBucketState;
import com.example.rateLimiter.util.RedisUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component("TOKEN_BUCKET")
@RequiredArgsConstructor
public class TokenBucketAlgorithm implements RateLimitAlgorithm {

    private final RedisUtil redisUtil;
//    private final TokenBucketRepository repository;

    private final RedisScript<List> tokenBucketScript;
    @Override
    public RateLimitResult allowRequest(String key, RateLimitRule rule) {
        long capacity = rule.getLimit();                   // max tokens
        long windowSeconds = rule.getWindowSeconds();
        double refillRate = (double) capacity / windowSeconds; // tokens per second

        long now =  Instant.now().toEpochMilli();       // current time in milliseconds
        long ttl = windowSeconds * 2; // TTL for bucket key

        long[] result = redisUtil.executeLuaScript(
                tokenBucketScript,
                key,
                capacity,        // ARGV[1]
                refillRate,      // ARGV[2]
                now,             // ARGV[3]
                ttl              // ARGV[4]
        );

        // 5. Compute reset info
        boolean allowed = result[0] == 1;
        long remaining = result[1];

        long retryAfterSeconds = allowed ? 0 : (long) Math.ceil(1 / refillRate);
        long retryAfterMilliSeconds = retryAfterSeconds * 1000;
        long resetTimestamp = now + (allowed ? 0 : retryAfterMilliSeconds);

        return new RateLimitResult(allowed, capacity, remaining, resetTimestamp, retryAfterSeconds);
    }




}

