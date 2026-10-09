-- uk_booking_reference from V11 already creates the unique lookup index.
CREATE INDEX idx_bookings_provider_type_pnr ON bookings(provider_type, provider_pnr);
