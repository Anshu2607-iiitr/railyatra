package com.railyatra.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.railyatra.auth.AuthPrincipal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

class MyBookingsServiceTest {
    @Test void scopesQueryToAuthenticatedUserAndUsesStablePagination() {
        BookingRepository repository = mock(BookingRepository.class);
        when(repository.findMine(eq(17L), eq("UPCOMING"), any(), any())).thenReturn(new PageImpl<>(List.of()));
        var result = new MyBookingsService(repository, mock(BookingPassengerRepository.class)).list(new AuthPrincipal(17L, "owner@example.test", "ROLE_USER"), "upcoming", 2, 10);
        ArgumentCaptor<Pageable> paging = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findMine(eq(17L), eq("UPCOMING"), eq(LocalDate.now(ZoneId.of("Asia/Kolkata"))), paging.capture());
        assertEquals(2, paging.getValue().getPageNumber());
        assertEquals(10, paging.getValue().getPageSize());
        assertNotNull(paging.getValue().getSort().getOrderFor("id"));
        assertEquals(0, result.totalElements());
    }

    @Test void adminDoesNotImplicitlySeeOtherUsersHistory() {
        BookingRepository repository = mock(BookingRepository.class);
        when(repository.findMine(eq(22L), eq("CANCELLED"), any(), any())).thenReturn(new PageImpl<>(List.of()));
        new MyBookingsService(repository, mock(BookingPassengerRepository.class)).list(new AuthPrincipal(22L, "admin@example.test", "ROLE_ADMIN"), "CANCELLED", 0, 10);
        verify(repository).findMine(eq(22L), eq("CANCELLED"), any(), any());
    }

    @Test void rejectsInvalidParametersBeforeQuerying() {
        BookingRepository repository = mock(BookingRepository.class);
        var service = new MyBookingsService(repository, mock(BookingPassengerRepository.class));
        var user = new AuthPrincipal(1L, "user@example.test", "ROLE_USER");
        assertThrows(ResponseStatusException.class, () -> service.list(user, "OTHER", 0, 10));
        assertThrows(ResponseStatusException.class, () -> service.list(user, "UPCOMING", -1, 10));
        assertThrows(ResponseStatusException.class, () -> service.list(user, "COMPLETED", 0, 51));
        assertThrows(ResponseStatusException.class, () -> service.list(user, "COMPLETED", 0, 0));
        assertThrows(ResponseStatusException.class, () -> service.list(null, "UPCOMING", 0, 10));
        verifyNoInteractions(repository);
    }

    @Test void cancellationIsOnlyPermittedForActiveUpcomingReservations() {
        Booking booking = mock(Booking.class);
        LocalDate today = LocalDate.of(2026, 10, 9);
        when(booking.getJourneyDate()).thenReturn(today);
        for (BookingStatus status : List.of(BookingStatus.CONFIRMED, BookingStatus.RAC, BookingStatus.WAITLISTED)) {
            when(booking.getCurrentStatus()).thenReturn(status);
            assertTrue(MyBookingsService.canCancel(booking, today));
        }
        when(booking.getCurrentStatus()).thenReturn(BookingStatus.CANCELLED);
        assertFalse(MyBookingsService.canCancel(booking, today));
        when(booking.getCurrentStatus()).thenReturn(BookingStatus.FAILED);
        assertFalse(MyBookingsService.canCancel(booking, today));
        when(booking.getCurrentStatus()).thenReturn(BookingStatus.CONFIRMED);
        when(booking.getJourneyDate()).thenReturn(today.minusDays(1));
        assertFalse(MyBookingsService.canCancel(booking, today));
    }
}
