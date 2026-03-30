package com.example.my_app;

import com.example.my_app.news.NewsItemRepository;
import com.example.my_app.news.worldnews.WorldNewsKeywordRepository;
import com.example.my_app.positions.StockPositionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class DailyAppTests {

  @MockitoBean NewsItemRepository newsItemRepository;
  @MockitoBean StockPositionRepository stockPositionRepository;
  @MockitoBean WorldNewsKeywordRepository worldNewsKeywordRepository;

  @Test
  void contextLoads() {}
}
