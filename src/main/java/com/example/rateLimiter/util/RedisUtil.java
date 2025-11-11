package com.example.rateLimiter.util;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisUtil {
    private final StringRedisTemplate redisTemplate;

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public Map<String, String> getHash(String key) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries == null || entries.isEmpty()) {
            return java.util.Collections.emptyMap(); // return empty map instead of null
        }

        // Convert Object,Object → String,String
        return entries.entrySet().stream()
                      .collect(java.util.stream.Collectors.toMap(
                              e -> e.getKey().toString(),
                              e -> e.getValue().toString()
                      ));
    }

    public void setHash(String key, Map<String, String> data) {
        if (data == null || data.isEmpty()) return;

        redisTemplate.opsForHash().putAll(key, data);

    }


//    public void set(String key, String value, long ttlSeconds) {
//        redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
//    }


    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    public void expire(String key, long seconds) {
        redisTemplate.expire(key, Duration.ofSeconds(seconds));
    }

    public long getTTL(String key) {
        Long ttl = redisTemplate.getExpire(key);
        return ttl != null ? ttl : 0L;
    }

}
