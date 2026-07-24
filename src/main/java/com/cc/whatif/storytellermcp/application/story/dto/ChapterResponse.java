package com.cc.whatif.storytellermcp.application.story.dto;

import com.cc.whatif.storytellermcp.persistence.story.model.enums.StoryStatus;

public record ChapterResponse(
        Long storyId,
        int chapterNumber,
        StoryStatus storyStatus,
        String virtueReveal,
        boolean rejected,
        String message
) {

    public ChapterResponse(Long storyId, int chapterNumber, StoryStatus storyStatus, String virtueReveal) {
        this(storyId, chapterNumber, storyStatus, virtueReveal, false, "");
    }

    public static ChapterResponse reject(String message) {
        return new ChapterResponse(null, 0, null, null, true, message);
    }
}
