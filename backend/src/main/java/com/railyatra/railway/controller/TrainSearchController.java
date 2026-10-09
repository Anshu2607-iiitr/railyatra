package com.railyatra.railway.controller;
import java.time.LocalDate;
import jakarta.validation.constraints.Max; import jakarta.validation.constraints.Min; import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import com.railyatra.railway.dto.TrainSearchResponse;
import com.railyatra.railway.service.TrainSearchService;
@RestController @RequestMapping("/api/trains") public class TrainSearchController {
  private final TrainSearchService service; public TrainSearchController(TrainSearchService service){this.service=service;}
  @GetMapping("/search") public Page<TrainSearchResponse> search(@RequestParam("from") @NotBlank @Pattern(regexp="^[A-Za-z0-9]{2,10}$") String from,@RequestParam("to") @NotBlank @Pattern(regexp="^[A-Za-z0-9]{2,10}$") String to,@RequestParam("date") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam(required=false,name="class") String classCode,@RequestParam(required=false,name="quota") String quota,@RequestParam(defaultValue="0",name="page") @Min(0) int page,@RequestParam(defaultValue="10",name="size") @Min(1) @Max(50) int size,@RequestParam(defaultValue="TRAIN_NUMBER",name="sort") @Pattern(regexp="^(TRAIN_NUMBER|DEPARTURE)$") String sort){return service.search(from,to,date,classCode,quota,page,size,sort);}
}
