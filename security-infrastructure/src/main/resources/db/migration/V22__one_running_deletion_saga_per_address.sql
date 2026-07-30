-- One running deletion saga per address — the invariant identity's side of the saga always
-- assumed and never had.
--
-- Every transition is written as "UPDATE ... WHERE email = ? AND state = 'STARTED'", so a second
-- STARTED row for one address makes ONE portal outcome settle BOTH sagas: an account is deleted
-- for good on the strength of a confirmation that belongs to another case, and the outcome of the
-- case nobody answered for then arrives to find "no running deletion" (or, worse, walks into the
-- CONTENT ERASED AFTER COMPENSATION branch). Nothing stopped that second row: StartAccountDeletion
-- opens a saga on every request, and two live sessions of the same person (or one request retried
-- against a slow first one) are two requests.
--
-- Unlike the portal's offboarding_sagas (V2 there), this schema is Postgres-only — the JDBC
-- adapters are exercised against a real postgres:16 container, never H2 — so the invariant can be
-- a PARTIAL UNIQUE index on the address itself instead of offboarding's nullable latch column.
-- Same guarantee, no column to keep in step with the state on every transition. Partial indexes
-- are already the idiom here (V6, V21).

-- Dedup BEFORE the index: forks the old read-then-insert may already have left behind would fail
-- the whole migration. Keep the NEWEST started saga per address running (created_at, then id, as
-- the deterministic tie-break) and close the older forks as COMPENSATED — they were never
-- completed, and an abandoned duplicate is exactly what compensation is for. Their updated_at
-- stays where it was, so the retention reaper treats them by their real age.
UPDATE account_deletion_sagas older SET state = 'COMPENSATED'
WHERE older.state = 'STARTED'
  AND EXISTS (SELECT 1 FROM account_deletion_sagas newer
              WHERE newer.email = older.email
                AND newer.state = 'STARTED'
                AND (newer.created_at > older.created_at
                     OR (newer.created_at = older.created_at AND newer.id > older.id)));

CREATE UNIQUE INDEX uq_deletion_sagas_running_email
    ON account_deletion_sagas (email) WHERE state = 'STARTED';
