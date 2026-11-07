# RFC 0001 — food4good architecture

**Status:** Draft · **Date:** 2026-11-01 · **Requirements:** [PRD](../prd.md) · **Vocabulary:** [GLOSSARY.md](../../GLOSSARY.md) · **Decisions:** [ADR-0001](../adr/0001-single-writer-for-stock.md), [ADR-0002](../adr/0002-postgres-only-no-broker.md), [ADR-0003](../adr/0003-jdbcclient-and-flyway-over-jpa.md), [ADR-0004](../adr/0004-clean-architecture-without-spring-modulith.md)

## 1. Summary

One Spring Boot application with two clean-architecture modules (`kitchen` and `storage`) and a `shared` module over a single PostgreSQL database. PostgreSQL is both the system of record and the work queue. Taking Orders is synchronous and cheap. Everything after that (reserving, buying, cooking) is done by background workers that claim rows with `FOR UPDATE SKIP LOCKED`. Stock is changed by exactly one writer. A Vue dashboard receives live updates over SSE. Traces go to Honeycomb over OTLP.

## 2. Stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 25, virtual threads on |
| Framework | Spring Boot 4.x |
| Build | Gradle, Kotlin DSL |
| Database | PostgreSQL 17 |
| Persistence | Spring `JdbcClient`, hand-written SQL; Flyway migrations |
| Farmers Market client | `@HttpExchange` interface on `RestClient`, JDK `HttpClient` underneath |
| Live updates | Server-Sent Events (`SseEmitter`) |
| Observability | OpenTelemetry via Spring Boot's OTel support → OTLP → Honeycomb |
| Frontend | Node 26 LTS, npm, Vue 3, Vite, TypeScript, PrimeVue 4.5.5 (styled, Aura), Pinia Colada; oxlint + oxfmt |
| Market stub | WireMock with a response template |
| Local runtime | Docker Compose, with an nginx gateway as the entry point |
| Tests | JUnit 5, Testcontainers (PostgreSQL, WireMock), ArchUnit |
| Dashboard tests | Playwright |
| Load test | k6 |

## 3. Architecture overview

```mermaid
flowchart LR
  UI[Vue dashboard] -- REST --> GW[nginx gateway]
  GW -- SSE --> UI
  GW --> API
  GW --> FE[frontend container]
  subgraph App[Spring Boot app]
    API[web adapter]
    K[kitchen module]
    S[storage module]
    API --> K
    K -- inbound port --> S
    S -- inbound port --> K
  end
  K & S --> DB[(PostgreSQL)]
  S -- HTTP --> FM[Farmers Market / WireMock]
  App -. OTLP .-> HC[Honeycomb]
```

### 3.1 Modules and layering

Every module has the same layout ([ADR-0004](../adr/0004-clean-architecture-without-spring-modulith.md)):

```
com.food4good.<module>
├── domain                   plain Java, no Spring
│   ├── model                entities, value objects, invariants
│   ├── ports
│   │   ├── in               use cases the module offers (ListRecipesUseCase)
│   │   └── out              what the module needs (RecipeRepositoryPort)
│   └── service              use case implementations (ListRecipesService)
└── infrastructure
    ├── adapters
    │   ├── in/rest          controllers and DTOs
    │   ├── in/worker        background workers
    │   └── out/persistence  JDBC repositories (out/market: Farmers Market client)
    └── configuration        bean wiring, properties
```

`com.food4good.shared` holds what every module uses, such as `Ingredient`, in the same layout.

**Cross-module rule.** Modules call each other only through the other module's `domain/ports/in` or through `shared`. An ArchUnit test enforces this, and that `domain` stays free of Spring and of `infrastructure`.

Cross-module calls are **synchronous and run inside the caller's transaction**. All modules share one database, so calling another module's port inside our transaction is atomic and needs no outbox. Asynchrony exists only where real waiting happens: the stock single writer, market calls and cooking time. Each has its own durable queue.

`domain` is plain Java, so services get their transactions through `TransactionPort` in `shared`.

