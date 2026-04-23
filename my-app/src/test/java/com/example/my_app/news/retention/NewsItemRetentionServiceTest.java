package com.example.my_app.news.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.my_app.news.NewsItemRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewsItemRetentionServiceTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Hong_Kong");

  /** Instant where {@code LocalDate} in Hong Kong is 2026-04-20. */
  private static final Instant INSTANT_2026_04_20_HKT =
      LocalDate.of(2026, 4, 20).atStartOfDay(ZONE).toInstant();

  @Mock private NewsItemRepository newsItemRepository;

  @Test
  void purgeOlderThanRetention_deletesBeforeCutoff() {
    NewsItemRetentionProperties properties =
        new NewsItemRetentionProperties(true, "0 0 3 * * *", ZONE.getId(), 90);
    Clock clock = Clock.fixed(INSTANT_2026_04_20_HKT, ZONE);
    NewsItemRetentionService service =
        new NewsItemRetentionService(newsItemRepository, properties, clock);

    LocalDate expectedCutoff = LocalDate.of(2026, 4, 20).minusDays(90);
    when(newsItemRepository.deleteByPublishedDateBefore(expectedCutoff)).thenReturn(3L);

    long removed = service.purgeOlderThanRetention();

    assertThat(removed).isEqualTo(3L);
    verify(newsItemRepository).deleteByPublishedDateBefore(expectedCutoff);
  }

  @Test
  void purgeOlderThanRetention_clampsRetentionDaysToAtLeastOne() {
    NewsItemRetentionProperties properties =
        new NewsItemRetentionProperties(true, "0 0 3 * * *", ZONE.getId(), 0);
    Clock clock = Clock.fixed(INSTANT_2026_04_20_HKT, ZONE);
    NewsItemRetentionService service =
        new NewsItemRetentionService(newsItemRepository, properties, clock);

    LocalDate expectedCutoff = LocalDate.of(2026, 4, 20).minusDays(1);
    when(newsItemRepository.deleteByPublishedDateBefore(expectedCutoff)).thenReturn(0L);

    service.purgeOlderThanRetention();

    verify(newsItemRepository).deleteByPublishedDateBefore(expectedCutoff);
  }
}
