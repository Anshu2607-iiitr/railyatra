package com.railyatra.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.railyatra.railway.domain.SeatHold;
import com.railyatra.railway.domain.Station;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BookingStatusTest {
    @Test
    void confirmedBookingKeepsInternalReferenceSeparateFromProviderPnr() {
        SeatHold hold = Mockito.mock(SeatHold.class);
        when(hold.getTrain()).thenReturn(Mockito.mock(com.railyatra.railway.domain.Train.class));
        when(hold.getTrainClass()).thenReturn(Mockito.mock(com.railyatra.railway.domain.TrainClass.class));
        when(hold.getJourneyDate()).thenReturn(java.time.LocalDate.of(2026, 10, 10));
        when(hold.getQuota()).thenReturn("GENERAL");
        Booking booking = new Booking(7L, hold, Mockito.mock(Station.class), Mockito.mock(Station.class), "RYABCDEFGH12", "retry-1", BigDecimal.valueOf(1200));

        booking.markPaid("SIM-123", BookingStatus.CONFIRMED, null);

        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals(BookingStatus.CONFIRMED, booking.getBookingStatus());
        assertEquals(BookingStatus.CONFIRMED, booking.getCurrentStatus());
        assertEquals("RYABCDEFGH12", booking.getInternalBookingReference());
        assertEquals("LOCAL", booking.getProviderType());
        assertEquals(null, booking.getProviderPnr());
    }

    @Test
    void promotionKeepsOriginalWaitlistStatusButChangesCurrentStatus() {
        SeatHold hold = Mockito.mock(SeatHold.class);
        when(hold.getTrain()).thenReturn(Mockito.mock(com.railyatra.railway.domain.Train.class));
        when(hold.getTrainClass()).thenReturn(Mockito.mock(com.railyatra.railway.domain.TrainClass.class));
        when(hold.getJourneyDate()).thenReturn(java.time.LocalDate.of(2026, 10, 10));
        when(hold.getQuota()).thenReturn("GENERAL");
        Booking booking = new Booking(7L, hold, Mockito.mock(Station.class), Mockito.mock(Station.class), "RYABCDEFGH12", "retry-2", BigDecimal.valueOf(1200));
        booking.markPaid("SIM-124", BookingStatus.WAITLISTED, 3);

        booking.promoteToRac(1);

        assertEquals(BookingStatus.WAITLISTED, booking.getBookingStatus());
        assertEquals(3, booking.getBookingStatusPosition());
        assertEquals(BookingStatus.RAC, booking.getCurrentStatus());
        assertEquals(1, booking.getCurrentStatusPosition());
    }
}
