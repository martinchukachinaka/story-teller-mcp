package com.cc.whatif.storytellermcp.presentation;

import com.cc.whatif.storytellermcp.application.story.service.StoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

@Service
@RequiredArgsConstructor
public class StoryMcp {

	private final StoryService storyService;

	@McpTool(description = "Get current temperature for a location")
	public String getTemperature(@McpToolParam(description = "City name", required = true) String city) {
		return String.format("Current temperature in %s: 22°C", city);
	}

	@McpTool(description = "Gets Stories available for telling")
	public List<String> findStories() {
		return storyService.findStories()
				.stream()
				.map(story -> defaultIfBlank(story.getTitle(), "Story[%d]".formatted(story.getId())))
				.toList();
	}
}
