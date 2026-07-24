package com.cc.whatif.storytellermcp.persistence.story.model;

import com.cc.whatif.storytellermcp.domain.story.Chapter;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "chapters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChapterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id", nullable = false)
    private StoryEntity story;

    @Column(nullable = false)
    private int chapterNumber;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    public ChapterEntity(Chapter chapter, StoryEntity story) {
        setStory(story);
        setChapterNumber(chapter.chapterNumber());
        setContent(chapter.content());
    }

    public Chapter toChapter() {
        return new Chapter(id, chapterNumber, content);
    }
}
