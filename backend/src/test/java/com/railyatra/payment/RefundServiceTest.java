package com.railyatra.payment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import com.railyatra.booking.BookingStatus;
import java.math.BigDecimal;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
class RefundServiceTest {
 private final RefundService service=new RefundService(null,null,null,null,mock(PlatformTransactionManager.class),null,180,null);
 private final Instant journey=Instant.parse("2026-11-10T12:00:00Z");
 private RefundService.Calculation calculate(long seconds,BookingStatus status,RefundRules rules){return service.calculate(new BigDecimal("1000.00"),journey,journey.minusSeconds(seconds),"3A",status,rules);}
 @Test void usesExactTimeWindowBoundaries(){
  var rules=new RefundRules();
  assertEquals(new BigDecimal("60.00"),calculate(48*3600,BookingStatus.CONFIRMED,rules).deduction());
  assertEquals(new BigDecimal("250.00"),calculate(48*3600-1,BookingStatus.CONFIRMED,rules).deduction());
  assertEquals(new BigDecimal("250.00"),calculate(12*3600,BookingStatus.CONFIRMED,rules).deduction());
  assertEquals(new BigDecimal("500.00"),calculate(12*3600-1,BookingStatus.CONFIRMED,rules).deduction());
  assertEquals(new BigDecimal("0.00"),calculate(0,BookingStatus.CONFIRMED,rules).refund());
 }
 @Test void honorsClassAndPassengerStatusRules(){
  var rules=new RefundRules();rules.setClassCharges(Map.of("3A",new BigDecimal("120.00")));
  assertEquals(new BigDecimal("120.00"),calculate(72*3600,BookingStatus.CONFIRMED,rules).deduction());
  assertEquals(new BigDecimal("20.00"),calculate(3600,BookingStatus.RAC,rules).deduction());
  assertEquals(new BigDecimal("1000.00"),calculate(3600,BookingStatus.WAITLISTED,rules).refund());
 }
 @Test void neverRefundsMoreThanPaidAndRejectsInvalidInputs(){
  var rules=new RefundRules();
  var result=service.calculate(new BigDecimal("10.00"),journey,journey.minusSeconds(72*3600),"3A",BookingStatus.CONFIRMED,rules);
  assertEquals(new BigDecimal("0.00"),result.refund());assertEquals(result.paid(),result.deduction().add(result.refund()));
  assertThrows(IllegalArgumentException.class,()->service.calculate(new BigDecimal("-1.00"),journey,journey,"SL",BookingStatus.RAC,rules));
  assertThrows(IllegalArgumentException.class,()->calculate(3600,BookingStatus.FAILED,rules));
  rules.setNearHours(100);assertThrows(IllegalArgumentException.class,()->calculate(3600,BookingStatus.RAC,rules));
 }
}
