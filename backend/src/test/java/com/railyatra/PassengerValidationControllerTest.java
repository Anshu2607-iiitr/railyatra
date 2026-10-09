package com.railyatra;

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
class PassengerValidationControllerTest {
    @Autowired MockMvc mockMvc;

    @Test
    @WithMockUser
    void rejectsMoreThanSixPassengersBeforeAnyBookingCanContinue() throws Exception {
        String passenger = "{\"name\":\"Test Traveller\",\"age\":25,\"gender\":\"MALE\",\"berthPreference\":\"NO_PREFERENCE\"}";
        mockMvc.perform(post("/api/passengers/validate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"passengers\":[" + String.join(",", java.util.Collections.nCopies(7, passenger)) + "]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Validation failed"));
    }
}
