UPDATE cancellation_quotes SET refund_status='REFUND_PENDING' WHERE refund_status='PENDING';
ALTER TABLE cancellation_quotes ADD COLUMN processing_at TIMESTAMP;
ALTER TABLE cancellation_quotes ADD COLUMN processing_token VARCHAR(36);
ALTER TABLE cancellation_quotes ADD COLUMN refund_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE cancellation_quotes ADD COLUMN refund_failure VARCHAR(255);
ALTER TABLE cancellation_quotes ADD CONSTRAINT ck_refund_status CHECK(refund_status IN ('NOT_REQUESTED','NOT_APPLICABLE','REFUND_PENDING','REFUND_PROCESSING','REFUNDED','REFUND_FAILED'));
CREATE TABLE mock_refund_receipts (
 idempotency_key VARCHAR(36) PRIMARY KEY,
 payment_id VARCHAR(100) NOT NULL,
 amount DECIMAL(12,2) NOT NULL CHECK(amount > 0),
 created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_mock_refund_payment ON mock_refund_receipts(payment_id);
