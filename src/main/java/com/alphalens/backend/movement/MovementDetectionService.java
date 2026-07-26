package com.alphalens.backend.movement;

import com.alphalens.backend.marketdata.Quote;
import com.alphalens.backend.model.Company;
import org.springframework.stereotype.Service;

@Service
public class MovementDetectionService {

    private final MovementThresholdProperties thresholds;

    public MovementDetectionService(MovementThresholdProperties thresholds) {
        this.thresholds = thresholds;
    }

    public PriceMovement detect(Company company, Quote quote) {
        double changePercent = quote.changePercent();
        double threshold = thresholds.forCapCategory(company.marketCap());
        boolean triggered = Math.abs(changePercent) > threshold;
        Direction direction = changePercent >= 0 ? Direction.UP : Direction.DOWN;

        return new PriceMovement(
                company.ticker(),
                company.exchange(),
                direction,
                changePercent,
                threshold,
                company.marketCap(),
                quote.lastPrice(),
                quote.previousClose(),
                triggered
        );
    }
}
