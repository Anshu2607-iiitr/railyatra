package com.railyatra.railway.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.railyatra.railway.domain.Seat;
public interface SeatRepository extends JpaRepository<Seat, Long> { long countByCoachTrainClassId(Long trainClassId); }
