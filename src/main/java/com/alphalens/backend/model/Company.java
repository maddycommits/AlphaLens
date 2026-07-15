package com.alphalens.backend.model;

/**
 * Mock company profile data.
 */
public record Company(
        String ticker,
        String name,
        String sector,
        String industry,
        String exchange,
        double marketCapBillions,
        String description
) {
}
