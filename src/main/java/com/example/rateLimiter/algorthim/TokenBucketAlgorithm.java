package com.example.rateLimiter.algorthim;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.example.rateLimiter.model.RateLimitResult;
import com.example.rateLimiter.model.RateLimitRule;
import com.example.rateLimiter.model.TokenBucketState;
import com.example.rateLimiter.repo.TokenBucketRepository;
import com.example.rateLimiter.util.RedisUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("TOKEN_BUCKET")
@RequiredArgsConstructor
public class TokenBucketAlgorithm implements RateLimitAlgorithm {

    private final RedisUtil redisUtil;
    private final TokenBucketRepository repository;
    @Override
    public RateLimitResult allowRequest(String key, RateLimitRule rule) {
        long capacity = rule.getLimit();                   // max tokens
        long windowSeconds = rule.getWindowSeconds();
        double refillRate = (double) capacity / windowSeconds; // tokens per second

        long now =  Instant.now().toEpochMilli();       // current time in milliseconds

        // 1. Get current bucket state
        TokenBucketState state = repository.load(
                key,           // Redis key
                capacity,      // default tokens
                now / 1000 // default lastRefill in seconds
        );
        long lastRefillMillis = state.getLastRefillTimestamp() * 1000; // convert seconds → ms


        // 2. Refill tokens based on elapsed time
        long elapsedMillis = now - lastRefillMillis;
        double tokensToAdd = (elapsedMillis / 1000.0) * refillRate; // convert ms → seconds
        double newTokenCount = Math.min(capacity, state.getTokens() + tokensToAdd); // cap tokens to bucket capacity

        // 3. Consume a token if available
        boolean allowed = newTokenCount >= 1;
        if (allowed) {
            newTokenCount -= 1;
        }

        // 4. Update Redis
        long newLastRefillSeconds = now / 1000;
        repository.save(
                key,
                new TokenBucketState(newTokenCount, newLastRefillSeconds ), // store timestamp in seconds
                windowSeconds * 2 // TTL in seconds
        );

        // 5. Compute reset info
        long retryAfterSeconds = 0;
        long resetTimestamp = now; // default to now if allowed
        if (!allowed) {
            retryAfterSeconds = (long) Math.ceil(1 / refillRate);
            resetTimestamp = now + retryAfterSeconds;
        }

        long remaining = (long) Math.floor(newTokenCount);

        return new RateLimitResult(allowed, capacity, remaining, resetTimestamp, retryAfterSeconds);
    }




}

