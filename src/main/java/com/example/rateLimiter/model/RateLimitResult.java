package com.example.rateLimiter.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RateLimitResult {
    private boolean allowed;
    private long limit;
    private long remaining;
    private long resetTimestamp; // epoch seconds
    private long retryAfterSeconds;
}