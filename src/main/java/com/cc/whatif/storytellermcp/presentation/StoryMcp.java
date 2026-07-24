package com.cc.whatif.storytellermcp.presentation;

import com.cc.whatif.storytellermcp.application.story.dto.ChapterRequest;
import com.cc.whatif.storytellermcp.application.story.dto.ChapterResponse;
import com.cc.whatif.storytellermcp.application.story.dto.StoryRequest;
import com.cc.whatif.storytellermcp.application.story.dto.StoryResponse;
import com.cc.whatif.storytellermcp.application.story.service.StoryService;
import com.cc.whatif.storytellermcp.domain.story.Chapter;
import com.cc.whatif.storytellermcp.infrastructure.exceptions.StoryException;
import com.cc.whatif.storytellermcp.persistence.story.model.enums.Genre;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.cc.whatif.storytellermcp.infrastructure.AppConstants.STORY_TEMPLATE;
import static com.cc.whatif.storytellermcp.infrastructure.AppConstants.VIRTUE_REVEAL_TEMPLATE;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

@Service
@RequiredArgsConstructor
public class StoryMcp {

    private final StoryService storyService;

    @McpTool(description = "Gets Stories available for telling")
    public List<String> findStories() {
        return storyService.findStories()
                .stream()
                .map(story -> defaultIfBlank(story.getTitle(), "Story[%d]".formatted(story.getId())))
                .toList();
    }

    @McpTool(name = "start_story",
            description = """
                    Starts a new interactive story. Before calling this, ask the user for each 
                    required parameter one at a time, presenting enum options as choices. 
                    Only call once all required fields are gathered, or the user says 'surprise me'. 
                    Rejects if a story is already in progress or awaiting a choice.""")
    public StoryResponse startStory(@McpToolParam(description = "The story parameters gathered from the player") StoryRequest request) {
        var story = storyService.createStory(request);
        return new StoryResponse(
                story.getId(),
                STORY_TEMPLATE.formatted(
                        story.getGenreBlend().stream().map(Genre::name).collect(joining(", ")),
                        story.getSetting(),
                        story.getFlavor(),
                        String.join(", ", story.getHeroes()),
                        story.getVillainDescription(),
                        story.getTone(),
                        story.getLength().getDescription(),
                        story.getVirtue(),
                        story.getVirtueApproach().getDescription(),
                        story.getLength().name()
                ),
                """
                        Generate chapter 1 following the prompt above, 
                        weaving in the hidden virtue per the given approach without ever naming it. 
                        Then call save_chapter with storyId=%s, the generated content, 2-3 choiceOptions, 
                        and isFinalChapter=false.
                        """.formatted(story.getId())
        );
    }

    @McpTool(name = "save_chapter",
            description = """
                    Saves a generated story chapter. Call this after generating chapter content per 
                    the instructions returned by start_story or a previous save_chapter response. 
                    The server computes the chapter number and updates story status: AWAITING_CHOICE 
                    for a non-final chapter — present its choiceOptions to the player as selectable 
                    options, then wait for their pick — or COMPLETE for the final chapter, in which case 
                    present the content and then relay the returned virtueReveal to the player verbatim 
                    as the closing line. Pass isFinalChapter=true only for the story's closing chapter, 
                    with no choiceOptions. Rejects gracefully (storyId not found, story already complete, 
                    or a non-final chapter submitted with no choiceOptions) rather than failing the call.""")
    public ChapterResponse saveChapter(@McpToolParam(description = "The generated chapter to persist") ChapterRequest request) {
        try {
            var story = storyService.saveChapter(request);
            int chapterNumber = story.getChapters().stream()
                    .mapToInt(Chapter::chapterNumber)
                    .max()
                    .orElseThrow();
            var virtueReveal = request.isFinalChapter()
                    ? VIRTUE_REVEAL_TEMPLATE.formatted(story.getVirtue())
                    : null;
            return new ChapterResponse(story.getId(), chapterNumber, story.getStatus(), virtueReveal);
        } catch (StoryException e) {
            return ChapterResponse.reject(e.getMessage());
        }
    }
}
