package com.railyatra;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.railyatra.railway.service.TrainSearchService;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("performance")
@SpringBootTest(properties = {"railyatra.seed.enabled=false", "railyatra.import.command=synthetic"})
class TrainSearchPerformanceIT {
  @Autowired TrainSearchService searchService;
  @Test void paginatedIntermediateStopSearchStaysWithinTwoSecondsForSyntheticScale() {
    Instant start = Instant.now();
    var result = searchService.search("S0005", "S0010", LocalDate.of(2026, 10, 2), "3A", "GN", 0, 10, "DEPARTURE");
    assertTrue(Duration.between(start, Instant.now()).compareTo(Duration.ofSeconds(2)) < 0);
    assertTrue(result.getTotalElements() >= 0);
  }
}
