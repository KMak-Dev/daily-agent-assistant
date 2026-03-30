package com.example.my_app.news.worldnews;

import org.springframework.stereotype.Component;

/**
 * Holds the search-news {@code text} query rebuilt from {@code world_news_keywords} at startup and
 * after keyword API changes. When the collection has no documents, {@link
 * WorldNewsProperties#text()} (env) is used instead.
 */
@Component
public class WorldNewsSearchTextHolder {

  private final WorldNewsProperties properties;

  private volatile boolean usePropertyFallback = true;
  private volatile String rebuiltFromKeywords = "";

  public WorldNewsSearchTextHolder(WorldNewsProperties properties) {
    this.properties = properties;
  }

  void setFromStartup(boolean collectionHadDocuments, String rebuilt) {
    this.usePropertyFallback = !collectionHadDocuments;
    this.rebuiltFromKeywords = rebuilt == null ? "" : rebuilt;
  }

  /** Text for the {@code text} query parameter, or blank to omit the param. Never null. */
  public String effectiveSearchText() {
    if (!usePropertyFallback) {
      return rebuiltFromKeywords;
    }
    String t = properties.text();
    return t == null ? "" : t.trim();
  }
}
