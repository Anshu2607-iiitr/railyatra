package com.railyatra.railway.importer;
import java.util.List; import org.springframework.stereotype.Component;
@Component public class RouteImporter { private final RailwayBatchWriter writer; public RouteImporter(RailwayBatchWriter writer){this.writer=writer;} public void importRows(List<RailwayDataSet.StopRow> rows){writer.stops(rows);} }
