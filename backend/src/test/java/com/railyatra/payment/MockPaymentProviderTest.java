package com.railyatra.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MockPaymentProviderTest {
    @Test
    void verifiesOnlyAProviderCreatedPaymentForTheExpectedAmount() {
        MockPaymentProvider provider = new MockPaymentProvider(org.mockito.Mockito.mock(MockRefundSettlementService.class));
        PaymentProvider.PaymentInitiation created = provider.createPayment(new PaymentProvider.CreatePaymentCommand(new BigDecimal("1250.00"), "INR", PaymentMethod.UPI, "hold-1"));

        assertEquals(PaymentStatus.PENDING, created.status());
        assertEquals(PaymentStatus.SUCCESS, provider.verifyPayment(created.providerPaymentId(), new BigDecimal("1250.00")).status());
        assertEquals(PaymentStatus.FAILED, provider.verifyPayment(created.providerPaymentId(), new BigDecimal("1251.00")).status());
    }
}
