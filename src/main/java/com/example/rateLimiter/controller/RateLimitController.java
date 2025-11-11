package com.example.rateLimiter.controller;

import com.example.rateLimiter.model.RateLimitResult;
import com.example.rateLimiter.model.RequestContext;
import com.example.rateLimiter.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RateLimitController {
    private final RateLimiterService rateLimiterService;

    @GetMapping("/test")
    public ResponseEntity<String> handleRequest(    @RequestHeader(value = "X-Client-Id", required = false) String clientId,
                                                    @RequestHeader(value = "X-Forwarded-For", required = false) String clientIp) {

        RequestContext requestContext = RequestContext.of(clientId, clientIp);

        if (requestContext.isEmpty()) {
            // Both missing → treat as invalid for your system //TODO add maybe a global anonymous rule
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                 .body("Missing identifiers: userId or IP must be present");
        }

        RateLimitResult result = rateLimiterService.isRequestAllowed(requestContext);

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-RateLimit-Limit", String.valueOf(result.getLimit()));
        headers.add("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
        headers.add("X-RateLimit-Reset", String.valueOf(result.getResetTimestamp()));

        if (!result.isAllowed()) {
            headers.add("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
            return new ResponseEntity<>("Too many requests", headers, HttpStatus.TOO_MANY_REQUESTS);
        }
        return new ResponseEntity<>("Request allowed", headers, HttpStatus.OK);
    }
}
