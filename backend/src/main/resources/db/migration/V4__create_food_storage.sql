CREATE TABLE ingredient_stock (
  ingredient text PRIMARY KEY,
  on_hand    int NOT NULL CHECK (on_hand >= 0),
  reserved   int NOT NULL CHECK (reserved >= 0),
  CHECK (reserved <= on_hand)
);

-- Applied once, so restarting never refills Food Storage.
INSERT INTO ingredient_stock (ingredient, on_hand, reserved)
SELECT ingredient, ${initial_stock}, 0
FROM unnest(ARRAY['tomato', 'lemon', 'potato', 'rice', 'ketchup', 'lettuce', 'onion', 'cheese', 'meat', 'chicken'])
  AS ingredient;

CREATE TABLE reservation_line (
  order_id   uuid    NOT NULL REFERENCES orders (id),
  ingredient text    NOT NULL REFERENCES ingredient_stock (ingredient),
  needed     int     NOT NULL CHECK (needed > 0),
  reserved   int     NOT NULL DEFAULT 0 CHECK (reserved >= 0 AND reserved <= needed),
  consumed   boolean NOT NULL DEFAULT false,
  PRIMARY KEY (order_id, ingredient)
);

CREATE TABLE storage_command (
  id         bigserial PRIMARY KEY,
  type       text        NOT NULL CHECK (type IN ('RESERVE', 'ADD_PURCHASE', 'CONSUME')),
  payload    jsonb       NOT NULL,
  dedupe_key text        NOT NULL UNIQUE,
  status     text        NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DONE', 'FAILED')),
  attempts   int         NOT NULL DEFAULT 0,
  last_error text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX storage_command_pending_idx ON storage_command (id) WHERE status = 'PENDING';
