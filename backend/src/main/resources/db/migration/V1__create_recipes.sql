CREATE TABLE recipe (
  id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name                     text NOT NULL UNIQUE CHECK (btrim(name) <> ''),
  preparation_time_seconds int  NOT NULL CHECK (preparation_time_seconds > 0)
);

CREATE TABLE recipe_ingredient (
  recipe_id  uuid NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,
  ingredient text NOT NULL CHECK (ingredient IN (
    'tomato', 'lemon', 'potato', 'rice', 'ketchup', 'lettuce', 'onion', 'cheese', 'meat', 'chicken'
  )),
  quantity   int  NOT NULL CHECK (quantity > 0),
  PRIMARY KEY (recipe_id, ingredient)
);
