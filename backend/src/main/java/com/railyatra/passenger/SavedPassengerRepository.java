package com.railyatra.passenger;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedPassengerRepository extends JpaRepository<SavedPassenger, Long> {
    List<SavedPassenger> findByUserIdOrderByIdAsc(Long userId);
    Optional<SavedPassenger> findByIdAndUserId(Long id, Long userId);
}
