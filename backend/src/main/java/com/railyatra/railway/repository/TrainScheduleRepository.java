package com.railyatra.railway.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.railyatra.railway.domain.TrainSchedule;
public interface TrainScheduleRepository extends JpaRepository<TrainSchedule, Long> {
 boolean existsByTrainIdAndEffectiveFrom(Long trainId, java.time.LocalDate effectiveFrom);
 java.util.List<TrainSchedule> findByTrainIdOrderByEffectiveFromDesc(Long trainId);
}
