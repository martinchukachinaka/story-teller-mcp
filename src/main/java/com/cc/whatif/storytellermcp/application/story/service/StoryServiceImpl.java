package com.cc.whatif.storytellermcp.application.story.service;

import com.cc.whatif.storytellermcp.application.story.repo.StoryRepo;
import com.cc.whatif.storytellermcp.domain.story.Story;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StoryServiceImpl implements StoryService {

	private final StoryRepo storyRepo;

	@Override
	public Story findStoryById(Long id) {
		return storyRepo.findById(id);
	}

	@Override
	public Story saveStory(Story story) {
		return storyRepo.save(story);
	}

	@Override
	public void deleteStory(Long id) {
		storyRepo.delete(Story.builder().id(id).build());
	}

	@Override
	public List<Story> findStories() {
		return storyRepo.findStories();
	}
}
