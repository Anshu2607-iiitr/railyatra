package com.railyatra.passenger;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PassengerBatchRequest(@NotEmpty @Size(max = 6) List<@Valid PassengerDetailsRequest> passengers) { }
