package com.crawler.research;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import static com.crawler.research.ResearchModels.*;

@Service
public class ResearchService {
    private final ResearchRepository repository;
    private final ScholarlySources sources;
    public ResearchService(ResearchRepository repository, ScholarlySources sources) {this.repository=repository;this.sources=sources;}
    public List<ProjectSummary> projects() {
        return repository.all().stream().sorted(Comparator.comparing(Project::createdAt).reversed()).map(p->new ProjectSummary(
                p.id(),p.name(),p.question(),p.createdAt(),p.papers().size(),
                p.papers().stream().filter(a->a.decision()==Decision.INCLUDED).count(),
                p.papers().stream().filter(a->a.decision()==Decision.EXCLUDED).count())).toList();
    }
    public Project get(String id) {return repository.get(id);}
    public Project create(ProjectRequest request) {
        return repository.create(new Project(UUID.randomUUID().toString(),request.name().trim(),request.question().trim(),Instant.now(),List.of(),List.of()));
    }
    public Project search(String id,SearchRequest request) {
        repository.get(id);
        if(request.fromYear()!=null && request.toYear()!=null && request.fromYear()>request.toYear())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Start year must not exceed end year");
        String searchId=UUID.randomUUID().toString();
        Instant time=Instant.now();
        List<Paper> found=new ArrayList<>();
        List<String> warnings=new ArrayList<>();
        for(String source:new LinkedHashSet<>(request.sources())) {
            SourceResult result=sources.search(source,request,searchId,time);
            found.addAll(result.papers());warnings.addAll(result.warnings());
        }
        return repository.update(id,project->{
            List<Paper> papers=new ArrayList<>(project.papers());
            int before=papers.size();
            for(Paper candidate:found) {
                int existing=-1;
                for(int i=0;i<papers.size();i++) if(samePaper(papers.get(i),candidate)) {existing=i;break;}
                if(existing<0) papers.add(candidate);
                else papers.set(existing,merge(papers.get(existing),candidate));
            }
            List<SearchRun> searches=new ArrayList<>(project.searches());
            searches.add(new SearchRun(searchId,request,time,found.size(),papers.size()-before,List.copyOf(warnings)));
            return new Project(project.id(),project.name(),project.question(),project.createdAt(),List.copyOf(papers),List.copyOf(searches));
        });
    }
    static boolean samePaper(Paper a,Paper b) {
        if(!a.doi().isBlank() && !b.doi().isBlank()) return a.doi().equals(b.doi());
        if(a.provenance().stream().anyMatch(x->b.provenance().stream().anyMatch(y->x.source().equals(y.source()) && !x.sourceId().isBlank() && x.sourceId().equals(y.sourceId())))) return true;
        // Conservative fallback avoids merging unrelated records that share a generic title.
        return a.year()!=null && a.year().equals(b.year()) && !a.authors().isEmpty() && !b.authors().isEmpty()
                && normalize(a.title()).equals(normalize(b.title()))
                && normalize(a.authors().get(0)).equals(normalize(b.authors().get(0)));
    }
    private static String normalize(String s) {return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
    static Paper merge(Paper a,Paper b) {
        List<Provenance> provenance=new ArrayList<>(a.provenance());
        b.provenance().stream().filter(p->!provenance.contains(p)).forEach(provenance::add);
        return new Paper(a.id(),choose(a.doi(),b.doi()),choose(a.title(),b.title()),a.authors().isEmpty()?b.authors():a.authors(),
                a.year()==null?b.year():a.year(),choose(a.venue(),b.venue()),choose(a.publicationType(),b.publicationType()),
                choose(a.abstractText(),b.abstractText()),choose(a.url(),b.url()),choose(a.openAccessUrl(),b.openAccessUrl()),
                List.copyOf(provenance),a.decision(),a.reason(),a.notes());
    }
    private static String choose(String a,String b) {return a.isBlank()?b:a;}
    public Project screen(String projectId,String paperId,ScreeningRequest request) {
        if(request.decision()==Decision.EXCLUDED && (request.reason()==null || request.reason().isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"An exclusion reason is required");
        return repository.update(projectId,project->{
            if(project.papers().stream().noneMatch(p->p.id().equals(paperId))) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Paper not found");
            List<Paper> papers=project.papers().stream().map(p->!p.id().equals(paperId)?p:new Paper(p.id(),p.doi(),p.title(),p.authors(),p.year(),p.venue(),p.publicationType(),
                    p.abstractText(),p.url(),p.openAccessUrl(),p.provenance(),request.decision(),
                    request.reason()==null?"":request.reason().trim(),request.notes()==null?"":request.notes().trim())).toList();
            return new Project(project.id(),project.name(),project.question(),project.createdAt(),papers,project.searches());
        });
    }
    public String csv(String id,Decision decision) {
        Project project=get(id);
        StringBuilder csv=new StringBuilder("\uFEFF");
        csv.append("Project ID,Project Name,Research Question,Paper ID,DOI,Title,Authors,Year,Venue,Publication Type,Abstract,Paper URL,Open Access URL,Screening Decision,Exclusion Reason,Notes,Sources,Source Record IDs,Source URLs,Search IDs,Search Queries,Search Filters,Retrieved At (UTC)\r\n");
        papers(project,decision).forEach(p->{
            List<String> cells=List.of(project.id(),project.name(),project.question(),p.id(),p.doi(),p.title(),String.join("; ",p.authors()),
                    p.year()==null?"":p.year().toString(),p.venue(),p.publicationType(),p.abstractText(),p.url(),p.openAccessUrl(),p.decision().name(),p.reason(),p.notes(),
                    join(p.provenance().stream().map(Provenance::source).toList()),join(p.provenance().stream().map(Provenance::sourceId).toList()),
                    join(p.provenance().stream().map(Provenance::sourceUrl).toList()),join(p.provenance().stream().map(Provenance::searchId).toList()),
                    join(p.provenance().stream().map(Provenance::query).toList()),join(p.provenance().stream().map(x->searchFilters(project,x.searchId())).toList()),join(p.provenance().stream().map(x->x.retrievedAt().toString()).toList()));
            csv.append(cells.stream().map(ResearchService::csvCell).collect(Collectors.joining(","))).append("\r\n");
        });
        return csv.toString();
    }
    private static String searchFilters(Project project,String searchId) {
        return project.searches().stream().filter(s->s.id().equals(searchId)).findFirst().map(s-> {
            SearchRequest r=s.request();
            return "Years: "+(r.fromYear()==null?"any":r.fromYear())+"-"+(r.toYear()==null?"any":r.toYear())
                    +"; type: "+(r.publicationType()==null?"any":r.publicationType())+"; sources: "+String.join("/",r.sources());
        }).orElse("");
    }
    private static String join(List<String> values) {return String.join("\n",values);}
    static String csvCell(String s) {
        String leading=s.stripLeading();
        if((!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0))>=0) || s.startsWith("\t") || s.startsWith("\r") || s.startsWith("\n")) s="'"+s;
        return "\""+s.replace("\"","\"\"")+"\"";
    }
    private List<Paper> papers(Project p,Decision decision) {
        return p.papers().stream().filter(x->decision==null || x.decision()==decision)
                .sorted(Comparator.comparing(Paper::title,String.CASE_INSENSITIVE_ORDER).thenComparing(Paper::id)).toList();
    }
    public String bibtex(String id,Decision decision) {
        StringBuilder bib=new StringBuilder();
        papers(get(id),decision).forEach(p->{
            String kind=switch(p.publicationType()) {case "article" -> "article";case "book-chapter" -> "incollection";default -> "misc";};
            bib.append("@").append(kind).append("{paper").append(p.id().replace("-",""));
            field(bib,"title",p.title());field(bib,"author",String.join(" and ",p.authors()));
            field(bib,"year",p.year()==null?"":p.year().toString());
            field(bib,kind.equals("article")?"journal":"booktitle",p.venue());
            field(bib,"doi",p.doi());field(bib,"url",p.url());
            field(bib,"abstract",p.abstractText());
            bib.append("\n}\n\n");
        });
        return bib.toString();
    }
    private static void field(StringBuilder b,String name,String value) {
        if(value.isBlank()) return;
        b.append(",\n  ").append(name).append(" = {").append(latex(value)).append("}");
    }
    static String latex(String s) {
        StringBuilder result=new StringBuilder();
        for(char c:s.toCharArray()) result.append(switch(c) {
            case '\\' -> "\\textbackslash{}";case '{','}','%','&','#','_','$' -> "\\"+c;
            case '~' -> "\\textasciitilde{}";case '^' -> "\\textasciicircum{}";case '\r','\n' -> " ";default -> Character.toString(c);
        });
        return result.toString();
    }
}
