package com.alphalens.backend.news;

import java.util.List;

public interface NewsService {

    List<NewsArticle> findRecentNews(String companyName, String ticker);
}
