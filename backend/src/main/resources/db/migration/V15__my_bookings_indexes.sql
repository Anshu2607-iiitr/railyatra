CREATE INDEX idx_bookings_user_journey_status ON bookings(user_id, journey_date, current_status);
CREATE INDEX idx_bookings_user_created_id ON bookings(user_id, created_at, id);
