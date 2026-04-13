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
     * Days to subtract from "today" in {@link #zoneId} when the job runs (default {@code 1} =
     * previous calendar day, typical for an 08:00 briefing).
     */
    int dayOffset) {}
