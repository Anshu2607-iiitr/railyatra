package com.railyatra.admin;
import com.railyatra.auth.AuthPrincipal;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestController @RequestMapping("/api/admin/inventory")
public class AdminInventoryController {
 private final AdminInventoryService service;
 public AdminInventoryController(AdminInventoryService service){this.service=service;}
 @GetMapping public ResponseEntity<AdminInventoryResponse> inspect(@AuthenticationPrincipal AuthPrincipal actor,@RequestParam String trainNumber,
  @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam(name="class") String cls,@RequestParam(defaultValue="GENERAL") String quota){
  return ResponseEntity.ok().header("Cache-Control","no-store").body(service.inspect(actor,trainNumber,date,cls,quota));
 }
 @PostMapping("/{id}/adjustments") public ResponseEntity<AdminInventoryService.AdjustmentResult> adjust(@AuthenticationPrincipal AuthPrincipal actor,@PathVariable Long id,
  @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody AdminInventoryAdjustment request){
  return ResponseEntity.ok().header("Cache-Control","no-store").body(service.adjust(actor,id,key,request));
 }
 @GetMapping("/{id}/adjustments") public ResponseEntity<AdminInventoryService.AuditPage> history(@AuthenticationPrincipal AuthPrincipal actor,@PathVariable Long id,
  @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
  return ResponseEntity.ok().header("Cache-Control","no-store").body(service.history(actor,id,page,size));
 }
}
