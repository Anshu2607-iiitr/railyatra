package com.railyatra.railway.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.railyatra.railway.domain.TrainClass;
public interface TrainClassRepository extends JpaRepository<TrainClass, Long> {
 boolean existsByTrainIdAndClassCode(Long trainId, String classCode);
 java.util.Optional<TrainClass> findByTrainIdAndClassCode(Long trainId,String classCode);
 java.util.List<TrainClass> findByTrainIdOrderByClassCodeAsc(Long trainId);
}
