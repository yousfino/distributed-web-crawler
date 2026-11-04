package com.crawler.research;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Set;

final class ScholarlyHttp {
    private ScholarlyHttp() {}
    static HttpClient client() {
        HttpClient.Builder builder=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10));
        String proxy=System.getenv("HTTPS_PROXY");
        if(proxy!=null && !proxy.isBlank()) {
            URI uri=URI.create(proxy);
            ProxySelector fallback=ProxySelector.of(new InetSocketAddress(uri.getHost(),uri.getPort()<0?80:uri.getPort()));
            builder.proxy(new ProxySelector() {
                public List<Proxy> select(URI target) {
                    return Set.of("localhost","127.0.0.1","::1").contains(target.getHost()) ? List.of(Proxy.NO_PROXY) : fallback.select(target);
                }
                public void connectFailed(URI uri,SocketAddress address,IOException error) {}
            });
        }
        return builder.build();
    }
}
