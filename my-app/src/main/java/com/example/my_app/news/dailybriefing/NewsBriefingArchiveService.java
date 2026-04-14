package com.example.my_app.news.dailybriefing;

import com.example.my_app.news.analysis.NewsAnalyzeResponse;
import java.time.Instant;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class NewsBriefingArchiveService {

  private final MongoTemplate mongoTemplate;

  public NewsBriefingArchiveService(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  /**
   * One row per ({@code time_zone}, {@code start_date}, {@code end_date}): inserts on first write,
   * updates briefing and timestamps on subsequent cron/API runs for the same window.
   */
  public void upsert(NewsAnalyzeResponse response, BriefingArchiveSource source) {
    Instant now = Instant.now();
    Query query =
        new Query(
            Criteria.where("timeZone")
                .is(response.timeZone())
                .and("startDate")
                .is(response.startDate())
                .and("endDate")
                .is(response.endDate()));

    Update update =
        new Update()
            .set("briefing", response.briefing())
            .set("articleCount", response.articleCount())
            .set("updatedAt", now)
            .set("lastSource", source.name())
            .setOnInsert("createdAt", now)
            .setOnInsert("timeZone", response.timeZone())
            .setOnInsert("startDate", response.startDate())
            .setOnInsert("endDate", response.endDate());

    mongoTemplate.upsert(query, update, NewsDailyBriefing.class);
  }
}
