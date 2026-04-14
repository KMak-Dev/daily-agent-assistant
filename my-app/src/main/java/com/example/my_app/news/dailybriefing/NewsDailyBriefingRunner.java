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
   * Analyzes a single calendar day ({@code publishedDate >= day && publishedDate <
   * day.plusDays(1)}) in {@link NewsDailyBriefingProperties#zoneId} and upserts {@code
   * news_daily_briefings} for that window (same key as API).
   */
  public void runForSchedule() {
    ZoneId zone = ZoneId.of(properties.zoneId().trim());
    LocalDate day = LocalDate.now(zone).minusDays(Math.max(0, properties.dayOffset()));
    log.info(
        "Daily briefing job: zone={} targetDate={} (dayOffset={})",
        zone.getId(),
        day,
        properties.dayOffset());

    NewsAnalyzeRequest request =
        new NewsAnalyzeRequest(day, day.plusDays(1), zone.getId(), null, false, null);
    NewsAnalyzeResponse response =
        newsAnalyzeService.analyze(request, BriefingArchiveSource.CRON);

    log.info(
        "Daily briefing saved: articles={} summariesFilled={}",
        response.articleCount(),
        response.summariesFilledThisRun());
  }
}
