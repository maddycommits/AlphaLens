package com.alphalens.backend.news;

public class NewsFetchException extends RuntimeException {

    public NewsFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
