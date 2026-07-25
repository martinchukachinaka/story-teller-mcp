package com.cc.whatif.storytellermcp.application.story.dto;

import com.cc.whatif.storytellermcp.persistence.story.model.enums.StoryStatus;

public record MakeChoiceResponse(
        Long storyId,
        int nextChapterNumber,
        StoryStatus status,
        String generationPrompt,
        String instruction,
        boolean rejected,
        String message
) {

    public MakeChoiceResponse(Long storyId,
                              int nextChapterNumber,
                              StoryStatus status,
                              String generationPrompt,
                              String instruction) {
        this(storyId, nextChapterNumber, status, generationPrompt, instruction, false, "");
    }

    public static MakeChoiceResponse reject(String message) {
        return new MakeChoiceResponse(null, 0, null, null, null, true, message);
    }
}
