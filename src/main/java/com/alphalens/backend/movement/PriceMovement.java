package com.alphalens.backend.movement;

import com.alphalens.backend.model.MarketCap;

public record PriceMovement(
        String ticker,
        String exchange,
        Direction direction,
        double changePercent,
        double thresholdApplied,
        MarketCap capCategory,
        double lastPrice,
        double previousClose,
        boolean triggered
) {
}
