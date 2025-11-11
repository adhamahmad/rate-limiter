package com.example.rateLimiter.algorthim;

import java.time.Instant;

import com.example.rateLimiter.model.AlgorithmType;
import com.example.rateLimiter.model.RateLimitResult;
import com.example.rateLimiter.model.RateLimitRule;
import com.example.rateLimiter.model.TokenBucketState;
import com.example.rateLimiter.repo.TokenBucketRepository;
import com.example.rateLimiter.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TokenBucketAlgorithmTest {

    private RedisUtil redisUtil;
    private TokenBucketRepository tokenBucketRepository;
    private TokenBucketAlgorithm algorithm;
    private RateLimitRule rule;
    private String key;

    @BeforeEach
    void setUp() {
        redisUtil = mock(RedisUtil.class);
        tokenBucketRepository = mock(TokenBucketRepository.class);
        algorithm = new TokenBucketAlgorithm(redisUtil,tokenBucketRepository);
        key = "user:123";

        rule = new RateLimitRule();
        rule.setAlgorithm(AlgorithmType.TOKEN_BUCKET);
        rule.setId("user_rule");
        rule.setLimit(10);
        rule.setWindowSeconds(60);
    }

    @Test
    void testAllowRequest_WhenTokensAvailable() {

        // bucket currently full (10 tokens)
        TokenBucketState state = new TokenBucketState(10, Instant.now().getEpochSecond());
        when(tokenBucketRepository.load(anyString(), anyLong(), anyLong())).thenReturn(state);

        RateLimitResult result = algorithm.allowRequest(key, rule);

        assertTrue(result.isAllowed());
        assertEquals(9, result.getRemaining());
        assertEquals(0, result.getRetryAfterSeconds());
        verify(tokenBucketRepository, times(1)).save(eq(key), any(), anyLong());
        verify(tokenBucketRepository, times(1)).load(eq(key), anyLong(), anyLong());
    }
    @Test
    void testAllowRequest_WhenNoTokensLeft() {

        // bucket empty
        TokenBucketState state = new TokenBucketState(0, Instant.now().getEpochSecond());
        when(tokenBucketRepository.load(anyString(), anyLong(), anyLong())).thenReturn(state);

        RateLimitResult result = algorithm.allowRequest(key, rule);

        assertFalse(result.isAllowed());
        assertEquals(0, result.getRemaining());
        assertEquals(result.getRetryAfterSeconds(), 6);
        verify(tokenBucketRepository, times(1)).save(eq(key), any(), anyLong());
        verify(tokenBucketRepository, times(1)).load(eq(key), anyLong(), anyLong());
    }

    @Test
    void testRefillTokensAfterElapsedTime() {

        // last refill was 30 seconds ago, only 5 tokens left
        long thirtySecondsAgo = Instant.now().getEpochSecond() - 30;
        TokenBucketState state = new TokenBucketState(5, thirtySecondsAgo);
        when(tokenBucketRepository.load(anyString(), anyLong(), anyLong())).thenReturn(state);

        RateLimitResult result = algorithm.allowRequest(key, rule);

        assertTrue(result.isAllowed());
        assertTrue(result.getRemaining() >= 5); // should be refilled a bit
        verify(tokenBucketRepository, times(1)).save(eq(key), any(), anyLong());
        verify(tokenBucketRepository, times(1)).load(eq(key), anyLong(), anyLong());
    }

}
