-- A link that MOVES an account had no expiry: a token mailed in January still worked in July.
-- Every other mailed token in this service already carries the moment it was issued and is judged
-- against a window; this one was the exception, and nothing was checking.
ALTER TABLE email_changes
    ADD COLUMN started_at TIMESTAMP NOT NULL DEFAULT now();

-- The reaper deletes by age, so it must not scan the table to find what to delete.
CREATE INDEX IF NOT EXISTS idx_email_changes_started_at ON email_changes (started_at);

-- The blocks table had no retention rule at all: expiry was enforced when reading, so a row that
-- stopped mattering months ago still sat there, one per address ever blocked. Now a reaper deletes
-- them, and deleting by age must not mean scanning the table to find what to delete.
CREATE INDEX IF NOT EXISTS idx_authentication_blocks_expiry ON authentication_blocks (expiry_date);
