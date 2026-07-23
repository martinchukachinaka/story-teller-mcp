package com.cc.whatif.storytellermcp.presentation;

import com.cc.whatif.storytellermcp.application.story.service.StoryService;
import com.cc.whatif.storytellermcp.domain.story.Story;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("stories")
@RequiredArgsConstructor
public class StoryController {

	private final StoryService storyService;

	@GetMapping
	public List<Story> getStories() {
		return storyService.findStories();
	}

	@PostMapping
	public Story createStory(@RequestBody Story story) {
		return storyService.saveStory(story);
	}
}
