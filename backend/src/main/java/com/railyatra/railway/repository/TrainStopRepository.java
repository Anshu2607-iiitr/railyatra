package com.railyatra.railway.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.railyatra.railway.domain.TrainStop;
public interface TrainStopRepository extends JpaRepository<TrainStop, Long> {
 List<TrainStop> findByTrainIdOrderByStopSequenceAsc(Long trainId);
 boolean existsByTrainIdAndStationId(Long trainId, Long stationId);
 java.util.Optional<TrainStop> findByTrainIdAndStationStationCode(Long trainId, String stationCode);
 @org.springframework.data.jpa.repository.Modifying
 @org.springframework.data.jpa.repository.Query("delete from TrainStop s where s.train.id = :trainId")
 void deleteRoute(@org.springframework.data.repository.query.Param("trainId") Long trainId);
}
