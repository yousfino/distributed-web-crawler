package com.crawler.materials;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import static com.crawler.materials.MaterialsModels.*;

@Component
public class MaterialsScanner {
    private final MaterialsHttp http;
    public MaterialsScanner(MaterialsHttp http){this.http=http;}
    public Inventory scan(Inventory initial,Consumer<Inventory> progress) {
        List<PaperPage> pages=new ArrayList<>();LinkedHashMap<String,Finding> findings=new LinkedHashMap<>();List<String> warnings=new ArrayList<>();
        Map<String,MaterialsHttp.Rules> robots=new HashMap<>();
        long deadline=System.nanoTime()+java.time.Duration.ofSeconds(90).toNanos();
        for(String paper:initial.paperUrls()) {
            if(Thread.currentThread().isInterrupted()) throw new IllegalStateException("Scan interrupted");
            if(System.nanoTime()>deadline) {pages.add(new PaperPage(paper,"","",null,"Scan time limit reached; page not checked",0));continue;}
            try {
                MaterialsHttp.Response response=http.fetch(paper,"GET",2_000_000,robots);
                if(response.status()<200 || response.status()>=300) {
                    pages.add(new PaperPage(paper,response.url(),"",response.status(),"Paper page did not return a successful HTTP response; no links scanned",0));continue;
                }
                String type=response.contentType().toLowerCase(Locale.ROOT);
                if(!type.contains("text/html") && !type.contains("application/xhtml+xml")) {
                    pages.add(new PaperPage(paper,response.url(),"",response.status(),"HTML paper pages only in this version; PDFs and other formats were not parsed",0));continue;
                }
                var document=Jsoup.parse(response.body());String title=document.title().toLowerCase(Locale.ROOT);
                if(title.contains("just a moment")||title.contains("checking your browser")||!document.select(".g-recaptcha, #captcha-form, #challenge-form").isEmpty()) {
                    pages.add(new PaperPage(paper,response.url(),"",response.status(),"Page requires human verification; no bypass attempted",0));continue;
                }
                MaterialDiscovery.Page discovered=MaterialDiscovery.discover(response.body(),paper,response.url());
                int candidates=0;
                for(var candidate:discovered.candidates()) {
                    Finding existing=findings.get(candidate.url());
                    if(existing==null && findings.size()>=100) continue;
                    candidates++;
                    if(existing==null) findings.put(candidate.url(),new Finding(UUID.randomUUID().toString(),candidate.kind(),candidate.label(),candidate.url(),List.of(candidate.evidence()),
                            new Check(Access.UNCHECKED,null,candidate.url(),"Awaiting HTTP check",null),Review.UNREVIEWED,""));
                    else if(existing.evidence().size()<30 && existing.evidence().stream().noneMatch(e->e.pageUrl().equals(candidate.evidence().pageUrl()) && e.anchorText().equals(candidate.evidence().anchorText()))) {
                        List<Evidence> evidence=new ArrayList<>(existing.evidence());evidence.add(candidate.evidence());
                        findings.put(existing.url(),new Finding(existing.id(),existing.kind(),existing.label(),existing.url(),List.copyOf(evidence),existing.check(),existing.review(),existing.notes()));
                    }
                }
                pages.add(new PaperPage(paper,response.url(),discovered.title(),response.status(),"HTML scanned for explicit links; missing materials are not evidence of absence",candidates));
                if(discovered.candidates().size()>candidates) warnings.add("The 100-resource inventory limit omitted some links from "+paper);
            } catch(Exception e) {
                if(e instanceof InterruptedException){Thread.currentThread().interrupt();throw new IllegalStateException("Scan interrupted");}
                pages.add(new PaperPage(paper,"","",null,detail(e),0));
            }
            progress.accept(snapshot(initial,pages,findings,warnings,Phase.SCANNING,null));
        }
        int checked=0;
        for(var entry:findings.entrySet()) {
            if(Thread.currentThread().isInterrupted()) throw new IllegalStateException("Scan interrupted");
            Finding f=entry.getValue();Check check;
            if(checked>=20 || System.nanoTime()>deadline) check=new Check(Access.UNCHECKED,null,f.url(),"Request/time budget reached; manually review this link",null);
            else {
                checked++;
                try {
                    MaterialsHttp.Response response=http.fetch(f.url(),"HEAD",0,robots);
                    if(response.status()==405 || response.status()==501) response=http.fetch(f.url(),"GET",0,robots);
                    int status=response.status();
                    Access access=status>=200&&status<300?Access.RESPONDED:status==404||status==410?Access.NOT_FOUND:
                            status==401||status==402||status==403?Access.RESTRICTED:status==429?Access.RATE_LIMITED:status>=500?Access.ERROR:Access.UNKNOWN;
                    String message=switch(access) {
                        case RESPONDED -> "HTTP response succeeded; file contents, relevance and reuse rights are not verified";
                        case NOT_FOUND -> "HTTP 404/410; the linked destination reported not found";
                        case RESTRICTED -> "Denied by the site or network; access could not be verified";
                        case RATE_LIMITED -> "HTTP 429; retry later or review manually";
                        case ERROR -> "Server error; this does not prove permanent unavailability";
                        default -> "HTTP response did not establish accessibility";
                    };
                    check=new Check(access,status,response.url(),message,Instant.now());
                } catch(Exception e) {if(e instanceof InterruptedException){Thread.currentThread().interrupt();throw new IllegalStateException("Scan interrupted");}check=new Check(Access.UNKNOWN,null,f.url(),detail(e),Instant.now());}
            }
            entry.setValue(new Finding(f.id(),f.kind(),f.label(),f.url(),f.evidence(),check,f.review(),f.notes()));
            progress.accept(snapshot(initial,pages,findings,warnings,Phase.SCANNING,null));
        }
        if(findings.size()>checked) warnings.add("Some resources were not checked because this scan is limited to 20 resource checks and a 90-second request budget.");
        if(pages.stream().anyMatch(p->!p.outcome().startsWith("HTML scanned"))) warnings.add("One or more paper pages could not be scanned. The inventory is incomplete; review page outcomes.");
        warnings.add("Candidates are inferred from explicit links on supplied HTML pages only. No PDF extraction, recursive project-page crawling, content verification or reuse-rights validation was performed.");
        return snapshot(initial,pages,findings,warnings,Phase.COMPLETE,Instant.now());
    }
    private static String detail(Exception e) {
        if(e instanceof IllegalArgumentException || e instanceof IllegalStateException) return MaterialDiscovery.clip(e.getMessage()==null?"Could not check this URL":e.getMessage(),300);
        if(e instanceof java.net.http.HttpTimeoutException || e instanceof java.net.SocketTimeoutException) return "Request timed out; accessibility is unknown";
        return "Could not retrieve this URL; check network access and review it manually";
    }
    private static Inventory snapshot(Inventory i,List<PaperPage> pages,Map<String,Finding> findings,List<String> warnings,Phase phase,Instant finished) {
        return new Inventory(i.id(),i.name(),i.paperUrls(),i.createdAt(),finished,phase,List.copyOf(pages),List.copyOf(findings.values()),List.copyOf(warnings));
    }
}
