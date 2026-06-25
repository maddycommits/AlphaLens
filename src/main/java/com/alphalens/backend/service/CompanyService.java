package com.alphalens.backend.service;

import com.alphalens.backend.dto.CompanyResponse;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Temporary in-memory data source for company profiles.
 * TODO: replace with a real data source (DB or market-data API) later.
 */
@Service
public class CompanyService {

    private final Map<String, CompanyResponse> companiesByTicker = Map.of(
            "AAPL", new CompanyResponse("AAPL", "Apple Inc.", "Technology"),
            "MSFT", new CompanyResponse("MSFT", "Microsoft Corporation", "Technology"),
            "GOOGL", new CompanyResponse("GOOGL", "Alphabet Inc.", "Communication Services"),
            "AMZN", new CompanyResponse("AMZN", "Amazon.com, Inc.", "Consumer Discretionary"),
            "TSLA", new CompanyResponse("TSLA", "Tesla, Inc.", "Consumer Discretionary"),
            "NVDA", new CompanyResponse("NVDA", "NVIDIA Corporation", "Technology"),
            "META", new CompanyResponse("META", "Meta Platforms, Inc.", "Communication Services")
    );

    public Optional<CompanyResponse> findByTicker(String ticker) {
        if (ticker == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(companiesByTicker.get(ticker.toUpperCase()));
    }
}
