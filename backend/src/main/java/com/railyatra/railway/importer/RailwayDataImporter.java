package com.railyatra.railway.importer;
import java.nio.file.Path;
public interface RailwayDataImporter { ImportReport importData(Path source); ImportReport importData(RailwayDataSet data, String dataSource); }
