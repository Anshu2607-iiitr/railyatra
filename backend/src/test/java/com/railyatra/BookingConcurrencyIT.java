package com.railyatra;

import com.railyatra.auth.AuthPrincipal;
import com.railyatra.auth.User;
import com.railyatra.auth.UserRepository;
import com.railyatra.passenger.BerthPreference;
import com.railyatra.passenger.Gender;
import com.railyatra.passenger.PassengerDetailsRequest;
import com.railyatra.railway.domain.Coach;
import com.railyatra.railway.domain.Seat;
import com.railyatra.railway.domain.SeatHoldStatus;
import com.railyatra.railway.domain.HoldReservationType;
import com.railyatra.railway.domain.SeatInventory;
import com.railyatra.railway.domain.SeatType;
import com.railyatra.railway.domain.Train;
import com.railyatra.railway.domain.TrainClass;
import com.railyatra.railway.dto.CreateSeatHoldRequest;
import com.railyatra.railway.repository.CoachRepository;
import com.railyatra.railway.repository.SeatAllocationRepository;
import com.railyatra.railway.repository.SeatHoldRepository;
import com.railyatra.railway.repository.SeatInventoryRepository;
import com.railyatra.railway.repository.SeatRepository;
import com.railyatra.railway.repository.TrainClassRepository;
import com.railyatra.railway.repository.TrainRepository;
import com.railyatra.railway.service.SeatAllocationService;
import com.railyatra.railway.service.SeatHoldService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"railyatra.seed.enabled=false", "railyatra.holds.duration-seconds=300", "railyatra.holds.expiry-check-ms=60000"})
class BookingConcurrencyIT {
    @Autowired private SeatHoldService holds;
    @Autowired private SeatAllocationService allocations;
    @Autowired private UserRepository users;
    @Autowired private TrainRepository trains;
    @Autowired private com.railyatra.railway.repository.TrainScheduleRepository schedules;
    @Autowired private TrainClassRepository classes;
    @Autowired private CoachRepository coaches;
    @Autowired private SeatRepository seats;
    @Autowired private SeatInventoryRepository inventories;
    @Autowired private SeatHoldRepository holdRepository;
    @Autowired private SeatAllocationRepository allocationRepository;

    @Test
    void onlyOneConcurrentHoldGetsTheFinalConfirmedSeatAndTheOtherIsWaitlisted() throws Exception {
        Fixture fixture = fixture("H");
        User userA = users.save(new User("User A", "a" + UUID.randomUUID() + "@test.local", "900" + randomDigits(), "hash"));
        User userB = users.save(new User("User B", "b" + UUID.randomUUID() + "@test.local", "901" + randomDigits(), "hash"));
        CreateSeatHoldRequest request = holdRequest(fixture.train(), fixture.date());

        List<Boolean> outcomes = concurrently(
            () -> createHold(userA, "hold-a-" + UUID.randomUUID(), request),
            () -> createHold(userB, "hold-b-" + UUID.randomUUID(), request));

        assertThat(outcomes).containsExactlyInAnyOrder(true, true);
        SeatInventory persisted = inventories.findById(fixture.inventory().getId()).orElseThrow();
        assertThat(persisted.getAvailableCount()).isZero();
        assertThat(holdRepository.countByInventoryIdAndStatus(fixture.inventory().getId(), SeatHoldStatus.ACTIVE)).isEqualTo(2);
        assertThat(holdRepository.findAll().stream().filter(hold -> hold.getInventory().getId().equals(fixture.inventory().getId()) && hold.getReservationType() == HoldReservationType.CONFIRMED).count()).isEqualTo(1);
    }

    @Test
    void concurrentAllocatorsCannotAssignTheSamePhysicalSeatTwice() throws Exception {
        Fixture fixture = fixture("A");
        PassengerDetailsRequest passenger = new PassengerDetailsRequest("Traveller", 28, Gender.MALE, BerthPreference.LOWER);

        List<Boolean> outcomes = concurrently(
            () -> allocate(fixture.inventory().getId(), "allocation-a-" + UUID.randomUUID(), passenger),
            () -> allocate(fixture.inventory().getId(), "allocation-b-" + UUID.randomUUID(), passenger));

        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertThat(allocationRepository.countByInventoryId(fixture.inventory().getId())).isEqualTo(1);
        SeatInventory persisted = inventories.findById(fixture.inventory().getId()).orElseThrow();
        assertThat(persisted.getAvailableCount()).isZero();
    }

    private boolean createHold(User user, String key, CreateSeatHoldRequest request) {
        try { holds.create(new AuthPrincipal(user.getId(), user.getEmail(), "ROLE_USER"), key, request); return true; }
        catch (ResponseStatusException exception) { return false; }
    }
    private boolean allocate(Long inventoryId, String reference, PassengerDetailsRequest passenger) {
        try { allocations.allocateConfirmedSeats(inventoryId, reference, List.of(passenger)); return true; }
        catch (ResponseStatusException exception) { return false; }
    }
    @SafeVarargs
    private final List<Boolean> concurrently(ThrowingBooleanSupplier... operations) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(operations.length);
        CyclicBarrier barrier = new CyclicBarrier(operations.length);
        try {
            List<Future<Boolean>> futures = java.util.Arrays.stream(operations).map(operation -> executor.submit(() -> { barrier.await(); return operation.get(); })).toList();
            return futures.stream().map(future -> { try { return future.get(); } catch (Exception exception) { return false; } }).toList();
        } finally { executor.shutdownNow(); }
    }
    private Fixture fixture(String prefix) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 7);
        Train train = trains.save(new Train("9" + id.substring(0, 6), "Concurrency Test " + prefix));
        schedules.save(new com.railyatra.railway.domain.TrainSchedule(train,"1111111",LocalDate.now().minusDays(1),null));
        TrainClass trainClass = classes.save(new TrainClass(train, "3A", "AC 3 Tier"));
        Coach coach = coaches.save(new Coach(trainClass, "B1", 1));
        seats.save(new Seat(coach, "1", SeatType.LOWER));
        LocalDate date = LocalDate.now().plusDays(1);
        SeatInventory inventory = inventories.saveAndFlush(new SeatInventory(train, trainClass, date, "GENERAL", 1, 0));
        return new Fixture(train, inventory, date);
    }
    private CreateSeatHoldRequest holdRequest(Train train, LocalDate date) {
        return new CreateSeatHoldRequest(train.getTrainNumber(), date, "3A", "GENERAL", List.of(new PassengerDetailsRequest("Traveller", 28, Gender.MALE, BerthPreference.LOWER)));
    }
    private String randomDigits() { return String.valueOf((long) (Math.random() * 10_000_000L)); }
    private record Fixture(Train train, SeatInventory inventory, LocalDate date) { }
    @FunctionalInterface private interface ThrowingBooleanSupplier { boolean get() throws Exception; }
}
