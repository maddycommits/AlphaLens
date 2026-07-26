package com.alphalens.backend.controller;

import com.alphalens.backend.dto.MovementAnalysisResponse;
import com.alphalens.backend.insight.InsightRecord;
import com.alphalens.backend.insight.InsightService;
import com.alphalens.backend.marketdata.MarketDataException;
import com.alphalens.backend.marketdata.MarketDataProvider;
import com.alphalens.backend.marketdata.Quote;
import com.alphalens.backend.model.Company;
import com.alphalens.backend.movement.MovementDetectionService;
import com.alphalens.backend.movement.PriceMovement;
import com.alphalens.backend.news.NewsArticle;
import com.alphalens.backend.news.NewsFetchException;
import com.alphalens.backend.news.NewsService;
import com.alphalens.backend.service.CompanyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/movements")
public class MovementAnalysisController {

    /** Arbitrary baseline price used only when a caller supplies simulatedChangePercent for demo/testing. */
    private static final double SIMULATED_PREVIOUS_CLOSE = 100.0;

    private final CompanyService companyService;
    private final MarketDataProvider marketDataProvider;
    private final MovementDetectionService movementDetectionService;
    private final NewsService newsService;
    private final InsightService insightService;

    public MovementAnalysisController(CompanyService companyService,
                                       MarketDataProvider marketDataProvider,
                                       MovementDetectionService movementDetectionService,
                                       NewsService newsService,
                                       InsightService insightService) {
        this.companyService = companyService;
        this.marketDataProvider = marketDataProvider;
        this.movementDetectionService = movementDetectionService;
        this.newsService = newsService;
        this.insightService = insightService;
    }

    @GetMapping("/{ticker}/analyze")
    public ResponseEntity<MovementAnalysisResponse> analyze(
            @PathVariable String ticker,
            @RequestParam(required = false) Double simulatedChangePercent) {

        Company company = companyService.findByTicker(ticker)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown ticker: " + ticker));

        Quote quote = resolveQuote(company, simulatedChangePercent);
        PriceMovement movement = movementDetectionService.detect(company, quote);

        InsightRecord insight = null;
        if (movement.triggered()) {
            insight = generateInsight(company, movement);
        }

        MovementAnalysisResponse response = new MovementAnalysisResponse(
                movement.ticker(),
                movement.triggered(),
                movement.changePercent(),
                movement.capCategory(),
                movement.thresholdApplied(),
                insight
        );
        return ResponseEntity.ok(response);
    }

    private Quote resolveQuote(Company company, Double simulatedChangePercent) {
        if (simulatedChangePercent != null) {
            double lastPrice = SIMULATED_PREVIOUS_CLOSE * (1 + simulatedChangePercent / 100.0);
            return new Quote(company.ticker(), lastPrice, SIMULATED_PREVIOUS_CLOSE);
        }
        try {
            return marketDataProvider.getQuote(company.ticker(), company.exchange());
        } catch (MarketDataException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to fetch a live quote for " + company.ticker() + ": " + e.getMessage(), e);
        }
    }

    private InsightRecord generateInsight(Company company, PriceMovement movement) {
        List<NewsArticle> articles;
        try {
            articles = newsService.findRecentNews(company.name(), company.ticker());
        } catch (NewsFetchException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to fetch news for " + company.ticker() + ": " + e.getMessage(), e);
        }

        try {
            return insightService.explain(movement, articles);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Failed to generate an insight for " + company.ticker() + ": " + e.getMessage(), e);
        }
    }
}
