package com.example.my_app.news.dailybriefing;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "news_daily_briefings")
public class NewsDailyBriefing {

  @Id private String id;

  @Indexed
  @Field("start_date")
  private LocalDate startDate;

  @Field("end_date")
  private LocalDate endDate;

  @Field("time_zone")
  private String timeZone;

  @Field("article_count")
  private int articleCount;

  @Field("summaries_filled_this_run")
  private int summariesFilledThisRun;

  private String briefing;

  @Indexed
  @Field("created_at")
  private Instant createdAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public void setTimeZone(String timeZone) {
    this.timeZone = timeZone;
  }

  public int getArticleCount() {
    return articleCount;
  }

  public void setArticleCount(int articleCount) {
    this.articleCount = articleCount;
  }

  public int getSummariesFilledThisRun() {
    return summariesFilledThisRun;
  }

  public void setSummariesFilledThisRun(int summariesFilledThisRun) {
    this.summariesFilledThisRun = summariesFilledThisRun;
  }

  public String getBriefing() {
    return briefing;
  }

  public void setBriefing(String briefing) {
    this.briefing = briefing;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
