package com.alphalens.backend.insight;

import com.alphalens.backend.movement.PriceMovement;
import com.alphalens.backend.news.NewsArticle;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InsightService {

    private static final String SYSTEM_PROMPT = """
            You are an equity research assistant. Given a stock's price movement and a set of \
            recent news articles, explain WHY the stock likely moved, in the exact JSON schema requested.

            Rules:
            - Only cite a cause if it is credibly supported by the provided articles.
            - If none of the articles credibly explain the movement, respond with category UNKNOWN and \
              confidence LOW, and say so plainly in the summary. Never speculate or invent a cause.
            - summary must be 2-3 sentences.
            - sources must only reference articles that were actually provided to you.
            """;

    private final ChatClient chatClient;

    public InsightService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public InsightRecord explain(PriceMovement movement, List<NewsArticle> articles) {
        String userPrompt = buildUserPrompt(movement, articles);

        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .entity(InsightRecord.class);
    }

    private String buildUserPrompt(PriceMovement movement, List<NewsArticle> articles) {
        StringBuilder sb = new StringBuilder();
        sb.append("Movement:\n")
                .append("- Ticker: ").append(movement.ticker()).append("\n")
                .append("- Exchange: ").append(movement.exchange()).append("\n")
                .append("- Direction: ").append(movement.direction()).append("\n")
                .append("- Change: ").append(String.format("%.2f", movement.changePercent())).append("%\n")
                .append("- Cap category: ").append(movement.capCategory()).append("\n")
                .append("- Last price: ").append(movement.lastPrice()).append("\n")
                .append("- Previous close: ").append(movement.previousClose()).append("\n\n");

        if (articles.isEmpty()) {
            sb.append("No recent news articles were found for this stock.\n");
        } else {
            sb.append("Recent articles:\n");
            for (NewsArticle article : articles) {
                sb.append("- Title: ").append(article.title()).append("\n")
                        .append("  Source: ").append(article.source()).append("\n")
                        .append("  Published: ").append(article.publishedAt()).append("\n")
                        .append("  URL: ").append(article.url()).append("\n");
            }
        }

        return sb.toString();
    }
}
