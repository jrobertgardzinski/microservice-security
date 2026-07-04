-- Flat RBAC: every user is a USER; MODERATOR and ADMIN are grants on top. Stored as a
-- comma-separated set on the user row (a small, fixed enum — a join table would be overkill).
ALTER TABLE users ADD COLUMN roles VARCHAR(255) NOT NULL DEFAULT 'USER';
