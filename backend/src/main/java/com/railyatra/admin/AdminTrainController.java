package com.railyatra.admin;

import com.railyatra.railway.domain.TrainType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.DayOfWeek;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/trains")
public class AdminTrainController {
    private final AdminTrainService trains;
    public AdminTrainController(AdminTrainService trains){this.trains=trains;}
    @GetMapping public AdminTrainResponse.Page list(@RequestParam(defaultValue="") String q,@RequestParam(required=false) Boolean active,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return trains.list(q,active,page,size);}
    @GetMapping("/options") public Map<String,Object> options(){return Map.of("types",TrainType.values(),"runningDays",DayOfWeek.values(),
        "classes",Arrays.stream(AdminTrainRequest.ClassCode.values()).map(cls->Map.of("code",cls.code(),"name",cls.label())).toList());}
    @GetMapping("/{id}") public AdminTrainResponse get(@PathVariable Long id){return trains.get(id);}
    @PostMapping public ResponseEntity<AdminTrainResponse> create(@Valid @RequestBody AdminTrainRequest request){var train=trains.create(request);return ResponseEntity.created(URI.create("/api/admin/trains/"+train.id())).body(train);}
    @PutMapping("/{id}") public AdminTrainResponse update(@PathVariable Long id,@Valid @RequestBody AdminTrainRequest request){return trains.update(id,request);}
    @PatchMapping("/{id}/deactivate") public AdminTrainResponse deactivate(@PathVariable Long id,@Valid @RequestBody Revision request){return trains.deactivate(id,request.version());}
    public record Revision(@NotNull @PositiveOrZero Long version){}
}
