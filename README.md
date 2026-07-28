## Phase 1: Single-node Redis Rate Limiter

### Algorithms
- Fixed Window
- Token Bucket

### Design
- Uses Redis for state storage.
- Focuses on core logic and correctness, not concurrency.
- Timestamps stored in seconds for persistence efficiency.

## Phase 2: Atomic Redis operations via Lua scripts
- Atomic Redis operations via Lua scripts.
- Concurrency safety.

  
## Performance Results

A k6 benchmark comparing the Redis Token Bucket implementations under identical ramp-up load (10 → 200 virtual users over 6 minutes) produced the following results:

| Metric | Non-Lua | Lua | Improvement |
|--------|---------:|---------:|------------:|
| Throughput | 2,810 req/s | **6,989 req/s** | **+149%** |
| Average Latency | 29.94 ms | **11.83 ms** | **60% lower** |
| P95 Latency | 64.39 ms | **25.46 ms** | **60% lower** |

The Lua implementation executes the Token Bucket algorithm atomically within Redis, replacing multiple Redis operations with a single `EVAL` command. This reduced Redis round trips, resulting in significantly higher throughput and lower latency under load.

### Known Limitations
- No distributed coordination across nodes yet.
- Minimal error handling and monitoring.
- Single-node Redis: no replication or failover.
- Rules are loaded in memory;
-Token bucket timestamps use Instant.now().toEpochMilli(), which can cause inconsistencies in distributed setups.

### Next Steps
Phase 3 will add:
- Metrics and logging.


