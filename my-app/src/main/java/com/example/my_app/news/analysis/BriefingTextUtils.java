package com.example.my_app.news.analysis;

import java.util.regex.Pattern;

/** Normalizes model-produced briefing text for storage and JSON APIs. */
public final class BriefingTextUtils {

  private static final Pattern MULTI_BLANK_LINE = Pattern.compile("\n{3,}");

  private BriefingTextUtils() {}

  /**
   * Converts {@code \r\n} / {@code \r} to {@code \n}, trims leading/trailing whitespace, and
   * collapses runs of three or more consecutive newlines to a single blank line (two {@code \n}).
   */
  public static String normalizeBriefingLineBreaks(String briefing) {
    if (briefing == null) {
      return null;
    }
    String t = briefing.replace("\r\n", "\n").replace('\r', '\n').strip();
    if (t.isEmpty()) {
      return t;
    }
    return MULTI_BLANK_LINE.matcher(t).replaceAll("\n\n");
  }
}
