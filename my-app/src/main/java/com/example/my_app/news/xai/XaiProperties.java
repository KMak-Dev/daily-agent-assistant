package com.example.my_app.news.xai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xai")
public record XaiProperties(
    String apiKey,
    String model,
    String baseUrl,
    int batchSize,
    int excerptMaxChars,
    /**
     * Default synthesis task text when {@code NewsAnalyzeRequest#briefingPrompt} is unset. Empty
     * uses the built-in default in {@link com.example.my_app.news.analysis.NewsAnalyzeService}.
     */
    String briefingPrompt) {

  public boolean hasApiKey() {
    return apiKey != null && !apiKey.isBlank();
  }
}
