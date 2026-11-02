-- The starting Recipes (PRD §6). Applied once; the Manager may edit them afterwards.
WITH seed (name, preparation_time_seconds, ingredients) AS (
  VALUES
    ('Grilled Chicken Salad', 90,  '{"chicken": 1, "lettuce": 2, "tomato": 1, "lemon": 1}'::jsonb),
    ('Cheeseburger',          120, '{"meat": 1, "cheese": 1, "lettuce": 1, "tomato": 1, "onion": 1, "ketchup": 1}'::jsonb),
    ('Lemon Chicken & Rice',  150, '{"chicken": 1, "rice": 2, "onion": 1, "lemon": 1}'::jsonb),
    ('Loaded Potatoes',       180, '{"potato": 2, "cheese": 1, "meat": 1, "onion": 1}'::jsonb),
    ('Tomato Rice',           60,  '{"rice": 2, "tomato": 2, "onion": 1}'::jsonb),
    ('Fries with Ketchup',    30,  '{"potato": 2, "ketchup": 1}'::jsonb)
),
inserted AS (
  INSERT INTO recipe (name, preparation_time_seconds)
  SELECT name, preparation_time_seconds FROM seed
  RETURNING id, name
)
INSERT INTO recipe_ingredient (recipe_id, ingredient, quantity)
SELECT inserted.id, item.key, item.value::int
FROM inserted
JOIN seed USING (name)
CROSS JOIN LATERAL jsonb_each_text(seed.ingredients) AS item;
