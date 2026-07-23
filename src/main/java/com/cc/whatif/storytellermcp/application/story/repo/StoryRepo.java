package com.cc.whatif.storytellermcp.application.story.repo;

import com.cc.whatif.storytellermcp.domain.story.Story;

import java.util.List;

public interface StoryRepo {
	Story findById(long id);

	Story save(Story story);

	void delete(Story story);

	List<Story> findStories();
}
