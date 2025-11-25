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
import static org.junit.jupiter.api.Assertions.assertFalse;

@Testcontainers
class TokenBucketAlgorithmTestcontainersIT {

    @Container
    public static GenericContainer<?> redisContainer =
            new GenericContainer<>("redis:7.0")
                    .withExposedPorts(6379);

    private StringRedisTemplate redisTemplate;
    private RedisUtil redisUtil;
    private RedisScript<List> tokenBucketScript;
    private TokenBucketAlgorithm algorithm;

    @BeforeEach
    void setup() {
        Integer port = redisContainer.getMappedPort(6379);

        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", port));
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisUtil = new RedisUtil(redisTemplate);

        String luaScript = """
            -- KEYS[1] = bucket key
            -- ARGV[1] = capacity
            -- ARGV[2] = refill_rate (tokens per second)
            -- ARGV[3] = current timestamp in milliseconds
            -- ARGV[4] = TTL in seconds
            
            local bucket = redis.call('HGETALL', KEYS[1])
            local tokens = nil
            local lastRefill = nil
            
            -- Parse HGETALL result
            if #bucket > 0 then
                for i = 1, #bucket, 2 do
                    if bucket[i] == 'tokens' then
                        tokens = tonumber(bucket[i+1])
                    elseif bucket[i] == 'lastRefill' then
                        lastRefill = tonumber(bucket[i+1])
                    end
                end
            end
            
            local now = tonumber(ARGV[3])
            local refillRate = tonumber(ARGV[2])
            local capacity = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[4])
            
            -- Initialize on first request
            if tokens == nil then
                tokens = capacity * 1000  -- millitokens
                lastRefill = now
            end
            
            -- Calculate elapsed time in milliseconds and refill tokens using integer arithmetic
            -- Formula: millitokens = (elapsedMs / 1000) * refillRate * 1000 = elapsedMs * refillRate
            local elapsedMs = now - lastRefill
            local millitokensToAdd = math.floor(elapsedMs * refillRate + 0.5)
            local newTokens = math.min(capacity * 1000, tokens + millitokensToAdd)
            
            local allowed = 0
            local remaining = math.floor(newTokens / 1000)
            
            -- Check if we have at least 1 full token (1000 millitokens)
            if newTokens >= 1000 then
                newTokens = newTokens - 1000
                allowed = 1
                remaining = math.floor(newTokens / 1000)
            end
            
            -- Update Redis with new state
            redis.call('HSET', KEYS[1], 'tokens', newTokens, 'lastRefill', now)
            redis.call('EXPIRE', KEYS[1], ttl)
            
            -- Return {allowed, tokens_remaining}
            return {allowed, remaining}
            """;

        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(luaScript);
        script.setResultType(List.class);
        tokenBucketScript = script;

        algorithm = new TokenBucketAlgorithm(redisUtil, tokenBucketScript);

        redisTemplate.getConnectionFactory().getConnection().flushDb();
    }

    @Test
    void singleRequestWithinCapacityShouldBeAllowed() {
        String key = "rate_limit:USER_ID:TOKEN_BUCKET:12345";
        RateLimitRule rule = new RateLimitRule();
        rule.setLimit(10);
        rule.setWindowSeconds(10);

        RateLimitResult result = algorithm.allowRequest(key, rule);

        assertTrue(result.isAllowed());
        assertEquals(9, result.getRemaining());
        assertEquals(10, result.getLimit());
    }

    @Test
    void multipleSequentialRequestsShouldConsumeTokens() {
        String key = "rate_limit:USER_ID:TOKEN_BUCKET:12345";
        RateLimitRule rule = new RateLimitRule();
        rule.setLimit(5);
        rule.setWindowSeconds(10);

        // Consume 5 tokens sequentially
        for (int i = 0; i < 5; i++) {
            RateLimitResult result = algorithm.allowRequest(key, rule);
            assertTrue(result.isAllowed(), "Request " + (i + 1) + " should be allowed");
            assertEquals(4 - i, result.getRemaining(), "Remaining tokens should be " + (4 - i));
        }

        // 6th request should be rejected
        RateLimitResult result = algorithm.allowRequest(key, rule);
        assertFalse(result.isAllowed(), "6th request should be rejected");
        assertEquals(0, result.getRemaining());
    }

