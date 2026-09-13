-- AUTH-13: a sign-in with no end.
--
-- Every refresh rotates the token and grants a FULL new window, so a session touched once a day
-- survived indefinitely: "signed in since last March" was a state this service could reach and
-- never reconsider. The refresh token's own validity answers a different question — how long a
-- session may sit IDLE — and no number of idle windows adds up to a promise that the person is
-- still there.
--
-- The absolute age is measured from when the LINEAGE started, which is why the column travels with
-- the family: a successor inherits its predecessor's value instead of starting the clock again.
ALTER TABLE sessions ADD COLUMN family_started_at TIMESTAMP;

-- Existing sessions are backfilled to NOW: nobody is signed out by a deployment. They get a full
-- absolute window starting today, and the ceiling applies to them from their next refresh on.
UPDATE sessions SET family_started_at = NOW() WHERE family_started_at IS NULL;
ALTER TABLE sessions ALTER COLUMN family_started_at SET NOT NULL;
