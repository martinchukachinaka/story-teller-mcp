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

    private Long id;

    private String title;

    @Builder.Default
    private List<Genre> genreBlend = new ArrayList<>();

    private String setting;

    private Flavor flavor;

    @Builder.Default
    private List<String> heroes = new ArrayList<>();

    private VillainMode villainMode;

    private String villainDescription;

    private Tone tone;

    private StoryLength length;

    private Virtue virtue;

    private VirtueApproach virtueApproach;

    @Builder.Default
    private StoryStatus status = StoryStatus.IN_PROGRESS;

    @Builder.Default
    private Set<Chapter> chapters = new HashSet<>();

    public void addChapter(Chapter chapter) {
        chapters.add(chapter);
    }

    public void removeChapter(Chapter chapter) {
        chapters.remove(chapter);
    }
}
