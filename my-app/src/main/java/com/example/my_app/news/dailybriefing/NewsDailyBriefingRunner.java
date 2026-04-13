package com.example.my_app.news.dailybriefing;

import com.example.my_app.news.analysis.NewsAnalyzeRequest;
import com.example.my_app.news.analysis.NewsAnalyzeResponse;
import com.example.my_app.news.analysis.NewsAnalyzeService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NewsDailyBriefingRunner {

  private static final Logger log = LoggerFactory.getLogger(NewsDailyBriefingRunner.class);

  private final NewsAnalyzeService newsAnalyzeService;
  private final NewsDailyBriefingRepository newsDailyBriefingRepository;
  private final NewsDailyBriefingProperties properties;

  public NewsDailyBriefingRunner(
      NewsAnalyzeService newsAnalyzeService,
      NewsDailyBriefingRepository newsDailyBriefingRepository,
      NewsDailyBriefingProperties properties) {
    this.newsAnalyzeService = newsAnalyzeService;
    this.newsDailyBriefingRepository = newsDailyBriefingRepository;
    this.properties = properties;
  }

  /**
   * Analyzes a single calendar day ({@code publishedDate >= day && publishedDate <
   * day.plusDays(1)}) in {@link NewsDailyBriefingProperties#zoneId} and appends one document to
   * {@code news_daily_briefings}.
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
    NewsAnalyzeResponse response = newsAnalyzeService.analyze(request);

    NewsDailyBriefing doc = new NewsDailyBriefing();
    doc.setStartDate(response.startDate());
    doc.setEndDate(response.endDate());
    doc.setTimeZone(response.timeZone());
    doc.setArticleCount(response.articleCount());
    doc.setSummariesFilledThisRun(response.summariesFilledThisRun());
    doc.setBriefing(response.briefing());
    doc.setCreatedAt(Instant.now());
    newsDailyBriefingRepository.save(doc);

    log.info(
        "Daily briefing saved: id={} articles={} summariesFilled={}",
        doc.getId(),
        doc.getArticleCount(),
        doc.getSummariesFilledThisRun());
  }
}
