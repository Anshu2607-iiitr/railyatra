package com.railyatra.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InternalReferenceGeneratorTest {
    @Test
    void generatesOpaqueRailYatraReferencesWithoutDuplicatesInSample() {
        InternalReferenceGenerator generator = new InternalReferenceGenerator();
        Set<String> generated = new HashSet<>();
        for (int index = 0; index < 10_000; index++) {
            String reference = generator.next();
            assertTrue(reference.matches("RY[A-Z2-9]{20}"));
            generated.add(reference);
        }
        assertEquals(10_000, generated.size());
    }
}
