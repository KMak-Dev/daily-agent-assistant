package com.example.my_app.news.worldnews;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "worldnews", name = "ingest-enabled", havingValue = "true")
public class WorldNewsIngestScheduler {

  private final WorldNewsIngestService ingestService;

  public WorldNewsIngestScheduler(WorldNewsIngestService ingestService) {
    this.ingestService = ingestService;
  }

  /**
   * Default ~24 runs per day (every hour), under a typical 50 calls/day API budget. Each run
   * performs one search request.
   */
  @Scheduled(
      initialDelayString = "${worldnews.ingest-initial-delay-ms}",
      fixedDelayString = "${worldnews.ingest-fixed-delay-ms}")
  public void scheduledIngest() {
    ingestService.ingestOnce();
  }
}
