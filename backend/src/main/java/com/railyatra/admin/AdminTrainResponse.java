package com.railyatra.admin;

import com.railyatra.railway.domain.TrainType;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record AdminTrainResponse(Long id, String trainNumber, String name, TrainType type, boolean active,
    long version, String dataSource, String source, String destination, List<DayOfWeek> runningDays,
    List<String> classes, List<Stop> stops, boolean configurationLocked) {
    public record Station(String code, String name, String city, String state) { }
    public record Stop(Station station, int stopSequence, LocalTime arrival, LocalTime departure, int dayOffset, int distanceKm) { }
    public record Page(List<AdminTrainResponse> content, int page, int size, long totalElements, int totalPages) { }
}
