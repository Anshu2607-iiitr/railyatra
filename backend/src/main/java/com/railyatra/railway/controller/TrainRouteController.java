package com.railyatra.railway.controller;
import java.util.List;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.*;
import com.railyatra.railway.dto.TrainRouteStopResponse;
import com.railyatra.railway.service.TrainRouteService;
@RestController @RequestMapping("/api/trains") public class TrainRouteController {
 private final TrainRouteService routes; public TrainRouteController(TrainRouteService routes){this.routes=routes;}
 @GetMapping("/{trainNumber}/route") public List<TrainRouteStopResponse> route(@PathVariable @Pattern(regexp="^[A-Za-z0-9]{2,10}$") String trainNumber){return routes.route(trainNumber);}
}
