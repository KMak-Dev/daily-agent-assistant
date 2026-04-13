package com.example.my_app.news.dailybriefing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "news.daily-briefing", name = "enabled", havingValue = "true")
public class NewsDailyBriefingScheduler {

  private static final Logger log = LoggerFactory.getLogger(NewsDailyBriefingScheduler.class);

  private final NewsDailyBriefingRunner newsDailyBriefingRunner;

  public NewsDailyBriefingScheduler(NewsDailyBriefingRunner newsDailyBriefingRunner) {
    this.newsDailyBriefingRunner = newsDailyBriefingRunner;
  }

  @Scheduled(cron = "${news.daily-briefing.cron}", zone = "${news.daily-briefing.zone-id}")
  public void runDailyBriefing() {
    try {
      newsDailyBriefingRunner.runForSchedule();
    } catch (Exception e) {
      log.error("Daily briefing job failed: {}", e.getMessage(), e);
    }
  }
}
