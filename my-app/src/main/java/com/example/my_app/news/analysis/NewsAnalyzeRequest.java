package com.example.my_app.news.analysis;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NewsAnalyzeRequest(
    LocalDate startDate,
    /**
     * Exclusive upper bound: articles included satisfy {@code publishedDate >= startDate &&
     * publishedDate < endDate}. When null, the service uses start of tomorrow in {@link
     * #timeZone()} (so the range includes all of "today" when paired with a start on or before
     * today).
     */
    LocalDate endDate,
    /** IANA zone id (e.g. {@code America/New_York}). Defaults to {@code UTC} when null or blank. */
    String timeZone,
    /** Overrides {@code xai.batch-size} when set. */
    Integer batchSize,
    /** When true, re-summarize articles even if {@code summary} is already set. */
    Boolean refreshSummaries,
    /**
     * Synthesis task for the final xAI call (briefing + how it relates to our positions). When
     * null/blank, uses {@code xai.briefing-prompt} or {@code defaultBriefingPrompt} in {@code
     * prompts/news-analyze.yaml}.
     */
    String briefingPrompt) {}
