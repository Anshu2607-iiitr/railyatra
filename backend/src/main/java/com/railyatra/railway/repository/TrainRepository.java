package com.railyatra.railway.repository;
import java.util.Optional;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import com.railyatra.railway.domain.Train;
public interface TrainRepository extends JpaRepository<Train, Long> {
  Optional<Train> findByTrainNumber(String trainNumber);
  boolean existsByTrainNumberIgnoreCase(String trainNumber);
  boolean existsByTrainNumberIgnoreCaseAndIdNot(String trainNumber, Long id);
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from Train t where t.id = :id")
  Optional<Train> findByIdForUpdate(@Param("id") Long id);
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from Train t where t.trainNumber = :number")
  Optional<Train> findByTrainNumberForUpdate(@Param("number") String number);
  @Query("select t from Train t where (:active is null or t.active = :active) and (lower(t.trainNumber) like lower(concat('%', :q, '%')) or lower(t.trainName) like lower(concat('%', :q, '%')))")
  Page<Train> adminSearch(@Param("q") String query, @Param("active") Boolean active, Pageable pageable);
  @Query(value = """
    SELECT t.train_number AS trainNumber, t.train_name AS trainName, t.train_type AS trainType, sch.runs_on AS runningDays, t.data_source AS dataSource, os.station_code AS originCode, os.station_name AS originName,
           ds.station_code AS destinationCode, ds.station_name AS destinationName, o.departure_time AS departureTime, d.arrival_time AS arrivalTime,
           o.stop_sequence AS originSequence, d.stop_sequence AS destinationSequence
    FROM trains t JOIN train_stops o ON o.train_id=t.id JOIN stations os ON os.id=o.station_id
      JOIN train_stops d ON d.train_id=t.id JOIN stations ds ON ds.id=d.station_id
      JOIN train_schedules sch ON sch.train_id=t.id
    WHERE t.active=TRUE AND os.station_code=:fromCode AND ds.station_code=:toCode AND o.stop_sequence < d.stop_sequence
      AND sch.effective_from <= :journeyDate AND (sch.effective_to IS NULL OR sch.effective_to >= :journeyDate)
      AND SUBSTRING(sch.runs_on, :dayOfWeek, 1) = '1'
      AND (:classCode IS NULL OR EXISTS (SELECT 1 FROM train_classes tc WHERE tc.train_id=t.id AND tc.class_code=:classCode AND tc.active=TRUE))
    ORDER BY CASE WHEN :sort = 'DEPARTURE' THEN o.departure_time END ASC, t.train_number ASC
    """, countQuery = """
    SELECT COUNT(*) FROM trains t JOIN train_stops o ON o.train_id=t.id JOIN stations os ON os.id=o.station_id
      JOIN train_stops d ON d.train_id=t.id JOIN stations ds ON ds.id=d.station_id JOIN train_schedules sch ON sch.train_id=t.id
    WHERE t.active=TRUE AND os.station_code=:fromCode AND ds.station_code=:toCode AND o.stop_sequence < d.stop_sequence
      AND sch.effective_from <= :journeyDate AND (sch.effective_to IS NULL OR sch.effective_to >= :journeyDate)
      AND SUBSTRING(sch.runs_on, :dayOfWeek, 1) = '1'
      AND (:classCode IS NULL OR EXISTS (SELECT 1 FROM train_classes tc WHERE tc.train_id=t.id AND tc.class_code=:classCode AND tc.active=TRUE))
    """, nativeQuery = true)
  Page<TrainSearchProjection> searchByOrderedStops(@Param("fromCode") String fromCode,@Param("toCode") String toCode,@Param("journeyDate") LocalDate journeyDate,@Param("dayOfWeek") int dayOfWeek,@Param("classCode") String classCode,@Param("sort") String sort, Pageable pageable);
}
