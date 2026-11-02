# Single writer for Food Storage stock

There are only ten Ingredients, so under a burst of Orders every one of them is a hot row. We serialize every stock change (reserving, adding Purchases, consuming Reservations) through a single writer instead of letting concurrent transactions contend for those rows. Each change is microseconds of work and the real bottleneck is the Farmers Market, so one writer is never the limiting factor, and it gives deterministic ordering with no locks or deadlocks to reason about.

## Considered Options

- **Conditional atomic updates** (`UPDATE … SET on_hand = on_hand - n WHERE on_hand >= n`): simple and correct, but every writer contends on the same ten rows. This is the fallback if Food Storage ever needs several concurrent writers.
- **Shopify-style unit-row pool** (one row per unit, claimed with `SELECT … FOR UPDATE SKIP LOCKED`, refilled by a background replenisher): spreads contention across rows, but it exists for SKUs with deep stock and many independent buyers. With ten Ingredients starting at five units each, the replenisher is machinery we'd operate for no gain.
