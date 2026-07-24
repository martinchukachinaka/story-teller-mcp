package com.cc.whatif.storytellermcp.persistence.story.model;

import com.cc.whatif.storytellermcp.domain.story.Chapter;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

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

    @ElementCollection
    @CollectionTable(name = "chapter_choice_options", joinColumns = @JoinColumn(name = "chapter_id"))
    @OrderColumn(name = "list_index")
    @Column(name = "choice_option", nullable = false)
    @Builder.Default
    private List<String> choiceOptions = new ArrayList<>();

    private Integer selectedOption;

    @Column(nullable = false)
    private boolean finalChapter;

    public ChapterEntity(Chapter chapter, StoryEntity story) {
        setId(chapter.id());
        setStory(story);
        setChapterNumber(chapter.chapterNumber());
        setContent(chapter.content());
        setChoiceOptions(chapter.choiceOptions());
        setSelectedOption(chapter.selectedOption());
        setFinalChapter(chapter.isFinalChapter());
    }

    public Chapter toChapter() {
        return new Chapter(id, chapterNumber, content, choiceOptions, selectedOption, finalChapter);
    }
}
