package com.cc.whatif.storytellermcp.application.story.service;


import com.cc.whatif.storytellermcp.application.story.dto.ChapterRequest;
import com.cc.whatif.storytellermcp.application.story.dto.MakeChoiceRequest;
import com.cc.whatif.storytellermcp.application.story.dto.StoryRequest;
import com.cc.whatif.storytellermcp.domain.story.Story;

import java.util.List;

public interface StoryService {

    Story findStoryById(Long id);

    Story createStory(StoryRequest storyRequest);

    Story saveStory(Story story);

    void deleteStory(Long id);

    List<Story> findStories();

    Story saveChapter(ChapterRequest request);

    Story makeChoice(MakeChoiceRequest choice);
}
