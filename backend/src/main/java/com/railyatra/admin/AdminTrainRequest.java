package com.railyatra.admin;

import com.railyatra.railway.domain.TrainType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record AdminTrainRequest(
    @NotBlank @Pattern(regexp="[A-Za-z0-9]{1,10}") String trainNumber,
    @NotBlank @Size(max=160) String name,
    @NotNull TrainType type,
    @NotBlank @Size(max=10) String source,
    @NotBlank @Size(max=10) String destination,
    @NotEmpty @Size(max=7) List<@NotNull DayOfWeek> runningDays,
    @NotEmpty @Size(max=8) List<@NotNull ClassCode> classes,
    @NotNull @Size(min=2,max=100) List<@NotNull @Valid Stop> stops,
    @PositiveOrZero Long version
) {
    public enum ClassCode {
        FIRST_AC("1A", "AC First Class"), SECOND_AC("2A", "AC 2 Tier"), THIRD_AC("3A", "AC 3 Tier"),
        ECONOMY_AC("3E", "AC 3 Tier Economy"), SL("SL", "Sleeper"), CC("CC", "AC Chair Car"),
        EC("EC", "Executive Chair Car"), SECOND_SITTING("2S", "Second Sitting");
        private final String code, label;
        ClassCode(String code, String label){this.code=code;this.label=label;}
        @com.fasterxml.jackson.annotation.JsonValue public String code(){return code;}
        public String label(){return label;}
        @com.fasterxml.jackson.annotation.JsonCreator public static ClassCode parse(String value){
            for(var cls:values()) if(cls.code.equals(value)) return cls;
            throw new IllegalArgumentException("Unsupported train class");
        }
    }
    public record Stop(@NotBlank @Size(max=10) String stationCode, @Min(1) int stopSequence,
        LocalTime arrival, LocalTime departure, @Min(0) @Max(14) int dayOffset,
        @Min(0) @Max(30000) int distanceKm) { }
}
