package com.example.my_app.news.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "news.item-retention", name = "enabled", havingValue = "true")
public class NewsItemRetentionScheduler {

  private static final Logger log = LoggerFactory.getLogger(NewsItemRetentionScheduler.class);

  private final NewsItemRetentionService newsItemRetentionService;

  public NewsItemRetentionScheduler(NewsItemRetentionService newsItemRetentionService) {
    this.newsItemRetentionService = newsItemRetentionService;
  }

  @Scheduled(cron = "${news.item-retention.cron}", zone = "${news.item-retention.zone-id}")
  public void runRetention() {
    try {
      newsItemRetentionService.purgeOlderThanRetention();
    } catch (Exception e) {
      log.error("News item retention job failed: {}", e.getMessage(), e);
    }
  }
}
