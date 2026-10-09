CREATE INDEX idx_bookings_created_at ON bookings(created_at);
CREATE INDEX idx_bookings_current_status ON bookings(current_status);
CREATE INDEX idx_cancellation_confirmed_at ON cancellation_quotes(confirmed_at);
CREATE INDEX idx_payment_status ON payment_transactions(status);
-- Active holds already use idx_seat_holds_expiry(status, expires_at), created in V10.
