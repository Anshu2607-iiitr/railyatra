package com.railyatra.booking;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction,String> {
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select p from PaymentTransaction p where p.booking.id=:bookingId")
 java.util.Optional<PaymentTransaction> lockByBookingId(@org.springframework.data.repository.query.Param("bookingId") String bookingId);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select p from PaymentTransaction p where p.providerPaymentId=:paymentId or (p.providerPaymentId is null and p.paymentReference=:paymentId)")
 java.util.Optional<PaymentTransaction> lockByProviderPaymentId(@org.springframework.data.repository.query.Param("paymentId") String paymentId);
}
