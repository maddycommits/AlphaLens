package com.alphalens.backend.movement;

import com.alphalens.backend.model.MarketCap;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "movement.thresholds")
public record MovementThresholdProperties(double large, double mid, double small) {

    public double forCapCategory(MarketCap marketCap) {
        return switch (marketCap) {
            case LARGE -> large;
            case MID -> mid;
            case SMALL -> small;
        };
    }
}
