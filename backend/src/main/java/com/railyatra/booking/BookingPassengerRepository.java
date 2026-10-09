package com.railyatra.booking;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BookingPassengerRepository extends JpaRepository<BookingPassenger,Long> {
    java.util.List<BookingPassenger> findByBookingIdOrderByPassengerIndexAsc(String bookingId);
    long countByBookingIdAndCancelledFalse(String bookingId);
}
