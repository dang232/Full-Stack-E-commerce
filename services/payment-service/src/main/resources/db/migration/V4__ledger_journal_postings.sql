ALTER TABLE payment_svc.ledger_entries
    ADD COLUMN IF NOT EXISTS journal_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS account_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS posting_type VARCHAR(16),
    ADD COLUMN IF NOT EXISTS reverses_journal_id VARCHAR(255);

UPDATE payment_svc.ledger_entries
SET journal_id = transaction_id,
    account_id = debit_account,
    posting_type = 'DEBIT'
WHERE journal_id IS NULL;

ALTER TABLE payment_svc.ledger_entries
    ALTER COLUMN journal_id SET NOT NULL,
    ALTER COLUMN account_id SET NOT NULL,
    ALTER COLUMN posting_type SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ledger_entries_journal_id ON payment_svc.ledger_entries (journal_id);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_account_currency ON payment_svc.ledger_entries (account_id, currency);

CREATE OR REPLACE FUNCTION payment_svc.prevent_ledger_entries_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries are immutable; append reversal entries instead';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_prevent_ledger_entries_update ON payment_svc.ledger_entries;
CREATE TRIGGER trg_prevent_ledger_entries_update
    BEFORE UPDATE ON payment_svc.ledger_entries
    FOR EACH ROW EXECUTE FUNCTION payment_svc.prevent_ledger_entries_mutation();

DROP TRIGGER IF EXISTS trg_prevent_ledger_entries_delete ON payment_svc.ledger_entries;
CREATE TRIGGER trg_prevent_ledger_entries_delete
    BEFORE DELETE ON payment_svc.ledger_entries
    FOR EACH ROW EXECUTE FUNCTION payment_svc.prevent_ledger_entries_mutation();
