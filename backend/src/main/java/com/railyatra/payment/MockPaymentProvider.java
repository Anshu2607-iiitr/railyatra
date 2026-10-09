package com.railyatra.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development-only simulator. It stores no card number, CVV, bank account, or UPI credential. */
@Component @ConditionalOnProperty(name="railyatra.payment-provider",havingValue="MOCK",matchIfMissing=true)
public class MockPaymentProvider implements PaymentProvider {
    private final MockRefundSettlementService refunds;
    public MockPaymentProvider(MockRefundSettlementService refunds){this.refunds=refunds;}
    @Override public String type(){return "MOCK";}
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    @Override public PaymentInitiation createPayment(CreatePaymentCommand command) {
        String id = "mockpay_" + UUID.randomUUID().toString().replace("-", "");
        attempts.put(id, new Attempt(command.amount(), command.method(), PaymentStatus.PENDING, Instant.now()));
        return new PaymentInitiation("MOCK", id, PaymentStatus.PENDING, true);
    }
    @Override public PaymentVerification verifyPayment(String providerPaymentId, BigDecimal expectedAmount) {
        Attempt attempt = attempts.get(providerPaymentId);
        if (attempt == null || attempt.amount().compareTo(expectedAmount) != 0 || attempt.status() == PaymentStatus.FAILED)
            return new PaymentVerification("MOCK", providerPaymentId, PaymentStatus.FAILED, expectedAmount, attempt == null ? PaymentMethod.UPI : attempt.method());
        Attempt success = new Attempt(attempt.amount(), attempt.method(), PaymentStatus.SUCCESS, attempt.createdAt());
        attempts.put(providerPaymentId, success);
        return new PaymentVerification("MOCK", providerPaymentId, PaymentStatus.SUCCESS, attempt.amount(), attempt.method());
    }
    @Override public RefundResult refund(RefundCommand command) {return refunds.refund(command);}
    private record Attempt(BigDecimal amount, PaymentMethod method, PaymentStatus status, Instant createdAt) { }
}
