package com.railyatra.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.railyatra.railway.domain.Train;
import com.railyatra.railway.domain.TrainClass;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FareCalculatorServiceTest {
    @Test
    void returnsAllCheckoutPricesFromTheServerCalculator() {
        FareService fares = Mockito.mock(FareService.class);
        Train train = Mockito.mock(Train.class); TrainClass trainClass = Mockito.mock(TrainClass.class);
        when(fares.quote(train, trainClass, "RNC", "NDLS")).thenReturn(new FareService.FareQuote(null, null, new BigDecimal("100.00")));

        FareResponse result = new FareCalculatorService(fares).calculate(train, trainClass, "RNC", "NDLS", 2);

        assertEquals(new BigDecimal("200.00"), result.baseFare());
        assertEquals(new BigDecimal("40.00"), result.reservationCharge());
        assertEquals(new BigDecimal("12.00"), result.tax());
        assertEquals(new BigDecimal("15.00"), result.serviceCharge());
        assertEquals(new BigDecimal("267.00"), result.total());
    }
}
