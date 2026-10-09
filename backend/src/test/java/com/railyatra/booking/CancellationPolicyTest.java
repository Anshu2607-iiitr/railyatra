package com.railyatra.booking;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
class CancellationPolicyTest {
 private final CancellationPolicy policy=new CancellationPolicy(new BigDecimal("60.00"),new BigDecimal("20.00"));
 @Test void splitsOriginalFareWithoutLosingOrCreatingCents(){
  BigDecimal total=BigDecimal.ZERO;
  for(int i=0;i<6;i++)total=total.add(policy.passengerFare(new BigDecimal("1000.01"),6,i));
  assertEquals(new BigDecimal("1000.01"),total);
 }
 @Test void capsChargeAtPaidFareAndDifferentiatesQueues(){
  assertEquals(new BigDecimal("10.00"),policy.charge(BookingStatus.CONFIRMED,new BigDecimal("10.00")));
  assertEquals(new BigDecimal("20.00"),policy.charge(BookingStatus.RAC,new BigDecimal("500.00")));
  assertEquals(new BigDecimal("0.00"),policy.charge(BookingStatus.WAITLISTED,new BigDecimal("500.00")));
 }
}
