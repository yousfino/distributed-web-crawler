package com.crawler.materials;

import com.crawler.research.ScholarlyHttp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@Component
public class MaterialsHttp {
    public record Response(String url,int status,String contentType,String body) {}
    private final HttpClient client=ScholarlyHttp.client();
    private final boolean allowLoopback;
    private final Map<String,Long> nextRequests=new HashMap<>();
    private final ScheduledExecutorService closer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"materials-body-deadline");t.setDaemon(true);return t;});
    public MaterialsHttp(@Value("${materials.allow-loopback:false}") boolean allowLoopback) {this.allowLoopback=allowLoopback;}
    public String validate(String value) {
        String canonical=MaterialDiscovery.canonical(value);URI uri=URI.create(canonical);
        String host=uri.getHost().replace("[","").replace("]","").toLowerCase(Locale.ROOT);
        boolean loop=host.equals("localhost")||host.equals("127.0.0.1")||host.equals("::1");
        if(allowLoopback && loop) return canonical;
        if(uri.getPort()!=-1 && uri.getPort()!=80 && uri.getPort()!=443) throw new IllegalArgumentException("Only public HTTP/HTTPS endpoints on standard ports are supported");
        if(!host.contains(".") || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".test") || host.endsWith(".invalid") || host.contains(":"))
            throw new IllegalArgumentException("Use a public paper URL");
        if(host.matches("[0-9.]+") && !publicAddress(address(host))) throw new IllegalArgumentException("Private/local addresses are not supported");
        // When using a trusted egress proxy, that proxy resolves names and must enforce destination/IP policy.
        if(System.getenv("HTTPS_PROXY")==null) {
            try {for(InetAddress ip:InetAddress.getAllByName(host)) if(!publicAddress(ip)) throw new IllegalArgumentException("Private/local addresses are not supported");}
            catch(UnknownHostException e){throw new IllegalArgumentException("The paper hostname could not be resolved");}
        }
        return canonical;
    }
    private static InetAddress address(String host) {
        try{return InetAddress.getByName(host);}catch(UnknownHostException e){throw new IllegalArgumentException("Invalid address");}
    }
    private static boolean publicAddress(InetAddress ip) {
        byte[] b=ip.getAddress();
        return !ip.isAnyLocalAddress()&&!ip.isLoopbackAddress()&&!ip.isLinkLocalAddress()&&!ip.isSiteLocalAddress()&&!ip.isMulticastAddress()
                && !(b.length==4 && ((b[0]&255)==100 && (b[1]&255)>=64 && (b[1]&255)<=127))
                && !(b.length==4 && ((b[0]&255)==0 || (b[0]&255)>=224 || ((b[0]&255)==169 && (b[1]&255)==254)));
    }
    public Response fetch(String url,String method,int limit,Map<String,Rules> robots) throws Exception {
        String current=validate(url);
        for(int redirects=0;redirects<=3;redirects++) {
            URI uri=URI.create(current);String origin=uri.getScheme()+"://"+uri.getRawAuthority();
            Rules rules=robots.get(origin);
            if(rules==null) {
                Response response=request(origin+"/robots.txt","GET",64000);
                rules=response.status()==404 || response.status()==410?Rules.allow():response.status()==200?Rules.parse(response.body()):Rules.deny("Robots policy unavailable (HTTP "+response.status()+"); not checked");
                robots.put(origin,rules);
            }
            if(!rules.allows(uri.getRawPath()+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery()))) throw new IllegalStateException(rules.message());
            Response response=request(current,method,limit);
            if(response.status()>=300 && response.status()<400) {
                String location=response.body(); // request() returns Location for redirect responses without reading the body.
                if(location.isBlank()) return response;
                String next=validate(uri.resolve(location).toString());
                if(uri.getScheme().equals("https") && URI.create(next).getScheme().equals("http")) throw new IllegalStateException("HTTPS downgrade redirect was not followed");
                current=next;continue;
            }
            return response;
        }
        throw new IllegalStateException("Redirect limit reached");
    }
    private Response request(String url,String method,int limit) throws Exception {
        validate(url);URI uri=URI.create(url);pace(uri.getHost());
        HttpRequest request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(7))
                .header("User-Agent","ResearchAtlas/1.0 (research materials link inventory)")
                .header("Accept","text/html,application/xhtml+xml,*/*;q=0.5")
                .method(method,HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<InputStream> response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
        try(InputStream in=response.body()) {
            String type=response.headers().firstValue("Content-Type").orElse("");
            if(response.statusCode()>=300 && response.statusCode()<400) return new Response(url,response.statusCode(),type,response.headers().firstValue("Location").orElse(""));
            if(limit==0) return new Response(url,response.statusCode(),type,"");
            var deadline=closer.schedule(()->{try{in.close();}catch(Exception ignored){}},7,TimeUnit.SECONDS);
            try {
                byte[] body=in.readNBytes(limit+1);
                if(body.length>limit) throw new IllegalStateException("Page exceeds the scan size limit");
                String charset=response.headers().firstValue("Content-Type").orElse("");
                java.nio.charset.Charset encoding=StandardCharsets.UTF_8;
                var match=Pattern.compile("charset=[\"']?([^;\\s\"']+)",Pattern.CASE_INSENSITIVE).matcher(charset);
                if(match.find()) try{encoding=java.nio.charset.Charset.forName(match.group(1));}catch(Exception ignored){}
                return new Response(url,response.statusCode(),type,new String(body,encoding));
            } finally {deadline.cancel(false);}
        }
    }
    private synchronized void pace(String host) throws InterruptedException {
        long now=System.nanoTime(),next=nextRequests.getOrDefault(host,now);
        if(next>now) TimeUnit.NANOSECONDS.sleep(next-now);
        nextRequests.put(host,System.nanoTime()+Duration.ofMillis(500).toNanos());
    }
    record Rule(String path,boolean allow) {}
    record Rules(List<Rule> entries,String message) {
        static Rules allow(){return new Rules(List.of(),"Allowed");}
        static Rules deny(String message){return new Rules(List.of(new Rule("/",false)),message);}
        boolean allows(String path) {
            Rule best=null;int length=-1;
            for(Rule rule:entries) {
                boolean end=rule.path().endsWith("$");String raw=end?rule.path().substring(0,rule.path().length()-1):rule.path();
                String pattern=Arrays.stream(raw.split("\\*",-1)).map(Pattern::quote).collect(java.util.stream.Collectors.joining(".*"));
                if(Pattern.compile("^"+pattern+(end?"$":".*")).matcher(path).matches() && (raw.length()>length || raw.length()==length && rule.allow())) {best=rule;length=raw.length();}
            }
            return best==null||best.allow();
        }
        static Rules parse(String body) {
            List<Rule> wildcard=new ArrayList<>(),specific=new ArrayList<>();List<String> agents=new ArrayList<>();boolean directives=false,hasSpecificGroup=false;
            for(String line:body.split("\\R")) {
                line=line.split("#",2)[0].trim();int colon=line.indexOf(':');if(colon<0)continue;
                String key=line.substring(0,colon).trim().toLowerCase(Locale.ROOT),value=line.substring(colon+1).trim();
                if(key.equals("user-agent")) {if(directives){agents.clear();directives=false;}agents.add(value.toLowerCase(Locale.ROOT));if(!value.isBlank()&&!value.equals("*")&&"researchatlas".contains(value.toLowerCase(Locale.ROOT)))hasSpecificGroup=true;}
                else if(key.equals("allow")||key.equals("disallow")) {
                    directives=true;if(value.isBlank())continue;
                    Rule rule=new Rule(value,key.equals("allow"));
                    if(agents.contains("*")) wildcard.add(rule);
                    if(agents.stream().anyMatch(a->!a.isBlank() && !a.equals("*") && "researchatlas".contains(a))) specific.add(rule);
                }
            }
            return new Rules(List.copyOf(hasSpecificGroup?specific:wildcard),"Disallowed by robots.txt; not checked");
        }
    }
}
