package com.railyatra.booking;
import java.math.BigDecimal;
public record FareResponse(BigDecimal baseFare, BigDecimal reservationCharge, BigDecimal tax, BigDecimal serviceCharge, BigDecimal total) { }
