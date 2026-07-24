package com.cc.whatif.storytellermcp.presentation;

import com.cc.whatif.storytellermcp.application.story.dto.ChapterRequest;
import com.cc.whatif.storytellermcp.application.story.dto.StoryRequest;
import com.cc.whatif.storytellermcp.application.story.service.StoryService;
import com.cc.whatif.storytellermcp.domain.story.Chapter;
import com.cc.whatif.storytellermcp.domain.story.Story;
import com.cc.whatif.storytellermcp.infrastructure.exceptions.StoryException;
import com.cc.whatif.storytellermcp.persistence.story.model.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.cc.whatif.storytellermcp.infrastructure.AppConstants.VIRTUE_REVEAL_TEMPLATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoryMcpTest {

    @Mock
    private StoryService storyService;

    private StoryMcp storyMcp;

    @BeforeEach
    void setUp() {
        storyMcp = new StoryMcp(storyService);
    }

    private Story fullyBuiltStory(Long id) {
        return Story.builder()
                .id(id)
                .title("The Great Sock Catastrophe")
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
                .status(StoryStatus.IN_PROGRESS)
                .build();
    }

    private StoryRequest anyValidRequest() {
        return new StoryRequest(
                List.of(Genre.ADVENTURE),
                "Untitled",
                "Kingdom of Tumblewick",
                Flavor.MAGIC,
                List.of("Eri"),
                VillainMode.RANDOM,
                null,
                Tone.LIGHTHEARTED,
                StoryLength.SHORT
        );
    }

    @Test
    void startStory_buildsPromptFromStoryFieldsAndDelegatesToService() {
        var story = fullyBuiltStory(1L);
        when(storyService.createStory(any(StoryRequest.class))).thenReturn(story);

        var response = storyMcp.startStory(anyValidRequest());

        assertThat(response.storyId()).isEqualTo(1L);
        assertThat(response.generationPrompt())
                .contains("ADVENTURE, COMEDY")
                .contains("Kingdom of Tumblewick")
                .contains("MAGIC")
                .contains("Eri, Chuchu")
                .contains("The Unraveller, lonely and misunderstood")
                .contains("LIGHTHEARTED")
                .contains("KINDNESS")
                .contains(story.getVirtueApproach().getDescription());
        assertThat(response.instructions())
                .contains("save_chapter")
                .contains("storyId=1")
                .contains("isFinalChapter=false");
        assertThat(response.rejected()).isFalse();
    }

    @Test
    void saveChapter_nonFinal_returnsChapterNumberAndStatusWithoutVirtueReveal() {
        var story = fullyBuiltStory(1L);
        story.addChapter(new Chapter(100L, 1, "Once upon a sock...", List.of("Chase it", "Ignore it"), null, false));
        story.setStatus(StoryStatus.AWAITING_CHOICE);
        when(storyService.saveChapter(any(ChapterRequest.class))).thenReturn(story);

        var response = storyMcp.saveChapter(new ChapterRequest(1L, "Once upon a sock...", List.of("Chase it", "Ignore it"), false));

        assertThat(response.storyId()).isEqualTo(1L);
        assertThat(response.chapterNumber()).isEqualTo(1);
        assertThat(response.storyStatus()).isEqualTo("AWAITING_CHOICE");
        assertThat(response.virtueReveal()).isNull();
        assertThat(response.rejected()).isFalse();
    }

    @Test
    void saveChapter_final_returnsVirtueRevealBuiltFromTemplate() {
        var story = fullyBuiltStory(1L);
        story.addChapter(new Chapter(100L, 1, "Ch1", List.of("a", "b"), null, false));
        story.addChapter(new Chapter(101L, 2, "The end.", List.of(), null, true));
        story.setStatus(StoryStatus.COMPLETE);
        when(storyService.saveChapter(any(ChapterRequest.class))).thenReturn(story);

        var response = storyMcp.saveChapter(new ChapterRequest(1L, "The end.", null, true));

        assertThat(response.chapterNumber()).isEqualTo(2);
        assertThat(response.storyStatus()).isEqualTo("COMPLETE");
        assertThat(response.virtueReveal()).isEqualTo(VIRTUE_REVEAL_TEMPLATE.formatted(Virtue.KINDNESS));
        assertThat(response.rejected()).isFalse();
    }

    @Test
    void saveChapter_rejectsGracefullyWhenServiceThrowsStoryOperationException() {
        when(storyService.saveChapter(any(ChapterRequest.class)))
                .thenThrow(new StoryException("Story[5] not found"));

        var response = storyMcp.saveChapter(new ChapterRequest(5L, "content", List.of("a", "b"), false));

        assertThat(response.rejected()).isTrue();
        assertThat(response.message()).isEqualTo("Story[5] not found");
        assertThat(response.storyId()).isNull();
        assertThat(response.chapterNumber()).isZero();
        assertThat(response.storyStatus()).isNull();
        assertThat(response.virtueReveal()).isNull();
    }
}
