package com.railyatra.railway.importer;
import java.util.List; import org.springframework.stereotype.Component;
@Component public class ScheduleImporter { private final RailwayBatchWriter writer; public ScheduleImporter(RailwayBatchWriter writer){this.writer=writer;} public void importRows(List<RailwayDataSet.ScheduleRow> rows){writer.schedules(rows);} }
