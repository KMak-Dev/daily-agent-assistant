package com.example.my_app.news;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NewsCreateRequest(
		String url,
		String title,
		String author,
		String content,
		@JsonProperty("published_date") LocalDate publishedDate) {
}
