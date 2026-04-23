package com.example.my_app.news.dailybriefing;

import com.example.my_app.news.analysis.NewsAnalyzeRequest;
import com.example.my_app.news.analysis.NewsAnalyzeResponse;
import com.example.my_app.news.analysis.NewsAnalyzeService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NewsDailyBriefingRunner {

  private static final Logger log = LoggerFactory.getLogger(NewsDailyBriefingRunner.class);

  private final NewsAnalyzeService newsAnalyzeService;
  private final NewsDailyBriefingProperties properties;

  public NewsDailyBriefingRunner(
      NewsAnalyzeService newsAnalyzeService, NewsDailyBriefingProperties properties) {
    this.newsAnalyzeService = newsAnalyzeService;
    this.properties = properties;
  }

  /**
   * Analyzes a half-open date window starting at the anchor day ({@code today(zone) - dayOffset})
   * with length {@link NewsDailyBriefingProperties#windowDays} calendar days, and upserts {@code
   * news_daily_briefings} for that window (same key as API).
   */
  public void runForSchedule() {
    ZoneId zone = ZoneId.of(properties.zoneId().trim());
    LocalDate day = LocalDate.now(zone).minusDays(Math.max(0, properties.dayOffset()));
    int configured = properties.windowDays();
    int span = Math.min(NewsAnalyzeService.MAX_ANALYZE_WINDOW_DAYS, Math.max(1, configured));
    if (span != configured) {
      log.warn(
          "Daily briefing job: windowDays={} out of range, using span={} (allowed 1..{})",
          configured,
          span,
          NewsAnalyzeService.MAX_ANALYZE_WINDOW_DAYS);
    }
    LocalDate endExclusive = day.plusDays(span);
    log.info(
        "Daily briefing job: zone={} startDate={} endExclusive={} spanDays={} (dayOffset={})",
        zone.getId(),
        day,
        endExclusive,
        span,
        properties.dayOffset());

    NewsAnalyzeRequest request =
        new NewsAnalyzeRequest(day, endExclusive, zone.getId(), null, false, null);
    NewsAnalyzeResponse response = newsAnalyzeService.analyze(request, BriefingArchiveSource.CRON);

    log.info(
        "Daily briefing saved: articles={} summariesFilled={}",
        response.articleCount(),
        response.summariesFilledThisRun());
  }
}
