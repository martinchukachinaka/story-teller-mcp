package com.cc.whatif.storytellermcp.persistence.story.model.enums;

public enum StoryLength {
    SHORT("2-3 chapters, ~250-400 words total (~80-130 words per chapter)"),
    LONG("4-5 chapters, ~400-500 words total (~100-130 words per chapter)");

    private final String description;

    StoryLength(String description) {
        this.description = description;
    }

    public String getDescription() {
        return "%s (%s)".formatted(name(), description);
    }
}