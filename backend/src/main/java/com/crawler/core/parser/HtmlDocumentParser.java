package com.crawler.core.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class HtmlDocumentParser extends AbstractDocumentParser {

    @Override
    public boolean supports(String contentType) {
        return contentType != null && contentType.toLowerCase().contains("text/html");
    }

    @Override
    protected String extractTitle(String rawBody) {
        Document doc = Jsoup.parse(rawBody);
        return doc.title();
    }

    @Override
    protected List<String> extractHeadings(String rawBody) {
        Document doc = Jsoup.parse(rawBody);
        Elements hTags = doc.select("h1, h2, h3");
        return hTags.stream()
                .map(Element::text)
                .filter(t -> !t.isBlank())
                .collect(Collectors.toList());
    }

    @Override
    protected Set<URI> extractLinks(URI baseUri, String rawBody) {
        Document doc = Jsoup.parse(rawBody, baseUri.toString());
        Elements anchors = doc.select("a[href]");
        Set<URI> normalizedLinks = new HashSet<>();

        for (Element link : anchors) {
            String absUrl = link.attr("abs:href");
            if (absUrl.startsWith("http://") || absUrl.startsWith("https://")) {
                try {
                    // Strip query params and fragments to reduce URL frontier duplicate space
                    URI parsedUri = URI.create(absUrl);
                    URI normalized = new URI(
                            parsedUri.getScheme().toLowerCase(),
                            parsedUri.getUserInfo(),
                            parsedUri.getHost().toLowerCase(),
                            parsedUri.getPort(),
                            parsedUri.getPath(),
                            null,
                            null
                    );
                    normalizedLinks.add(normalized);
                } catch (Exception ignored) {
                    // Skip malformed URIs
                }
            }
        }
        return Collections.unmodifiableSet(normalizedLinks);
    }

    @Override
    protected Map<String, Integer> extractKeywordFrequencies(String rawBody) {
        Document doc = Jsoup.parse(rawBody);
        String text = doc.body() != null ? doc.body().text().toLowerCase() : "";
        String[] tokens = text.split("[^a-zA-Z0-9]+");

        Map<String, Integer> frequencies = new HashMap<>();
        for (String token : tokens) {
            if (token.length() >= 3) { // Filter noise/stop-tokens
                frequencies.merge(token, 1, Integer::sum);
            }
        }
        return frequencies;
    }
}