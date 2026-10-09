package com.railyatra.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminDashboardRepository {
    private final JdbcTemplate jdbc;

    public AdminDashboardRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // Aggregation happens in the database. No loading trains/bookings into application memory.
    public AdminDashboardResponse snapshot(LocalDate date, String zone, Instant start, Instant end, Instant now) {
        return jdbc.queryForObject("""
            SELECT
              (SELECT COUNT(*) FROM trains) AS total_trains,
              (SELECT COUNT(*) FROM stations) AS stations,
              (SELECT COUNT(*) FROM bookings WHERE created_at >= ? AND created_at < ?) AS bookings_today,
              COALESCE(SUM(CASE WHEN current_status = 'CONFIRMED' THEN 1 ELSE 0 END), 0) AS confirmed,
              COALESCE(SUM(CASE WHEN current_status = 'RAC' THEN 1 ELSE 0 END), 0) AS rac,
              COALESCE(SUM(CASE WHEN current_status = 'WAITLISTED' THEN 1 ELSE 0 END), 0) AS waiting_list,
              (SELECT COUNT(*) FROM cancellation_quotes WHERE confirmed_at IS NOT NULL) AS cancellations,
              (SELECT COALESCE(SUM(amount), 0) FROM payment_transactions
                WHERE status IN ('SUCCESS', 'PARTIALLY_REFUNDED', 'REFUNDED')) AS gross_revenue,
              (SELECT COALESCE(SUM(refunded_amount), 0) FROM payment_transactions
                WHERE status IN ('SUCCESS', 'PARTIALLY_REFUNDED', 'REFUNDED')) AS completed_refunds,
              (SELECT COUNT(*) FROM seat_holds WHERE status = 'ACTIVE' AND expires_at > ?) AS active_holds
            FROM bookings
            """, (rs, row) -> {
                var gross = rs.getBigDecimal("gross_revenue").setScale(2);
                var refunds = rs.getBigDecimal("completed_refunds").setScale(2);
                return new AdminDashboardResponse(date, zone, rs.getLong("total_trains"), rs.getLong("stations"),
                    rs.getLong("bookings_today"), rs.getLong("confirmed"), rs.getLong("rac"),
                    rs.getLong("waiting_list"), rs.getLong("cancellations"), gross.subtract(refunds),
                    gross, refunds, rs.getLong("active_holds"));
            // Flyway uses timestamp without timezone; Hibernate stores Instant in UTC.
            // LocalDateTime binds UTC wall time without the JDBC driver's JVM timezone conversion.
            }, LocalDateTime.ofInstant(start, ZoneOffset.UTC), LocalDateTime.ofInstant(end, ZoneOffset.UTC),
               LocalDateTime.ofInstant(now, ZoneOffset.UTC));
    }
}
