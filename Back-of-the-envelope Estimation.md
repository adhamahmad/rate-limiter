\# Phase 1 Token Bucket BOE Summary



\## Assumptions

\- \*\*Algorithm:\*\* Token Bucket (stricter than Fixed Window)

\- \*\*Redis per-key storage:\*\* hash with fields `"tokens"` (float) + `"lastRefill"` (timestamp in seconds)

\- \*\*Key size:\*\* ~20 bytes; Redis hash overhead: ~150 bytes

\- \*\*Redis throughput:\*\* 100,000 commands/sec (single-node estimate)

\- \*\*Commands per request:\*\* 2 (load + save)

\- \*\*Latency per request:\*\* ~2–5 ms



---



\## Memory Estimation

\- \*\*Memory per key:\*\* ~200 bytes



\*\*Example scaling:\*\*



| Active keys | Memory required |

|------------|----------------|

| 10,000     | ~2 MB          |

| 100,000    | ~20 MB         |

| 1,000,000  | ~200 MB        |



> Memory footprint is modest even at high scale.



---



\## Throughput / Concurrent Requests

\- \*\*Max requests/sec:\*\* Redis commands/sec ÷ commands per request = 100,000 ÷ 2 = 50,000 requests/sec

\- \*\*Memory limit:\*\* ~5 million active users, but throughput limits simultaneous requests:

&nbsp; - 50,000 users sending 1 request/sec  

&nbsp; - 250,000 users sending 1 request every 5 sec



---



\## Latency

\- Each request requires 2 Redis round-trips → ~2–5 ms per request depending on network and Redis location.



---



\## Limitations / Assumptions

\- Rules loaded in memory: require restart to update; not synchronized across nodes  

\- Timestamps use `Instant.now().toEpochMilli()`: may cause inconsistencies in distributed setups  

\- Single-node Redis; no replication or failover  

\- `load → compute → save` is non-atomic; race conditions possible under high concurrency



---



\## Scaling Considerations (Optional for Discussion)

\- Use Lua scripts to make token updates atomic  

\- Shard keys across multiple Redis instances for higher throughput  

\- Centralize rules/config for dynamic updates across nodes  

\- Consider Redis clustering or multi-region setups for high availability



