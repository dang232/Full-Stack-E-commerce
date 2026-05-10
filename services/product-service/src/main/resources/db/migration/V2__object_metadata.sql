CREATE TABLE IF NOT EXISTS product_svc.object_metadata (
    object_key VARCHAR(1024) PRIMARY KEY,
    storage_class VARCHAR(64) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    content_length BIGINT NOT NULL CHECK (content_length >= 0),
    sha256_hex CHAR(64) NOT NULL,
    quarantine_state VARCHAR(32) NOT NULL,
    image_width INTEGER,
    image_height INTEGER,
    owner_type VARCHAR(64) NOT NULL,
    owner_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_object_metadata_owner ON product_svc.object_metadata (owner_type, owner_id);
