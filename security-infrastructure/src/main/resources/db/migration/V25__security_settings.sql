-- The RUNTIME rung of the layered configuration ladder (shared/config): one row per key, the key
-- string identical to the property name, so the very same name is visible on every rung of the
-- ladder — application.yml, this table. No row means the rung is VACANT and resolution falls
-- through to properties, then to the hardcoded default: deleting a row is how a runtime override
-- is withdrawn. Nothing is ever "restored", because precedence is a function of the read, not a
-- mutation of lower rungs.
--
-- value is TEXT on purpose: the table carries any future key, and each adapter parses and
-- validates for its own type. An unparseable or illegal value is logged and treated as a vacant
-- rung (the ladder falls through) — a hand-edited row must never take password validation down.
--
-- updated_at records WHEN the runtime decision was made. Writes today happen at the database
-- console (the demo's plot twist), so the column defaults instead of trusting the writer.
CREATE TABLE security_settings (
    name       VARCHAR(255) PRIMARY KEY,
    value      TEXT         NOT NULL,
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);
