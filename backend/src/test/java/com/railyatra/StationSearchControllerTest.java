package com.railyatra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StationSearchControllerTest {
  @Autowired MockMvc mockMvc;
  @Test void searchesByStationNameAndReturnsThePublicDto() throws Exception {
    mockMvc.perform(get("/api/stations/search").param("q", "ran"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].code").value("RNC"))
      .andExpect(jsonPath("$[0].name").value("Ranchi Junction"))
      .andExpect(jsonPath("$[0].city").value("Ranchi"));
  }
}
