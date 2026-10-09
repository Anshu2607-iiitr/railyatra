package com.railyatra.railway.dto;

import com.railyatra.passenger.PassengerDetailsRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record CreateSeatHoldRequest(
    @NotBlank String trainNumber,
    @NotNull LocalDate journeyDate,
    @NotBlank String classCode,
    String quota,
    @NotEmpty @Size(max = 6) List<@Valid PassengerDetailsRequest> passengers) { }
