ALTER TABLE seat_holds ADD COLUMN reservation_type VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED';
ALTER TABLE bookings ADD COLUMN booking_status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT';
ALTER TABLE bookings ADD COLUMN current_status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT';
ALTER TABLE bookings ADD COLUMN booking_status_position INTEGER;
ALTER TABLE bookings ADD COLUMN current_status_position INTEGER;
UPDATE bookings SET booking_status = status, current_status = status;
CREATE INDEX idx_bookings_status_queue ON bookings(train_id, journey_date, train_class_id, quota, current_status, current_status_position);
