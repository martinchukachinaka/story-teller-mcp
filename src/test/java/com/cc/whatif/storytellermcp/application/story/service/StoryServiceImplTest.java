package com.cc.whatif.storytellermcp.application.story.service;

import com.cc.whatif.storytellermcp.application.story.dto.ChapterRequest;
import com.cc.whatif.storytellermcp.application.story.dto.StoryRequest;
import com.cc.whatif.storytellermcp.application.story.repo.StoryRepo;
import com.cc.whatif.storytellermcp.domain.story.Chapter;
import com.cc.whatif.storytellermcp.domain.story.Story;
import com.cc.whatif.storytellermcp.infrastructure.exceptions.StoryException;
import com.cc.whatif.storytellermcp.persistence.story.model.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoryServiceImplTest {

    @Mock
    private StoryRepo storyRepo;

    @InjectMocks
    private StoryServiceImpl storyService;

    private StoryRequest fullRequest() {
        return new StoryRequest(
                List.of(Genre.ADVENTURE, Genre.COMEDY),
                "The Great Sock Catastrophe",
                "Kingdom of Tumblewick",
                Flavor.MAGIC,
                List.of("Eri", "Chuchu"),
                VillainMode.CUSTOM,
                "The Unraveller, lonely and misunderstood",
                Tone.LIGHTHEARTED,
                StoryLength.LONG
        );
    }

    private StoryRequest requestMissingOptionalFields() {
        return new StoryRequest(
                List.of(Genre.MYSTERY),
                "Untitled",
                "A washing machine",
                Flavor.SCI_FI,
                List.of("Eri"),
                null,
                null,
                Tone.DRAMATIC,
                null
        );
    }

    @BeforeEach
    void stubSaveToReturnItsArgument() {
        lenient().when(storyRepo.save(any(Story.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createStory_preservesProvidedOptionalFields() {
        var story = storyService.createStory(fullRequest());

        assertThat(story.getVillainMode()).isEqualTo(VillainMode.CUSTOM);
        assertThat(story.getVillainDescription()).isEqualTo("The Unraveller, lonely and misunderstood");
        assertThat(story.getLength()).isEqualTo(StoryLength.LONG);
        assertThat(story.getTitle()).isEqualTo("The Great Sock Catastrophe");
    }

    @Test
    void createStory_defaultsMissingOptionalFields() {
        var story = storyService.createStory(requestMissingOptionalFields());

        assertThat(story.getVillainMode()).isEqualTo(VillainMode.RANDOM);
        assertThat(story.getVillainDescription())
                .isEqualTo("Invent one that fits the genre and setting — misunderstood or lonely, not truly evil.");
        assertThat(story.getLength()).isEqualTo(StoryLength.SHORT);
    }

    @Test
    void createStory_alwaysPicksAVirtueAndApproachServerSide() {
        var story = storyService.createStory(fullRequest());

        assertThat(story.getVirtue()).isIn((Object[]) Virtue.values());
        assertThat(story.getVirtueApproach()).isIn((Object[]) VirtueApproach.values());
    }

    @Test
    void createStory_persistsViaRepo() {
        storyService.createStory(fullRequest());

        verify(storyRepo).save(any(Story.class));
    }

    private Story storyWithStatus(Long id, StoryStatus status, Set<Chapter> existingChapters) {
        return Story.builder()
                .id(id)
                .title("Test Story")
                .virtue(Virtue.KINDNESS)
                .virtueApproach(VirtueApproach.PLOT_DRIVEN)
                .status(status)
                .chapters(existingChapters != null ? new HashSet<>(existingChapters) : new HashSet<>())
                .build();
    }

    private Story storyInProgress(Long id, Set<Chapter> existingChapters) {
        return storyWithStatus(id, StoryStatus.IN_PROGRESS, existingChapters);
    }

    @Test
    void saveChapter_nonFinal_addsChapterAndSetsAwaitingChoice() {
        when(storyRepo.findById(1L)).thenReturn(storyInProgress(1L, null));
        var request = new ChapterRequest(1L, "Once upon a sock...", List.of("Chase it", "Ignore it"), false);

        var saved = storyService.saveChapter(request);

        ArgumentCaptor<Story> captor = ArgumentCaptor.forClass(Story.class);
        verify(storyRepo).save(captor.capture());
        var persisted = captor.getValue();

        assertThat(persisted.getStatus()).isEqualTo(StoryStatus.AWAITING_CHOICE);
        assertThat(persisted.getChapters()).hasSize(1);
        var chapter = persisted.getChapters().iterator().next();
        assertThat(chapter.chapterNumber()).isEqualTo(1);
        assertThat(chapter.content()).isEqualTo("Once upon a sock...");
        assertThat(chapter.choiceOptions()).containsExactly("Chase it", "Ignore it");
        assertThat(chapter.isFinalChapter()).isFalse();
        assertThat(saved.getStatus()).isEqualTo(StoryStatus.AWAITING_CHOICE);
    }

    @Test
    void saveChapter_final_ignoresChoiceOptionsAndSetsComplete() {
        when(storyRepo.findById(1L)).thenReturn(storyInProgress(1L, null));
        var request = new ChapterRequest(1L, "The end.", List.of("This shouldn't be kept"), true);

        storyService.saveChapter(request);

        ArgumentCaptor<Story> captor = ArgumentCaptor.forClass(Story.class);
        verify(storyRepo).save(captor.capture());
        var chapter = captor.getValue().getChapters().iterator().next();

        assertThat(chapter.isFinalChapter()).isTrue();
        assertThat(chapter.choiceOptions()).isEmpty();
        assertThat(captor.getValue().getStatus()).isEqualTo(StoryStatus.COMPLETE);
    }

    @Test
    void saveChapter_computesChapterNumberFromExistingChapters() {
        var existing = Set.of(
                new Chapter(10L, 1, "Ch1", List.of("a", "b"), null, false),
                new Chapter(11L, 2, "Ch2", List.of("a", "b"), null, false)
        );
        when(storyRepo.findById(1L)).thenReturn(storyInProgress(1L, existing));
        var request = new ChapterRequest(1L, "Ch3", List.of("a", "b"), false);

        storyService.saveChapter(request);

        ArgumentCaptor<Story> captor = ArgumentCaptor.forClass(Story.class);
        verify(storyRepo).save(captor.capture());
        var chapterNumbers = captor.getValue().getChapters().stream().map(Chapter::chapterNumber).toList();

        assertThat(chapterNumbers).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    void saveChapter_rejectsWhenStoryNotFound() {
        when(storyRepo.findById(99L)).thenThrow(new StoryException("Story[99] not found"));
        var request = new ChapterRequest(99L, "content", List.of("a", "b"), false);

        var ex = assertThrows(StoryException.class, () -> storyService.saveChapter(request));
        assertThat(ex.getMessage()).isEqualTo("Story[99] not found");
        verify(storyRepo, never()).save(any());
    }

    @Test
    void saveChapter_rejectsWhenStoryAlreadyComplete() {
        when(storyRepo.findById(1L)).thenReturn(storyWithStatus(1L, StoryStatus.COMPLETE, null));
        var request = new ChapterRequest(1L, "content", List.of("a", "b"), false);

        assertThrows(StoryException.class, () -> storyService.saveChapter(request));
        verify(storyRepo, never()).save(any());
    }

    @Test
    void saveChapter_rejectsWhenNonFinalChapterHasNoChoiceOptions() {
        when(storyRepo.findById(1L)).thenReturn(storyInProgress(1L, null));
        var request = new ChapterRequest(1L, "content", List.of(), false);

        assertThrows(StoryException.class, () -> storyService.saveChapter(request));
        verify(storyRepo, never()).save(any());
    }

    @Test
    void saveChapter_rejectsWhenNonFinalChapterHasNullChoiceOptions() {
        when(storyRepo.findById(1L)).thenReturn(storyInProgress(1L, null));
        var request = new ChapterRequest(1L, "content", null, false);

        assertThrows(StoryException.class, () -> storyService.saveChapter(request));
        verify(storyRepo, never()).save(any());
    }
}
