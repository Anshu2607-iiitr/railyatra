package com.railyatra.railway.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.railyatra.railway.domain.FareRule;
public interface FareRuleRepository extends JpaRepository<FareRule, Long> {
 java.util.Optional<FareRule> findByTrainIdAndTrainClassIdAndOriginStationStationCodeAndDestinationStationStationCodeAndActiveTrue(Long trainId, Long classId, String origin, String destination);
 java.util.Optional<FareRule> findFirstByTrainIdAndTrainClassIdAndActiveTrue(Long trainId, Long classId);
}
