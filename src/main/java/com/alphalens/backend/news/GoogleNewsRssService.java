package com.alphalens.backend.news;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * No-API-key news source: Google News' public RSS search feed.
 * Good enough for a single-user watchlist; swap for a paid news API if coverage gaps hurt (see architecture doc section 7).
 */
@Component
public class GoogleNewsRssService implements NewsService {

    private static final int MAX_ARTICLES = 10;
    private static final Duration RECENCY_WINDOW = Duration.ofHours(48);

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://news.google.com")
            .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; AlphaLens/0.1)")
            .build();

    @Override
    public List<NewsArticle> findRecentNews(String companyName, String ticker) {
        String query = companyName + " " + ticker + " stock";
        String uri = UriComponentsBuilder.fromPath("/rss/search")
                .queryParam("q", query)
                .queryParam("hl", "en-US")
                .queryParam("gl", "US")
                .queryParam("ceid", "US:en")
                .build()
                .encode()
                .toUriString();

        String xml = restClient.get()
                .uri(uri)
                .retrieve()
                .body(String.class);

        if (xml == null || xml.isBlank()) {
            return List.of();
        }

        return parse(xml);
    }

    private List<NewsArticle> parse(String xml) {
        Instant cutoff = Instant.now().minus(RECENCY_WINDOW);
        List<NewsArticle> articles = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength() && articles.size() < MAX_ARTICLES; i++) {
                Element item = (Element) items.item(i);
                String title = textOf(item, "title");
                String link = textOf(item, "link");
                String source = textOf(item, "source");
                String pubDate = textOf(item, "pubDate");

                Instant publishedAt = parsePubDate(pubDate);
                if (publishedAt != null && publishedAt.isBefore(cutoff)) {
                    continue;
                }
                if (title == null || title.isBlank()) {
                    continue;
                }
                articles.add(new NewsArticle(title, link, source, publishedAt));
            }
        } catch (Exception e) {
            throw new NewsFetchException("Failed to parse Google News RSS response", e);
        }
        return articles;
    }

    private String textOf(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent();
    }

    private Instant parsePubDate(String pubDate) {
        if (pubDate == null || pubDate.isBlank()) {
            return null;
        }
        try {
            return DateTimeFormatter.RFC_1123_DATE_TIME.parse(pubDate, Instant::from);
        } catch (Exception e) {
            return null;
        }
    }
}
