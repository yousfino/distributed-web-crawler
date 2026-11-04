package com.crawler.research;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.crawler.research.ResearchModels.*;

@RestController
@RequestMapping("/api/research/projects")
public class ResearchController {
    private final ResearchService service;
    public ResearchController(ResearchService service) {this.service=service;}
    @GetMapping public List<ProjectSummary> projects() {return service.projects();}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Project create(@Valid @RequestBody ProjectRequest request) {return service.create(request);}
    @GetMapping("/{id}") public Project project(@PathVariable String id) {return service.get(id);}
    @PostMapping("/{id}/searches") public Project search(@PathVariable String id,@Valid @RequestBody SearchRequest request) {return service.search(id,request);}
    @PatchMapping("/{id}/papers/{paperId}") public Project screen(@PathVariable String id,@PathVariable String paperId,@Valid @RequestBody ScreeningRequest request) {return service.screen(id,paperId,request);}
    @GetMapping("/{id}/export.csv") public ResponseEntity<String> csv(@PathVariable String id,@RequestParam(required=false) Decision decision) {
        return ResponseEntity.ok().contentType(new MediaType("text","csv",java.nio.charset.StandardCharsets.UTF_8))
                .header("Content-Disposition","attachment; filename=\"research-"+safe(id)+".csv\"")
                .header("Cache-Control","no-store").body(service.csv(id,decision));
    }
    @GetMapping("/{id}/export.bib") public ResponseEntity<String> bib(@PathVariable String id,@RequestParam(required=false) Decision decision) {
        return ResponseEntity.ok().contentType(new MediaType("application","x-bibtex",java.nio.charset.StandardCharsets.UTF_8))
                .header("Content-Disposition","attachment; filename=\"research-"+safe(id)+".bib\"")
                .header("Cache-Control","no-store").body(service.bibtex(id,decision));
    }
    private String safe(String id) {return id.replaceAll("[^a-zA-Z0-9-]","");}
}
