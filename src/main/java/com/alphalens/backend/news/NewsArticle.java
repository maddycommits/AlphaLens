package com.alphalens.backend.news;

import java.time.Instant;

public record NewsArticle(String title, String url, String source, Instant publishedAt) {
}
