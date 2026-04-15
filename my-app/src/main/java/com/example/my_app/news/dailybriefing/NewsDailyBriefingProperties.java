package com.example.my_app.news.dailybriefing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "news.daily-briefing")
public record NewsDailyBriefingProperties(
    boolean enabled,
    /**
     * Spring six-field cron (second minute hour day month weekday), evaluated in {@link #zoneId}.
     */
    String cron,
    /**
     * IANA zone for the cron trigger and for default {@code LocalDate} math (e.g. Asia/Hong_Kong).
     */
    String zoneId,
    /**
     * Days to subtract from "today" in {@link #zoneId} when the job runs to form the anchor date
     * (default {@code 7} in {@code application.properties}: anchor is one week before "today" on the
     * calendar; use {@code 1} for "yesterday" as anchor).
     */
    int dayOffset,
    /**
     * Half-open analyze window length in calendar days: {@code publishedDate >= startDate} and
     * {@code publishedDate < startDate + windowDays}. Clamped to {@code [1, 7]} at runtime (same
     * maximum as {@code POST /api/news/analyze}).
     */
    int windowDays) {}
