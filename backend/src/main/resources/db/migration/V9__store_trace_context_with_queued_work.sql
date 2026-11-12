-- The W3C traceparent of the span that queued each row, so the span that later does the work can link back to it.
ALTER TABLE orders ADD COLUMN traceparent text;
ALTER TABLE storage_command ADD COLUMN traceparent text;
ALTER TABLE market_job ADD COLUMN traceparent text;
