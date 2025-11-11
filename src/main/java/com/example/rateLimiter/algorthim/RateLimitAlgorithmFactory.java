package com.example.rateLimiter.algorthim;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RateLimitAlgorithmFactory {

    private final Map<String, RateLimitAlgorithm> algorithms;


    public RateLimitAlgorithm getAlgorithm(String name) {
        return algorithms.get(name);
    }
}
