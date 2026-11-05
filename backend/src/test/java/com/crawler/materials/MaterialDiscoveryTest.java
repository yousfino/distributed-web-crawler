package com.crawler.materials;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.crawler.materials.MaterialsModels.*;

class MaterialDiscoveryTest {
    @Test void discoversEvidenceWithoutTreatingEveryLinkAsResearchMaterial() {
        var page=MaterialDiscovery.discover("""
            <html><head><title>Fallback</title><meta name="citation_title" content="A research paper"></head><body>
            <nav><a href="https://github.com/site/theme">Site source code</a></nav>
            <p>Data availability: supporting data are <a href="https://doi.org/10.1234/data">here</a>.</p>
            <p><a href="https://github.com/team/study#readme">Study code</a></p>
            <p><a href="/supplement.pdf?version=2#page=1">Supporting information</a></p>
            <p><a href="https://protocols.io/view/study">Preregistered protocol</a></p>
            <p><a href="https://zenodo.org/records/123">Repository record</a></p>
            <p><a href="/project">Project page</a></p>
            <p><a href="/contact">Contact</a><a href="mailto:author@example.org">Email</a></p>
            <div id="references"><a href="https://github.com/other/other">Unrelated code citation</a></div>
            </body></html>
            ""","https://example.org/paper","https://example.org/paper");
        assertEquals("A research paper",page.title());assertEquals(6,page.candidates().size());
        assertEquals(List.of(Kind.DATASET,Kind.CODE,Kind.SUPPLEMENT,Kind.PROTOCOL,Kind.REPOSITORY,Kind.PROJECT),page.candidates().stream().map(MaterialDiscovery.Candidate::kind).toList());
        assertTrue(page.candidates().get(0).evidence().context().contains("Data availability"));
        assertEquals("here",page.candidates().get(0).evidence().anchorText());
        assertEquals("https://example.org/supplement.pdf?version=2",page.candidates().get(2).url());
    }
    @Test void canonicalizationKeepsQueriesAndRejectsCredentialsAndNonWebSchemes() {
        assertEquals("https://example.org/a?download=1",MaterialDiscovery.canonical("https://EXAMPLE.org/a?download=1#ref"));
        assertThrows(IllegalArgumentException.class,()->MaterialDiscovery.canonical("file:///etc/passwd"));
        assertThrows(IllegalArgumentException.class,()->MaterialDiscovery.canonical("https://user:pass@example.org/"));
    }
    @Test void destinationPolicyRejectsLocalTargetsAndUnusualPortsByDefault() {
        MaterialsHttp http=new MaterialsHttp(false);
        for(String url:List.of("http://127.0.0.1/","http://169.254.169.254/","http://10.0.0.1/","http://localhost/","http://service.internal/","https://example.org:9000/"))
            assertThrows(IllegalArgumentException.class,()->http.validate(url),url);
    }
    @Test void robotsUsesSpecificGroupsAndAllowPrecedence() {
        var rules=MaterialsHttp.Rules.parse("User-agent: *\nDisallow: /\nUser-agent: ResearchAtlas\nDisallow: /private/\nAllow: /private/open$\n");
        assertTrue(rules.allows("/paper"));assertFalse(rules.allows("/private/file"));assertTrue(rules.allows("/private/open"));assertFalse(rules.allows("/private/open?x=1"));
        assertTrue(MaterialsHttp.Rules.parse("User-agent: *\nDisallow: /\nUser-agent: ResearchAtlas\nDisallow:\n").allows("/paper"));
    }
}
