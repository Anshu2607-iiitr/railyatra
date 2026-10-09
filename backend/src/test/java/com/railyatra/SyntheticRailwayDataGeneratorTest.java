package com.railyatra;

import org.junit.jupiter.api.Test;
import com.railyatra.railway.importer.ImportValidator;
import com.railyatra.railway.importer.SyntheticRailwayDataGenerator;
import static org.junit.jupiter.api.Assertions.*;

class SyntheticRailwayDataGeneratorTest {
  @Test void createsARepeatableValidatedDatasetAboveTheRequiredScale() {
    var generator = new SyntheticRailwayDataGenerator();
    var first = generator.generate(); var second = generator.generate();
    assertTrue(first.trains().size() >= 5000);
    assertTrue(first.stops().size() >= 40_000);
    assertEquals(first, second);
    assertTrue(first.trains().stream().allMatch(train -> "SYNTHETIC".equals(train.dataSource())));
    new ImportValidator().validate(first);
  }
}
