package com.cc.whatif.storytellermcp.persistence.story.repository;

import com.cc.whatif.storytellermcp.application.story.repo.StoryRepo;
import com.cc.whatif.storytellermcp.domain.story.Story;
import com.cc.whatif.storytellermcp.persistence.story.model.StoryEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StoryRepoImpl implements StoryRepo {

	private final StoryEntityRepo storyEntityRepo;

	@Override
	public Story findById(long id) {
		return storyEntityRepo.findById(id)
				.map(StoryEntity::toStory)
				.orElseThrow(() -> new IllegalStateException("Story[%s] not found".formatted(id)));
	}

	@Override
	public Story save(Story story) {
		return storyEntityRepo.save(new StoryEntity(story)).toStory();
	}

	@Override
	public void delete(Story story) {
		storyEntityRepo.deleteById(story.getId());
	}

	@Override
	public List<Story> findStories() {
		return storyEntityRepo.findAll()
				.stream()
				.map(StoryEntity::toStory
				).toList();
	}
}
