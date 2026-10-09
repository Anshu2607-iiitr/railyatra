package com.railyatra.passenger;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PassengerDetailsRequest(
    @NotBlank @Size(max = 140) @Pattern(regexp = "^[\\p{L} .'-]+$", message = "Name may contain letters, spaces, apostrophes, dots, and hyphens only") String name,
    @NotNull @Min(1) @Max(125) Integer age,
    @NotNull Gender gender,
    @NotNull BerthPreference berthPreference) { }
