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

### Known Limitations
- No distributed coordination across nodes yet.
- Minimal error handling and monitoring.
- Single-node Redis: no replication or failover.
- Rules are loaded in memory;
-Token bucket timestamps use Instant.now().toEpochMilli(), which can cause inconsistencies in distributed setups.

### Next Steps
Phase 3 will add:
- Metrics and logging.


