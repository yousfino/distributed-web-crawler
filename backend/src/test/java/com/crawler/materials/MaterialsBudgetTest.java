package com.crawler.materials;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.crawler.materials.MaterialsModels.*;

class MaterialsBudgetTest {
    @Test void retainsUncheckedCandidatesWhenHttpCheckBudgetIsReached() throws Exception {
        MaterialsHttp http=mock(MaterialsHttp.class);
        StringBuilder html=new StringBuilder("<title>Study</title>");
        for(int i=0;i<25;i++)html.append("<a href='https://example.org/data?file=").append(i).append("'>Dataset ").append(i).append("</a>");
        when(http.fetch(eq("https://example.org/paper"),eq("GET"),anyInt(),anyMap())).thenReturn(new MaterialsHttp.Response("https://example.org/paper",200,"text/html",html.toString()));
        when(http.fetch(anyString(),eq("HEAD"),eq(0),anyMap())).thenAnswer(call->new MaterialsHttp.Response(call.getArgument(0),200,"text/csv",""));
        Inventory initial=new Inventory("test","Budget test",List.of("https://example.org/paper"),Instant.now(),null,Phase.QUEUED,List.of(),List.of(),List.of());
        Inventory result=new MaterialsScanner(http).scan(initial,progress->{});
        assertEquals(25,result.findings().size());assertEquals(20,result.findings().stream().filter(f->f.check().access()==Access.RESPONDED).count());
        assertEquals(5,result.findings().stream().filter(f->f.check().access()==Access.UNCHECKED).count());
        assertTrue(result.warnings().stream().anyMatch(w->w.contains("20 resource checks")));
        verify(http,times(20)).fetch(anyString(),eq("HEAD"),eq(0),anyMap());
    }
}
