package com.railyatra.railway.service;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import com.railyatra.railway.dto.StationSearchResponse;
import com.railyatra.railway.repository.StationRepository;

@Service
public class StationSearchService {
  private final StationRepository stations;
  public StationSearchService(StationRepository stations) { this.stations = stations; }
  public List<StationSearchResponse> search(String query) { return stations.search(query.trim(), PageRequest.of(0, 10)).stream().map(StationSearchResponse::from).toList(); }
}
