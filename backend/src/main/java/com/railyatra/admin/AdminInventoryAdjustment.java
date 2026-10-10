package com.railyatra.admin;
import jakarta.validation.constraints.*;
public record AdminInventoryAdjustment(
 @NotNull @Min(0) @Max(10000) Integer totalCapacity,
 @NotNull @Min(0) @Max(10000) Integer racCapacity,
 @NotNull @PositiveOrZero Long expectedVersion,
 @NotBlank @Size(min=5,max=500) String reason) {}
