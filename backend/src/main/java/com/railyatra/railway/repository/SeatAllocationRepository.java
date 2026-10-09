package com.railyatra.railway.repository;

import com.railyatra.railway.domain.Seat;
import com.railyatra.railway.domain.SeatAllocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatAllocationRepository extends JpaRepository<SeatAllocation, Long> {
    List<SeatAllocation> findByAllocationReferenceOrderByPassengerIndexAsc(String allocationReference);
    long countByInventoryId(Long inventoryId);
    @Query("select s from Seat s join s.coach c where c.trainClass.id = :classId and s.active = true and s.id not in (select a.seat.id from SeatAllocation a where a.inventory.id = :inventoryId and a.releasedAt is null) order by c.coachPosition asc, s.seatNumber asc")
    List<Seat> findFreeSeats(@Param("inventoryId") Long inventoryId, @Param("classId") Long classId);
}
