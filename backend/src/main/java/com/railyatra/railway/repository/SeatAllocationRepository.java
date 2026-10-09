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
}
