package com.cc.whatif.storytellermcp.presentation;

import com.cc.whatif.storytellermcp.application.story.dto.StoryRequest;
import com.cc.whatif.storytellermcp.application.story.dto.StoryResponse;
import com.cc.whatif.storytellermcp.application.story.service.StoryService;
import com.cc.whatif.storytellermcp.persistence.story.model.enums.Genre;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.cc.whatif.storytellermcp.infrastructure.AppConstants.STORY_TEMPLATE;
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
}
