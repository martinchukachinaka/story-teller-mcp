package com.cc.whatif.storytellermcp.application.story.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record MakeChoiceRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("ID of the story returned by start_story")
        Long storyId,
        @JsonProperty(required = true)
        @JsonPropertyDescription("the chapterNumber returned by the save_chapter call whose choiceOptions this choice responds to")
        int chapterNumber,
        @JsonProperty(required = true)
        @JsonPropertyDescription("0-based index of the option the player chose, from the choiceOptions most recently returned by save_chapter")
        int selectedOption
) {
}