The stock writer, the Kitchen scheduler and the market buyer share `SingleInstanceWorker` in `shared`: an advisory lock on one dedicated connection, waking on `NOTIFY` with a 1 s poll as backup. Each holds a connection, so the pool is 13: the default 10 plus these three. Setting `FOOD4GOOD_KITCHEN_COOKS=0` stops Orders at ready, which tests use to observe Reservations before stock is consumed. With zero Cooks, capacity and estimates assume one, since a Kitchen with no capacity could never accept the Orders that setting holds.

| Inbound port | Offered by | Used by | Effect |
|---|---|---|---|
| `RequestIngredientsUseCase.request(requests)` | storage | kitchen | Enqueues one `REQUEST_INGREDIENTS` storage command holding every request |
| `ConsumeReservationUseCase.consume(orderId)` | storage | kitchen | Enqueues a `CONSUME` storage command |
| `MarkOrderReadyUseCase.markReady(orderId)` | kitchen | storage | Order → **ready** (called when a Reservation completes) |

## 4. Data model

PostgreSQL enforces the stock invariants (PRD NFR-3), not just the application code.

```sql
CREATE TABLE recipe (
  id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name                     text NOT NULL UNIQUE CHECK (btrim(name) <> ''),
  preparation_time_seconds int  NOT NULL CHECK (preparation_time_seconds > 0)
);

CREATE TABLE recipe_ingredient (
  recipe_id  uuid NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,
  ingredient text NOT NULL CHECK (ingredient IN ('tomato', 'lemon', '...')),
  quantity   int  NOT NULL CHECK (quantity > 0),
  PRIMARY KEY (recipe_id, ingredient)
);

CREATE TABLE ingredient_stock (
  ingredient  text PRIMARY KEY,
  on_hand     int  NOT NULL CHECK (on_hand  >= 0),
  reserved    int  NOT NULL CHECK (reserved >= 0),
  CHECK (reserved <= on_hand)
);

CREATE TABLE orders (
  id             uuid PRIMARY KEY,
  recipe_id      uuid        NOT NULL,          -- no foreign key: too costly per Order, and Recipes can't be deleted
  recipe_name    text        NOT NULL,          -- as accepted; a later edit does not change it
  preparation_ms bigint      NOT NULL,          -- scaled preparation time, frozen at acceptance
  status         text        NOT NULL CHECK (status IN ('WAITING_FOR_INGREDIENTS','READY','PREPARING','DELIVERED')),
  accepted_at    timestamptz NOT NULL,
  ready_at       timestamptz,
  preparing_at   timestamptz,
  prepare_until  timestamptz,
  delivered_at   timestamptz,
  traceparent    text
);
CREATE INDEX orders_ready_idx     ON orders (ready_at)      WHERE status = 'READY';
CREATE INDEX orders_preparing_idx ON orders (prepare_until) WHERE status = 'PREPARING';
CREATE INDEX orders_status_history_idx ON orders (status, accepted_at DESC, id DESC);
CREATE INDEX orders_history_idx   ON orders (accepted_at DESC, id);

CREATE TABLE reservation_line (
  order_id    uuid NOT NULL REFERENCES orders(id),
  ingredient  text NOT NULL REFERENCES ingredient_stock(ingredient),
  needed      int  NOT NULL CHECK (needed > 0),
  reserved    int  NOT NULL DEFAULT 0 CHECK (reserved >= 0 AND reserved <= needed),
  consumed    boolean NOT NULL DEFAULT false,
  PRIMARY KEY (order_id, ingredient)
);

CREATE TABLE purchase (
  id             bigserial PRIMARY KEY,
  ingredient     text   NOT NULL,
  quantity       int    NOT NULL CHECK (quantity > 0),
  market_job_id  bigint NOT NULL,
  attempt        int    NOT NULL,
  purchased_at   timestamptz NOT NULL,
  UNIQUE (market_job_id, attempt)               -- idempotency
);

CREATE TABLE storage_command (
  id           bigserial PRIMARY KEY,           -- processing order
  type         text  NOT NULL CHECK (type IN ('REQUEST_INGREDIENTS','RESERVE','ADD_PURCHASE','CONSUME')),
  payload      jsonb NOT NULL,
  dedupe_key   text  NOT NULL UNIQUE,           -- e.g. RESERVE:<orderId>
  status       text  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','DONE','FAILED')),
  attempts     int   NOT NULL DEFAULT 0,
  last_error   text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  traceparent  text
);
CREATE INDEX storage_command_pending_idx ON storage_command (id) WHERE status = 'PENDING';

CREATE TABLE market_job (
  id               bigserial PRIMARY KEY,
  order_id         uuid NOT NULL REFERENCES orders(id),
  ingredient       text NOT NULL,
  remaining        int  NOT NULL CHECK (remaining >= 0),
  status           text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','IN_FLIGHT','DONE','FAILED')),
  attempt          int  NOT NULL DEFAULT 0,       -- total calls made
  zero_streak      int  NOT NULL DEFAULT 0,       -- consecutive zero sales; drives backoff
  error_attempts   int  NOT NULL DEFAULT 0,       -- defects, not zero sales
  next_attempt_at  timestamptz NOT NULL DEFAULT now(),
  lease_until      timestamptz,
  traceparent      text,
  UNIQUE (order_id, ingredient)
);
CREATE INDEX market_job_due_idx ON market_job (next_attempt_at) WHERE status = 'PENDING';
```

