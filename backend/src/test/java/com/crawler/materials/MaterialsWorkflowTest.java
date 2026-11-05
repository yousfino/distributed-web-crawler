package com.crawler.materials;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static com.crawler.materials.MaterialsModels.*;

@SpringBootTest
@AutoConfigureMockMvc
class MaterialsWorkflowTest {
    static HttpServer server;static final List<String> requests=new CopyOnWriteArrayList<>();
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            String path=exchange.getRequestURI().getPath(),method=exchange.getRequestMethod();requests.add(method+" "+path);
            int status=200;String body="",type="text/html; charset=UTF-8";
            if(path.equals("/robots.txt")) {type="text/plain";body="User-agent: *\nDisallow: /private/\n";}
            else if(path.startsWith("/paper/")) body="<title>Evidence &amp; materials</title><p>Data availability: <a href='/data.csv?version=1#top'>Download dataset</a></p><a href='/supplement.pdf'>Supplementary file</a><a href='/protocol'>Study protocol</a><a href='/code-repository'>Source code repository</a><a href='/private/dataset'>Dataset (private)</a>";
            else if(path.equals("/empty")) body="<title>Paper without materials</title><a href='/about'>About</a>";
            else if(path.equals("/denied")) status=403;
            else if(path.equals("/pdf-paper")) {type="application/pdf";body="not parsed";}
            else if(path.equals("/data.csv")) {type="text/csv";body="x,y\n1,2";}
            else if(path.equals("/supplement.pdf")) status=404;
            else if(path.equals("/protocol")) {status=302;exchange.getResponseHeaders().add("Location","/protocol-final");}
            else if(path.equals("/protocol-final")) status=403;
            else if(path.equals("/code-repository") && method.equals("HEAD")) status=405;
            else if(path.equals("/code-repository")) body="<title>Study code</title>";
            else if(path.equals("/private/dataset")) body="must not be fetched";
            else status=404;
            exchange.getResponseHeaders().set("Content-Type",type);
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,method.equals("HEAD")?-1:bytes.length);
            if(!method.equals("HEAD"))exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();registry.add("materials.allow-loopback",()->true);
    }
    @AfterAll static void stop(){server.stop(0);}
    @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired MaterialsService service;@Autowired MaterialsRepository repository;
    String url(String path){return "http://127.0.0.1:"+server.getAddress().getPort()+path;}
    Inventory await(String id) throws Exception {
        long limit=System.nanoTime()+java.time.Duration.ofSeconds(40).toNanos();Inventory value;
        do {value=repository.get(id);if(!MaterialsService.active(value))return value;Thread.sleep(50);}while(System.nanoTime()<limit);
        fail("Scan did not complete");return value;
    }
    @Test void tenPaperInventoryDeduplicatesLinksChecksAccessAndPreservesReviewEvidence() throws Exception {
        List<String> urls=new ArrayList<>();for(int i=0;i<10;i++)urls.add(url("/paper/"+i));
        String response=mvc.perform(post("/api/materials/inventories").contentType("application/json").content(mapper.writeValueAsString(new ScanRequest("Materials pilot",urls))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        Inventory inventory=await(mapper.readTree(response).get("id").asText());
        assertEquals(Phase.COMPLETE,inventory.phase());assertEquals(10,inventory.pages().size());assertEquals(5,inventory.findings().size());
        Finding data=inventory.findings().stream().filter(f->f.kind()==Kind.DATASET && f.url().contains("data.csv")).findFirst().orElseThrow();
        assertEquals(10,data.evidence().size());assertEquals(Access.RESPONDED,data.check().access());assertEquals(200,data.check().httpStatus());
        assertTrue(data.url().endsWith("?version=1"));
        assertTrue(inventory.findings().stream().anyMatch(f->f.check().access()==Access.NOT_FOUND));
        assertTrue(inventory.findings().stream().anyMatch(f->f.check().access()==Access.RESTRICTED && f.check().finalUrl().endsWith("/protocol-final")));
        assertTrue(inventory.findings().stream().anyMatch(f->f.kind()==Kind.CODE && f.check().access()==Access.RESPONDED));
        assertFalse(requests.stream().anyMatch(r->r.contains("/private/dataset")));
        assertTrue(requests.contains("HEAD /code-repository"));assertTrue(requests.contains("GET /code-repository"));
        mvc.perform(patch("/api/materials/inventories/"+inventory.id()+"/findings/"+data.id()).contentType("application/json")
                .content("{\"decision\":\"VERIFIED\",\"notes\":\"=Report note, with \\\"quotes\\\"\"}" )).andExpect(status().isOk());
        Finding saved=repository.get(inventory.id()).findings().stream().filter(f->f.id().equals(data.id())).findFirst().orElseThrow();assertEquals(Review.VERIFIED,saved.review());
        String csv=service.csv(inventory.id(),Review.VERIFIED);assertTrue(csv.startsWith("\uFEFFInventory ID"));assertTrue(csv.contains("\"'=Report note, with \"\"quotes\"\"\""));assertTrue(csv.contains("Discovery Reason"));assertTrue(csv.contains(url("/paper/9")));assertFalse(csv.contains("/supplement.pdf"));
        mvc.perform(get("/api/materials/inventories/"+inventory.id()+"/export.csv")).andExpect(status().isOk()).andExpect(content().contentType("text/csv;charset=UTF-8"));
        mvc.perform(patch("/api/materials/inventories/"+inventory.id()+"/findings/missing").contentType("application/json").content("{\"decision\":\"REJECTED\"}")).andExpect(status().isNotFound());
    }
    @Test void emptyDeniedAndPdfPagesHaveDistinctHonestOutcomes() throws Exception {
        Inventory result=await(service.create(new ScanRequest("Coverage test",List.of(url("/empty"),url("/denied"),url("/pdf-paper")))).id());
        assertTrue(result.findings().isEmpty());assertEquals(3,result.pages().size());
        assertTrue(result.pages().get(0).outcome().contains("missing materials are not evidence of absence"));
        assertEquals(403,result.pages().get(1).httpStatus());assertTrue(result.pages().get(2).outcome().contains("PDFs"));
        assertTrue(result.warnings().stream().anyMatch(w->w.contains("incomplete")));
    }
    @Test void interruptedScansAreRecoveredWithoutInventingCompletion() {
        Inventory stale=repository.create(new Inventory(UUID.randomUUID().toString(),"Interrupted",List.of(url("/paper/1")),java.time.Instant.now(),null,Phase.SCANNING,List.of(),List.of(),List.of()));
        service.recover();Inventory recovered=repository.get(stale.id());
        assertEquals(Phase.INTERRUPTED,recovered.phase());assertNotNull(recovered.finishedAt());assertTrue(recovered.warnings().get(0).contains("restarted"));
    }
    @Test void rejectsNonHttpAndOversizedBatch() throws Exception {
        mvc.perform(post("/api/materials/inventories").contentType("application/json").content("{\"name\":\"Bad input\",\"paperUrls\":[\"file:///etc/passwd\"]}" )).andExpect(status().isBadRequest());
        mvc.perform(post("/api/materials/inventories").contentType("application/json").content(mapper.writeValueAsString(new ScanRequest("Too many",Collections.nCopies(11,url("/empty")))))).andExpect(status().isBadRequest());
    }
}
