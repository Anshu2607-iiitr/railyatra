package com.railyatra.admin;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Aggregates only: no private booking, passenger or payment details. */
public record AdminDashboardResponse(
    LocalDate businessDate, String timeZone, long totalTrains, long stations,
    long bookingsToday, long confirmed, long rac, long waitingList, long cancellations,
    BigDecimal revenue, BigDecimal grossRevenue, BigDecimal completedRefunds, long activeSeatHolds
) { }
