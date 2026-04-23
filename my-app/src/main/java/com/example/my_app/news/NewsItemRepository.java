package com.example.my_app.news;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface NewsItemRepository extends MongoRepository<NewsItem, String> {

  boolean existsByUrl(String url);

  /**
   * {@code publishedDate >= from} and {@code publishedDate < to} (half-open interval on {@code
   * to}).
   */
  @Query(value = "{ 'published_date': { $gte: ?0, $lt: ?1 } }", sort = "{ 'published_date': -1 }")
  List<NewsItem> findByPublishedDateBetweenOrderByPublishedDateDesc(
      LocalDate fromInclusive, LocalDate toExclusive);

  long deleteByPublishedDateBefore(LocalDate cutoffExclusive);
}
