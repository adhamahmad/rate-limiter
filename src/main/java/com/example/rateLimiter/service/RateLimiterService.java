package com.example.rateLimiter.service;

import java.util.List;
import java.util.stream.Collectors;

import com.example.rateLimiter.algorthim.RateLimitAlgorithm;
import com.example.rateLimiter.algorthim.RateLimitAlgorithmFactory;
import com.example.rateLimiter.config.RateLimitConfig;
import com.example.rateLimiter.model.RateLimitResult;
import com.example.rateLimiter.model.RateLimitRule;
import com.example.rateLimiter.model.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final RateLimitConfig rulesConfig;
    private final RateLimitAlgorithmFactory algorithmFactory;

    /**
     * Fetch all rules that match the current request.
     * Returns both user and IP rules that match the identifiers.
     */
    public List<RateLimitRule> getMatchingRules(RequestContext context) {
        return rulesConfig.getRules().stream()
                          .filter(rule -> matches(rule, context))
                          .collect(Collectors.toList());
    }

    /**
     * Determines if a given rule applies to this request context.
     */
    private boolean matches(RateLimitRule rule, RequestContext context) { //TODO modify to a factory and strategy pattern to support more rule types in the future
        switch (rule.getType()) {
            case USER_ID:
                return context.getUserId() != null &&
                        context.getUserId().equals(rule.getIdentifier());
            case IP:
                return context.getIpAddress() != null &&
                        context.getIpAddress().equals(rule.getIdentifier());
            default:
                return false;
        }
    }

    /**
     * Determines if the request is allowed based on all applicable rules.
     *
     */
    public RateLimitResult isRequestAllowed(RequestContext context) {
        List<RateLimitRule> matchingRules = getMatchingRules(context);

        if (matchingRules.isEmpty()) {
            return new RateLimitResult(true, 0, 0, 0,0); // No rate-limit rule applies
        }
        RateLimitResult finalResult = null;

        for (RateLimitRule rule : matchingRules) {
            // Resolve algorithm dynamically (e.g., FIXED_WINDOW, TOKEN_BUCKET)
            RateLimitAlgorithm algorithm = algorithmFactory.getAlgorithm(rule.getAlgorithm().toString());

            // Build a Redis key, e.g., "rate_limit:USER_ID:FIXED_WINDOW:12345"
            String key = String.format("rate_limit:%s:%s:%s", rule.getType(), rule.getAlgorithm().toString(),rule.getIdentifier());

            // Apply rate limiting for this rule
            RateLimitResult result = algorithm.allowRequest(key, rule);

            if (finalResult == null) {
                finalResult = result;
            } else {
                // Pick the stricter rule (e.g., lowest remaining)
                boolean allowed = finalResult.isAllowed() && result.isAllowed();
                long limit = Math.min(finalResult.getLimit(), result.getLimit());
                long remaining = Math.min(finalResult.getRemaining(), result.getRemaining());
                long resetTimestamp = Math.min(finalResult.getResetTimestamp(), result.getResetTimestamp());
                long retryAfterSeconds = Math.max(finalResult.getRetryAfterSeconds(), result.getRetryAfterSeconds());

                finalResult = new RateLimitResult(allowed, limit, remaining, resetTimestamp, retryAfterSeconds);
            }
        }

        return finalResult; // All applicable rules allow the request
    }
}

