package com.crawler.materials;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class MaterialsModels {
    private MaterialsModels() {}
    public enum Phase { QUEUED, SCANNING, COMPLETE, FAILED, INTERRUPTED }
    public enum Kind { DATASET, CODE, SUPPLEMENT, PROTOCOL, PROJECT, REPOSITORY }
    public enum Access { RESPONDED, NOT_FOUND, RESTRICTED, RATE_LIMITED, ERROR, UNKNOWN, UNCHECKED }
    public enum Review { UNREVIEWED, VERIFIED, REJECTED, FOLLOW_UP }
    public record ScanRequest(@NotBlank @Size(max=200) String name,
                              @NotEmpty @Size(max=10) List<@NotBlank @Size(max=2000) String> paperUrls) {}
    public record ReviewRequest(@NotNull Review decision,@Size(max=10000) String notes) {}
    public record Evidence(String paperUrl,String pageUrl,String anchorText,String context,String reason,Instant discoveredAt) {}
    public record Check(Access access,Integer httpStatus,String finalUrl,String details,Instant checkedAt) {}
    public record Finding(String id,Kind kind,String label,String url,List<Evidence> evidence,Check check,Review review,String notes) {}
    public record PaperPage(String requestedUrl,String finalUrl,String title,Integer httpStatus,String outcome,int candidates) {}
    public record Inventory(String id,String name,List<String> paperUrls,Instant createdAt,Instant finishedAt,
                            Phase phase,List<PaperPage> pages,List<Finding> findings,List<String> warnings) {}
    public record Summary(String id,String name,Instant createdAt,Phase phase,int papers,int findings,long verified) {}
}
