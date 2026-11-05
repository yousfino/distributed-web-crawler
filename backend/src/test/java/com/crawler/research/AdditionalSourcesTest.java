package com.crawler.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static com.crawler.research.ResearchModels.*;

class AdditionalSourcesTest {
    HttpServer server;
    final List<String> queries=new CopyOnWriteArrayList<>();
    final List<Long> requests=new CopyOnWriteArrayList<>();
    int searchStatus=200,fetchStatus=200,scholarStatus=200;
    String searchBody="{\"esearchresult\":{\"idlist\":[\"12345\",\"32100\",\"54321\"]}}";
    String scholarBody=fixture("scholar.html");
    static String fixture(String name) {
        try(var in=AdditionalSourcesTest.class.getResourceAsStream("/scholarly/"+name)) {
            return new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8);
        } catch(Exception e) {throw new IllegalStateException(e);}
    }
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/esearch.fcgi",exchange->{
            queries.add(exchange.getRequestURI().getRawQuery());requests.add(System.nanoTime());
            byte[] bytes=searchBody.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(searchStatus,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.createContext("/efetch.fcgi",exchange->{
            queries.add(exchange.getRequestURI().getRawQuery());requests.add(System.nanoTime());
            byte[] bytes=fixture("pubmed.xml").getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(fetchStatus,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.createContext("/scholar",exchange->{
            queries.add(exchange.getRequestURI().getRawQuery());byte[] bytes=scholarBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(scholarStatus,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
    }
    @AfterEach void stop(){server.stop(0);}
    String endpoint(){return "http://127.0.0.1:"+server.getAddress().getPort();}
    SearchRequest request(Integer from,Integer to,String type){return new SearchRequest("health & evidence",from,to,type,List.of("pubmed"));}
    @Test void pubmedSearchFetchesBatchedRecordsWithFiltersAndPacing() {
        PubMedSource source=new PubMedSource(new ObjectMapper(),endpoint(),"research@example.org");
        SourceResult result=source.search(request(2020,2024,null),"search-id",Instant.parse("2026-11-04T12:00:00Z"));
        assertTrue(result.warnings().isEmpty(),result.warnings().toString());assertEquals(3,result.papers().size());
        Paper article=result.papers().get(0);
        assertEquals("10.1234/test",article.doi());assertEquals("Evidence, \"review\" & health",article.title());
        assertEquals(List.of("Zoë Smith","Health Research Group"),article.authors());
        assertEquals("BACKGROUND: Health evidence.\nRESULTS: Useful findings.",article.abstractText());
        assertEquals("https://pmc.ncbi.nlm.nih.gov/articles/PMC12345/",article.openAccessUrl());
        assertEquals("12345",article.provenance().get(0).sourceId());assertEquals("search-id",article.provenance().get(0).searchId());
        assertEquals("preprint",result.papers().get(1).publicationType());assertEquals(2021,result.papers().get(1).year());
        assertEquals("book-chapter",result.papers().get(2).publicationType());
        assertTrue(queries.get(0).contains("health+%26+evidence"));assertTrue(queries.get(0).contains("mindate=2020&maxdate=2024"));
        assertTrue(queries.get(1).contains("id=12345,32100,54321"));assertTrue(queries.get(1).contains("email=research%40example.org"));
        assertTrue(requests.get(1)-requests.get(0)>=java.time.Duration.ofMillis(300).toNanos());
    }
    @Test void pubmedFiltersMetadataAndHandlesEmptyResultsAndFailures() {
        PubMedSource source=new PubMedSource(new ObjectMapper(),endpoint(),"");
        SourceResult filtered=source.search(request(2023,2024,"article"),"id",Instant.now());
        assertEquals(1,filtered.papers().size());assertTrue(queries.get(0).contains("Journal+Article"));
        searchBody="{\"esearchresult\":{\"idlist\":[]}}";int before=queries.size();
        assertTrue(source.search(request(null,null,null),"id",Instant.now()).warnings().isEmpty());assertEquals(before+1,queries.size());
        searchStatus=429;
        assertTrue(source.search(request(null,null,null),"id",Instant.now()).warnings().get(0).contains("rate limit"));
        searchStatus=200;searchBody="{\"esearchresult\":{\"idlist\":[\"12345\"]}}";fetchStatus=503;
        assertTrue(source.search(request(null,null,null),"id",Instant.now()).warnings().get(0).contains("503"));
    }
    @Test void pubmedXmlNeverLoadsExternalEntities() {
        String malicious="<!DOCTYPE PubmedArticleSet [<!ENTITY ex SYSTEM 'file:///etc/passwd'>]><PubmedArticleSet>&ex;</PubmedArticleSet>";
        assertThrows(Exception.class,()->PubMedSource.parse(malicious,request(null,null,null),"id",Instant.now()));
    }
    @Test void scholarOwnAdapterParsesOnlyAvailableMetadataAndLimitsRepeatRequests() {
        GoogleScholarSource source=new GoogleScholarSource(endpoint()+"/scholar");
        SourceResult result=source.search(request(null,null,null),"id",Instant.now());
        assertEquals(2,result.papers().size());assertTrue(result.warnings().isEmpty());
        Paper p=result.papers().get(0);assertEquals("10.1234/test",p.doi());assertEquals(2023,p.year());
        assertEquals(List.of("Z Smith","A Lee"),p.authors());assertEquals("Research Journal",p.venue());
        assertEquals("",p.abstractText());assertEquals("",p.publicationType());assertEquals("",p.openAccessUrl());
        assertEquals("scholar",p.provenance().get(0).source());
        assertTrue(source.search(request(null,null,null),"id",Instant.now()).warnings().get(0).contains("10 seconds"));
        assertEquals(1,queries.size());
    }
    @Test void scholarReportsCaptchaBlockedAndUnrecognizedPagesWithoutRetrying() throws Exception {
        scholarStatus=403;
        assertTrue(new GoogleScholarSource(endpoint()+"/scholar").search(request(null,null,null),"id",Instant.now()).warnings().get(0).contains("not guaranteed"));
        scholarStatus=200;scholarBody="<html><form id='captcha-form'>Unusual traffic</form></html>";
        SourceResult captcha=new GoogleScholarSource(endpoint()+"/scholar").search(request(null,null,null),"id",Instant.now());
        assertTrue(captcha.papers().isEmpty());assertTrue(captcha.warnings().get(0).contains("CAPTCHA"));
        assertTrue(GoogleScholarSource.parse("<html>Changed markup</html>",request(null,null,null),"id",Instant.now()).warnings().get(0).contains("unrecognized"));
        assertEquals(2,queries.size());
    }
    @Test void scholarHonorsYearBoundsAndDeclinesUnreliableTypeFiltering() throws Exception {
        SourceResult filtered=GoogleScholarSource.parse(fixture("scholar.html"),request(2023,2024,null),"id",Instant.now());
        assertEquals(1,filtered.papers().size());assertFalse(filtered.warnings().isEmpty());
        SourceResult unsupported=new GoogleScholarSource(endpoint()+"/scholar").search(request(null,null,"article"),"id",Instant.now());
        assertTrue(unsupported.papers().isEmpty());assertTrue(unsupported.warnings().get(0).contains("publication-type"));assertTrue(queries.isEmpty());
    }
}
