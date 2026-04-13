package com.example.my_app.news.analysis;

import java.time.LocalDate;

public record NewsAnalyzeResponse(
    LocalDate startDate,
    LocalDate endDate,
    String timeZone,
    int articleCount,
    /** Summaries written or updated during this request (xAI batch calls). */
    int summariesFilledThisRun,
    String briefing) {}
