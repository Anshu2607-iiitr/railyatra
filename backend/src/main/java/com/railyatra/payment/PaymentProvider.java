package com.railyatra.payment;

import java.math.BigDecimal;

/** Adapter boundary for local simulation and future authorised payment gateways. */
public interface PaymentProvider {
    PaymentInitiation createPayment(CreatePaymentCommand command);
    PaymentVerification verifyPayment(String providerPaymentId, BigDecimal expectedAmount);
    String type();
    /** Providers must replay the same result for the same idempotency key, including after timeouts. */
    RefundResult refund(RefundCommand command);
    default RefundResult refund(String providerPaymentId, BigDecimal amount) { return refund(new RefundCommand(providerPaymentId,amount,java.util.UUID.randomUUID().toString())); }
    record RefundCommand(String providerPaymentId, BigDecimal amount, String idempotencyKey) { }

    record CreatePaymentCommand(BigDecimal amount, String currency, PaymentMethod method, String merchantReference) { }
    record PaymentInitiation(String providerType, String providerPaymentId, PaymentStatus status, boolean simulated) { }
    record PaymentVerification(String providerType, String providerPaymentId, PaymentStatus status, BigDecimal verifiedAmount, PaymentMethod method) { }
    record RefundResult(String providerType, String providerPaymentId, PaymentStatus status, BigDecimal refundedAmount) { }
}
