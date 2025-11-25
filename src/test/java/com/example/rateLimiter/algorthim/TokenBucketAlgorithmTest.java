package com.example.rateLimiter.algorthim;

import java.time.Instant;
import java.util.List;

import com.example.rateLimiter.model.RateLimitResult;
import com.example.rateLimiter.model.RateLimitRule;
import com.example.rateLimiter.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TokenBucketAlgorithmTest {

    private RedisUtil redisUtil;
    private TokenBucketAlgorithm algorithm;
    private RateLimitRule rule;
    private RedisScript<List> tokenBucketScript;

    @BeforeEach
    void setUp() {
        redisUtil = mock(RedisUtil.class);
        tokenBucketScript = mock(RedisScript.class);
        algorithm = new TokenBucketAlgorithm(redisUtil, tokenBucketScript);

        rule = new RateLimitRule();
        rule.setId("user_rule");
        rule.setLimit(10);
        rule.setWindowSeconds(60);
    }

    @Test
    void shouldAllowUntilTokensExhausted_thenBlock() {
        String key = "rate_limit:USER_ID:TOKEN_BUCKET:12345";

        when(redisUtil.executeLuaScript(eq(tokenBucketScript), eq(key), anyLong(), anyDouble(), anyLong(), anyLong()))
                .thenReturn(new long[]{1, 9})   // first call - allowed, 9 remaining
                .thenReturn(new long[]{1, 8})   // second call - allowed, 8 remaining
                .thenReturn(new long[]{1, 7})   // third call - allowed, 7 remaining
                .thenReturn(new long[]{1, 6})   // fourth call - allowed, 6 remaining
                .thenReturn(new long[]{1, 5})   // fifth call - allowed, 5 remaining
                .thenReturn(new long[]{1, 4})   // sixth call - allowed, 4 remaining
                .thenReturn(new long[]{1, 3})   // seventh call - allowed, 3 remaining
                .thenReturn(new long[]{1, 2})   // eighth call - allowed, 2 remaining
                .thenReturn(new long[]{1, 1})   // ninth call - allowed, 1 remaining
                .thenReturn(new long[]{1, 0})   // tenth call - allowed, 0 remaining
                .thenReturn(new long[]{0, 0});  // eleventh call - blocked

        for (int i = 0; i < 10; i++) {
            RateLimitResult result = algorithm.allowRequest(key, rule);
            assertTrue(result.isAllowed(), "Request " + (i + 1) + " should be allowed");
            assertEquals(9 - i, result.getRemaining());
            assertEquals(0, result.getRetryAfterSeconds());
        }

        RateLimitResult blocked = algorithm.allowRequest(key, rule);
        assertFalse(blocked.isAllowed());
        assertEquals(0, blocked.getRemaining());
        assertTrue(blocked.getRetryAfterSeconds() > 0);

        verify(redisUtil, times(11)).executeLuaScript(eq(tokenBucketScript), eq(key), anyLong(), anyDouble(), anyLong(), anyLong());
    }

    @Test
    void shouldBlockWhenNoTokensAvailable() {
        String key = "rate_limit:USER_ID:TOKEN_BUCKET:12345";

        when(redisUtil.executeLuaScript(eq(tokenBucketScript), eq(key), anyLong(), anyDouble(), anyLong(), anyLong()))
                .thenReturn(new long[]{0, 0});

        RateLimitResult result = algorithm.allowRequest(key, rule);

        assertFalse(result.isAllowed());
        assertEquals(0, result.getRemaining());
        assertTrue(result.getRetryAfterSeconds() > 0);

        verify(redisUtil, times(1)).executeLuaScript(eq(tokenBucketScript), eq(key), anyLong(), anyDouble(), anyLong(), anyLong());
    }

    @Test
    void shouldRefillTokensAfterElapsedTime() {
        String key = "rate_limit:USER_ID:TOKEN_BUCKET:12345";

        when(redisUtil.executeLuaScript(eq(tokenBucketScript), eq(key), anyLong(), anyDouble(), anyLong(), anyLong()))
                .thenReturn(new long[]{1, 7});  // tokens refilled after elapsed time, allowed with 7 remaining

        RateLimitResult result = algorithm.allowRequest(key, rule);

        assertTrue(result.isAllowed());
        assertTrue(result.getRemaining() >= 5);
        assertEquals(0, result.getRetryAfterSeconds());

        verify(redisUtil, times(1)).executeLuaScript(eq(tokenBucketScript), eq(key), anyLong(), anyDouble(), anyLong(), anyLong());
    }
}