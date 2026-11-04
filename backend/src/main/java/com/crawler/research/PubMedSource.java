package com.crawler.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import static com.crawler.research.ResearchModels.*;

@Component
public class PubMedSource {
    private final ObjectMapper mapper;
    private final HttpClient client=ScholarlyHttp.client();
    private final String endpoint,email;
    private long nextRequestNanos=System.nanoTime();
    public PubMedSource(ObjectMapper mapper,
            @Value("${research.pubmed-url:https://eutils.ncbi.nlm.nih.gov/entrez/eutils}") String endpoint,
            @Value("${research.contact-email:}") String email) {
        this.mapper=mapper;this.endpoint=endpoint.replaceAll("/+$","");this.email=email;
    }
    public SourceResult search(SearchRequest request,String searchId,Instant retrievedAt) {
        try {
            String term="("+request.query()+")";
            if(request.publicationType()!=null) term+=" AND "+switch(request.publicationType()) {
                case "article" -> "\"Journal Article\"[Publication Type]";
                case "preprint" -> "\"Preprint\"[Publication Type]";
                default -> "\"Book Chapter\"[Publication Type]";
            };
            String params="db=pubmed&retmode=json&retmax=25&sort=relevance&term="+encode(term);
            if(request.fromYear()!=null || request.toYear()!=null) params+="&datetype=pdat&mindate="+
                    (request.fromYear()==null?1600:request.fromYear())+"&maxdate="+(request.toYear()==null?2200:request.toYear());
            JsonNode result=mapper.readTree(get("esearch.fcgi",params,"application/json")).path("esearchresult");
            if(!result.path("idlist").isArray() || result.hasNonNull("ERROR") || result.path("errorlist").has("phrasesnotfound")) {
                return warning("could not interpret the search; check PubMed query syntax");
            }
            List<String> ids=new ArrayList<>();
            for(JsonNode id:result.path("idlist")) if(id.asText().matches("[0-9]+") && ids.size()<25) ids.add(id.asText());
            if(ids.isEmpty()) return new SourceResult(List.of(),List.of());
            String xml=get("efetch.fcgi","db=pubmed&retmode=xml&id="+String.join(",",ids),"application/xml");
            return new SourceResult(parse(xml,request,searchId,retrievedAt),List.of());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();return warning("search interrupted");
        } catch (UpstreamFailure e) {
            return warning(e.getMessage());
        } catch (Exception e) {
            return warning("could not retrieve papers; check connectivity and try again");
        }
    }
    // Shared across all PubMed searches in this application, below NCBI's 3 requests/second limit without a key.
    private synchronized String get(String action,String query,String accept) throws Exception {
        long wait=nextRequestNanos-System.nanoTime();
        if(wait>0) java.util.concurrent.TimeUnit.NANOSECONDS.sleep(wait);
        nextRequestNanos=System.nanoTime()+Duration.ofMillis(350).toNanos();
        String url=endpoint+"/"+action+"?"+query+"&tool=research_atlas"+(email.isBlank()?"":"&email="+encode(email));
        HttpResponse<String> response=client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Accept",accept).header("User-Agent","ResearchAtlas/1.0").GET().build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if(response.statusCode()!=200) throw new UpstreamFailure(switch(response.statusCode()) {
            case 429 -> "rate limit reached; try again later";
            case 401,403 -> "access denied; check network access";
            default -> "upstream HTTP "+response.statusCode();
        });
        return response.body();
    }
    private static final class UpstreamFailure extends Exception { UpstreamFailure(String message) {super(message);} }
    private static SourceResult warning(String text) {return new SourceResult(List.of(),List.of("pubmed: "+text));}
    private static String encode(String value) {return URLEncoder.encode(value,StandardCharsets.UTF_8);}

    static List<Paper> parse(String xml,SearchRequest request,String searchId,Instant retrievedAt) throws Exception {
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
        // PubMed includes a public DOCTYPE; allow that declaration without loading its DTD or resolving entities.
        Document document=factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        if(!document.getDocumentElement().getTagName().equals("PubmedArticleSet")) throw new IllegalArgumentException("Unexpected PubMed XML");
        if(document.getDoctype()!=null && document.getDoctype().getInternalSubset()!=null && !document.getDoctype().getInternalSubset().isBlank())
            throw new IllegalArgumentException("Custom XML entities are not supported");
        List<Paper> papers=new ArrayList<>();
        for(Node node=document.getDocumentElement().getFirstChild();node!=null;node=node.getNextSibling()) {
            if(!(node instanceof Element record)) continue;
            boolean book=record.getTagName().equals("PubmedBookArticle");
            if(!book && !record.getTagName().equals("PubmedArticle")) continue;
            Element citation=child(record,book?"BookDocument":"MedlineCitation");
            Element article=book?citation:child(citation,"Article");
            String pmid=text(child(citation,"PMID")),title=text(child(article,"ArticleTitle"));
            if(!pmid.matches("[0-9]+") || title.isBlank()) continue;
            List<String> authors=new ArrayList<>();
            Element authorList=child(article,"AuthorList");
            if(authorList!=null) for(Element author:children(authorList,"Author")) {
                String name=text(child(author,"CollectiveName"));
                if(name.isBlank()) {
                    String given=text(child(author,"ForeName"));
                    if(given.isBlank()) given=text(child(author,"Initials"));
                    name=(given+" "+text(child(author,"LastName"))).trim();
                }
                if(!name.isBlank()) authors.add(name);
            }
            String doi="",pmc="";
            Element data=child(record,book?"PubmedBookData":"PubmedData");
            Element articleIds=child(data,"ArticleIdList");
            if(articleIds==null && book) articleIds=child(citation,"ArticleIdList");
            if(articleIds!=null) for(Element id:children(articleIds,"ArticleId")) {
                if(id.getAttribute("IdType").equals("doi")) doi=ScholarlySources.normalizeDoi(text(id));
                if(id.getAttribute("IdType").equals("pmc") && text(id).matches("PMC[0-9]+")) pmc=text(id);
            }
            if(doi.isBlank() && article!=null) for(Element id:children(article,"ELocationID"))
                if(id.getAttribute("EIdType").equals("doi")) doi=ScholarlySources.normalizeDoi(text(id));
            Element journal=child(article,"Journal");
            Element pubDate=book?child(child(citation,"Book"),"PubDate"):child(child(journal,"JournalIssue"),"PubDate");
            String date=text(child(pubDate,"Year"));
            if(date.isBlank()) date=text(child(pubDate,"MedlineDate"));
            if(date.isBlank()) date=text(child(child(article,"ArticleDate"),"Year"));
            Matcher yearMatch=Pattern.compile("\\b(1[6-9][0-9]{2}|2[0-2][0-9]{2})\\b").matcher(date);
            Integer year=yearMatch.find()?Integer.parseInt(yearMatch.group()):null;
            String type=book?"book-chapter":"article";
            Element types=child(article,"PublicationTypeList");
            if(types!=null && children(types,"PublicationType").stream().anyMatch(t->text(t).equalsIgnoreCase("Preprint"))) type="preprint";
            if(request.fromYear()!=null && (year==null || year<request.fromYear())) continue;
            if(request.toYear()!=null && (year==null || year>request.toYear())) continue;
            if(request.publicationType()!=null && !request.publicationType().equals(type)) continue;
            List<String> paragraphs=new ArrayList<>();
            Element abstractNode=child(article,"Abstract");
            if(abstractNode!=null) for(Element paragraph:children(abstractNode,"AbstractText")) {
                String label=paragraph.getAttribute("Label");
                paragraphs.add((label.isBlank()?"":label+": ")+text(paragraph));
            }
            String url="https://pubmed.ncbi.nlm.nih.gov/"+pmid+"/";
            papers.add(new Paper(UUID.randomUUID().toString(),doi,title,List.copyOf(authors),year,
                    book?text(child(child(citation,"Book"),"BookTitle")):text(child(journal,"Title")),type,String.join("\n",paragraphs),url,
                    pmc.isBlank()?"":"https://pmc.ncbi.nlm.nih.gov/articles/"+pmc+"/",
                    List.of(new Provenance("pubmed",pmid,url,searchId,request.query(),retrievedAt)),Decision.UNDECIDED,"",""));
        }
        return papers;
    }
    private static String text(Element element) {return element==null?"":element.getTextContent().replaceAll("\\s+"," ").trim();}
    private static Element child(Element parent,String name) {
        if(parent!=null) for(Node node=parent.getFirstChild();node!=null;node=node.getNextSibling())
            if(node instanceof Element element && element.getTagName().equals(name)) return element;
        return null;
    }
    private static List<Element> children(Element parent,String name) {
        List<Element> result=new ArrayList<>();
        if(parent!=null) for(Node node=parent.getFirstChild();node!=null;node=node.getNextSibling())
            if(node instanceof Element element && element.getTagName().equals(name)) result.add(element);
        return result;
    }
}
