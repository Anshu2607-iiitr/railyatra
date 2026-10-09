ALTER TABLE payment_transactions DROP CONSTRAINT ck_payment_status;
ALTER TABLE payment_transactions ADD COLUMN provider_type VARCHAR(40) NOT NULL DEFAULT 'MOCK';
ALTER TABLE payment_transactions ADD COLUMN payment_method VARCHAR(30) NOT NULL DEFAULT 'UPI';
ALTER TABLE payment_transactions ADD COLUMN provider_payment_id VARCHAR(100);
ALTER TABLE payment_transactions ADD COLUMN refunded_amount DECIMAL(12,2) NOT NULL DEFAULT 0;
ALTER TABLE payment_transactions ADD CONSTRAINT ck_payment_status CHECK(status IN ('CREATED','PENDING','SUCCESS','FAILED','REFUNDED','PARTIALLY_REFUNDED'));
CREATE INDEX idx_payment_provider_payment ON payment_transactions(provider_type, provider_payment_id);
