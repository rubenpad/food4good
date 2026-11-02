# PostgreSQL as both system of record and work queue; no message broker

We keep all state and all asynchronous work (Ingredient Requests, the Cook queue, Farmers Market retries) in PostgreSQL. Work queues are tables consumed with `SELECT … FOR UPDATE SKIP LOCKED`. Our load is bursts of about 1,000 Orders, which PostgreSQL handles easily. Keeping state and queued work in one transaction means there is no dual write to guard against. A broker would add an outbox, consumer deduplication across two systems, and a second piece of infrastructure, and none of it would buy us anything at this scale.

## Considered Options

- **PostgreSQL + Kafka**: replayable event log and per-key ordering, but our history already lives in PostgreSQL. Long-running Cook work maps poorly onto partitions, and the number of Cooks would be tied to the partition count.
- **PostgreSQL + RabbitMQ**: the best fit of the brokers (work queues, single active consumer, delayed retries), but it still needs a transactional outbox to avoid dual writes.
- **MongoDB / Redis Streams**: weaker multi-record atomicity, or not durable enough to be the system of record.

## Consequences

Modules talk to each other through ports, so moving a queue onto a broker later is an adapter change, not a domain change. Revisit this decision if Kitchen or Food Storage become separate services, or if load grows well beyond single-database throughput.
