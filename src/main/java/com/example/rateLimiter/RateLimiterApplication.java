package com.example.rateLimiter;

import com.example.rateLimiter.config.RateLimitConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class RateLimiterApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext context =  SpringApplication.run(RateLimiterApplication.class, args);
		RateLimitConfig config = context.getBean(RateLimitConfig.class);
		config.getRules().forEach(rule ->
				System.out.println("Loaded rule: " + rule.getId() + " -> " + rule.getIdentifier())
		);
	}

}
