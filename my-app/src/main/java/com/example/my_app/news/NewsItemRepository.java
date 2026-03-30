package com.example.my_app.news;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NewsItemRepository extends MongoRepository<NewsItem, String> {

  List<NewsItem> findByPublishedDateBetweenOrderByPublishedDateDesc(
      LocalDate startInclusive, LocalDate endInclusive);
}
