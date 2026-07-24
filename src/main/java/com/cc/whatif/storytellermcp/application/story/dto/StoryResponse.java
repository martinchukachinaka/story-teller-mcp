package com.cc.whatif.storytellermcp.application.story.dto;

public record StoryResponse(
        Long storyId,
        String generationPrompt,
        String instructions,
        boolean rejected,
        String message
) {

    public StoryResponse(Long storyId, String generationPrompt, String instructions) {
        this(storyId, generationPrompt, instructions, false, "");
    }
}