Recipes live in their own tables, and the starting six are inserted once by a migration (PRD §6). Starting stock is also inserted once by a migration, from `FOOD4GOOD_STORAGE_INITIAL_STOCK`, so a restart never refills Food Storage. Editing a Recipe never touches accepted Orders: `orders` keeps the Recipe's id and name, `preparation_ms` freezes the scaled preparation time, and `reservation_line` holds the quantities (PRD FR-8.3). Cooks and the Maximum wait are configuration (PRD §7).

## 5. Flows

### 5.1 Place Orders (synchronous, `POST /api/orders`)

```mermaid
sequenceDiagram
  participant UI
  participant API as kitchen: PlaceOrders
  participant DB
  UI->>API: POST /api/orders {quantity}
  API->>DB: BEGIN; pg_advisory_xact_lock(ADMISSION)
  API->>DB: SELECT backlog work (open orders)
  loop until quantity or capacity reached
    API->>API: draw Recipe uniformly; fits? (backlog + prep ≤ maxWait × cooks)
  end
  API->>DB: INSERT orders, one storage_command(REQUEST_INGREDIENTS) — batched
  API->>DB: COMMIT (+ NOTIFY storage)
  API-->>UI: {requested, accepted, orderIds, estimatedWait}
```

**Admission is serialized** by a transaction-scoped advisory lock. Two concurrent requests must not both see the same free capacity. The backlog work is

```
Σ preparation_ms                          over WAITING_FOR_INGREDIENTS and READY
+ Σ greatest(prepare_until − now, 0) over PREPARING
```

which `orders_status_history_idx` serves. Capacity is `maxWait × cooks`, and the estimated wait is `backlogWork ÷ cooks`. 

**Requests are admitted in groups.** One worker takes every request that is waiting and admits them together in one transaction, in arrival order, so a burst shares a few commits instead of queueing for one each. This adds no delay: a lone request goes straight through. The lock remains so another instance can't overbook. If the transaction fails, every request in that group gets an error and no Order is created.

To keep that transaction small, it writes the Orders and **one** storage command holding all their Ingredient Requests. The stock writer expands that command into Reservation lines and one `RESERVE` per Order. The requests are durable from the moment the Orders are, so a crash can't lose them. Reservation lines appearing moments later changes nothing visible, because an Order's Reservation is only ever read by the stock writer.

### 5.2 Reserve (storage single writer)

The **storage worker** is one virtual thread holding a session-level `pg_try_advisory_lock(STORAGE_WRITER)`. A second instance's worker fails to take the lock and stays idle (ADR-0001). The worker loop:

