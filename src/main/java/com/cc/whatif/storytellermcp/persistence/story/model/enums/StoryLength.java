package com.cc.whatif.storytellermcp.persistence.story.model.enums;

public enum StoryLength {
    SHORT("2-3 chapters, ~300-450 words total (~100-150 words per chapter)"),
    LONG("5-10 chapters, ~450-750 words total (~45-150 words per chapter)");

    private final String description;

    StoryLength(String description) {
        this.description = description;
    }

    public String getDescription() {
        return "%s (%s)".formatted(name(), description);
    }
}