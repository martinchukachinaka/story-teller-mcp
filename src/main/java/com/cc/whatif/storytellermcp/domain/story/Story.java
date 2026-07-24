package com.cc.whatif.storytellermcp.domain.story;

import com.cc.whatif.storytellermcp.persistence.story.model.enums.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Story {

    Long id;

    String title;

    @Builder.Default
    List<Genre> genreBlend = new ArrayList<>();

    String setting;

    Flavor flavor;

    @Builder.Default
    List<String> heroes = new ArrayList<>();

    VillainMode villainMode;

    String villainDescription;

    Tone tone;

    StoryLength length;

    Virtue virtue;

    VirtueApproach virtueApproach;

    @Builder.Default
    StoryStatus status = StoryStatus.IN_PROGRESS;

    @Builder.Default
    Set<Chapter> chapters = new HashSet<>();

    public void addChapter(Chapter chapter) {
        chapters.add(chapter);
    }

    public void removeChapter(Chapter chapter) {
        chapters.remove(chapter);
    }
}
