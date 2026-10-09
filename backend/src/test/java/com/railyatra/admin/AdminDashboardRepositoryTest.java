package com.railyatra.admin;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Runs the real Flyway schema and aggregate SQL; every fixture is rolled back. */
@JdbcTest
@Import({AdminDashboardRepository.class, AdminDashboardService.class})
class AdminDashboardRepositoryTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminDashboardService dashboard;
    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");
    private static final Instant START = Instant.parse("2026-10-09T18:30:00Z");
    private static final Instant END = Instant.parse("2026-10-10T18:30:00Z");

    @Test void emptyDatabaseReturnsRealZeros() {
        var stats = dashboard.snapshotAt(NOW);
        assertEquals(0, stats.totalTrains()); assertEquals(0, stats.stations());
        assertEquals(0, stats.bookingsToday()); assertEquals(0, stats.confirmed());
        assertEquals(0, stats.rac()); assertEquals(0, stats.waitingList());
        assertEquals(0, stats.cancellations()); assertEquals(0, stats.activeSeatHolds());
        assertEquals(new BigDecimal("0.00"), stats.revenue());
    }

    @Test void countsStatusesIndiaDayBoundariesSettledRefundsAndUnexpiredHolds() {
        jdbc.update("INSERT INTO stations(id,station_code,station_name,city,state) VALUES(1,'TONE','One','City','State'),(2,'TTWO','Two','City','State')");
        jdbc.update("INSERT INTO trains(id,train_number,train_name) VALUES(1,'T0001','Test train')");
        jdbc.update("INSERT INTO train_classes(id,train_id,class_code,class_name) VALUES(1,1,'SL','Sleeper')");
        jdbc.update("INSERT INTO users(id,full_name,email,phone,password_hash,role) VALUES(1,'Test User','admin-stats@test.local','9000000001','test-hash','ROLE_USER')");
        jdbc.update("INSERT INTO seat_inventories(id,train_id,journey_date,train_class_id,quota,total_capacity,available_count,rac_capacity,rac_available_count) VALUES(1,1,'2026-10-15',1,'GENERAL',20,20,10,10)");
        booking("cnf", "CONFIRMED", START);
        booking("rac", "RAC", NOW);
        booking("wl", "WAITLISTED", END.minusSeconds(1));
        booking("cancelled", "CANCELLED", START.minusSeconds(1));
        booking("failed", "FAILED", END);
        booking("pending", "PENDING_PAYMENT", NOW);
        payment("cnf", "PARTIALLY_REFUNDED", "1000.00", "200.00");
        payment("rac", "FAILED", "800.00", "0.00");
        payment("wl", "REFUNDED", "500.00", "500.00");
        payment("pending", "PENDING", "900.00", "0.00");
        quote("q1", "cancelled", NOW);
        quote("q2", "cnf", NOW); // Partial cancellation also counts as one completed operation.
        quote("q3", "rac", null); // Unconfirmed quote is not a cancellation.
        hold("active", "ACTIVE", NOW.plusSeconds(120));
        hold("expired-not-swept", "ACTIVE", NOW);
        hold("released", "RELEASED", NOW.plusSeconds(120));

        var stats = dashboard.snapshotAt(NOW);
        assertEquals("2026-10-10", stats.businessDate().toString());
        assertEquals("Asia/Kolkata", stats.timeZone());
        assertEquals(1, stats.totalTrains()); assertEquals(2, stats.stations());
        assertEquals(4, stats.bookingsToday());
        assertEquals(1, stats.confirmed()); assertEquals(1, stats.rac()); assertEquals(1, stats.waitingList());
        assertEquals(2, stats.cancellations()); assertEquals(1, stats.activeSeatHolds());
        assertEquals(new BigDecimal("1500.00"), stats.grossRevenue());
        assertEquals(new BigDecimal("700.00"), stats.completedRefunds());
        assertEquals(new BigDecimal("800.00"), stats.revenue());
        jdbc.update("UPDATE bookings SET current_status='CONFIRMED',status='CONFIRMED' WHERE id='rac'");
        jdbc.update("UPDATE payment_transactions SET refunded_amount=400 WHERE booking_id='cnf'");
        var refreshed = dashboard.snapshotAt(NOW);
        assertEquals(2, refreshed.confirmed()); assertEquals(0, refreshed.rac());
        assertEquals(new BigDecimal("600.00"), refreshed.revenue());
    }

    private void hold(String id, String status, Instant expiry) {
        jdbc.update("""
            INSERT INTO seat_holds(id,user_id,train_id,journey_date,train_class_id,quota,passenger_count,
              inventory_id,request_key,created_at,expires_at,status)
            VALUES(?,1,1,'2026-10-15',1,'GENERAL',1,1,?,?,?,?)
            """, id, id, utc(NOW), utc(expiry), status);
    }
    private void booking(String id, String status, Instant created) {
        hold(id, "CONSUMED", NOW.plusSeconds(600));
        jdbc.update("""
            INSERT INTO bookings(id,user_id,hold_id,train_id,train_class_id,origin_station_id,
              destination_station_id,journey_date,quota,internal_booking_reference,provider_type,
              idempotency_key,total_fare,status,booking_status,current_status,created_at,updated_at)
            VALUES(?,1,?,1,1,1,2,'2026-10-15','GENERAL',?,'LOCAL',?,1000,?,?,?,?,?)
            """, id, id, "RY" + id, id, status, status, status, utc(created), utc(created));
    }
    private void payment(String id, String status, String amount, String refunded) {
        jdbc.update("INSERT INTO payment_transactions(id,booking_id,payment_reference,amount,status,processed_at,refunded_amount) VALUES(?,?,?,?,?,?,?)",
            id, id, "PAY" + id, new BigDecimal(amount), status, utc(NOW), new BigDecimal(refunded));
    }
    private void quote(String id, String booking, Instant confirmed) {
        jdbc.update("INSERT INTO cancellation_quotes(id,booking_id,passenger_ids,charge,refund,expires_at,confirmed_at) VALUES(?,?, '1',60,440,?,?)",
            id, booking, utc(NOW.plusSeconds(60)), confirmed == null ? null : utc(confirmed));
    }
    private LocalDateTime utc(Instant instant) { return LocalDateTime.ofInstant(instant, ZoneOffset.UTC); }
}
