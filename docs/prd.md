# food4good — Product Requirements

**Status:** Draft · **Date:** 2026-11-01 · **Vocabulary:** [GLOSSARY.md](../GLOSSARY.md)

## 1. Background

A well-known restaurant is running a free-food donation event for local residents. The one condition is that each person receives a Dish chosen at random. The Manager needs a system to place Orders with the Kitchen, follow every Order until it is delivered, and see what Food Storage holds and what it has bought from the Farmers Market.

Demand comes in bursts: a queue of people arrives and the Manager places many Orders at once. The real constraint is not the server. It is how fast the Kitchen can cook, so the system must absorb bursts without losing an Order and be honest about how long the queue will take.

## 2. Goals

1. The Manager can place one or many Orders with a single action and immediately learn how many were accepted and the estimated wait.
2. Every accepted Order is delivered eventually. None is lost, duplicated or stuck without a visible status, including across restarts.
3. The Kitchen never accepts more work than it can clear within the Maximum wait.
4. Food Storage never gives away the same unit twice, and stock never goes negative.
5. The Manager sees Orders, stock and Purchases change live, without refreshing.

## 3. Non-goals (v1)

- Authentication, roles or multiple Managers with different permissions.
- Multiple donation events. There is one implicit, ongoing event, and stock resets only with a fresh database.
- Choosing the Dish, or Recipe choice that takes stock into account. Selection is uniformly random.
- Editing Cooks or the Maximum wait from the UI. They are configuration, shown read-only.
- Deleting Recipes.
- Cancelling an Order.
- Reconciling Farmers Market sales after a timeout. The market is not idempotent, and a timed-out call is treated as selling nothing.
- Cloud deployment, Kubernetes, multiple app instances, a message broker, an OpenTelemetry Collector, Grafana, browser telemetry.

## 4. Users

**Manager**: the only role. Uses one dashboard to place Orders and watch the event. Anyone with access acts as the Manager.

## 5. Functional requirements

### FR-1 Place Orders

The Manager enters a quantity (default 1) and places Orders.

- **FR-1.1** Each accepted unit of quantity creates exactly one Order.
- **FR-1.2** On acceptance, each Order is immediately assigned a Recipe chosen uniformly at random from the current Recipes.
- **FR-1.3 Admission.** Orders are admitted one by one. Admission stops as soon as the next Order would push the Backlog past the Maximum wait (FR-3). The remaining quantity is rejected and no Orders are created for it.
- **FR-1.4** The response states how many Orders were requested, how many were accepted, the identifiers of the accepted Orders, and the estimated time to clear the Backlog.
- **FR-1.5** Quantity must be a positive whole number. A technical ceiling (10,000) protects the server. It is not a business limit.

_Acceptance:_
- Given a Backlog with room for about 40 more Orders, when the Manager asks for 100, then between 1 and 100 Orders are accepted (usually around 40, depending on the Recipes drawn), the rest are rejected, and the response says "accepted X of 100".
- Given a full Backlog, when the Manager asks for any quantity, then 0 are accepted and the UI says why.

### FR-2 Capacity estimate

- **FR-2.1** Before placing Orders, the Manager sees a live estimate of how many more Orders fit right now: (Maximum wait × Cooks − current Backlog work) ÷ average preparation time, rounded down and never below 0.
- **FR-2.2** The estimate is labelled as an estimate. The exact count is only known once Recipes are drawn (FR-1.3).

### FR-3 Backlog

- **FR-3.1** The Backlog is the sum of the preparation times of every accepted Order not yet delivered, divided by the number of Cooks. For an Order that is preparing, only the remaining time counts.
- **FR-3.2** Time spent waiting for ingredients is not counted. The Backlog is an honest lower bound, not a promise.

### FR-4 Ingredients for an Order

- **FR-4.1** As soon as an Order is accepted, the Kitchen sends Food Storage an Ingredient Request for its Recipe.
- **FR-4.2** For each Ingredient, Food Storage reserves as many units as are available, up to the quantity needed.
- **FR-4.3** For any shortfall, Food Storage buys from the Farmers Market, one call at a time per shortfall, until the shortfall is covered. Units bought go into that Order's Reservation first. Any surplus becomes available to everyone.
- **FR-4.4** When the market sells nothing (including timeouts and errors), Food Storage waits and tries again, with growing intervals. Any sale resets the interval. It never gives up.
- **FR-4.5** When every Ingredient in the Reservation is covered, the Order becomes **ready**.

_Acceptance:_
- Given 5 tomatoes available and two Orders each needing 3, then one Order reserves 3, the other reserves 2 and buys at least 1, and no tomato is reserved twice.
- Given the market sells 4 cheese when 1 is short, then 1 goes to the Reservation and 3 become available.

### FR-5 Cooking

- **FR-5.1** The Kitchen has a configured number of Cooks. Each Cook prepares one Dish at a time.
- **FR-5.2** When a Cook is free, it takes the **ready** Order that became ready earliest.
- **FR-5.3** When preparation starts, the Order becomes **preparing** and its Reservation is consumed: on-hand stock decreases by the Recipe quantities.
- **FR-5.4** After the Recipe's preparation time, the Order becomes **delivered** and the Cook is free.

### FR-6 Order lifecycle

Every accepted Order moves through **waiting for ingredients → ready → preparing → delivered**, and no other status exists. Orders are never cancelled or failed.

