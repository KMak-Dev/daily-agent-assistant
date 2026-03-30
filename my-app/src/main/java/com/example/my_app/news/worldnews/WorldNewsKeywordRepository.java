package com.example.my_app.news.worldnews;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface WorldNewsKeywordRepository extends MongoRepository<WorldNewsKeyword, String> {}
