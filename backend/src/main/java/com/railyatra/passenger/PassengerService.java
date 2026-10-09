package com.railyatra.passenger;

import com.railyatra.auth.AuthPrincipal;
import com.railyatra.auth.User;
import com.railyatra.auth.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PassengerService {
    private final SavedPassengerRepository savedPassengers;
    private final UserRepository users;

    public PassengerService(SavedPassengerRepository savedPassengers, UserRepository users) {
        this.savedPassengers = savedPassengers;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<SavedPassengerResponse> list(AuthPrincipal principal) {
        return savedPassengers.findByUserIdOrderByIdAsc(principal.id()).stream().map(SavedPassengerResponse::from).toList();
    }

    @Transactional
    public SavedPassengerResponse create(AuthPrincipal principal, PassengerDetailsRequest request) {
        return SavedPassengerResponse.from(savedPassengers.save(new SavedPassenger(currentUser(principal), request)));
    }

    @Transactional
    public SavedPassengerResponse update(AuthPrincipal principal, Long id, PassengerDetailsRequest request) {
        SavedPassenger passenger = ownedPassenger(principal, id);
        passenger.update(request);
        return SavedPassengerResponse.from(passenger);
    }

    @Transactional
    public void delete(AuthPrincipal principal, Long id) { savedPassengers.delete(ownedPassenger(principal, id)); }

    @Transactional(readOnly = true)
    public List<PassengerDetailsRequest> validate(PassengerBatchRequest request) {
        return request.passengers().stream().map(this::normalise).toList();
    }

    private PassengerDetailsRequest normalise(PassengerDetailsRequest passenger) {
        return new PassengerDetailsRequest(passenger.name().trim().replaceAll("\\s+", " "), passenger.age(), passenger.gender(), passenger.berthPreference());
    }
    private User currentUser(AuthPrincipal principal) {
        return users.findById(principal.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }
    private SavedPassenger ownedPassenger(AuthPrincipal principal, Long id) {
        return savedPassengers.findByIdAndUserId(id, principal.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved passenger not found"));
    }
}
