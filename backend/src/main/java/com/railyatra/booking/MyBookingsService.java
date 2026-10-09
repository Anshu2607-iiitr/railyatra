package com.railyatra.booking;

import com.railyatra.auth.AuthPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MyBookingsService {
    private final BookingRepository bookings;
    private final BookingPassengerRepository passengers;
    public MyBookingsService(BookingRepository bookings,BookingPassengerRepository passengers) { this.bookings = bookings; this.passengers=passengers; }

    @Transactional(readOnly = true)
    public Result list(AuthPrincipal principal, String tab, int page, int size) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");
        String normalized = tab.toUpperCase(Locale.ROOT);
        if (!List.of("UPCOMING", "COMPLETED", "CANCELLED").contains(normalized) || page < 0 || size < 1 || size > 50)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a valid tab, page >= 0 and size between 1 and 50");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        var result = bookings.findMine(principal.id(), normalized, today,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new Result(result.getContent().stream().map(b -> new Card(b.getInternalBookingReference(),
                b.getTrain().getTrainNumber(), b.getTrain().getTrainName(), b.getOriginStation().getStationCode(),
                b.getDestinationStation().getStationCode(), b.getJourneyDate(), b.getCurrentStatus(),
                b.getCurrentStatusPosition(), (int)passengers.countByBookingIdAndCancelledFalse(b.getId()), b.getTotalFare(),
                b.getTrainClass().getClassCode(), b.getProviderType(), canCancel(b, today))).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    static boolean canCancel(Booking booking, LocalDate today) {
        return !booking.getJourneyDate().isBefore(today) && List.of(BookingStatus.CONFIRMED, BookingStatus.RAC,
                BookingStatus.WAITLISTED).contains(booking.getCurrentStatus());
    }

    public record Card(String internalBookingReference, String trainNumber, String trainName, String origin,
                       String destination, LocalDate journeyDate, BookingStatus currentStatus, Integer currentStatusPosition,
                       int passengerCount, BigDecimal totalFare, String classCode, String providerType, boolean canCancel) { }
    public record Result(List<Card> content, int page, int size, long totalElements, int totalPages) { }
}
