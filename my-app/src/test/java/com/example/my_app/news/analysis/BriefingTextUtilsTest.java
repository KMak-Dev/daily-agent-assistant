package com.example.my_app.news.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BriefingTextUtilsTest {

  @Test
  void normalizeBriefingLineBreaks_unifiesCrLfAndCollapsesLongRuns() {
    String in = "A\r\nB\n\n\nC\rD";
    assertThat(BriefingTextUtils.normalizeBriefingLineBreaks(in)).isEqualTo("A\nB\n\nC\nD");
  }

  @Test
  void normalizeBriefingLineBreaks_trimsEnds() {
    assertThat(BriefingTextUtils.normalizeBriefingLineBreaks("\n\nx\n\n")).isEqualTo("x");
  }

  @Test
  void normalizeBriefingLineBreaks_nullStaysNull() {
    assertThat(BriefingTextUtils.normalizeBriefingLineBreaks(null)).isNull();
  }
}
