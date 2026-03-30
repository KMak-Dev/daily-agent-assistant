package com.example.my_app.news.worldnews;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class WorldNewsClient {

  private static final Logger log = LoggerFactory.getLogger(WorldNewsClient.class);

  private final RestClient restClient;
  private final WorldNewsProperties properties;
  private final WorldNewsSearchTextHolder searchTextHolder;

  public WorldNewsClient(
      @Qualifier("worldNewsRestClient") RestClient restClient,
      WorldNewsProperties properties,
      WorldNewsSearchTextHolder searchTextHolder) {
    this.restClient = restClient;
    this.properties = properties;
    this.searchTextHolder = searchTextHolder;
  }

  /**
   * Calls {@code GET /search-news}. Each invocation counts as one API request toward your daily
   * quota.
   */
  public Optional<SearchNewsResponse> searchNews(int offset, int number) {
    if (!properties.hasApiKey()) {
      return Optional.empty();
    }
    var builder =
        UriComponentsBuilder.fromPath("/search-news")
            .queryParam("api-key", properties.apiKey())
            .queryParam("language", properties.language())
            .queryParam("number", number)
            .queryParam("offset", offset);
    String text = searchTextHolder.effectiveSearchText();
    if (!text.isBlank()) {
      builder.queryParam("text", text);
    }
    if (properties.sourceCountry() != null && !properties.sourceCountry().isBlank()) {
      builder.queryParam("source-country", properties.sourceCountry().trim());
    }
    if (properties.category() != null && !properties.category().isBlank()) {
      builder.queryParam("category", properties.category().trim());
    }
    String uri = builder.build().toUriString();
    try {
      SearchNewsResponse body = restClient.get().uri(uri).retrieve().body(SearchNewsResponse.class);
      return Optional.ofNullable(body);
    } catch (RestClientResponseException e) {
      log.warn(
          "World News API HTTP {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
      return Optional.empty();
    } catch (Exception e) {
      log.warn("World News API request failed: {}", e.getMessage());
      return Optional.empty();
    }
  }
}
