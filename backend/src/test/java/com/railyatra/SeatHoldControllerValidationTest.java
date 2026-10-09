package com.railyatra;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SeatHoldControllerValidationTest {
    @Autowired MockMvc mockMvc;

    @Test
    @WithMockUser
    void rejectsMoreThanSixPassengersBeforeCreatingAnySeatHold() throws Exception {
        String passenger = "{\"name\":\"Test Traveller\",\"age\":25,\"gender\":\"MALE\",\"berthPreference\":\"NO_PREFERENCE\"}";
        String body = "{\"trainNumber\":\"12000\",\"journeyDate\":\"2026-10-10\",\"classCode\":\"3A\",\"passengers\":[" + String.join(",", Collections.nCopies(7, passenger)) + "]}";
        mockMvc.perform(post("/api/seat-holds").header("Idempotency-Key", "test-hold-key").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Validation failed"));
    }
}
