package com.railyatra.booking;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CancellationQuoteRepository extends JpaRepository<CancellationQuote,String>{
 java.util.List<CancellationQuote> findByBookingIdAndConfirmedAtIsNotNullOrderByConfirmedAtDesc(String bookingId);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select q from CancellationQuote q where q.id=:id")
 java.util.Optional<CancellationQuote> lockById(@org.springframework.data.repository.query.Param("id") String id);
 boolean existsByBookingIdAndRefundStatusAndIdNot(String bookingId,String status,String id);
 @org.springframework.data.jpa.repository.Query("select q.id from CancellationQuote q where q.confirmedAt is not null and (q.refundStatus='REFUND_PENDING' or (q.refundStatus='REFUND_PROCESSING' and q.processingAt<:cutoff)) order by q.confirmedAt, q.id")
 java.util.List<String> pendingIds(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff,org.springframework.data.domain.Pageable page);
}
