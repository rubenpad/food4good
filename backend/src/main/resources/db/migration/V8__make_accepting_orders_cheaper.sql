-- Checking this for every accepted Order was two thirds of the cost of inserting them. The Recipe was
-- read moments earlier and Recipes can't be deleted. Restore it if they ever can be.
ALTER TABLE orders DROP CONSTRAINT orders_recipe_id_fkey;

-- orders_status_history_idx serves the same queries, which now name the open statuses.
DROP INDEX orders_open_idx;
