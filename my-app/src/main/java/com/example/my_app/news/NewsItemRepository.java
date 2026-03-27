package com.example.my_app.news;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface NewsItemRepository extends MongoRepository<NewsItem, String> {
}
