package com.alphalens.backend.marketdata;

public interface MarketDataProvider {

    Quote getQuote(String ticker, String exchange);
}