1. Wake on `NOTIFY storage` or after a 1 s fallback poll.
2. `SELECT … FROM storage_command WHERE status='PENDING' ORDER BY id LIMIT 50`. There is no `SKIP LOCKED`, because there is only one writer.
3. Apply each command in **its own transaction** together with `status='DONE'`. A defect increments `attempts`. At 5 the command becomes `FAILED` and is logged, and the worker moves on (NFR-6).

| Command | Effect (one transaction) |
|---|---|
| `REQUEST_INGREDIENTS(requests)` | Inserts the Reservation lines of every Order in the group, and enqueues one `RESERVE` per Order, so one bad Order can't fail the group. |
| `RESERVE(orderId)` | For each line: `take = min(needed, on_hand − reserved)`; `reserved += take` on stock and line. For every line still short, insert `market_job(remaining = needed − reserved)`. If nothing is short, call `MarkOrderReadyUseCase.markReady`. |
| `ADD_PURCHASE(jobId, attempt, qty)` | Insert `purchase` (the unique key makes it idempotent). `on_hand += qty`. `give = min(qty, line.needed − line.reserved)`; `reserved += give` on stock and line. Surplus `qty − give` stays available. If every line is full, call `MarkOrderReadyUseCase.markReady`. |
| `CONSUME(orderId)` | For each line not yet consumed: `on_hand −= reserved`, `stock.reserved −= reserved`, `consumed = true`. |

Each command is a few row updates, so even a 1,000-Order burst (1,000 `RESERVE`s) drains in seconds.

### 5.2b Repair stuck Orders (reconciler)

Between commands, every `FOOD4GOOD_STORAGE_RECONCILE_INTERVAL` (30 s), the stock writer looks for Orders that nothing is working on anymore: waiting for ingredients, short of something, with no pending storage command and no live market job. This only happens after a defect, for example a command or market job that failed 5 times.

- **Repair:** queue the Order's `RESERVE` again. That reserves what's available, including surplus, and brings back its market jobs, even ones that finished or failed. If the Order has no Reservation lines at all, its `REQUEST_INGREDIENTS` is queued again from the command it was accepted with.
- **No races:** it runs in the stock writer, so it never overlaps a stock change. The market buyer records a sale and its `ADD_PURCHASE` in one transaction, so a job is never between states.
- **Limit:** 3 repairs per Order, each logged. After that, it's logged once and the Order stays waiting.
- **Trade-off:** after a failed `ADD_PURCHASE`, the units sold never reached stock. The repair buys them again, and the original sale stays lost.

### 5.3 Buy from the Farmers Market (market buyer)

One market buyer runs across all instances (`SingleInstanceWorker`), which makes the limit on concurrent calls a limit for the whole system. It hands each due job to its own virtual thread.

```mermaid
sequenceDiagram
  participant B as storage: market buyer
  participant DB
  participant FM as Farmers Market
  B->>DB: claim due jobs: UPDATE … SET status='IN_FLIGHT', lease_until=now()+lease<br/>WHERE id IN (SELECT … WHERE status='PENDING' AND next_attempt_at<=now()<br/>ORDER BY next_attempt_at FOR UPDATE SKIP LOCKED LIMIT free_slots)
  B->>FM: GET /buy?ingredient=cheese (1 s connect / 2 s read)
  FM-->>B: {quantitySold: n}  (timeout/5xx ⇒ n = 0)
  alt n > 0
    B->>DB: INSERT storage_command(ADD_PURCHASE, jobId, attempt, n); remaining −= n;<br/>zero_streak = 0; DONE if remaining = 0 else PENDING, next_attempt_at = now()
  else n = 0
    B->>DB: zero_streak += 1; PENDING; next_attempt_at = now() + backoff(zero_streak)
  end
```

