package com.example.my_app.news.worldnews;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "worldnews")
public record WorldNewsProperties(
    String apiKey,
    String language,
    String text,
    String sourceCountry,
    String category,
    int pageSize,
    boolean ingestEnabled,
    long ingestInitialDelayMs,
    long ingestFixedDelayMs) {

  public boolean hasApiKey() {
    return apiKey != null && !apiKey.isBlank();
  }
}
