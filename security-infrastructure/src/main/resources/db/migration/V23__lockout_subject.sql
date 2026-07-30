-- Failed attempts stop being counted per ADDRESS alone and start being counted per PAIR:
-- this source against this account.
--
-- Counting by source alone had two edges and the estate cut itself on both. While a successful
-- sign-in cleared the source's record, one known-good credential — an attacker's own account —
-- was an amnesty for every account that address had been guessing at. Once the clearing was
-- removed (P18 poz. 6), three mistyped passwords locked out everyone behind an office NAT, a
-- CGNAT or a CI runner; the project's own e2e suite blocked itself halfway through a run.
--
-- The account is stored as a KEYED FINGERPRINT, not as an address. Counting needs only equality,
-- and an address written down on every failed attempt — including addresses belonging to no
-- account here — would be a ready-made register for enumeration, worth more to whoever reached
-- this table than the counter it serves. HMAC rather than a plain digest because e-mail addresses
-- are low-entropy: an unkeyed hash of them is a dictionary away from being reversed.
--
-- Existing rows keep an empty fingerprint. They are failures nobody can now attribute to an
-- account, they age out of the fifteen-minute window within the hour, and until then they still
-- count towards the source-wide ceiling — which is exactly the right amount of trust to give them.
ALTER TABLE rejected_authentications ADD COLUMN account_fingerprint VARCHAR(64) NOT NULL DEFAULT '';

-- the per-pair count and the per-pair delete; the source-wide ceiling still uses idx_rejected_ip_time
CREATE INDEX idx_rejected_pair_time
    ON rejected_authentications (ip_address, account_fingerprint, occurred_at);
