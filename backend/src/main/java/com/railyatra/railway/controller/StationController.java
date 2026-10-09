package com.railyatra.railway.controller;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.railyatra.railway.dto.StationSearchResponse;
import com.railyatra.railway.service.StationSearchService;

@RestController
@RequestMapping("/api/stations")
public class StationController {
  private final StationSearchService stationSearchService;
  public StationController(StationSearchService stationSearchService) { this.stationSearchService = stationSearchService; }
  @GetMapping("/search")
  public List<StationSearchResponse> search(@RequestParam("q") @NotBlank @Size(max = 60) String query) { return stationSearchService.search(query); }
}
