package com.cc.whatif.storytellermcp.domain.story;

import java.util.List;

public record Chapter(
        Long id,
        int chapterNumber,
        String content,
        List<String> choiceOptions,
        Integer selectedOption,
        boolean isFinalChapter
) {

    public Chapter {
        choiceOptions = choiceOptions != null ? choiceOptions : List.of();
    }

    public Chapter(int chapterNumber, String content, List<String> choiceOptions, boolean isFinalChapter) {
        this(null, chapterNumber, content, choiceOptions, null, isFinalChapter);
    }
}
