## Phase 1: Single-node Redis Rate Limiter

### Algorithms
- Fixed Window
- Token Bucket

### Design
- Uses Redis for state storage.
- Focuses on core logic and correctness, not concurrency.
- Timestamps stored in seconds for persistence efficiency.

### Known Limitations
- Not atomic under high concurrency (will be handled in Phase 2 with Lua scripts).
- No distributed coordination across nodes yet.
- Minimal error handling and monitoring.
- Single-node Redis: no replication or failover.
- Rules are loaded in memory;
-Token bucket timestamps use Instant.now().toEpochMilli(), which can cause inconsistencies in distributed setups.

### Next Steps
Phase 2 will add:
- Atomic Redis operations via Lua scripts.
- Concurrency safety.
- Metrics and logging.

[Phase 1 BOE Summary](Back-of-the-envelope%20Estimation.md)


