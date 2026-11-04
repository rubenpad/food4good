-- One command holds every Ingredient Request from a group of Orders, keeping admission small.
ALTER TABLE storage_command DROP CONSTRAINT storage_command_type_check;
ALTER TABLE storage_command ADD CONSTRAINT storage_command_type_check
  CHECK (type IN ('REQUEST_INGREDIENTS', 'RESERVE', 'ADD_PURCHASE', 'CONSUME'));
