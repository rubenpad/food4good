CREATE TABLE orders (
  id             uuid PRIMARY KEY,
  recipe_id      uuid        NOT NULL REFERENCES recipe (id),
  recipe_name    text        NOT NULL,
  preparation_ms bigint      NOT NULL CHECK (preparation_ms > 0),
  status         text        NOT NULL DEFAULT 'WAITING_FOR_INGREDIENTS'
    CHECK (status IN ('WAITING_FOR_INGREDIENTS', 'READY', 'PREPARING', 'DELIVERED')),
  accepted_at    timestamptz NOT NULL DEFAULT now(),
  ready_at       timestamptz,
  preparing_at   timestamptz,
  delivered_at   timestamptz
);

CREATE INDEX orders_history_idx ON orders (accepted_at DESC, id DESC);
CREATE INDEX orders_status_history_idx ON orders (status, accepted_at DESC, id DESC);
