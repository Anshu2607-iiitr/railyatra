package com.railyatra.payment;
import java.math.BigDecimal;
public record PaymentResponse(String providerType, String paymentReference, PaymentStatus status, PaymentMethod method, BigDecimal amount, boolean simulated, String notice) { }
