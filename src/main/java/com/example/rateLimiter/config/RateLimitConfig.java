package com.example.rateLimiter.config;

import java.util.List;

import com.example.rateLimiter.model.RateLimitRule;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
@Setter
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimitConfig {
    private List<RateLimitRule> rules;

}
