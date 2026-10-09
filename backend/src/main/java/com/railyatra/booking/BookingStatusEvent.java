package com.railyatra.booking;
import java.time.Instant;
public record BookingStatusEvent(String internalReference, String bookingStatus, String currentStatus, Instant changedAt) { }
