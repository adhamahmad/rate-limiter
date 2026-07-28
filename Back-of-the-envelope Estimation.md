# Phase 2 Token Bucket BOE Summary

## Assumptions

- **Algorithm:** Token Bucket
- **Redis per-key storage:** Hash with fields `"tokens"` (stored as millitokens) and `"lastRefill"` (timestamp in milliseconds)
- **Key size:** ~20 bytes; Redis hash overhead: ~150 bytes
- **Redis deployment:** Single-node Redis
- **Rate limiting logic:** Executed atomically using a Redis Lua script
- **Commands per request:** 1 (Lua script execution)
- **Latency per request:** ~1–3 ms (typical on a local network)

---

## Memory Estimation

- **Memory per key:** ~200 bytes

**Example scaling:**

| Active keys | Memory required |
|-------------|----------------:|
| 10,000      | ~2 MB |
| 100,000     | ~20 MB |
| 1,000,000   | ~200 MB |

> Memory footprint remains modest even at high scale.

---

## Throughput / Concurrent Requests

- Each request executes a single Lua script, reducing network overhead while ensuring atomic execution.
- Actual throughput depends on Redis CPU capacity, Lua script execution time, network latency, and hardware.
- Benchmarking is required to determine the maximum sustainable requests per second for a given deployment.

---

## Latency

- Each request requires a single Redis round trip, with the complete rate limiting operation executed atomically inside Redis.
- Typical latency is approximately **1–3 ms**, depending on network conditions and Redis deployment.

---

## Limitations / Assumptions

- Single-node Redis; no replication, clustering, or failover.
- Rules are loaded in application memory and require a restart to update.
- Current time is supplied by the application (`Instant.now().toEpochMilli()`), so clock skew between application instances may affect distributed deployments.
- Limited observability (no metrics, tracing, or monitoring dashboards).

---

## Scaling Considerations

- Redis Cluster or sharding for horizontal scalability.
- Dynamic rule management shared across application instances.
- Metrics and monitoring (Micrometer, Prometheus, Grafana).
- High availability through Redis replication and failover.
- Additional rate limiting algorithms such as Sliding Window and Leaky Bucket.
