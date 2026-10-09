package com.railyatra.railway.repository;

import com.railyatra.railway.domain.SeatHold;
import com.railyatra.railway.domain.SeatHoldStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface SeatHoldRepository extends JpaRepository<SeatHold, String> {
    Optional<SeatHold> findByUserIdAndRequestKey(Long userId, String requestKey);
    long countByInventoryIdAndStatus(Long inventoryId, SeatHoldStatus status);
    List<SeatHold> findByStatusAndExpiresAtLessThanEqual(SeatHoldStatus status, Instant instant);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from SeatHold h where h.id = :id")
    Optional<SeatHold> findByIdForUpdate(@Param("id") String id);
}
