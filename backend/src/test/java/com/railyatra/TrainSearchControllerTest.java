package com.railyatra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc
class TrainSearchControllerTest {
  @Autowired MockMvc mockMvc;
  @Test void findsATrainBetweenIntermediateOrderedStops() throws Exception {
    mockMvc.perform(get("/api/trains/search").param("from","BKSC").param("to","CNB").param("date","2026-10-02").param("class","3A").param("quota","GN").param("page","0").param("size","10").param("sort","DEPARTURE"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].trainNumber").value("18625")).andExpect(jsonPath("$.content[0].from").value("BKSC")).andExpect(jsonPath("$.content[0].to").value("CNB"));
  }
  @Test void excludesForwardTrainFromReverseSearchAndHonoursPagination() throws Exception {
    mockMvc.perform(get("/api/trains/search").param("from","CNB").param("to","BKSC").param("date","2026-10-02").param("page","0").param("size","1"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].trainNumber").value("18626")).andExpect(jsonPath("$.size").value(1));
  }
}
