package com.cc.whatif.storytellermcp.application.story.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record ChapterRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("The id of the story this chapter belongs to, from start_story or the previous save_chapter response")
        Long storyId,
        @JsonProperty(required = true)
        @JsonPropertyDescription("The generated chapter content")
        String content,
        @JsonPropertyDescription("2-3 selectable choice options ending this chapter; omit when isFinalChapter is true")
        List<String> choiceOptions,
        @JsonProperty(required = true)
        @JsonPropertyDescription("True only for the story's closing chapter, which has no further choices")
        boolean isFinalChapter
) {
}
