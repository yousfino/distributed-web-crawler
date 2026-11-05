package com.crawler.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

import static com.crawler.research.ResearchModels.*;

@Component
public class ScholarlySources {
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final PubMedSource pubmed;
    private final GoogleScholarSource scholar;
    private final String openalexUrl, crossrefUrl, apiKey, email;
    public ScholarlySources(ObjectMapper mapper,
            @Value("${research.openalex-url:https://api.openalex.org/works}") String openalexUrl,
            @Value("${research.crossref-url:https://api.crossref.org/works}") String crossrefUrl,
            @Value("${research.openalex-api-key:}") String apiKey,
            @Value("${research.contact-email:}") String email, PubMedSource pubmed, GoogleScholarSource scholar) {
        this.pubmed=pubmed;this.scholar=scholar;
        this.mapper=mapper; this.openalexUrl=openalexUrl; this.crossrefUrl=crossrefUrl; this.apiKey=apiKey; this.email=email;
        this.client=ScholarlyHttp.client();
    }

    public SourceResult search(String source, SearchRequest request, String searchId, Instant retrievedAt) {
        if (source.equals("scholar")) return scholar.search(request, searchId, retrievedAt);
        if (source.equals("pubmed")) return pubmed.search(request, searchId, retrievedAt);
        try {
            URI uri=URI.create(source.equals("openalex") ? openalexQuery(request) : crossrefQuery(request));
            HttpRequest http=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json").header("User-Agent", "AcademicResearchLibrary/1.0" + (email.isBlank()?"":" (mailto:"+email+")")).GET().build();
            HttpResponse<String> response=client.send(http, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()!=200) {
                String hint = response.statusCode()==429 ? "rate limit reached; try again later" :
                        response.statusCode()==401 || response.statusCode()==403 ? "access denied; check network access and API credentials" : "HTTP " + response.statusCode();
                return new SourceResult(List.of(), List.of(source+": "+hint));
            }
            JsonNode root=mapper.readTree(response.body());
            JsonNode items=source.equals("openalex") ? root.path("results") : root.path("message").path("items");
            if (!items.isArray()) return new SourceResult(List.of(), List.of(source+": unexpected response format"));
            List<Paper> papers=new ArrayList<>();
            for (JsonNode item:items) {
                Paper paper=source.equals("openalex") ? openalex(item, request, searchId, retrievedAt) : crossref(item, request, searchId, retrievedAt);
                if (!paper.title().isBlank() && matches(paper, request)) papers.add(paper);
            }
            return new SourceResult(papers, List.of());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SourceResult(List.of(), List.of(source+": search interrupted"));
        } catch (Exception e) {
            // Do not expose endpoint URLs containing API keys or upstream response bodies.
            return new SourceResult(List.of(), List.of(source+": could not retrieve papers; check connectivity and try again"));
        }
    }
    private String openalexQuery(SearchRequest r) {
        String url=openalexUrl+"?search="+encode(r.query())+"&per-page=25";
        List<String> filters=new ArrayList<>();
        if(r.fromYear()!=null) filters.add("from_publication_date:"+r.fromYear()+"-01-01");
        if(r.toYear()!=null) filters.add("to_publication_date:"+r.toYear()+"-12-31");
        if(r.publicationType()!=null) filters.add("type:"+r.publicationType());
        if(!filters.isEmpty()) url+="&filter="+encode(String.join(",",filters));
        if(!apiKey.isBlank()) url+="&api_key="+encode(apiKey);
        if(!email.isBlank()) url+="&mailto="+encode(email);
        return url;
    }
    private String crossrefQuery(SearchRequest r) {
        String url=crossrefUrl+"?query.bibliographic="+encode(r.query())+"&rows=25";
        List<String> filters=new ArrayList<>();
        if(r.fromYear()!=null) filters.add("from-pub-date:"+r.fromYear()+"-01-01");
        if(r.toYear()!=null) filters.add("until-pub-date:"+r.toYear()+"-12-31");
        if(r.publicationType()!=null) filters.add("type:"+switch(r.publicationType()) { case "article" -> "journal-article"; case "preprint" -> "posted-content"; default -> "book-chapter"; });
        if(!filters.isEmpty()) url+="&filter="+encode(String.join(",",filters));
        if(!email.isBlank()) url+="&mailto="+encode(email);
        return url;
    }
    private Paper openalex(JsonNode n, SearchRequest r, String id, Instant time) {
        List<String> authors=new ArrayList<>();
        n.path("authorships").forEach(a->{String name=text(a.path("author"),"display_name");if(!name.isBlank())authors.add(name);});
        String doi=normalizeDoi(text(n,"doi"));
        JsonNode location=n.path("primary_location");
        String sourceId=text(n,"id");
        String url=text(location,"landing_page_url");
        if(url.isBlank()) url=doi.isBlank()?sourceId:"https://doi.org/"+doi;
        return paper(doi,text(n,"display_name"),authors,n.path("publication_year").isInt()?n.path("publication_year").asInt():null,
                text(location.path("source"),"display_name"),text(n,"type"),abstractFromIndex(n.path("abstract_inverted_index")),url,
                text(n.path("open_access"),"oa_url"),new Provenance("openalex",sourceId,sourceId,id,r.query(),time));
    }
    private Paper crossref(JsonNode n, SearchRequest r, String id, Instant time) {
        List<String> authors=new ArrayList<>();
        n.path("author").forEach(a->{String name=(text(a,"given")+" "+text(a,"family")).trim();if(name.isBlank())name=text(a,"name");if(!name.isBlank())authors.add(name);});
        JsonNode parts=n.path("published").path("date-parts").path(0).path(0);
        if(!parts.isInt()) parts=n.path("issued").path("date-parts").path(0).path(0);
        String doi=normalizeDoi(text(n,"DOI"));
        String type=text(n,"type");
        type=switch(type) {case "journal-article" -> "article";case "posted-content" -> "preprint";default -> type;};
        // A generic Crossref license/link does not establish open access.
        return paper(doi,first(n.path("title")),authors,parts.isInt()?parts.asInt():null,first(n.path("container-title")),type,
                Jsoup.parse(text(n,"abstract")).text(),text(n,"URL"),"",
                new Provenance("crossref",doi,"https://doi.org/"+doi,id,r.query(),time));
    }
    private Paper paper(String doi,String title,List<String> authors,Integer year,String venue,String type,String abstractText,String url,String oa,Provenance provenance) {
        return new Paper(UUID.randomUUID().toString(),doi,title,List.copyOf(authors),year,venue,type,abstractText,url,oa,List.of(provenance),Decision.UNDECIDED,"","");
    }
    private boolean matches(Paper p,SearchRequest r) {
        if(r.fromYear()!=null && (p.year()==null || p.year()<r.fromYear())) return false;
        if(r.toYear()!=null && (p.year()==null || p.year()>r.toYear())) return false;
        return r.publicationType()==null || r.publicationType().equals(p.publicationType());
    }
    static String normalizeDoi(String doi) {return doi.trim().replaceFirst("(?i)^(https?://(dx\\.)?doi\\.org/|doi:\\s*)", "").toLowerCase(Locale.ROOT);}
    private static String text(JsonNode n,String field) {return n.path(field).asText("");}
    private static String first(JsonNode n) {return n.isArray() && !n.isEmpty()?n.get(0).asText(""):"";}
    private static String encode(String s) {return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    private static String abstractFromIndex(JsonNode index) {
        TreeMap<Integer,String> words=new TreeMap<>();
        index.fields().forEachRemaining(e->e.getValue().forEach(position->{if(position.canConvertToInt() && position.asInt()>=0) words.put(position.asInt(),e.getKey());}));
        return String.join(" ",words.values());
    }
}
