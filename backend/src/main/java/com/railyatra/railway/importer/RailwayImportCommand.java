package com.railyatra.railway.importer;

import java.nio.file.Path;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments; import org.springframework.boot.ApplicationRunner; import org.springframework.stereotype.Component;

@Component
@org.springframework.core.annotation.Order(50)
public class RailwayImportCommand implements ApplicationRunner {
  private static final Logger log=LoggerFactory.getLogger(RailwayImportCommand.class);
  private final RailwayDataImporter importer; private final SyntheticRailwayDataGenerator generator; private final RailwayBatchWriter writer;
  @Value("${railyatra.import.command:}") private String command; @Value("${railyatra.import.path:}") private String path;
  public RailwayImportCommand(RailwayDataImporter importer,SyntheticRailwayDataGenerator generator,RailwayBatchWriter writer){this.importer=importer;this.generator=generator;this.writer=writer;}
  @Override public void run(ApplicationArguments args) { if(command==null||command.isBlank()) return; ImportReport report=switch(command.toLowerCase()){case "synthetic" -> importer.importData(generator.generate(),"SYNTHETIC");case "regenerate-synthetic" -> {writer.removeSyntheticStaticData();yield importer.importData(generator.generate(),"SYNTHETIC");}case "file" -> {if(path==null||path.isBlank()) throw new IllegalArgumentException("RAILYATRA_IMPORT_PATH is required for file imports");yield importer.importData(Path.of(path));}default -> throw new IllegalArgumentException("Unknown RAILYATRA_IMPORT_COMMAND: "+command);}; log.info("Railway import completed source={} elapsed={} records={}",report.dataSource(),report.elapsed(),report.records()); }
}
