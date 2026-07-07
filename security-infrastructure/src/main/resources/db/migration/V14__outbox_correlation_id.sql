-- Traceability across the async boundary: the correlation id of the request that wrote the event
-- rides with the outbox row, so the poller can forward it as a Kafka header and the consuming
-- service (email, memes, comments) logs the SAME cid as the originating HTTP request. Nullable —
-- rows written before this migration, and any event created outside a request, simply carry none.
ALTER TABLE outbox_events ADD COLUMN cid VARCHAR(64);
