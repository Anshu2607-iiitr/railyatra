package com.railyatra.railway.service;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import com.railyatra.railway.dto.TrainSearchResponse;
import com.railyatra.railway.repository.TrainRepository;
@Service public class TrainSearchService {
  private final TrainRepository trains; public TrainSearchService(TrainRepository trains){this.trains=trains;}
  public Page<TrainSearchResponse> search(String from,String to,LocalDate date,String classCode,String quota,int page,int size,String sort){if(from.equalsIgnoreCase(to))throw new IllegalArgumentException("Origin and destination must be different");return trains.searchByOrderedStops(from.toUpperCase(),to.toUpperCase(),date,date.getDayOfWeek().getValue(),blankToNull(classCode),sort,PageRequest.of(page,size)).map(row->TrainSearchResponse.from(row,blankToNull(classCode),blankToNull(quota)));}
  private String blankToNull(String value){return value==null||value.isBlank()?null:value.trim().toUpperCase();}
}
