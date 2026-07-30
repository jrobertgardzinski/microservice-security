-- Retention, and the indexes the retention scans need.
--
-- Three tables kept personal data for ever, because nothing ever deleted from them:
--
--   outbox_events            -- the address twice per row (event_key AND payload), so in effect a
--                               permanent register of every address this service has ever mailed:
--                               verification links, password resets, MFA codes, and the deletion
--                               fact with the leaver's purge rules
--   account_deletion_sagas   -- the address in a column of its own, on the very rows whose purpose
--                               is carrying out "delete my account"
--   rejected_authentications -- an IP address and a user-agent per failed attempt; the only deletion
--                               was per-source and fires when a source SUCCEEDS, so a source that
--                               only ever fails was never cleaned at all
--
-- The reapers (SettledOutboxReaper, SettledDeletionSagaReaper, RejectedAuthenticationReaper) delete
-- by timestamp, and none of the existing indexes serves that: the outbox has a PARTIAL index for the
-- drain (created_at WHERE published_at IS NULL AND failed_at IS NULL) which by definition excludes
-- every row a reaper wants, the sagas have one for the STARTED scan, and the rejections have
-- (ip_address, occurred_at) whose leading column is the wrong one for a scan by time alone.

-- Settled = published (with the broker) or failed permanently (V19). A row with NULL in both is
-- still awaiting the drain and is never swept, whatever its age.
CREATE INDEX idx_outbox_published_at ON outbox_events (published_at) WHERE published_at IS NOT NULL;
CREATE INDEX idx_outbox_failed_at    ON outbox_events (failed_at)    WHERE failed_at IS NOT NULL;

-- A STARTED saga must survive at any age (compensateOverdue unlocks accounts from exactly those
-- rows), so the retention index carries the same exclusion the DELETE does.
CREATE INDEX idx_deletion_sagas_settled ON account_deletion_sagas (updated_at) WHERE state <> 'STARTED';

CREATE INDEX idx_rejected_occurred_at ON rejected_authentications (occurred_at);
