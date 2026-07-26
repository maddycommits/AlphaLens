package com.alphalens.backend.marketdata;

public record Quote(String ticker, double lastPrice, double previousClose) {

    public double changePercent() {
        return ((lastPrice - previousClose) / previousClose) * 100.0;
    }
}
