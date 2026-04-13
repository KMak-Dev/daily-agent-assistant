package com.example.my_app.news.analysis;

public record NewsAnalyzePrompts(
    String summarizerInstructions, String synthesizerInstructions, String defaultBriefingPrompt) {}
