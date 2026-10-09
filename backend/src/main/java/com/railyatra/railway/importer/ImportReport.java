package com.railyatra.railway.importer;
import java.time.Duration;
import java.util.Map;
public record ImportReport(String dataSource, Duration elapsed, Map<String, Integer> records) { }
