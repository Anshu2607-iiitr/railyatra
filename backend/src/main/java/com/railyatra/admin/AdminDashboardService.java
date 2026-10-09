package com.railyatra.admin;

import java.time.Instant;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDashboardService {
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private final AdminDashboardRepository repository;

    public AdminDashboardService(AdminDashboardRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public AdminDashboardResponse snapshot() { return snapshotAt(Instant.now()); }

    AdminDashboardResponse snapshotAt(Instant now) {
        var date = now.atZone(BUSINESS_ZONE).toLocalDate();
        return repository.snapshot(date, BUSINESS_ZONE.getId(), date.atStartOfDay(BUSINESS_ZONE).toInstant(),
            date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant(), now);
    }
}
