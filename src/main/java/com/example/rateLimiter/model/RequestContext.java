package com.example.rateLimiter.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RequestContext {
    private String userId;
    private String ipAddress;

    // Optional helper to safely create a context with non-null fields
    public static RequestContext of(String userId, String ipAddress) {
        return new RequestContext(
                userId == null ? "" : userId.trim(),
                ipAddress == null ? "" : ipAddress.trim()
        );
    }

    public boolean isEmpty() {
        return userId.isEmpty() && ipAddress.isEmpty();
    }
}
