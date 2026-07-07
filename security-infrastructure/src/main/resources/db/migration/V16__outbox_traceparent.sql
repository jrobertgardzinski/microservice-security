-- Distributed tracing across the async boundary: the W3C traceparent of the request that wrote the
-- event rides the outbox row too (alongside the cid), so the poller can re-establish that trace
-- context when it publishes — and the whole account-deletion saga shows as ONE trace in Tempo,
-- not a chain of disconnected ones. Nullable — events written outside a traced request carry none.
ALTER TABLE outbox_events ADD COLUMN traceparent VARCHAR(64);
