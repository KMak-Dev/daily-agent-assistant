package com.example.my_app.news.dailybriefing;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "news_daily_briefings")
@CompoundIndex(
    name = "uk_briefing_window",
    def = "{'time_zone': 1, 'start_date': 1, 'end_date': 1}",
    unique = true)
public class NewsDailyBriefing {

  @Id private String id;

  @Field("start_date")
  private LocalDate startDate;

  @Field("end_date")
  private LocalDate endDate;

  @Field("time_zone")
  private String timeZone;

  @Field("article_count")
  private int articleCount;

  private String briefing;

  @Field("last_source")
  private String lastSource;

  @Indexed
  @Field("created_at")
  private Instant createdAt;

  @Indexed
  @Field("updated_at")
  private Instant updatedAt;

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

  public String getBriefing() {
    return briefing;
  }

  public void setBriefing(String briefing) {
    this.briefing = briefing;
  }

  public String getLastSource() {
    return lastSource;
  }

  public void setLastSource(String lastSource) {
    this.lastSource = lastSource;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
