package com.alphalens.backend.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Pulls delayed quotes from Yahoo Finance's public (unauthenticated) chart endpoint.
 * Per the architecture doc, NSE/BSE tickers need a ".NS"/".BO" suffix; US tickers need none.
 */
@Component
public class YahooFinanceMarketDataProvider implements MarketDataProvider {

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://query1.finance.yahoo.com")
            .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; AlphaLens/0.1)")
            .build();

    @Override
    public Quote getQuote(String ticker, String exchange) {
        String symbol = toYahooSymbol(ticker, exchange);
        ChartResponse response = restClient.get()
                .uri("/v8/finance/chart/{symbol}", symbol)
                .retrieve()
                .body(ChartResponse.class);

        if (response == null || response.chart() == null || response.chart().result() == null
                || response.chart().result().isEmpty()) {
            throw new MarketDataException("No quote data returned for " + symbol);
        }

        Meta meta = response.chart().result().get(0).meta();
        if (meta == null || meta.regularMarketPrice() == null || meta.previousClose() == null) {
            throw new MarketDataException("Incomplete quote data returned for " + symbol);
        }

        return new Quote(ticker, meta.regularMarketPrice(), meta.previousClose());
    }

    private String toYahooSymbol(String ticker, String exchange) {
        if (exchange == null) {
            return ticker;
        }
        return switch (exchange.toUpperCase()) {
            case "NSE" -> ticker + ".NS";
            case "BSE" -> ticker + ".BO";
            default -> ticker;
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChartResponse(Chart chart) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Chart(List<Result> result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Result(Meta meta) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Meta(Double regularMarketPrice, Double previousClose) {
    }
}
