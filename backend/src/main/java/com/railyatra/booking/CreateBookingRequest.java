package com.railyatra.booking;
import com.railyatra.passenger.PassengerDetailsRequest;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.util.List;
public record CreateBookingRequest(
 @NotBlank @Size(max=36) String holdId,
 @NotBlank @Size(max=10) String originStationCode,
 @NotBlank @Size(max=10) String destinationStationCode,
 @NotEmpty @Size(max=6) List<@Valid PassengerDetailsRequest> passengers,
 @NotBlank @Size(max=100) String paymentReference) {}
