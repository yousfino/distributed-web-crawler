package com.crawler.research;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class ResearchModels {
    private ResearchModels() {}
    public enum Decision { UNDECIDED, INCLUDED, EXCLUDED }
    public record ProjectRequest(@NotBlank @Size(max=200) String name,
                                 @NotBlank @Size(max=4000) String question) {}
    public record SearchRequest(@NotBlank @Size(max=500) String query,
                                @Min(1600) @Max(2200) Integer fromYear,
                                @Min(1600) @Max(2200) Integer toYear,
                                @Pattern(regexp="article|preprint|book-chapter") String publicationType,
                                @NotEmpty @Size(max=4) List<@NotBlank @Pattern(regexp="openalex|crossref|pubmed|scholar") String> sources) {}
    public record ScreeningRequest(@NotNull Decision decision,
                                   @Size(max=2000) String reason,
                                   @Size(max=10000) String notes) {}
    public record Provenance(String source, String sourceId, String sourceUrl,
                             String searchId, String query, Instant retrievedAt) {}
    public record Paper(String id, String doi, String title, List<String> authors,
                        Integer year, String venue, String publicationType, String abstractText,
                        String url, String openAccessUrl, List<Provenance> provenance,
                        Decision decision, String reason, String notes) {}
    public record SearchRun(String id, SearchRequest request, Instant searchedAt,
                            int found, int added, List<String> warnings) {}
    public record Project(String id, String name, String question, Instant createdAt,
                          List<Paper> papers, List<SearchRun> searches) {}
    public record ProjectSummary(String id, String name, String question, Instant createdAt,
                                 int papers, long included, long excluded) {}
    public record SourceResult(List<Paper> papers, List<String> warnings) {}
}
