ALTER TABLE booking_passengers ADD COLUMN cancelled BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE booking_passengers SET cancelled = TRUE WHERE booking_id IN (SELECT id FROM bookings WHERE current_status = 'CANCELLED');
CREATE INDEX idx_passengers_booking_cancelled ON booking_passengers(booking_id, cancelled);
CREATE TABLE cancellation_quotes (
 id VARCHAR(36) PRIMARY KEY,
 booking_id VARCHAR(36) NOT NULL REFERENCES bookings(id),
 passenger_ids VARCHAR(160) NOT NULL,
 charge DECIMAL(12,2) NOT NULL,
 refund DECIMAL(12,2) NOT NULL,
 expires_at TIMESTAMP NOT NULL,
 confirmed_at TIMESTAMP,
 refund_status VARCHAR(24) NOT NULL DEFAULT 'NOT_REQUESTED',
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_cancellation_amounts CHECK(charge >= 0 AND refund >= 0)
);
CREATE INDEX idx_cancellation_booking ON cancellation_quotes(booking_id, confirmed_at);
CREATE INDEX idx_cancellation_refund_queue ON cancellation_quotes(refund_status, confirmed_at);
