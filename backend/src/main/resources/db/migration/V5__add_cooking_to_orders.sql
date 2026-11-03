ALTER TABLE orders ADD COLUMN prepare_until timestamptz;

CREATE INDEX orders_ready_idx ON orders (ready_at, id) WHERE status = 'READY';
CREATE INDEX orders_preparing_idx ON orders (prepare_until) WHERE status = 'PREPARING';
CREATE INDEX orders_open_idx ON orders (status) WHERE status <> 'DELIVERED';
