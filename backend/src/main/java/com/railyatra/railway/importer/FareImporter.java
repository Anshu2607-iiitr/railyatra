package com.railyatra.railway.importer;
import java.util.List; import org.springframework.stereotype.Component;
@Component public class FareImporter { private final RailwayBatchWriter writer; public FareImporter(RailwayBatchWriter writer){this.writer=writer;} public void importRows(List<RailwayDataSet.FareRow> rows){writer.fares(rows);} }
