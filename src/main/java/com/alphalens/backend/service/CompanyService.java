package com.alphalens.backend.service;

import com.alphalens.backend.model.Company;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Temporary in-memory data source for company profiles.
 * TODO: replace with a JPA-backed repository once a real data source is wired up.
 */
@Service
public class CompanyService {

    private final Map<String, Company> companiesByTicker = Map.of(
            "AAPL", new Company("AAPL", "Apple Inc.", "Technology", "Consumer Electronics", "NASDAQ", 3400.0,
                    "Designs, manufactures, and markets smartphones, computers, and wearables."),
            "MSFT", new Company("MSFT", "Microsoft Corporation", "Technology", "Software—Infrastructure", "NASDAQ", 3100.0,
                    "Develops and licenses software, services, devices, and cloud solutions."),
            "GOOGL", new Company("GOOGL", "Alphabet Inc.", "Communication Services", "Internet Content & Information", "NASDAQ", 2200.0,
                    "Provides search, advertising, cloud computing, and other internet-related services."),
            "AMZN", new Company("AMZN", "Amazon.com, Inc.", "Consumer Discretionary", "Internet Retail", "NASDAQ", 2000.0,
                    "Operates e-commerce, cloud computing, and digital streaming businesses."),
            "TSLA", new Company("TSLA", "Tesla, Inc.", "Consumer Discretionary", "Auto Manufacturers", "NASDAQ", 900.0,
                    "Designs and manufactures electric vehicles, energy storage, and solar products."),
            "NVDA", new Company("NVDA", "NVIDIA Corporation", "Technology", "Semiconductors", "NASDAQ", 3000.0,
                    "Designs GPUs and AI computing platforms for gaming, data center, and automotive markets."),
            "META", new Company("META", "Meta Platforms, Inc.", "Communication Services", "Internet Content & Information", "NASDAQ", 1300.0,
                    "Builds social media, advertising, and virtual/augmented reality platforms.")
    );

    public Optional<Company> findByTicker(String ticker) {
        if (ticker == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(companiesByTicker.get(ticker.toUpperCase()));
    }
}
