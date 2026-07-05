-- The Source split: identity (IP) keys the lockout, observed context is forensics. The failed
-- attempts start remembering what the attempt LOOKED like (user agent) without it ever keying
-- the block. GDPR: the column lives exactly as long as the failure rows do.
ALTER TABLE rejected_authentications ADD COLUMN user_agent VARCHAR(400) NOT NULL DEFAULT '';
