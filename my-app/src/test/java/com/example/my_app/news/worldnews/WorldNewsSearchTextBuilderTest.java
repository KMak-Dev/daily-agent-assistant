package com.example.my_app.news.worldnews;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorldNewsSearchTextBuilderTest {

  @Test
  void build_orOrNot_matchesExample() {
    var a = row(0, "geopolitcs", WorldNewsKeywordOperator.OR);
    var b = row(1, "gold", WorldNewsKeywordOperator.OR);
    var c = row(2, "India", WorldNewsKeywordOperator.NOT);
    assertThat(WorldNewsSearchTextBuilder.build(List.of(a, b, c)))
        .isEqualTo("geopolitcs OR gold -India");
  }

  @Test
  void build_skipsBlankKeywords() {
    var a = row(0, "gold", WorldNewsKeywordOperator.OR);
    var b = row(1, "  ", WorldNewsKeywordOperator.OR);
    var c = row(2, "silver", WorldNewsKeywordOperator.OR);
    assertThat(WorldNewsSearchTextBuilder.build(List.of(a, b, c))).isEqualTo("gold OR silver");
  }

  @Test
  void build_notOnly_joinsWithSpace() {
    var a = row(0, "a", WorldNewsKeywordOperator.NOT);
    var b = row(1, "b", WorldNewsKeywordOperator.NOT);
    assertThat(WorldNewsSearchTextBuilder.build(List.of(a, b))).isEqualTo("-a -b");
  }

  private static WorldNewsKeyword row(int order, String keyword, WorldNewsKeywordOperator op) {
    var k = new WorldNewsKeyword();
    k.setSortOrder(order);
    k.setKeyword(keyword);
    k.setOperator(op);
    return k;
  }
}
