package com.crawler.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.crawler.research.ResearchModels.*;

@SpringBootTest
@AutoConfigureMockMvc
class ResearchWorkflowTest {
    static HttpServer server;
    static volatile int openalexStatus=200, crossrefStatus=200;
    static final List<String> queries=new CopyOnWriteArrayList<>();
    static final String OPENALEX=fixture("openalex.json");
    static final String CROSSREF=fixture("crossref.json");
    private static String fixture(String name) {
        try (var input=ResearchWorkflowTest.class.getResourceAsStream("/scholarly/"+name)) {
            return new String(Objects.requireNonNull(input).readAllBytes(),StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {throw new java.io.UncheckedIOException(e);}
    }
    @DynamicPropertySource static void endpoints(DynamicPropertyRegistry registry) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/openalex",exchange->{
            queries.add(exchange.getRequestURI().getRawQuery());
            byte[] body=OPENALEX.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(openalexStatus,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        server.createContext("/crossref",exchange->{
            queries.add(exchange.getRequestURI().getRawQuery());
            byte[] body=CROSSREF.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(crossrefStatus,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        server.start();
        registry.add("research.openalex-url",()->"http://127.0.0.1:"+server.getAddress().getPort()+"/openalex");
        registry.add("research.crossref-url",()->"http://127.0.0.1:"+server.getAddress().getPort()+"/crossref");
    }
    @AfterAll static void shutdown(){server.stop(0);}
    @BeforeEach void reset(){openalexStatus=200;crossrefStatus=200;queries.clear();}
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ResearchRepository repository;
    @Autowired ResearchService service;
    private String project() throws Exception {
        return mapper.readTree(mvc.perform(post("/api/research/projects").contentType("application/json")
                .content("{\"name\":\"Health evidence\",\"question\":\"How effective are interventions?\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
    }
    private Project search(String id,Integer fromYear) {
        return service.search(id,new SearchRequest("health & evidence",fromYear,2024,"article",List.of("openalex","crossref")));
    }
    @Test void searchMergesDoiAcrossSourcesAndPersistsReviewAcrossRepeatSearches() throws Exception {
        String id=project();Project p=search(id,2020);
        assertEquals(2,p.papers().size());assertEquals(2,p.searches().get(0).added());assertEquals(3,p.searches().get(0).found());
        Paper paper=p.papers().stream().filter(a->a.doi().equals("10.1234/test")).findFirst().orElseThrow();
        assertEquals(2,paper.provenance().size());assertEquals("Health evidence evidence",paper.abstractText());
        assertEquals("https://example.org/open.pdf",paper.openAccessUrl());
        mvc.perform(patch("/api/research/projects/"+id+"/papers/"+paper.id()).contentType("application/json")
                .content("{\"decision\":\"INCLUDED\",\"reason\":\"Relevant population\",\"notes\":\"Read methods, then compare.\"}"))
                .andExpect(status().isOk());
        Project again=search(id,2020);
        assertEquals(2,again.papers().size());assertEquals(0,again.searches().get(1).added());
        Paper stored=repository.get(id).papers().stream().filter(a->a.id().equals(paper.id())).findFirst().orElseThrow();
        assertEquals(Decision.INCLUDED,stored.decision());assertEquals("Read methods, then compare.",stored.notes());
        assertEquals(4,stored.provenance().size());
        assertEquals("health & evidence",stored.provenance().get(0).query());
        assertTrue(queries.stream().anyMatch(q->q.contains("from_publication_date%3A2020-01-01")));
        assertTrue(queries.stream().anyMatch(q->q.contains("from-pub-date%3A2020-01-01")));
    }
    @Test void exportsReadableCsvAndBibtexWithIncludedScope() throws Exception {
        String id=project();Project p=search(id,2020);
        Paper first=p.papers().stream().filter(a->a.doi().equals("10.1234/test")).findFirst().orElseThrow();
        service.screen(id,first.id(),new ScreeningRequest(Decision.INCLUDED,"","Line 1\nLine 2"));
        String all=service.csv(id,null);
        assertTrue(all.startsWith("\uFEFFProject ID,Project Name"));assertTrue(all.contains("\"Evidence, \"\"review\"\" & health\""));
        assertTrue(all.contains("\"'=Second {study}\""));assertTrue(all.contains("Zoë Smith"));assertTrue(all.contains("Useful & readable."));
        assertTrue(all.contains("Search Queries"));assertTrue(all.contains("Retrieved At (UTC)"));
        String csv=service.csv(id,Decision.INCLUDED);assertFalse(csv.contains("10.1234/second"));assertTrue(csv.contains("Line 1\nLine 2"));
        String bib=service.bibtex(id,Decision.INCLUDED);assertTrue(bib.contains("@article{paper"));assertTrue(bib.contains("doi = {10.1234/test}"));
        assertTrue(bib.contains("\\& health"));assertFalse(bib.contains("10.1234/second"));
        assertTrue(service.bibtex(id,null).contains("\\{study\\}"));
        mvc.perform(get("/api/research/projects/"+id+"/export.csv").param("decision","INCLUDED"))
                .andExpect(status().isOk()).andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/research/projects/"+id+"/export.bib")).andExpect(status().isOk());
    }
    @Test void respectsFiltersAndRecordsSourceFailuresWithoutLosingAvailableResults() throws Exception {
        String id=project();openalexStatus=429;
        Project p=search(id,2023);
        assertEquals(1,p.papers().size());assertEquals(2023,p.papers().get(0).year());
        assertTrue(p.searches().get(0).warnings().get(0).contains("rate limit"));
        crossrefStatus=403;
        Project failed=search(id,2023);
        assertEquals(1,failed.papers().size());assertEquals(2,failed.searches().get(1).warnings().size());assertEquals(0,failed.searches().get(1).found());
    }
    @Test void rejectsInvalidInputsAndKeepsProjectsIsolated() throws Exception {
        String id=project();Project p=search(id,2020);String other=project();
        String paperId=p.papers().get(0).id();
        mvc.perform(patch("/api/research/projects/"+id+"/papers/"+paperId).contentType("application/json")
                .content("{\"decision\":\"EXCLUDED\",\"reason\":\"\",\"notes\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("An exclusion reason is required"));
        mvc.perform(patch("/api/research/projects/"+other+"/papers/"+paperId).contentType("application/json")
                .content("{\"decision\":\"INCLUDED\"}" )).andExpect(status().isNotFound());
        mvc.perform(post("/api/research/projects/"+id+"/searches").contentType("application/json")
                .content("{\"query\":\"health\",\"fromYear\":2024,\"toYear\":2020,\"sources\":[\"crossref\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/research/projects/"+id+"/searches").contentType("application/json")
                .content("{\"query\":\"health\",\"sources\":[\"unknown\"]}" )).andExpect(status().isBadRequest());
        assertTrue(repository.get(other).papers().isEmpty());
    }
    @Test void deduplicationDoesNotMergeDifferentDoisOrDifferentYears() {
        Paper a=paper("10.1/a",2023),b=paper("10.1/b",2023),c=paper("",2023),d=paper("",2022);
        assertFalse(ResearchService.samePaper(a,b));assertTrue(ResearchService.samePaper(a,c));assertFalse(ResearchService.samePaper(a,d));
        assertEquals("10.1234/test",ScholarlySources.normalizeDoi("HTTPS://DX.DOI.ORG/10.1234/TEST"));
    }
    private Paper paper(String doi,int year) {return new Paper(UUID.randomUUID().toString(),doi,"Same title",List.of("Author"),year,"","article","","","",List.of(),Decision.UNDECIDED,"","");}
}
