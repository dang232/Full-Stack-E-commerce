CREATE TABLE processed_refund (
    refund_id VARCHAR(64) PRIMARY KEY,
    seller_id VARCHAR(64) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
