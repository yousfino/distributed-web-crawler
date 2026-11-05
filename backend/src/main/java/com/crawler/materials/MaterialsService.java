package com.crawler.materials;

import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import static com.crawler.materials.MaterialsModels.*;

@Service
public class MaterialsService {
    private final MaterialsRepository repository;
    private final MaterialsScanner scanner;
    private final MaterialsHttp http;
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"materials-scan");t.setDaemon(true);return t;});
    public MaterialsService(MaterialsRepository repository,MaterialsScanner scanner,MaterialsHttp http){this.repository=repository;this.scanner=scanner;this.http=http;}
    @EventListener(ApplicationReadyEvent.class) public void recover() {
        for(Inventory i:repository.all()) if(active(i)) repository.update(i.id(),current->new Inventory(current.id(),current.name(),current.paperUrls(),current.createdAt(),Instant.now(),Phase.INTERRUPTED,current.pages(),current.findings(),
                append(current.warnings(),"The application restarted before this scan finished. Create a new scan to check the remaining links.")));
    }
    @PreDestroy public void close(){executor.shutdownNow();try{executor.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    public List<Summary> list(){return repository.all().stream().sorted(Comparator.comparing(Inventory::createdAt).reversed()).map(i->new Summary(i.id(),i.name(),i.createdAt(),i.phase(),i.paperUrls().size(),i.findings().size(),i.findings().stream().filter(f->f.review()==Review.VERIFIED).count())).toList();}
    public Inventory get(String id){return repository.get(id);}
    public Inventory create(ScanRequest request) {
        List<String> urls=new ArrayList<>();
        for(String value:request.paperUrls()) {
            String url=value.trim();if(url.matches("(?i)^10\\.[0-9]{4,9}/.+"))url="https://doi.org/"+url;
            try{urls.add(http.validate(url));}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}
        }
        Inventory inventory=repository.create(new Inventory(UUID.randomUUID().toString(),request.name().trim(),List.copyOf(new LinkedHashSet<>(urls)),Instant.now(),null,Phase.QUEUED,List.of(),List.of(),List.of()));
        try{executor.execute(()->run(inventory));}
        catch(RejectedExecutionException e){repository.update(inventory.id(),i->new Inventory(i.id(),i.name(),i.paperUrls(),i.createdAt(),Instant.now(),Phase.FAILED,i.pages(),i.findings(),List.of("Scan queue is full; try a new scan later")));throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Scan queue is full; try again later");}
        return inventory;
    }
    private void run(Inventory initial) {
        try {
            repository.update(initial.id(),i->new Inventory(i.id(),i.name(),i.paperUrls(),i.createdAt(),null,Phase.SCANNING,i.pages(),i.findings(),i.warnings()));
            Inventory complete=scanner.scan(initial,progress->repository.update(initial.id(),current->progress));
            repository.update(initial.id(),current->complete);
        } catch(Exception e) {
            repository.update(initial.id(),i->new Inventory(i.id(),i.name(),i.paperUrls(),i.createdAt(),Instant.now(),Phase.FAILED,i.pages(),i.findings(),append(i.warnings(),"Scan could not finish. Saved findings are retained; create a new scan to retry.")));
        }
    }
    public Inventory review(String id,String findingId,ReviewRequest request) {
        return repository.update(id,i->{
            if(active(i)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Wait for the scan to finish before reviewing findings");
            if(i.findings().stream().noneMatch(f->f.id().equals(findingId)))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Resource not found");
            List<Finding> findings=i.findings().stream().map(f->!f.id().equals(findingId)?f:new Finding(f.id(),f.kind(),f.label(),f.url(),f.evidence(),f.check(),request.decision(),request.notes()==null?"":request.notes().trim())).toList();
            return new Inventory(i.id(),i.name(),i.paperUrls(),i.createdAt(),i.finishedAt(),i.phase(),i.pages(),findings,i.warnings());
        });
    }
    public String csv(String id,Review review) {
        Inventory i=get(id);StringBuilder csv=new StringBuilder("\uFEFFInventory ID,Inventory Name,Scan State,Resource ID,Candidate Type,Link Label,Linked URL,Final URL,HTTP Status,Access Result,Check Details,Checked At (UTC),Review Decision,Review Notes,Evidence Paper URLs,Evidence Page URLs,Anchor Text,Surrounding Context,Discovery Reason,Discovered At (UTC)\r\n");
        i.findings().stream().filter(f->review==null||f.review()==review).sorted(Comparator.comparing(Finding::url)).forEach(f->{
            Check c=f.check();List<String> cells=List.of(i.id(),i.name(),i.phase().name(),f.id(),f.kind().name(),f.label(),f.url(),c.finalUrl(),c.httpStatus()==null?"":c.httpStatus().toString(),c.access().name(),c.details(),c.checkedAt()==null?"":c.checkedAt().toString(),f.review().name(),f.notes(),
                    join(f.evidence().stream().map(Evidence::paperUrl).toList()),join(f.evidence().stream().map(Evidence::pageUrl).toList()),join(f.evidence().stream().map(Evidence::anchorText).toList()),join(f.evidence().stream().map(Evidence::context).toList()),join(f.evidence().stream().map(Evidence::reason).toList()),join(f.evidence().stream().map(e->e.discoveredAt().toString()).toList()));
            csv.append(cells.stream().map(MaterialsService::cell).collect(Collectors.joining(","))).append("\r\n");
        });return csv.toString();
    }
    public static boolean active(Inventory i){return i.phase()==Phase.QUEUED||i.phase()==Phase.SCANNING;}
    private static List<String> append(List<String> values,String value){List<String> copy=new ArrayList<>(values);copy.add(value);return List.copyOf(copy);}
    private static String join(List<String> values){return String.join("\n",values);}
    private static String cell(String value){String s=value.stripLeading();if((!s.isEmpty()&&"=+-@".indexOf(s.charAt(0))>=0)||value.startsWith("\t")||value.startsWith("\n")||value.startsWith("\r"))value="'"+value;return "\""+value.replace("\"","\"\"")+"\"";}
}
