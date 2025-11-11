package com.example.rateLimiter.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.example.rateLimiter.util.RedisUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TokenBucketState {
    private double tokens;
    private long lastRefillTimestamp;

}

