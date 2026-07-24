package com.cc.whatif.storytellermcp.story.domain;

import com.cc.whatif.storytellermcp.domain.story.Chapter;
import com.cc.whatif.storytellermcp.domain.story.Story;
import com.cc.whatif.storytellermcp.persistence.story.model.ChapterEntity;
import com.cc.whatif.storytellermcp.persistence.story.model.StoryEntity;
import com.cc.whatif.storytellermcp.persistence.story.model.enums.*;
import com.cc.whatif.storytellermcp.persistence.story.repository.StoryEntityRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class StoryEntityPersistenceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private StoryEntityRepo storyEntityRepo;

    private StoryEntity.StoryEntityBuilder baseStoryEntity() {
        return StoryEntity.builder()
                .genreBlend(List.of(Genre.ADVENTURE, Genre.COMEDY))
                .setting("Kingdom of Tumblewick")
                .flavor(Flavor.MAGIC)
                .heroes(List.of("Eri", "Chuchu"))
                .villainMode(VillainMode.RANDOM)
                .villainDescription("The Unraveller, lonely and misunderstood")
                .tone(Tone.LIGHTHEARTED)
                .length(StoryLength.SHORT)
                .virtue(Virtue.KINDNESS)
                .virtueApproach(VirtueApproach.PLOT_DRIVEN)
                .status(StoryStatus.IN_PROGRESS);
    }

    @Test
    void persistsAndReloadsStoryWithChaptersAndCollections() {
        StoryEntity storyEntity = baseStoryEntity().build();
        storyEntity.addChapter(ChapterEntity.builder().chapterNumber(1).content("Once upon a sock...").build());

        StoryEntity saved = entityManager.persistFlushFind(storyEntity);
        entityManager.clear();

        StoryEntity reloaded = entityManager.find(StoryEntity.class, saved.getId());
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getGenreBlend()).containsExactly(Genre.ADVENTURE, Genre.COMEDY);
        assertThat(reloaded.getHeroes()).containsExactly("Eri", "Chuchu");
        assertThat(reloaded.getChapterEntities()).hasSize(1);
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void persistsAndReloadsChapterChoiceOptionsSelectedOptionAndFinalFlag() {
        StoryEntity storyEntity = baseStoryEntity().build();
        storyEntity.addChapter(ChapterEntity.builder()
                .chapterNumber(1)
                .content("Chapter one")
                .choiceOptions(List.of("Chase it", "Ignore it"))
                .selectedOption(1)
                .finalChapter(false)
                .build());
        storyEntity.addChapter(ChapterEntity.builder()
                .chapterNumber(2)
                .content("The end.")
                .choiceOptions(List.of())
                .finalChapter(true)
                .build());

        StoryEntity saved = entityManager.persistFlushFind(storyEntity);
        entityManager.clear();

        StoryEntity reloaded = entityManager.find(StoryEntity.class, saved.getId());
        assertThat(reloaded.getChapterEntities()).hasSize(2);

        var chapterOne = reloaded.getChapterEntities().stream()
                .filter(c -> c.getChapterNumber() == 1)
                .findFirst()
                .orElseThrow();
        var chapterTwo = reloaded.getChapterEntities().stream()
                .filter(c -> c.getChapterNumber() == 2)
                .findFirst()
                .orElseThrow();

        assertThat(chapterOne.getChoiceOptions()).containsExactly("Chase it", "Ignore it");
        assertThat(chapterOne.getSelectedOption()).isEqualTo(1);
        assertThat(chapterOne.isFinalChapter()).isFalse();

        assertThat(chapterTwo.getChoiceOptions()).isEmpty();
        assertThat(chapterTwo.getSelectedOption()).isNull();
        assertThat(chapterTwo.isFinalChapter()).isTrue();
    }

    @Test
    void chapterIdsRemainStableAcrossRepeatedSaves() {
        // Mirrors what StoryServiceImpl.saveChapter + StoryRepoImpl.save do in production:
        // reload as a domain Story, append a chapter, convert back to a fresh StoryEntity, save again.
        // Regression coverage for the bug where ChapterEntity's Chapter-conversion constructor
        // never copied the chapter's id, causing every prior chapter to be deleted and
        // re-inserted (via orphanRemoval) on each subsequent save.
        Story story = Story.builder()
                .title("Multi-save Story")
                .genreBlend(List.of(Genre.ADVENTURE))
                .setting("Kingdom of Tumblewick")
                .flavor(Flavor.MAGIC)
                .heroes(List.of("Eri"))
                .villainMode(VillainMode.RANDOM)
                .villainDescription("The Unraveller, lonely and misunderstood")
                .tone(Tone.LIGHTHEARTED)
                .length(StoryLength.SHORT)
                .virtue(Virtue.KINDNESS)
                .virtueApproach(VirtueApproach.PLOT_DRIVEN)
                .status(StoryStatus.AWAITING_CHOICE)
                .chapters(new HashSet<>(Set.of(
                        new Chapter(null, 1, "Chapter one", List.of("Chase it", "Ignore it"), null, false)
                )))
                .build();

        StoryEntity firstSave = storyEntityRepo.save(new StoryEntity(story));
        Long chapterOneOriginalId = firstSave.getChapterEntities().iterator().next().getId();
        assertThat(chapterOneOriginalId).isNotNull();

        entityManager.flush();
        entityManager.clear();

        Story reloadedAfterFirstSave = storyEntityRepo.findById(firstSave.getId()).orElseThrow().toStory();
        reloadedAfterFirstSave.addChapter(new Chapter(null, 2, "Chapter two, the end.", List.of(), null, true));
        reloadedAfterFirstSave.setStatus(StoryStatus.COMPLETE);

        storyEntityRepo.save(new StoryEntity(reloadedAfterFirstSave));
        entityManager.flush();
        entityManager.clear();

        StoryEntity finalState = storyEntityRepo.findById(firstSave.getId()).orElseThrow();
        assertThat(finalState.getChapterEntities()).hasSize(2);

        var chapterOneAfter = finalState.getChapterEntities().stream()
                .filter(c -> c.getChapterNumber() == 1)
                .findFirst()
                .orElseThrow();
        var chapterTwoAfter = finalState.getChapterEntities().stream()
                .filter(c -> c.getChapterNumber() == 2)
                .findFirst()
                .orElseThrow();

        assertThat(chapterOneAfter.getId()).isEqualTo(chapterOneOriginalId);
        assertThat(chapterOneAfter.getContent()).isEqualTo("Chapter one");
        assertThat(chapterTwoAfter.getContent()).isEqualTo("Chapter two, the end.");
        assertThat(chapterTwoAfter.isFinalChapter()).isTrue();
    }
}
