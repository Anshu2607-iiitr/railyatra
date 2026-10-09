package com.railyatra.booking;
import com.railyatra.railway.domain.Train; import com.railyatra.railway.domain.TrainClass; import java.math.*; import org.springframework.stereotype.Service;
@Service public class FareCalculatorService {
 private final FareService fares; public FareCalculatorService(FareService fares){this.fares=fares;}
 public FareResponse calculate(Train train, TrainClass trainClass, String origin, String destination, int passengerCount){
  BigDecimal base=fares.quote(train,trainClass,origin,destination).totalFare().multiply(BigDecimal.valueOf(passengerCount)).setScale(2,RoundingMode.HALF_UP);
  BigDecimal reservation=BigDecimal.valueOf(20L*passengerCount).setScale(2); BigDecimal tax=base.add(reservation).multiply(new BigDecimal("0.05")).setScale(2,RoundingMode.HALF_UP); BigDecimal service=new BigDecimal("15.00");
  return new FareResponse(base,reservation,tax,service,base.add(reservation).add(tax).add(service).setScale(2,RoundingMode.HALF_UP));
 }
}