- **Concurrency.** A semaphore of `MAX_CONCURRENT_MARKET_CALLS` (20) limits in-flight calls. Each call runs on its own virtual thread.
- **No connection or lock is held during the HTTP call.** The job is claimed and committed first, then the call is made, then the result is written in a second transaction.
- **Crash recovery.** A job left `IN_FLIGHT` past `lease_until` is reclaimed by the next claim query, which `RestartTest` checks. The sale from the lost call may have happened, which is the accepted gap in the PRD (§3).
- **Client.** `FarmersMarketPort.buy(ingredient)` is an outbound port of Food Storage. It makes one HTTP call and returns the units sold, 0 on any failure.
- **Backoff.** `min(250 ms × 2^(zero_streak−1), 5 s)` with ±20 % jitter. A sale resets it.
- One call is one attempt, because the market accepts no quantity.

### 5.4 Cook (kitchen scheduler)

One kitchen scheduler loop, holding `pg_try_advisory_lock(KITCHEN)`, wakes on `NOTIFY kitchen` (when an Order becomes ready), on the earliest `prepare_until`, or after a 1 s fallback poll. Each tick has two steps:

1. **Deliver.** `UPDATE orders SET status='DELIVERED', delivered_at=now() WHERE status='PREPARING' AND prepare_until <= now()`.
2. **Start.** `free = cooks − count(PREPARING)`. Claim up to `free` READY Orders `ORDER BY ready_at FOR UPDATE SKIP LOCKED`. Set them `PREPARING`, `preparing_at=now()`, `prepare_until=now()+preparation_ms`, and call `ConsumeReservationUseCase.consume(orderId)` in the same transaction.

No thread sleeps through a preparation and no transaction stays open. Because `prepare_until` is stored, a restart resumes cooking exactly where it stopped (NFR-2).

`CONSUME` runs asynchronously, so for a moment an Order is **preparing** while its units still show as reserved. The invariants still hold, and on-hand stock drops by the time the next storage command is processed.

### 5.5 Order lifecycle

```mermaid
stateDiagram-v2
  [*] --> WAITING_FOR_INGREDIENTS: accepted (5.1)
  WAITING_FOR_INGREDIENTS --> READY: Reservation complete (5.2)
  READY --> PREPARING: Cook free, earliest ready_at (5.4)
  PREPARING --> DELIVERED: prepare_until reached (5.4)
```

Every transition is a conditional `UPDATE … WHERE id = ? AND status = <expected>`, so a replayed command is a no-op.

## 6. API

All endpoints are under `/api`. JSON uses camelCase. The OpenAPI document is generated from code and committed under `docs/rfc/openapi.yaml`.

| Method & path | Purpose |
|---|---|
| `POST /orders` `{quantity}` | → `200 {requested, accepted, orderIds[], estimatedWaitSeconds}`. `400` if quantity < 1 or > 10,000. Accepting 0 is still `200`. |
| `GET /orders?status=&cursor=&limit=` | Order history, newest first, keyset-paginated on `(accepted_at, id)`: `{items, nextCursor}`. `limit` is 1–200, default 50. `nextCursor` is null on the last page. |
| `GET /kitchen` | `{cooks, busyCooks, ordersWaitingForIngredients, ordersReady, ordersPreparing, maxWaitSeconds, backlogSeconds, estimatedCapacity}` |
| `GET /storage` | `[{ingredient, onHand, reserved, available}]` |
| `GET /purchases?cursor=&limit=` | Purchase history, newest first: `{items, nextCursor}`. `limit` is 1–200, default 50. |
| `GET /recipes` | Recipes with id, Ingredients, quantities and preparation times, ordered by name |
| `POST /recipes` | Creates a Recipe: `201` with its location. `400` if invalid, `409` if the name is taken. |
| `PUT /recipes/{id}` | Replaces a Recipe's name, Ingredients and preparation time. `400`, `404`, `409`. |
| `GET /events` | SSE stream (§7) |

Errors are RFC 9457 problem details, with the message for the Manager in `detail`.

`estimatedCapacity` = ⌊(maxWait × cooks − backlogWork) ÷ avgPrep⌋, never below 0 (PRD FR-2).

## 7. Live updates (SSE)

