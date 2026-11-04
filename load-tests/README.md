# Burst load test

The burst from PRD NFR-1: 1,000 Orders as 100 concurrent requests of 10, against a throwaway Compose stack that is removed afterwards. It doesn't touch your dev database, and PostgreSQL syncs to disk as in production.

```bash
./load-tests/run.sh
```

It exits non-zero if any of these fail:

- **Speed (NFR-1):** `POST /api/orders` p99 is under `P99_TARGET_MS` (200) for the measured burst.
- **Nothing lost or duplicated (NFR-2):** the Orders in the history are exactly the accepted ones, and all are delivered within 5 minutes.
- **Stock rules (NFR-3):** `GET /api/storage` never shows a broken rule, sampled during the burst and until delivery finishes.

## Settings

| Variable | Default | Meaning |
|---|---|---|
| `WARMUP_BURSTS` | 3 | Unmeasured bursts first. `0` measures a just-started JVM. |
| `P99_TARGET_MS` | 200 | The target. |
| `LOAD_INITIAL_STOCK` | 10000000 | Starting stock per Ingredient. `5` is the real default, and sends Orders through the Farmers Market. |

The stack also sets a Maximum wait of 100000 h, so nothing is refused, and a preparation time scale of 0.001, so delivery finishes quickly. Neither affects how fast Orders are accepted.

## Results (2026-11-03, 8 CPUs, 7 GB, WSL2, dev stack also running)

| Run | p99 | Target |
|---|---|---|
| Before any fix, warm | 1230 ms | Fails |
| Before any fix, cold | 1976 ms | Fails |
| Now, warm (3 runs) | 173, 194, 211 ms | 2 of 3 pass |
| Now, cold | 540 ms | Fails |

Every run accepted all Orders, with none lost or duplicated, and stock rules held.

**With the default stock of 5, 891 of 1,000 Orders were still waiting after 5 minutes.** See RFC §10.
