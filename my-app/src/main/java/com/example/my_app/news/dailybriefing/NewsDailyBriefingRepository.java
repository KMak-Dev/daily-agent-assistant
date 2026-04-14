package com.example.my_app.news.dailybriefing;

import java.time.LocalDate;
import java.util.Date;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface NewsDailyBriefingRepository extends MongoRepository<NewsDailyBriefing, String> {

  Optional<NewsDailyBriefing> findByTimeZoneAndStartDateAndEndDate(
      String timeZone, LocalDate startDate, LocalDate endDate);

  Page<NewsDailyBriefing> findByTimeZone(String timeZone, Pageable pageable);

  /** Range on {@code start_date}; use {@link Date} so criteria match BSON dates (not strings). */
  @Query("{ 'start_date': { $gte: ?0, $lte: ?1 } }")
  Page<NewsDailyBriefing> findByStartDateUtcBetween(
      Date fromInclusiveUtcMidnight, Date toInclusiveUtcMidnight, Pageable pageable);

  @Query("{ 'time_zone': ?0, 'start_date': { $gte: ?1, $lte: ?2 } }")
  Page<NewsDailyBriefing> findByTimeZoneAndStartDateUtcBetween(
      String timeZone,
      Date fromInclusiveUtcMidnight,
      Date toInclusiveUtcMidnight,
      Pageable pageable);
}
