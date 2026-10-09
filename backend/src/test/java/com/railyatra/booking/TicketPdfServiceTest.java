package com.railyatra.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.railyatra.auth.AuthPrincipal;
import com.railyatra.passenger.Gender;
import com.railyatra.railway.domain.Station;
import com.railyatra.railway.domain.Train;
import com.railyatra.railway.domain.TrainClass;
import com.railyatra.railway.domain.TrainStop;
import com.railyatra.railway.repository.TrainStopRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

class TicketPdfServiceTest {
    @Test
    void generatesPdfOnlyForTheBookingOwner() {
        BookingRepository bookings = Mockito.mock(BookingRepository.class);
        BookingPassengerRepository passengers = Mockito.mock(BookingPassengerRepository.class);
        TrainStopRepository stops = Mockito.mock(TrainStopRepository.class);
        FareCalculatorService fares = Mockito.mock(FareCalculatorService.class);
        Booking booking = Mockito.mock(Booking.class);
        Train train = Mockito.mock(Train.class);
        TrainClass trainClass = Mockito.mock(TrainClass.class);
        Station origin = Mockito.mock(Station.class); Station destination = Mockito.mock(Station.class);
        TrainStop departure = Mockito.mock(TrainStop.class); TrainStop arrival = Mockito.mock(TrainStop.class);
        BookingPassenger passenger = Mockito.mock(BookingPassenger.class);
        when(bookings.findByInternalBookingReference("RYABCDE123")).thenReturn(Optional.of(booking));
        when(booking.getUserId()).thenReturn(7L); when(booking.getId()).thenReturn("booking-1");
        when(booking.getTrain()).thenReturn(train); when(train.getId()).thenReturn(11L);
        when(booking.getTrainClass()).thenReturn(trainClass); when(trainClass.getClassCode()).thenReturn("3A");
        when(booking.getOriginStation()).thenReturn(origin); when(origin.getStationCode()).thenReturn("RNC"); when(origin.getStationName()).thenReturn("Ranchi Junction");
        when(booking.getDestinationStation()).thenReturn(destination); when(destination.getStationCode()).thenReturn("NDLS");
        when(booking.getInternalBookingReference()).thenReturn("RYABCDE123"); when(booking.getProviderType()).thenReturn("LOCAL");
        when(booking.getCreatedAt()).thenReturn(Instant.parse("2026-10-09T10:00:00Z")); when(booking.getJourneyDate()).thenReturn(LocalDate.of(2026, 10, 10));
        when(booking.getQuota()).thenReturn("GENERAL"); when(booking.getPaymentReference()).thenReturn("PAY-123");
        when(booking.getBookingStatus()).thenReturn(BookingStatus.CONFIRMED); when(booking.getCurrentStatus()).thenReturn(BookingStatus.CONFIRMED);
        when(train.getTrainNumber()).thenReturn("18622"); when(train.getTrainName()).thenReturn("Ranchi Express");
        when(passengers.findByBookingIdOrderByPassengerIndexAsc("booking-1")).thenReturn(List.of(passenger));
        when(passenger.getPassengerName()).thenReturn("Asha Kumar"); when(passenger.getAge()).thenReturn(28); when(passenger.getGender()).thenReturn(Gender.FEMALE);
        when(passenger.getCoachCode()).thenReturn("B1"); when(passenger.getSeatNumber()).thenReturn("21"); when(passenger.getBerthType()).thenReturn("LOWER");
        when(stops.findByTrainIdAndStationStationCode(11L, "RNC")).thenReturn(Optional.of(departure));
        when(stops.findByTrainIdAndStationStationCode(11L, "NDLS")).thenReturn(Optional.of(arrival));
        when(departure.getDepartureTime()).thenReturn(LocalTime.of(16, 25)); when(arrival.getArrivalTime()).thenReturn(LocalTime.of(10, 10));
        when(fares.calculate(train, trainClass, "RNC", "NDLS", 1)).thenReturn(new FareResponse(new BigDecimal("1000"), new BigDecimal("20"), new BigDecimal("60"), new BigDecimal("15"), new BigDecimal("1095")));
        TicketPdfService service = new TicketPdfService(bookings, passengers, stops, fares, "http://localhost:5173/pnr?reference=");

        byte[] pdf = service.generate(new AuthPrincipal(7L, "owner@example.test", "ROLE_USER"), "ryabcde123");

        assertEquals('%', (char) pdf[0]);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.generate(new AuthPrincipal(8L, "other@example.test", "ROLE_USER"), "RYABCDE123"));
        assertEquals(404, exception.getStatusCode().value());
    }
}
