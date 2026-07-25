package com.cc.whatif.storytellermcp.application.story.service;

import com.cc.whatif.storytellermcp.application.story.dto.ChapterRequest;
import com.cc.whatif.storytellermcp.application.story.dto.MakeChoiceRequest;
import com.cc.whatif.storytellermcp.application.story.dto.StoryRequest;
import com.cc.whatif.storytellermcp.application.story.repo.StoryRepo;
import com.cc.whatif.storytellermcp.domain.story.Chapter;
import com.cc.whatif.storytellermcp.domain.story.Story;
import com.cc.whatif.storytellermcp.infrastructure.exceptions.StoryException;
import com.cc.whatif.storytellermcp.persistence.story.model.enums.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.springframework.util.CollectionUtils.isEmpty;

@Service
@RequiredArgsConstructor
public class StoryServiceImpl implements StoryService {

    private final StoryRepo storyRepo;

    private static final Virtue[] VIRTUES = Virtue.values();

    private static final VirtueApproach[] VIRTUE_APPROACHES = VirtueApproach.values();

    @Override
    public Story findStoryById(Long id) {
        return storyRepo.findById(id);
    }

    @Override
    public Story createStory(StoryRequest request) {
        var villainMode = request.villainMode() != null ? request.villainMode() : VillainMode.RANDOM;
        var villainDescription = defaultIfBlank(request.villainDescription(),
                "Invent one that fits the genre and setting — misunderstood or lonely, not truly evil.");
        var length = request.length() != null ? request.length() : StoryLength.SHORT;

        return storyRepo.save(Story.builder()
                .title(request.title())
                .genreBlend(request.genreBlend())
                .setting(request.setting())
                .flavor(request.flavor())
                .heroes(request.heroes())
                .tone(request.tone())
                .villainMode(villainMode)
                .villainDescription(villainDescription)
                .length(length)
                .virtue(getRandom(VIRTUES))
                .virtueApproach(getRandom(VIRTUE_APPROACHES))
                .build());
    }

    private <T> T getRandom(T[] array) {
        int randomIndex = ThreadLocalRandom.current().nextInt(array.length);
        return array[randomIndex];
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

    @Override
    public Story saveChapter(ChapterRequest request) {
        Story story = storyRepo.findById(request.storyId());
        if (story.getStatus() == StoryStatus.COMPLETE) {
            throw new StoryException("Story[%s] is already complete".formatted(request.storyId()));
        }

        var choiceOptions = request.isFinalChapter() ? List.<String>of() : request.choiceOptions();
        if (!request.isFinalChapter() && isEmpty(choiceOptions)) {
            throw new StoryException(
                    "Non-final chapters require 2-3 choiceOptions; got none for story[%s]".formatted(request.storyId()));
        }

        int chapterNumber = story.getChapters().size() + 1;
        story.addChapter(new Chapter(chapterNumber, request.content(), choiceOptions, request.isFinalChapter()));
        story.setStatus(request.isFinalChapter() ? StoryStatus.COMPLETE : StoryStatus.AWAITING_CHOICE);

        return storyRepo.save(story);
    }

    @Override
    public Story makeChoice(MakeChoiceRequest choice) {
        Story story = storyRepo.findById(choice.storyId());
        if (story.getStatus() != StoryStatus.AWAITING_CHOICE) {
            throw new StoryException(
                    "Story isn't awaiting a choice — story[%s] is %s".formatted(choice.storyId(), story.getStatus()));
        }

        Chapter chapter = story.getChapters().stream()
                .filter(c -> c.chapterNumber() == choice.chapterNumber())
                .findFirst()
                .orElseThrow(() -> new StoryException(
                        "Selected Chapter does not exist — story[%s], chapter[%s]"
                                .formatted(choice.storyId(), choice.chapterNumber())));

        if (chapter.selectedOption() != null) {
            throw new StoryException(
                    "Choice has already been made — story[%s], chapter[%s]"
                            .formatted(choice.storyId(), choice.chapterNumber()));
        }

        if (choice.selectedOption() < 0 || choice.selectedOption() >= chapter.choiceOptions().size()) {
            throw new StoryException(
                    "Impossible Choice my friend — story[%s], chapter[%s], selectedOption=%s, availableOptions=%s"
                            .formatted(choice.storyId(), choice.chapterNumber(), choice.selectedOption(),
                                    chapter.choiceOptions().size()));
        }

        story.removeChapter(chapter);
        story.addChapter(new Chapter(
                chapter.id(),
                chapter.chapterNumber(),
                chapter.content(),
                chapter.choiceOptions(),
                choice.selectedOption(),
                chapter.isFinalChapter()));
        story.setStatus(StoryStatus.IN_PROGRESS);

        return storyRepo.save(story);
    }
}
