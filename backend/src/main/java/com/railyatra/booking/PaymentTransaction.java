package com.railyatra.booking;
import com.railyatra.payment.PaymentMethod; import jakarta.persistence.*; import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="payment_transactions") public class PaymentTransaction {
 @Id private String id; @ManyToOne(optional=false) @JoinColumn(name="booking_id") private Booking booking;
 @Column(name="payment_reference", nullable=false) private String paymentReference; @Column(nullable=false, precision=12, scale=2) private BigDecimal amount;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private com.railyatra.payment.PaymentStatus status; @Column(name="processed_at", nullable=false) private Instant processedAt;
 @Column(name="provider_type", nullable=false) private String providerType; @Enumerated(EnumType.STRING) @Column(name="payment_method", nullable=false) private PaymentMethod paymentMethod; @Column(name="provider_payment_id") private String providerPaymentId; @Column(name="refunded_amount", nullable=false, precision=12, scale=2) private BigDecimal refundedAmount=BigDecimal.ZERO;
 protected PaymentTransaction(){} public PaymentTransaction(Booking b,String ref,BigDecimal amount,com.railyatra.payment.PaymentStatus status,String providerType,PaymentMethod paymentMethod){id=UUID.randomUUID().toString();booking=b;paymentReference=ref;this.amount=amount;this.status=status;this.providerType=providerType;this.paymentMethod=paymentMethod;this.providerPaymentId=ref;processedAt=Instant.now();}
 public String getProviderType(){return providerType;} public String getProviderPaymentId(){return providerPaymentId==null?paymentReference:providerPaymentId;} public BigDecimal getAmount(){return amount;} public BigDecimal getRefundedAmount(){return refundedAmount;} public com.railyatra.payment.PaymentStatus getStatus(){return status;}
 public void recordRefund(BigDecimal value){if(value.signum()<0||refundedAmount.add(value).compareTo(amount)>0)throw new IllegalStateException("Refund exceeds payment");refundedAmount=refundedAmount.add(value);status=refundedAmount.compareTo(amount)==0?com.railyatra.payment.PaymentStatus.REFUNDED:com.railyatra.payment.PaymentStatus.PARTIALLY_REFUNDED;}
}
