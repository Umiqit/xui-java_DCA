-- Initial schema for tables shared between site and bot.
-- Idempotent: safe to run against both empty and existing databases.

CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    tg_id       BIGINT UNIQUE NOT NULL,
    username    TEXT,
    full_name   TEXT,
    balance     NUMERIC(19,2) DEFAULT 0,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS servers (
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT NOT NULL,
    location    TEXT DEFAULT '',
    url         TEXT NOT NULL,
    username    TEXT NOT NULL,
    password    TEXT NOT NULL,
    cert_path   TEXT DEFAULT '',
    active      BOOLEAN DEFAULT TRUE,
    weight      INTEGER DEFAULT 1,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS keys (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    server_id       BIGINT REFERENCES servers(id) ON DELETE CASCADE,
    inbound_id      INTEGER NOT NULL,
    xui_client_id   TEXT NOT NULL,
    xui_email       TEXT NOT NULL,
    remark          TEXT DEFAULT '',
    expiry_ts       BIGINT DEFAULT 0,
    traffic_total   BIGINT DEFAULT 0,
    traffic_up      BIGINT DEFAULT 0,
    traffic_down    BIGINT DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS payments (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount      NUMERIC(19,2) NOT NULL,
    currency    TEXT DEFAULT 'XTR',
    payload     TEXT,
    status      TEXT DEFAULT 'pending',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Legacy databases may have these columns as DOUBLE PRECISION.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'users' AND column_name = 'balance'
                 AND data_type = 'double precision') THEN
        ALTER TABLE users ALTER COLUMN balance TYPE NUMERIC(19,2);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'payments' AND column_name = 'amount'
                 AND data_type = 'double precision') THEN
        ALTER TABLE payments ALTER COLUMN amount TYPE NUMERIC(19,2);
    END IF;
END $$;

-- Legacy keys table may lack server_id (was created without it by the old repair service).
ALTER TABLE keys ADD COLUMN IF NOT EXISTS server_id BIGINT;

-- Bind legacy keys to the first server if they have none.
UPDATE keys SET server_id = (SELECT MIN(id) FROM servers)
WHERE server_id IS NULL AND EXISTS (SELECT 1 FROM servers);

-- Enforce NOT NULL only if no NULLs remain.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM keys WHERE server_id IS NULL) THEN
        ALTER TABLE keys ALTER COLUMN server_id SET NOT NULL;
    END IF;
EXCEPTION WHEN OTHERS THEN
    -- leave nullable rather than fail the migration
    NULL;
END $$;
