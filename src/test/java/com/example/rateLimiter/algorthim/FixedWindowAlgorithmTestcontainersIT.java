package com.example.rateLimiter.algorthim;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.example.rateLimiter.model.RateLimitResult;
import com.example.rateLimiter.model.RateLimitRule;
import com.example.rateLimiter.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class FixedWindowAlgorithmTestcontainersIT {

    @Container
    public static GenericContainer<?> redisContainer =
            new GenericContainer<>("redis:7.0")
                    .withExposedPorts(6379);

    private StringRedisTemplate redisTemplate;
    private RedisUtil redisUtil;
    private RedisScript<List> fixedWindowScript;
    private FixedWindowAlgorithm algorithm;

    @BeforeEach
    void setup() {
        // Get the mapped port from Testcontainers
        Integer port = redisContainer.getMappedPort(6379);

        // Create connection factory
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", port));
        connectionFactory.afterPropertiesSet();

        // Setup Redis template and utility
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisUtil = new RedisUtil(redisTemplate);

        // Lua script for fixed window rate limiting
        String luaScript = """
                local current = redis.call('INCR', KEYS[1])
                local ttl = redis.call('TTL', KEYS[1])
                if current == 1 then
                    redis.call('EXPIRE', KEYS[1], ARGV[1])
                    ttl = tonumber(ARGV[1])
                end
                return {current, ttl}
                """;
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(luaScript);
        script.setResultType(List.class);
        fixedWindowScript = script;

        algorithm = new FixedWindowAlgorithm(redisUtil, fixedWindowScript);

        // Flush DB to start fresh
        redisTemplate.getConnectionFactory().getConnection().flushDb();
    }

    @Test
    void concurrentRequestsShouldRespectLimit() throws InterruptedException, ExecutionException {
        String key = "rate_limit:USER_ID:FIXED_WINDOW:12345";
        RateLimitRule rule = new RateLimitRule();
        rule.setLimit(5);
        rule.setWindowSeconds(10);

        int threads = 20; // number of concurrent requests
        // CyclicBarrier makes all threads wait at a point, then releases them all at once
        CyclicBarrier barrier = new CyclicBarrier(threads);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CompletableFuture<RateLimitResult>[] futures = new CompletableFuture[threads];
        long fireTime = System.currentTimeMillis();

        // Submit all 20 tasks
        for (int i = 0; i < threads; i++) {
            futures[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    // All threads wait here until all 20 are ready
                    barrier.await();

                    // NOW all 20 fire at the same time
                    long requestTime = System.currentTimeMillis();
                    System.out.println("Request fired at: " + (requestTime - fireTime) + "ms" + "by " + Thread.currentThread().getName());

                    return algorithm.allowRequest(key, rule);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);  // Wait for all to finish

        // Count allowed requests
        long allowedCount = 0;
        for (CompletableFuture<RateLimitResult> future : futures) {
            if (future.get().isAllowed()) allowedCount++;
        }

        // Only first 'limit' requests should be allowed
        assertEquals(rule.getLimit(), allowedCount);

        // TTL should be set correctly
        Long ttl = redisTemplate.getExpire(key);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= rule.getWindowSeconds());
    }
}
