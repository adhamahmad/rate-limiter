package com.example.rateLimiter.algorthim;

import com.example.rateLimiter.model.AlgorithmType;
import com.example.rateLimiter.model.RateLimitRule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class RateLimitAlgorithmFactoryTest {

    @Autowired
    private RateLimitAlgorithmFactory factory;

    @Test
    void shouldReturnFixedWindowAlgorithm_whenRuleAlgorithmIsFixedWindow() {
        RateLimitRule rule = new RateLimitRule();
        rule.setAlgorithm(AlgorithmType.FIXED_WINDOW);

        RateLimitAlgorithm algorithm = factory.getAlgorithm(rule.getAlgorithm().toString());

        assertTrue(algorithm instanceof FixedWindowAlgorithm);
    }

    @Test
    void shouldReturnTokenBucketAlgorithm_whenRuleTypeIsTokenBucket() {
        RateLimitRule rule = new RateLimitRule();
        rule.setAlgorithm(AlgorithmType.TOKEN_BUCKET);

        RateLimitAlgorithm algorithm = factory.getAlgorithm(rule.getAlgorithm().toString());

        assertTrue(algorithm instanceof TokenBucketAlgorithm);
    }
}
