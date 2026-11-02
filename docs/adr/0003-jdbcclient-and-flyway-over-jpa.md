# Hand-written SQL with JdbcClient and Flyway, not JPA

Correctness here depends on specific SQL: `SELECT … FOR UPDATE SKIP LOCKED` to claim work, advisory locks for the single stock writer, and conditional updates. An ORM would hide or reorder exactly that behaviour. Persistence adapters therefore use Spring's `JdbcClient` with explicit SQL, and the schema is owned by Flyway migrations. jOOQ was the type-safe alternative, but its code-generation step costs more than it saves for a schema this small.