    @Test
    void concurrentRequestsShouldRespectCapacity() throws InterruptedException, ExecutionException { // flaky
        String key = "rate_limit:USER_ID:TOKEN_BUCKET:12345";
        RateLimitRule rule = new RateLimitRule();
        rule.setLimit(10);
        rule.setWindowSeconds(10);

        int threads = 20;
        CyclicBarrier barrier = new CyclicBarrier(threads);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CompletableFuture<RateLimitResult>[] futures = new CompletableFuture[threads];
        long fireTime = System.currentTimeMillis();

        for (int i = 0; i < threads; i++) {
            futures[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    barrier.await();

                    long requestTime = System.currentTimeMillis();
                    System.out.println("Request fired at: " + (requestTime - fireTime) + "ms by " +
                            Thread.currentThread().getName());

                    return algorithm.allowRequest(key, rule);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        long allowedCount = 0;
        for (CompletableFuture<RateLimitResult> future : futures) {
            if (future.get().isAllowed()) {
                allowedCount++;
            }
        }

        // Only 'capacity' requests should be allowed
        assertEquals(rule.getLimit(), allowedCount); // here <10> but was: <9>

    }

    @Test
    void tokenRefillShouldOccurAfterElapsedTime() throws InterruptedException {
        String key = "rate_limit:USER_ID:TOKEN_BUCKET:12345";
        RateLimitRule rule = new RateLimitRule();
        rule.setLimit(5);
        rule.setWindowSeconds(10);

        // Exhaust all tokens
        for (int i = 0; i < 5; i++) {
            RateLimitResult result = algorithm.allowRequest(key, rule);
            assertTrue(result.isAllowed());
        }

        // Next request should be rejected
        RateLimitResult result = algorithm.allowRequest(key, rule);
        assertFalse(result.isAllowed());

        // Wait 2 seconds (should refill 1 token at 0.5 tokens/sec)
        Thread.sleep(2000);

        // Now request should be allowed
        result = algorithm.allowRequest(key, rule);
        assertTrue(result.isAllowed());
    }

    @Test
    void burstTrafficShouldBeHandledCorrectly() throws InterruptedException, ExecutionException {
        String key = "rate_limit:USER_ID:TOKEN_BUCKET:12345";
        RateLimitRule rule = new RateLimitRule();
        rule.setLimit(5);
        rule.setWindowSeconds(10);

        int firstBurst = 10; // More than capacity
        int secondBurst = 10; // Another burst after wait

        // First burst: 10 concurrent requests when capacity is 5
        ExecutorService executor = Executors.newFixedThreadPool(firstBurst);
        CyclicBarrier barrier = new CyclicBarrier(firstBurst);
        CompletableFuture<RateLimitResult>[] futures = new CompletableFuture[firstBurst];

        for (int i = 0; i < firstBurst; i++) {
            futures[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    barrier.await();
                    return algorithm.allowRequest(key, rule);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        long allowedInFirstBurst = 0;
        for (CompletableFuture<RateLimitResult> future : futures) {
            if (future.get().isAllowed()) {
                allowedInFirstBurst++;
            }
        }

        // Only 5 should be allowed (capacity)
        assertEquals(5, allowedInFirstBurst);

        // Wait for partial refill
        Thread.sleep(2000); // 1 token refilled at 0.5 tokens/sec

        // Second burst: 5 concurrent requests
        executor = Executors.newFixedThreadPool(secondBurst);
        CyclicBarrier barrier2 = new CyclicBarrier(secondBurst);
        futures = new CompletableFuture[secondBurst];

        for (int i = 0; i < secondBurst; i++) {
            futures[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    barrier2.await();
                    return algorithm.allowRequest(key, rule);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        long allowedInSecondBurst = 0;
        for (CompletableFuture<RateLimitResult> future : futures) {
            if (future.get().isAllowed()) {
                allowedInSecondBurst++;
            }
        }

        // Should have 1 token refilled + any partial refill
        assertTrue(allowedInSecondBurst > 0, "Should allow some requests after refill");
    }
//
    @Test
    void ttlShouldBeSetCorrectly() {
        String key = "rate_limit:USER_ID:TOKEN_BUCKET:12345";
        RateLimitRule rule = new RateLimitRule();
        rule.setLimit(5);
        rule.setWindowSeconds(10);
        long expectedTtl = rule.getWindowSeconds() * 2;

        algorithm.allowRequest(key, rule);

        Long ttl = redisTemplate.getExpire(key);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= expectedTtl);
    }

    @Test
    void resetTimestampShouldReflectRetryAfter() {
        String key = "rate_limit:USER_ID:TOKEN_BUCKET:12345";
        RateLimitRule rule = new RateLimitRule();
        rule.setLimit(1);
        rule.setWindowSeconds(10);

        // Consume the only token
        RateLimitResult result = algorithm.allowRequest(key, rule);
        assertTrue(result.isAllowed());

        // Next request should be rejected
        result = algorithm.allowRequest(key, rule);
        assertFalse(result.isAllowed());

        long before = System.currentTimeMillis();
        long resetTimestamp = result.getResetTimestamp();
        long after = System.currentTimeMillis();

        // Reset timestamp should be in the future
        assertTrue(resetTimestamp > before, "Reset timestamp should be after current time");
        assertTrue(resetTimestamp > after, "Reset timestamp should be after request time");
    }
}