### FR-7 Live dashboard

A single page with these panels. The spec's required UI elements are noted in brackets.

| Panel | Shows |
|---|---|
| **Place Orders** | Quantity input, capacity estimate (FR-2), result of the last request (FR-1.4) |
| **Kitchen** [orders in preparation] | Orders waiting for ingredients, ready and preparing, with Recipe and status; Cooks busy/total; current Backlog; Maximum wait |
| **Food Storage** [stock + Purchase history] | Per Ingredient: on hand, reserved, available. Purchase history: Ingredient, units, time |
| **Order history** [orders placed] | Every Order with Recipe, status and timestamps, newest first, paginated |
| **Recipes** [recipes with ingredients] | Each Recipe with its Ingredients, quantities and preparation time; forms to create and edit Recipes (FR-8) |

- **FR-7.1** Status, stock and Purchase changes appear within about a second without a page refresh.
- **FR-7.2** After a lost connection, the dashboard reconnects and catches up automatically.

### FR-8 Manage Recipes

The Manager can create and edit Recipes while the event runs.

- **FR-8.1** A Recipe has a unique name, at least one Ingredient from the ten, a positive whole quantity of each, and a positive preparation time.
- **FR-8.2** Invalid input is refused with a clear error, and a duplicate name is reported as a conflict.
- **FR-8.3** An edit affects only Orders accepted afterwards. An accepted Order keeps the Recipe name, Ingredients, quantities and preparation time it was accepted with, including in the Order history and the Backlog.
- **FR-8.4** New and edited Recipes join the random draw for the next Order, and the capacity estimate (FR-2) uses the new average preparation time.

_Acceptance:_
- Given an Order waiting for ingredients, when its Recipe is edited to need more units, then that Order's Reservation and preparation time do not change.
- Given a Recipe named "Tomato Rice", when the Manager creates another with that name, then it is refused and nothing changes.

## 6. Starting Recipes

The event starts with these six Recipes, inserted once and editable afterwards (FR-8). Together they use every Ingredient. Preparation times are kept short so the Backlog moves visibly in a demo.

| Recipe | Ingredients | Preparation time |
|---|---|---|
| Grilled Chicken Salad | chicken 1, lettuce 2, tomato 1, lemon 1 | 90 s |
| Cheeseburger | meat 1, cheese 1, lettuce 1, tomato 1, onion 1, ketchup 1 | 120 s |
| Lemon Chicken & Rice | chicken 1, rice 2, onion 1, lemon 1 | 150 s |
| Loaded Potatoes | potato 2, cheese 1, meat 1, onion 1 | 180 s |
| Tomato Rice | rice 2, tomato 2, onion 1 | 60 s |
| Fries with Ketchup | potato 2, ketchup 1 | 30 s |

Average preparation time is 105 s.

## 7. Configuration

All values come from environment variables at startup and are shown read-only in the UI.

| Setting | Default | Notes |
|---|---|---|
| Cooks | 4 | |
| Maximum wait | 2 h | About 270 Orders of capacity with the starting Recipes |
| Preparation time scale | 1.0 | e.g. 0.01 for load tests, so 1,000 Orders finish in minutes |
| Initial stock per Ingredient | 5 | |
| Farmers Market URL | local stub | |
| Market connect / read timeout | 1 s / 2 s | |
| Max concurrent market calls | 20 | |
| Honeycomb API key | unset | Telemetry export off when unset |

## 8. Non-functional requirements

- **NFR-1 Burst intake.** A burst of 1,000 Orders sent as 100 concurrent requests of 10 is acknowledged at **p99 < 200 ms**.
- **NFR-2 No loss, no duplicates.** Every accepted Order exists exactly once and reaches **delivered**. A restart at any point loses no Order, Reservation, Purchase or cooking progress.
- **NFR-3 Stock invariants.** At all times, per Ingredient: on hand ≥ 0, reserved ≥ 0, reserved ≤ on hand. A unit is never in two Reservations.
- **NFR-4 Market isolation.** A slow or empty Farmers Market delays only the Orders that need what it lacks. It never slows Order intake or the Orders whose ingredients are available.
- **NFR-5 Observability.** One Order's journey (acceptance, reservation, every market attempt, cooking, delivery) can be followed end to end in Honeycomb by Order id. Metrics cover Backlog time, work waiting, the market zero-sale rate and failed work.
- **NFR-6 Visible failure.** Work that fails repeatedly because of a defect (not a market zero) stops after 5 attempts, is logged, and leaves its Order visibly in its current status.
- **NFR-7 Local run.** One command (`docker compose up`) starts the whole system with no external accounts.

NFR-1 to NFR-3 are verified by a load test. If they fail on one instance, adding instances is the next step.

## 9. Assumptions and constraints

- The Farmers Market takes only an Ingredient name and sells a random 0–10 units. It has no rate limit and is not idempotent. Locally it is simulated, with random response delays so timeouts occur.
- One app instance in v1, built to stay correct if a second one starts.
- Architecture, stack and the reasoning behind them live in the RFC (`docs/rfc/0001-food4good-architecture.md`) and in [docs/adr/](adr/).

## 10. Success criteria

- Every FR acceptance scenario passes as an automated test.
- The load test meets NFR-1 to NFR-3.
- A demo run of 100+ Orders shows every status moving live on the dashboard, with Purchases appearing as stock runs out.
