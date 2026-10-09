package com.railyatra.railway.importer;

import java.sql.Date;
import java.sql.Time;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RailwayBatchWriter {
  private final JdbcTemplate jdbc; private final int batchSize;
  public RailwayBatchWriter(JdbcTemplate jdbc,@Value("${railyatra.import.batch-size:1000}") int batchSize){this.jdbc=jdbc;this.batchSize=batchSize;}
  public void stations(List<RailwayDataSet.StationRow> rows){ jdbc.batchUpdate("INSERT INTO stations(station_code,station_name,city,state,active,data_source) SELECT ?,?,?,?,TRUE,? WHERE NOT EXISTS (SELECT 1 FROM stations WHERE station_code=?)", rows, batchSize, (ps,r)->{ps.setString(1,r.stationCode());ps.setString(2,r.stationName());ps.setString(3,r.city());ps.setString(4,r.state());ps.setString(5,r.dataSource());ps.setString(6,r.stationCode());}); }
  public void trains(List<RailwayDataSet.TrainRow> rows){ jdbc.batchUpdate("INSERT INTO trains(train_number,train_name,active,data_source) SELECT ?,?,TRUE,? WHERE NOT EXISTS (SELECT 1 FROM trains WHERE train_number=?)", rows,batchSize,(ps,r)->{ps.setString(1,r.trainNumber());ps.setString(2,r.trainName());ps.setString(3,r.dataSource());ps.setString(4,r.trainNumber());}); }
  public void stops(List<RailwayDataSet.StopRow> rows){ jdbc.batchUpdate("INSERT INTO train_stops(train_id,station_id,stop_sequence,arrival_time,departure_time,day_offset,distance_km) SELECT t.id,s.id,?,?,?,?,? FROM trains t CROSS JOIN stations s WHERE t.train_number=? AND s.station_code=? AND NOT EXISTS (SELECT 1 FROM train_stops x WHERE x.train_id=t.id AND x.stop_sequence=?)", rows,batchSize,(ps,r)->{ps.setInt(1,r.stopSequence());ps.setTime(2,r.arrivalTime()==null?null:Time.valueOf(r.arrivalTime()));ps.setTime(3,r.departureTime()==null?null:Time.valueOf(r.departureTime()));ps.setInt(4,r.dayOffset());ps.setInt(5,r.distanceKm());ps.setString(6,r.trainNumber());ps.setString(7,r.stationCode());ps.setInt(8,r.stopSequence());}); }
  public void schedules(List<RailwayDataSet.ScheduleRow> rows){ jdbc.batchUpdate("INSERT INTO train_schedules(train_id,runs_on,effective_from) SELECT t.id,?,? FROM trains t WHERE t.train_number=? AND NOT EXISTS (SELECT 1 FROM train_schedules x WHERE x.train_id=t.id AND x.effective_from=?)",rows,batchSize,(ps,r)->{ps.setString(1,r.runsOn());ps.setDate(2,Date.valueOf(r.effectiveFrom()));ps.setString(3,r.trainNumber());ps.setDate(4,Date.valueOf(r.effectiveFrom()));}); }
  public void classes(List<RailwayDataSet.ClassRow> rows){ jdbc.batchUpdate("INSERT INTO train_classes(train_id,class_code,class_name,active) SELECT t.id,?,?,TRUE FROM trains t WHERE t.train_number=? AND NOT EXISTS (SELECT 1 FROM train_classes x WHERE x.train_id=t.id AND x.class_code=?)",rows,batchSize,(ps,r)->{ps.setString(1,r.classCode());ps.setString(2,r.className());ps.setString(3,r.trainNumber());ps.setString(4,r.classCode());}); }
  public void coaches(List<RailwayDataSet.CoachRow> rows){ jdbc.batchUpdate("INSERT INTO coaches(train_class_id,coach_code,coach_position) SELECT c.id,?,? FROM train_classes c JOIN trains t ON t.id=c.train_id WHERE t.train_number=? AND c.class_code=? AND NOT EXISTS (SELECT 1 FROM coaches x WHERE x.train_class_id=c.id AND x.coach_code=?)",rows,batchSize,(ps,r)->{ps.setString(1,r.coachCode());ps.setInt(2,r.coachPosition());ps.setString(3,r.trainNumber());ps.setString(4,r.classCode());ps.setString(5,r.coachCode());}); }
  public void seats(List<RailwayDataSet.SeatRow> rows){ jdbc.batchUpdate("INSERT INTO seats(coach_id,seat_number,berth_type,active) SELECT co.id,?,?,TRUE FROM coaches co JOIN train_classes c ON c.id=co.train_class_id JOIN trains t ON t.id=c.train_id WHERE t.train_number=? AND c.class_code=? AND co.coach_code=? AND NOT EXISTS (SELECT 1 FROM seats x WHERE x.coach_id=co.id AND x.seat_number=?)",rows,batchSize,(ps,r)->{ps.setString(1,r.seatNumber());ps.setString(2,r.berthType());ps.setString(3,r.trainNumber());ps.setString(4,r.classCode());ps.setString(5,r.coachCode());ps.setString(6,r.seatNumber());}); }
  public void fares(List<RailwayDataSet.FareRow> rows){ jdbc.batchUpdate("INSERT INTO fare_rules(train_id,train_class_id,origin_station_id,destination_station_id,base_fare,per_km_fare,minimum_fare,active) SELECT t.id,c.id,o.id,d.id,?,?,?,TRUE FROM trains t JOIN train_classes c ON c.train_id=t.id JOIN stations o ON o.station_code=? JOIN stations d ON d.station_code=? WHERE t.train_number=? AND c.class_code=? AND NOT EXISTS (SELECT 1 FROM fare_rules f WHERE f.train_id=t.id AND f.train_class_id=c.id AND f.origin_station_id=o.id AND f.destination_station_id=d.id)",rows,batchSize,(ps,r)->{ps.setBigDecimal(1,r.baseFare());ps.setBigDecimal(2,r.perKmFare());ps.setBigDecimal(3,r.minimumFare());ps.setString(4,r.originCode());ps.setString(5,r.destinationCode());ps.setString(6,r.trainNumber());ps.setString(7,r.classCode());}); }
  public void removeSyntheticStaticData(){
    jdbc.update("DELETE FROM fare_rules WHERE train_id IN (SELECT id FROM trains WHERE data_source='SYNTHETIC')");
    jdbc.update("DELETE FROM seats WHERE coach_id IN (SELECT co.id FROM coaches co JOIN train_classes c ON c.id=co.train_class_id JOIN trains t ON t.id=c.train_id WHERE t.data_source='SYNTHETIC')");
    jdbc.update("DELETE FROM coaches WHERE train_class_id IN (SELECT c.id FROM train_classes c JOIN trains t ON t.id=c.train_id WHERE t.data_source='SYNTHETIC')");
    jdbc.update("DELETE FROM train_classes WHERE train_id IN (SELECT id FROM trains WHERE data_source='SYNTHETIC')");
    jdbc.update("DELETE FROM train_schedules WHERE train_id IN (SELECT id FROM trains WHERE data_source='SYNTHETIC')");
    jdbc.update("DELETE FROM train_stops WHERE train_id IN (SELECT id FROM trains WHERE data_source='SYNTHETIC')");
    jdbc.update("DELETE FROM trains WHERE data_source='SYNTHETIC'");
    jdbc.update("DELETE FROM stations WHERE data_source='SYNTHETIC'");
  }
}
