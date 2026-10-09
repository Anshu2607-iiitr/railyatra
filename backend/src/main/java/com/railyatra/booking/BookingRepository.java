package com.railyatra.booking;
import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository;
public interface BookingRepository extends JpaRepository<Booking,String> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"train","trainClass","originStation","destinationStation","hold"})
    @org.springframework.data.jpa.repository.Query("select b from Booking b where b.userId = :userId and ((:tab = 'UPCOMING' and b.currentStatus <> com.railyatra.booking.BookingStatus.CANCELLED and b.journeyDate >= :today) or (:tab = 'COMPLETED' and b.currentStatus <> com.railyatra.booking.BookingStatus.CANCELLED and b.journeyDate < :today) or (:tab = 'CANCELLED' and b.currentStatus = com.railyatra.booking.BookingStatus.CANCELLED))")
    org.springframework.data.domain.Page<Booking> findMine(@org.springframework.data.repository.query.Param("userId") Long userId, @org.springframework.data.repository.query.Param("tab") String tab, @org.springframework.data.repository.query.Param("today") java.time.LocalDate today, org.springframework.data.domain.Pageable pageable);
    Optional<Booking> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
    Optional<Booking> findByHoldId(String holdId);
    Optional<Booking> findByInternalBookingReference(String internalBookingReference);
    java.util.List<Booking> findByTrainIdAndJourneyDateAndTrainClassIdAndQuotaAndCurrentStatusOrderByCreatedAtAscIdAsc(Long trainId, java.time.LocalDate journeyDate, Long trainClassId, String quota, BookingStatus currentStatus);
}