Services announce what changed through `OrderChangesPort` and `StockChangesPort`. Inside a transaction, an announcement only takes effect after commit, so a client never sees a change that rolls back.

To avoid flooding the browser during a burst, `LiveEventsHub` **coalesces**: every 250 ms it reads the current state of whatever changed and sends one batch per event type. Each client has its own bounded queue and thread, so a slow client is dropped rather than holding up the others; it reconnects and catches up.

| SSE event | Payload |
|---|---|
| `orders` | `[{id, recipe, status, ...timestamps}]` changed since the last batch |
| `storage` | full stock snapshot (10 rows) |
| `purchases` | Purchases created |
| `kitchen` | `/kitchen` snapshot |

Each batch carries a monotonic `id`. A client that reconnects refetches all queries instead of trying to replay, which is simpler and good enough with one instance (PRD FR-7.2).

**With more than one instance**, a client is connected to one instance and would miss events committed on another. The fix is to publish events through `NOTIFY f4g_events` and have every instance `LISTEN` and fan out. This is deferred until the load test calls for more instances.

## 8. Frontend

- **Single dashboard** with PrimeVue panels matching PRD FR-7: `DataTable` with virtual scrolling for histories, `Tag` for statuses, `InputNumber` plus `Button` for placing Orders, and `Toast` for request results.
- **Server state** lives in Pinia Colada: one query per endpoint, with keys `['orders', filters]`, `['storage']`, `['purchases']`, `['kitchen']` and `['recipes']`. `PlaceOrders` is a mutation.
- **`useLiveEvents()`** opens a single `EventSource`, patches changed rows into the cache with `queryCache.setQueryData`, and invalidates every query on `open`, which covers reconnects. When an Order joins or leaves a page, that page is refetched instead of patched, because its cursor would otherwise skip Orders.
- **Tooling:** oxlint and oxfmt, and `vue-tsc` for type checking.
- **PrimeVue stays on 4.5.5 with `@primeuix/themes` 2.0.3, pinned exactly.** These are the last MIT releases. PrimeVue 5 and themes 3 use the commercial PrimeUI license and need a license key, so do not upgrade without a licensing decision.

## 9. Observability

- **Setup.** The OTel SDK is configured by Spring Boot, with Micrometer Observation bridged to OTel. OTLP/HTTP goes to `api.honeycomb.io` with the `x-honeycomb-team` header. When `HONEYCOMB_API_KEY` is unset, the exporters are disabled and logs stay local.
- **Spans.**
  - One span per use case (`PlaceOrders`, `Reserve`, `AddPurchase`, `Consume`, `StartCooking`, `Deliver`) and one per market call. The market-call span comes from `RestClient` observation.
  - Attributes: `order.id`, `recipe`, `ingredient`, `market.quantity_sold`, `market.attempt` and `admission.accepted`.
- **Across async hops.** Every queued row (`orders`, `storage_command`, `market_job`) stores the creating span's W3C `traceparent`. Workers start a new root span with a **span link** to it, and copy `order.id`, so one Order's journey can be queried in Honeycomb without minutes-long open traces.
- **Metrics.**
  - Gauges: `kitchen.backlog.seconds`, `kitchen.cooks.busy`, `storage.commands.pending`, `market.jobs.pending`.
  - Counters: `market.calls{result=sold|zero|error}`, `jobs.failed{queue}`.
- **Logs** are structured JSON with trace and span IDs.

## 10. Risks and known gaps

