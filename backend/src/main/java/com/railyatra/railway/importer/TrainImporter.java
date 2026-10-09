package com.railyatra.railway.importer;
import java.util.List; import org.springframework.stereotype.Component;
@Component public class TrainImporter { private final RailwayBatchWriter writer; public TrainImporter(RailwayBatchWriter writer){this.writer=writer;} public void importRows(List<RailwayDataSet.TrainRow> rows){writer.trains(rows);} }
