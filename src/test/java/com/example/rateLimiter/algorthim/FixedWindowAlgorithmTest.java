package com.example.rateLimiter.algorthim;

import com.example.rateLimiter.model.RateLimitResult;
import com.example.rateLimiter.model.RateLimitRule;
import com.example.rateLimiter.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FixedWindowAlgorithmTest {

    private RedisUtil redisUtil;
    private FixedWindowAlgorithm algorithm;
    private RateLimitRule rule;

    @BeforeEach
    void setUp() {
        redisUtil = mock(RedisUtil.class);
        algorithm = new FixedWindowAlgorithm(redisUtil);

        rule = new RateLimitRule();
        rule.setId("user_rule");
        rule.setLimit(3);
        rule.setWindowSeconds(10);
    }

    @Test
    void shouldAllowUntilLimitReached_thenBlock() {
        String key = "rate_limit:USER_ID:FIXED_WINDOW:12345";

        when(redisUtil.increment(key))
                .thenReturn(1L)
                .thenReturn(2L)
                .thenReturn(3L)
                .thenReturn(4L);

        // Mock TTL behavior — always return 10 seconds remaining
        when(redisUtil.getTTL(key)).thenReturn(10L);

        RateLimitResult first  = algorithm.allowRequest(key, rule);
        RateLimitResult second = algorithm.allowRequest(key, rule);
        RateLimitResult third  = algorithm.allowRequest(key, rule);
        RateLimitResult fourth = algorithm.allowRequest(key, rule);

        assertTrue(first.isAllowed());
        assertTrue(second.isAllowed());
        assertTrue(third.isAllowed());
        assertFalse(fourth.isAllowed());

        assertEquals(2, first.getRemaining());
        assertEquals(0, fourth.getRemaining()); // once limit exceeded

        verify(redisUtil, times(4)).increment(key);
        verify(redisUtil, times(1)).expire(key, 10); // only first request sets expiry
        verify(redisUtil, atLeastOnce()).getTTL(key);
    }
}
