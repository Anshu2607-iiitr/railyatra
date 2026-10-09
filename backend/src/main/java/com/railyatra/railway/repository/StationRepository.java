package com.railyatra.railway.repository;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.railyatra.railway.domain.Station;
public interface StationRepository extends JpaRepository<Station, Long> {
  Optional<Station> findByStationCode(String stationCode);
  @Query("""
      select s from Station s where s.active = true and (
        lower(s.stationCode) like concat(lower(:query), '%') or
        lower(s.stationName) like concat('%', lower(:query), '%') or
        lower(s.city) like concat('%', lower(:query), '%')
      ) order by case when lower(s.stationCode) like concat(lower(:query), '%') then 0 else 1 end, s.stationName asc
      """)
  List<Station> search(@Param("query") String query, Pageable pageable);
}
