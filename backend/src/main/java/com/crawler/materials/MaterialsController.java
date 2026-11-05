package com.crawler.materials;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static com.crawler.materials.MaterialsModels.*;

@RestController
@RequestMapping("/api/materials/inventories")
public class MaterialsController {
    private final MaterialsService service;
    public MaterialsController(MaterialsService service){this.service=service;}
    @GetMapping public List<Summary> list(){return service.list();}
    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED) public Inventory create(@Valid @RequestBody ScanRequest request){return service.create(request);}
    @GetMapping("/{id}") public Inventory get(@PathVariable String id){return service.get(id);}
    @PatchMapping("/{id}/findings/{findingId}") public Inventory review(@PathVariable String id,@PathVariable String findingId,@Valid @RequestBody ReviewRequest request){return service.review(id,findingId,request);}
    @GetMapping("/{id}/export.csv") public ResponseEntity<String> csv(@PathVariable String id,@RequestParam(required=false) Review review){return ResponseEntity.ok().contentType(new MediaType("text","csv",StandardCharsets.UTF_8)).header("Content-Disposition","attachment; filename=\"materials-inventory.csv\"").header("Cache-Control","no-store").body(service.csv(id,review));}
}
