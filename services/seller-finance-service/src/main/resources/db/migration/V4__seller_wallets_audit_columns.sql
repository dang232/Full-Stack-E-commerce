-- BaseJpaEntity (extended by SellerWalletJpaEntity) declares created_at +
-- updated_at, but V2 only created columns on `payouts`. Without these on
-- `seller_wallets`, every wallet read fails Hibernate's strict schema check
-- with "ERROR: column swje1_0.created_at does not exist", which surfaces as
-- 500s on /sellers/me/finance/wallet (and every downstream that touches the
-- wallet repo).

ALTER TABLE seller_finance_svc.seller_wallets
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE seller_finance_svc.seller_wallets
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;

UPDATE seller_finance_svc.seller_wallets
   SET created_at = COALESCE(created_at, NOW()),
       updated_at = COALESCE(updated_at, created_at, NOW())
 WHERE created_at IS NULL OR updated_at IS NULL;

ALTER TABLE seller_finance_svc.seller_wallets
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;
