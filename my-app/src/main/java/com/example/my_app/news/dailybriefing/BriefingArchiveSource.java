package com.example.my_app.news.dailybriefing;

/** Who triggered persistence of a briefing row (same window is upserted regardless). */
public enum BriefingArchiveSource {
  CRON,
  API
}
