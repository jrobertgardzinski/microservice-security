-- One permanently unpublishable row must not wedge the whole outbox drain. The drain publishes in
-- creation order and stops at the first failure to preserve ordering; that is right for a transient
-- broker outage, but a PERMANENT failure (a payload past max.request.size, a serialization fault)
-- would otherwise block every later event forever — no verification mail, no reset, no MFA code.
-- Marking such a row failed lets the drain skip it and carry on, while transient failures still stop
-- the loop and retry next tick.
ALTER TABLE outbox_events ADD COLUMN failed_at TIMESTAMP;
-- keep the "still to publish" scan cheap: an unpublished-and-not-failed row is what the drain wants
DROP INDEX idx_outbox_unpublished;
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL AND failed_at IS NULL;
