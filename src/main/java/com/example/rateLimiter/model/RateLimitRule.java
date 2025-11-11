package com.example.rateLimiter.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class RateLimitRule {
    private String id;
    private RuleType type;
    private String identifier;
    private AlgorithmType algorithm;
    private int limit;
    private int windowSeconds;

}
