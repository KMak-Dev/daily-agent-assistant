package com.example.my_app.news.retention;

import com.example.my_app.news.NewsItemRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NewsItemRetentionService {

  private static final Logger log = LoggerFactory.getLogger(NewsItemRetentionService.class);

  private final NewsItemRepository newsItemRepository;
  private final NewsItemRetentionProperties properties;
  private final Clock clock;

  @Autowired
  public NewsItemRetentionService(
      NewsItemRepository newsItemRepository, NewsItemRetentionProperties properties) {
    this(newsItemRepository, properties, Clock.systemUTC());
  }

  NewsItemRetentionService(
      NewsItemRepository newsItemRepository,
      NewsItemRetentionProperties properties,
      Clock clock) {
    this.newsItemRepository = newsItemRepository;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Deletes {@code news_items} with {@code published_date} strictly before the cutoff derived from
   * {@code today} in {@link NewsItemRetentionProperties#zoneId()} minus {@link
   * NewsItemRetentionProperties#retentionDays()} (clamped to at least 1 day).
   *
   * @return number of documents removed
   */
  public long purgeOlderThanRetention() {
    int days = Math.max(1, properties.retentionDays());
    ZoneId zone = ZoneId.of(properties.zoneId());
    LocalDate today = LocalDate.now(clock.withZone(zone));
    LocalDate cutoff = today.minusDays(days);
    long removed = newsItemRepository.deleteByPublishedDateBefore(cutoff);
    if (removed > 0) {
      log.info(
          "News item retention removed {} document(s) with published_date before {} (zone {}, retention {} days)",
          removed,
          cutoff,
          zone,
          days);
    } else {
      log.debug(
          "News item retention: no documents with published_date before {} (zone {}, retention {} days)",
          cutoff,
          zone,
          days);
    }
    return removed;
  }
}
