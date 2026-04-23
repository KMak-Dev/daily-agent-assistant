package com.example.my_app.news.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "news.item-retention")
public record NewsItemRetentionProperties(
    boolean enabled,
    /**
     * Spring six-field cron (second minute hour day month weekday), evaluated in {@link #zoneId}.
     */
    String cron,
    /**
     * IANA zone for the cron trigger and for {@code LocalDate.now(zone)} when computing the cutoff
     * date.
     */
    String zoneId,
    /**
     * Documents with {@code published_date} strictly before {@code today.minusDays(retentionDays)}
     * (in {@link #zoneId}) are deleted.
     */
    int retentionDays) {}