| Risk | Mitigation |
|---|---|
| **NFR-1 holds for warm bursts, not cold ones.** Measured with `load-tests/`: warm p99 122–177 ms over 5 runs, cold 446 ms, against 200 ms. Before group admission, warm p99 was 1230 ms. | Group admission and small transactions (§5.1), and a single-statement Order insert without the Recipe foreign key. A capacity row updated conditionally would not have helped: the row stays locked until commit, so requests would queue the same way. Cold is a just-started JVM, so it only affects the first burst after a restart. |
| **PgBouncer doesn't fit today.** Considered for burst load. One instance with one admission worker has no shortage of connections, and transaction pooling breaks the session advisory locks and `LISTEN` the workers rely on. | Revisit if several instances make total connections a limit, with workers connecting directly. |
| **Buying is slow from low stock.** With 5 units each, 891 of 1,000 Orders were still waiting after 5 minutes. Each Order buys separately through at most 20 concurrent calls, and surplus doesn't reach Orders already waiting (§12). | Not fixed. Starting with more stock avoids it, and surplus top-up is the likely fix. |
| Market non-idempotency: a lost response after a sale. | Accepted gap (PRD §3). Units sold by a call whose response was lost never reach stock. |
| Waiting Reservations do not benefit from another Order's surplus: an Order short of cheese keeps buying even after surplus cheese lands in stock. | Matches the agreed rule. Possible v2: `ADD_PURCHASE` surplus tops up waiting lines in FIFO order. See §12. |
| Single storage writer is a throughput ceiling. | Each command takes microseconds to milliseconds. Fallback is conditional atomic updates (ADR-0001). |
| SSE with multiple instances. | `LISTEN/NOTIFY` fan-out (§7), deferred. |

## 11. Testing and verification

**One seam: the public HTTP API (REST + SSE).** Behaviour tests boot the whole app against Testcontainers PostgreSQL and a WireMock Farmers Market, act only through `/api`, and assert only on what the API returns. There are no in-memory fakes or test-only hooks in production code. Configuration provides the control instead:

| Control | How |
|---|---|
| Random Recipe choice | Edit all the Recipes through `PUT /api/recipes/{id}` to have the same known Ingredients, so the draw does not matter |
| Time | Tiny `PREP_TIME_SCALE`, then await the status the API reports |
| Farmers Market | WireMock stubs per test: zero sales, sequenced sales, delays past the read timeout, 5xx |
| Restart recovery | Stop and restart the app against the same database container; every accepted Order still reaches **delivered** |
| Stock invariants | `GET /api/storage` after each step: on hand ≥ 0, reserved ≥ 0, reserved ≤ on hand |
| Concurrency | N concurrent `POST /api/orders`, then check invariants and that the count of distinct Orders equals the sum of `accepted` |

**Keeping it fast.** Each test class shares one app and one in-memory PostgreSQL, and classes run in parallel. Before each test, `ApiTest` stops the workers (which waits for market calls in progress) and rebuilds the database with Flyway, so every test starts from what a new database has. Tests that need their own instances, such as the two-instance tests, keep their own stack. To run a single fork: `./gradlew test -PtestForks=1`.

Three checks sit outside that seam:

| Check | What |
|---|---|
| Architecture | ArchUnit: `domain` free of Spring and `infrastructure`; modules reach each other only through `domain/ports/in` or `shared` |
| Dashboard (Playwright) | A few browser flows that API tests can't see, such as forms showing API errors and phone layout, run by `e2e/run.sh` on a throwaway Compose stack with an in-memory database. Behaviour belongs in API tests; this only covers what the Manager sees. |
| Load (k6) | `load-tests/run.sh`, on a real stack with PostgreSQL syncing to disk: 1,000 Orders = 100 concurrent × 10, after warm-up bursts, with a preparation time scale of 0.001 and enough capacity. Pass when p99 < 200 ms, every Order exists exactly once, stock invariants hold, and all are **delivered** before a timeout. |

## 12. Open questions

1. **Surplus top-up for waiting Reservations** (§10). The load test now shows wasteful buying, so this is worth deciding.

## 13. Repository layout

```
backend/            Gradle project (modules = Java packages, verified by ArchUnit)
frontend/           Vue + Vite app
infra/
  docker-compose.yml   postgres, wiremock, backend, frontend, gateway
  nginx.conf           gateway routes: /api/ to the backend, everything else to the frontend
  wiremock/mappings/   buy.json — quantitySold random 0–10, uniform delay 0–3 s
e2e/                Playwright dashboard tests and their throwaway Compose stack
load-tests/         k6 scripts
docs/  prd.md · rfc/ · adr/
GLOSSARY.md
```
