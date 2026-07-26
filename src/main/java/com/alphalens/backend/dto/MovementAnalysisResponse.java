package com.alphalens.backend.dto;

import com.alphalens.backend.insight.InsightRecord;
import com.alphalens.backend.model.MarketCap;

public record MovementAnalysisResponse(
        String ticker,
        boolean triggered,
        double changePercent,
        MarketCap capCategory,
        double thresholdApplied,
        InsightRecord insight
) {
}
