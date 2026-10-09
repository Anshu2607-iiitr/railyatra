package com.railyatra;
import org.junit.jupiter.api.Test; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc; import org.springframework.boot.test.context.SpringBootTest; import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc class TrainRouteControllerTest { @Autowired MockMvc mockMvc;
 @Test void returnsStopsInRouteOrder() throws Exception {mockMvc.perform(get("/api/trains/18625/route")).andExpect(status().isOk()).andExpect(jsonPath("$[0].stationCode").value("RNC")).andExpect(jsonPath("$[1].stationCode").value("MURI")).andExpect(jsonPath("$[6].stationCode").value("NDLS")).andExpect(jsonPath("$[1].stopSequence").value(2));}
}
