package com.crawler.materials;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import static com.crawler.materials.MaterialsModels.*;

final class MaterialDiscovery {
    record Candidate(Kind kind,String label,String url,Evidence evidence) {}
    record Page(String title,List<Candidate> candidates) {}
    static Page discover(String html,String paperUrl,String pageUrl) {
        Document doc=Jsoup.parse(html,pageUrl);
        String title=doc.select("meta[name=citation_title]").attr("content");
        if(title.isBlank()) title=doc.title();
        List<Candidate> candidates=new ArrayList<>();
        // Skip navigation, references and footer links: repository links there need not describe study materials.
        for(Element a:doc.select("a[href]")) {
            if(a.parents().stream().anyMatch(p->Set.of("nav","footer").contains(p.tagName()) || p.id().matches("(?i).*(references|bibliography).*"))) continue;
            String raw=a.absUrl("href");
            String url;
            try {url=canonical(raw);}catch(Exception e){continue;}
            String label=a.text().replaceAll("\\s+"," ").trim();
            String context=context(a);
            String signal=(label+" "+URI.create(url).getPath()).toLowerCase(Locale.ROOT);
            String host=URI.create(url).getHost();
            Kind kind=null;String reason="";
            if(signal.matches("(?s).*(supplement(ar(y|ies)|al)?|supporting[-_ ]?(information|material|data)|additional[-_ ]?files?).*")) {
                kind=Kind.SUPPLEMENT;reason="Link label or path explicitly mentions supporting/supplementary material";
            } else if(signal.matches("(?s).*(protocol|preregistr|pre-registr|registered[-_ ]?report).*" ) || domain(host,"protocols.io")) {
                kind=Kind.PROTOCOL;reason="Link identifies a protocol or preregistration";
            } else if(signal.matches("(?s).*(dataset|data[-_ ]?(set|download|repository|archive|availability)|download[-_ ]?data|research[-_ ]?data).*")) {
                kind=Kind.DATASET;reason="Link label or path explicitly identifies research data";
            } else if(repositoryPath(url,"github.com") || repositoryPath(url,"gitlab.com") || repositoryPath(url,"bitbucket.org") || signal.matches("(?s).*(source[-_ ]?code|code[-_ ]?(repository|availability)|analysis[-_ ]?(code|scripts)|software[-_ ]?repository).*")) {
                kind=Kind.CODE;reason="Link identifies source code or a concrete code repository";
            } else if(domain(host,"zenodo.org") || domain(host,"figshare.com") || domain(host,"datadryad.org") || domain(host,"osf.io") || domain(host,"openneuro.org")) {
                kind=Kind.REPOSITORY;reason="Research repository link; material type needs human review";
            } else if(signal.matches("(?s).*(project[-_ ]?(page|website|site)|study[-_ ]?(website|page)|companion[-_ ]?(website|page)).*")) {
                kind=Kind.PROJECT;reason="Link explicitly identifies a related project/study page";
            }
            // Context enriches evidence but never turns every link in a long paragraph into a material.
            if(kind==null && a.parent()!=null && a.parent().select("a[href]").size()<=3) {
                String nearby=context.toLowerCase(Locale.ROOT);
                if(nearby.contains("data availability")) {kind=Kind.DATASET;reason="Link occurs in an explicit data-availability statement; verify relevance";}
                else if(nearby.contains("code availability")) {kind=Kind.CODE;reason="Link occurs in an explicit code-availability statement; verify relevance";}
            }
            if(kind!=null) candidates.add(new Candidate(kind,label.isBlank()?URI.create(url).getPath():label,url,
                    new Evidence(paperUrl,pageUrl,clip(label,500),clip(context,1200),reason,Instant.now())));
        }
        return new Page(clip(title,1000),List.copyOf(candidates));
    }
    static String canonical(String value) {
        URI uri=URI.create(value.trim()).normalize();
        if(uri.getScheme()==null || !Set.of("http","https").contains(uri.getScheme().toLowerCase(Locale.ROOT)) || uri.getHost()==null || uri.getUserInfo()!=null) throw new IllegalArgumentException("Use an HTTP or HTTPS URL without credentials");
        return URI.create(uri.getScheme().toLowerCase(Locale.ROOT)+"://"+uri.getHost().toLowerCase(Locale.ROOT)+(uri.getPort()<0?"":":"+uri.getPort())+
                (uri.getRawPath()==null || uri.getRawPath().isEmpty()?"/":uri.getRawPath())+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery())).toString();
    }
    private static boolean repositoryPath(String url,String domain) {
        URI uri=URI.create(url);if(!domain(uri.getHost(),domain)) return false;
        String[] parts=Arrays.stream(uri.getPath().split("/")).filter(p->!p.isBlank()).toArray(String[]::new);
        return parts.length>=2 && !Set.of("topics","search","collections","features","explore","orgs").contains(parts[0]);
    }
    private static boolean domain(String host,String domain) {return host.equals(domain)||host.endsWith("."+domain);}
    private static String context(Element a) {
        Element parent=a.parent();
        for(int i=0;i<3 && parent!=null;i++,parent=parent.parent()) if(Set.of("p","li","section","div").contains(parent.tagName())) return parent.text();
        return a.text();
    }
    static String clip(String text,int limit) {return text.length()<=limit?text:text.substring(0,limit)+"…";}
}
