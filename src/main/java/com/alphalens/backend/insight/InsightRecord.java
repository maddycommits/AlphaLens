package com.alphalens.backend.insight;

import java.util.List;

public record InsightRecord(
        String headline,
        String summary,
        InsightCategory category,
        Confidence confidence,
        Sentiment sentiment,
        List<SourceRef> sources
) {
}
