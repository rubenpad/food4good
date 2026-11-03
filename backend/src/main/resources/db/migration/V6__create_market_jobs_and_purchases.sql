CREATE TABLE market_job (
  id              bigserial PRIMARY KEY,
  order_id        uuid        NOT NULL REFERENCES orders (id),
  ingredient      text        NOT NULL REFERENCES ingredient_stock (ingredient),
  remaining       int         NOT NULL CHECK (remaining >= 0),
  status          text        NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'IN_FLIGHT', 'DONE', 'FAILED')),
  attempt         int         NOT NULL DEFAULT 0,
  zero_streak     int         NOT NULL DEFAULT 0,
  error_attempts  int         NOT NULL DEFAULT 0,
  last_error      text,
  next_attempt_at timestamptz NOT NULL DEFAULT now(),
  lease_until     timestamptz,
  UNIQUE (order_id, ingredient)
);

CREATE INDEX market_job_due_idx ON market_job (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX market_job_leased_idx ON market_job (lease_until) WHERE status = 'IN_FLIGHT';

CREATE TABLE purchase (
  id            bigserial PRIMARY KEY,
  ingredient    text        NOT NULL REFERENCES ingredient_stock (ingredient),
  quantity      int         NOT NULL CHECK (quantity > 0),
  market_job_id bigint      NOT NULL REFERENCES market_job (id),
  attempt       int         NOT NULL,
  purchased_at  timestamptz NOT NULL DEFAULT now(),
  UNIQUE (market_job_id, attempt)
);
