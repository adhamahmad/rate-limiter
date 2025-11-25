package com.example.rateLimiter.algorthim;

import java.util.List;

import com.example.rateLimiter.model.RateLimitResult;
import com.example.rateLimiter.model.RateLimitRule;
import com.example.rateLimiter.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FixedWindowAlgorithmTest {

    private RedisUtil redisUtil;
    private FixedWindowAlgorithm algorithm;
    private RateLimitRule rule;
    private RedisScript<List> fixedWindowScript;

    @BeforeEach
    void setUp() {
        redisUtil = mock(RedisUtil.class);
        fixedWindowScript = mock(RedisScript.class);
        algorithm = new FixedWindowAlgorithm(redisUtil,fixedWindowScript);


        rule = new RateLimitRule();
        rule.setId("user_rule");
        rule.setLimit(3);
        rule.setWindowSeconds(10);
    }

    @Test
    void shouldAllowUntilLimitReached_thenBlock() {
        String key = "rate_limit:USER_ID:FIXED_WINDOW:12345";

        when(redisUtil.executeLuaScript(fixedWindowScript, key, rule.getWindowSeconds()))
                .thenReturn(new long[]{1, 10})  // first call
                .thenReturn(new long[]{2, 10})  // second call
                .thenReturn(new long[]{3, 10})  // third call
                .thenReturn(new long[]{4, 10}); // fourth call

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

        verify(redisUtil, times(4)).executeLuaScript(fixedWindowScript, key, rule.getWindowSeconds());
    }
}
