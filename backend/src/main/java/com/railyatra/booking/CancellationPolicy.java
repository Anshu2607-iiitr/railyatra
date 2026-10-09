package com.railyatra.booking;
import java.math.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Local development policy, never an official railway cancellation tariff. */
@Component
public class CancellationPolicy {
 private final BigDecimal confirmedCharge, racCharge;
 public CancellationPolicy(@Value("${railyatra.cancellation.confirmed-charge:60.00}") BigDecimal confirmedCharge,@Value("${railyatra.cancellation.rac-charge:20.00}") BigDecimal racCharge){
  if(confirmedCharge.signum()<0||racCharge.signum()<0)throw new IllegalArgumentException("Cancellation charges must not be negative");
  this.confirmedCharge=confirmedCharge;this.racCharge=racCharge;
 }
 public BigDecimal passengerFare(BigDecimal paid,int originalCount,int index){
  BigInteger[] split=paid.setScale(2,RoundingMode.UNNECESSARY).movePointRight(2).toBigIntegerExact().divideAndRemainder(BigInteger.valueOf(originalCount));
  return new BigDecimal(split[0].add(index<split[1].intValueExact()?BigInteger.ONE:BigInteger.ZERO),2);
 }
 public BigDecimal charge(BookingStatus status,BigDecimal passengerFare){return (status==BookingStatus.CONFIRMED?confirmedCharge:status==BookingStatus.RAC?racCharge:BigDecimal.ZERO).min(passengerFare).setScale(2,RoundingMode.HALF_UP);}
}
