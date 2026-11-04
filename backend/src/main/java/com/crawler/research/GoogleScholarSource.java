package com.crawler.research;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import static com.crawler.research.ResearchModels.*;

/** Direct, best-effort public-page adapter. It never bypasses a CAPTCHA or retries an access denial. */
@Component
public class GoogleScholarSource {
    private final String endpoint;
    private final HttpClient client=ScholarlyHttp.client();
    private long nextRequestNanos=System.nanoTime();
    public GoogleScholarSource(@Value("${research.scholar-url:https://scholar.google.com/scholar}") String endpoint) {
        this.endpoint=endpoint;
    }
    public SourceResult search(SearchRequest request,String searchId,Instant retrievedAt) {
        if(request.publicationType()!=null) return warning("publication-type filtering is unavailable; choose Any type or another source");
        if(!reserveRequest()) return warning("please wait at least 10 seconds between Scholar searches, or open Scholar manually");
        try {
            String url=endpoint+"?hl=en&num=10&q="+encode(request.query());
            if(request.fromYear()!=null) url+="&as_ylo="+request.fromYear();
            if(request.toYear()!=null) url+="&as_yhi="+request.toYear();
            HttpResponse<String> response=client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                    .header("Accept","text/html").header("User-Agent","ResearchAtlas/1.0 (scholarly discovery)").GET().build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()==403 || response.statusCode()==429) return warning("access blocked or rate limited by Google; open Scholar manually. Automated access is not guaranteed");
            if(response.statusCode()!=200) return warning("upstream HTTP "+response.statusCode()+"; open Scholar manually");
            return parse(response.body(),request,searchId,retrievedAt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();return warning("search interrupted");
        } catch (Exception e) {return warning("could not retrieve results; check connectivity or open Scholar manually");}
    }
    private synchronized boolean reserveRequest() {
        long now=System.nanoTime();
        if(now<nextRequestNanos) return false;
        nextRequestNanos=now+Duration.ofSeconds(10).toNanos();return true;
    }
    static SourceResult parse(String html,SearchRequest request,String searchId,Instant retrievedAt) throws Exception {
        Document doc=Jsoup.parse(html,"https://scholar.google.com/");
        String body=doc.text().toLowerCase(Locale.ROOT);
        if(!doc.select("#gs_captcha_ccl, #captcha-form, .g-recaptcha, iframe[src*=recaptcha], form[action*=Captcha]").isEmpty()
                || body.contains("unusual traffic") || body.contains("before you continue to google"))
            return warning("Google requires a CAPTCHA or consent; open Scholar manually. Automated access is not guaranteed");
        if(doc.selectFirst("#gs_res_ccl_mid")==null) return warning("unrecognized page format; open Scholar manually");
        List<Paper> papers=new ArrayList<>();
        int skipped=0;
        for(Element result:doc.select("#gs_res_ccl_mid .gs_r.gs_or")) {
            if(papers.size()>=10) break;
            Element heading=result.selectFirst(".gs_rt");
            if(heading==null) continue;
            Element link=heading.selectFirst("a[href]");
            String title=link==null?heading.text().replaceFirst("^\\[[^]]+]\\s*",""):link.text();
            if(title.isBlank()) continue;
            String url=link==null?"":webUrl(link.absUrl("href"));
            String metadata=result.select(".gs_a").text();
            Matcher date=Pattern.compile("\\b(1[6-9][0-9]{2}|2[0-2][0-9]{2})\\b").matcher(metadata);
            Integer year=null;while(date.find()) year=Integer.parseInt(date.group());
            if((request.fromYear()!=null && (year==null || year<request.fromYear())) || (request.toYear()!=null && (year==null || year>request.toYear()))) {skipped++;continue;}
            String[] parts=metadata.split("\\s+[-–—]\\s+",3);
            List<String> authors=parts.length<2?List.of():Arrays.stream(parts[0].split(",\\s*")).filter(s->!s.isBlank() && !s.contains("…") && !s.contains("...")).toList();
            String venue=parts.length<2?"":parts[1].replaceFirst(",?\\s*\\b[12][0-9]{3}\\b.*$","").trim();
            String doi="";
            if(!url.isBlank()) {
                URI uri=URI.create(url);
                if("doi.org".equalsIgnoreCase(uri.getHost()) || "dx.doi.org".equalsIgnoreCase(uri.getHost())) doi=ScholarlySources.normalizeDoi(url);
            }
            String sourceId=result.attr("data-cid");
            if(sourceId.isBlank()) sourceId=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((title+"|"+year+"|"+url).getBytes(StandardCharsets.UTF_8)));
            String sourceUrl=url.isBlank()?"https://scholar.google.com/scholar?q="+encode(title):url;
            // Scholar snippets are not abstracts, and publication type/open access cannot be established from this page.
            papers.add(new Paper(UUID.randomUUID().toString(),doi,title,authors,year,venue,"","",url,"",
                    List.of(new Provenance("scholar",sourceId,sourceUrl,searchId,request.query(),retrievedAt)),Decision.UNDECIDED,"",""));
        }
        return new SourceResult(List.copyOf(papers),skipped==0?List.of():List.of("scholar: some records lacked a usable year or fell outside the requested range"));
    }
    private static String webUrl(String value) {
        try {URI uri=URI.create(value);return Set.of("http","https").contains(uri.getScheme()) && uri.getHost()!=null?value:"";}
        catch (IllegalArgumentException e) {return "";}
    }
    private static String encode(String value) {return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private static SourceResult warning(String message) {return new SourceResult(List.of(),List.of("scholar: "+message));}
}
