package com.railyatra.railway.importer;
import java.util.List; import org.springframework.stereotype.Component;
@Component public class StationImporter { private final RailwayBatchWriter writer; public StationImporter(RailwayBatchWriter writer){this.writer=writer;} public void importRows(List<RailwayDataSet.StationRow> rows){writer.stations(rows);} }
