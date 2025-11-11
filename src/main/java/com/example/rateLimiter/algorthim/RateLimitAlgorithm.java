package com.example.rateLimiter.algorthim;

import com.example.rateLimiter.model.RateLimitResult;
import com.example.rateLimiter.model.RateLimitRule;

public interface RateLimitAlgorithm {
    RateLimitResult allowRequest(String key, RateLimitRule rule);
}