-- Transactional outbox: domain events (today: mail requests) are written in the SAME transaction
-- as the state change that caused them, then published to Kafka by a poller. Guarantees that a
-- committed change and its event never part ways (at-least-once towards the broker).
CREATE TABLE outbox_events (
    id           UUID PRIMARY KEY,
    topic        VARCHAR(100)  NOT NULL,
    event_key    VARCHAR(255)  NOT NULL,
    payload      TEXT          NOT NULL,
    created_at   TIMESTAMP     NOT NULL,
    published_at TIMESTAMP
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
