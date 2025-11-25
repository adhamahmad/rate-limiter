package com.example.rateLimiter.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisScriptConfig {

    // --- Fixed Window Lua Script ---
    @Bean
    public RedisScript<List> fixedWindowScript() {
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
        return script;
    }

    // --- Token Bucket Lua Script ---
    @Bean
    public RedisScript<List> tokenBucketScript() {

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
        return script;
    }
}
