package com.example.my_app.news.worldnews;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.my_app.news.NewsItemRepository;
import com.example.my_app.positions.StockPositionRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Verifies the ingest scheduler runs and reaches {@link WorldNewsClient}. Uses short delays via
 * {@link TestPropertySource}; production defaults remain hourly (see {@code
 * application.properties}).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "worldnews.ingest-enabled=true",
      "worldnews.api-key=test-key",
      "worldnews.ingest-initial-delay-ms=100",
      "worldnews.ingest-fixed-delay-ms=250"
    })
class WorldNewsIngestSchedulerIT {

  @MockitoBean WorldNewsClient worldNewsClient;
  @MockitoBean NewsItemRepository newsItemRepository;
  @MockitoBean StockPositionRepository stockPositionRepository;
  @MockitoBean WorldNewsKeywordRepository worldNewsKeywordRepository;

  @BeforeEach
  void stubClient() {
    when(worldNewsClient.searchNews(anyInt(), anyInt())).thenReturn(Optional.empty());
  }

  @Test
  void scheduledIngest_invokesSearchNews() {
    await()
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> verify(worldNewsClient).searchNews(anyInt(), anyInt()));
  }
}
