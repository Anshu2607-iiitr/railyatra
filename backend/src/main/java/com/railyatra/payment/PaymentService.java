package com.railyatra.payment;

import com.railyatra.auth.AuthPrincipal;
import com.railyatra.railway.domain.SeatHold;
import com.railyatra.railway.domain.SeatHoldStatus;
import com.railyatra.railway.service.SeatHoldService;
import com.railyatra.booking.FareCalculatorService;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {
    private final PaymentProvider provider;
    private final SeatHoldService holds; private final FareCalculatorService fares;
    public PaymentService(PaymentProvider provider, SeatHoldService holds, FareCalculatorService fares) { this.provider = provider; this.holds = holds; this.fares=fares; }

    @Transactional
    public PaymentResponse create(AuthPrincipal principal, CreatePaymentRequest request) {
        SeatHold hold = holds.lockActiveHoldForBooking(principal.id(), request.holdId());
        if (hold.getStatus() != SeatHoldStatus.ACTIVE) throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat hold is no longer active");
        if (request.passengers().size() != hold.getPassengerCount()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passenger count must match the active seat hold");
        BigDecimal amount = fares.calculate(hold.getTrain(), hold.getTrainClass(), request.originStationCode(), request.destinationStationCode(), request.passengers().size()).total();
        PaymentProvider.PaymentInitiation payment = provider.createPayment(new PaymentProvider.CreatePaymentCommand(amount, "INR", request.method(), hold.getId()));
        return new PaymentResponse(payment.providerType(), payment.providerPaymentId(), payment.status(), request.method(), amount, payment.simulated(), "Development payment simulation — no real funds are collected.");
    }
}
