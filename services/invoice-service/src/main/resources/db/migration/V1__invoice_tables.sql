-- V1__invoice_tables.sql
-- Invoice service schema

CREATE SCHEMA IF NOT EXISTS invoice_svc;

SET search_path TO invoice_svc;

CREATE TABLE invoices (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    order_id         UUID        NOT NULL,
    seller_id        VARCHAR(64) NOT NULL,
    buyer_tax_code   VARCHAR(20),
    items            JSONB,
    vat_breakdown    JSONB,
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    gdt_invoice_number VARCHAR(50),
    xml_payload      TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_invoices PRIMARY KEY (id),
    CONSTRAINT uq_invoices_order_id UNIQUE (order_id),
    CONSTRAINT chk_invoices_status CHECK (status IN ('DRAFT','SUBMITTED','ACCEPTED','REJECTED'))
);

CREATE INDEX idx_invoices_order_id  ON invoices (order_id);
CREATE INDEX idx_invoices_seller_id ON invoices (seller_id);
CREATE INDEX idx_invoices_status    ON invoices (status);
CREATE INDEX idx_invoices_seller_status ON invoices (seller_id, status);

CREATE TABLE seller_authorizations (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    seller_id        VARCHAR(64) NOT NULL,
    authorized_at    TIMESTAMPTZ,
    tax_code         VARCHAR(20) NOT NULL,
    digital_cert_id  VARCHAR(128),
    status           VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT pk_seller_authorizations PRIMARY KEY (id),
    CONSTRAINT uq_seller_authorizations_seller UNIQUE (seller_id),
    CONSTRAINT chk_seller_authorizations_status CHECK (status IN ('ACTIVE','REVOKED'))
);

CREATE INDEX idx_seller_auth_seller_id ON seller_authorizations (seller_id);
