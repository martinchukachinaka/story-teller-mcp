package com.cc.whatif.storytellermcp.story.domain;

import com.cc.whatif.storytellermcp.persistence.story.model.ChapterEntity;
import com.cc.whatif.storytellermcp.persistence.story.model.StoryEntity;
import com.cc.whatif.storytellermcp.persistence.story.model.enums.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class StoryEntityPersistenceTest {

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void persistsAndReloadsStoryWithChaptersAndCollections() {
		StoryEntity storyEntity = StoryEntity.builder()
				.genreBlend(List.of("Adventure", "Comedy"))
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
		storyEntity.addChapter(ChapterEntity.builder().chapterNumber(1).content("Once upon a sock...").build());

		StoryEntity saved = entityManager.persistFlushFind(storyEntity);
		entityManager.clear();

		StoryEntity reloaded = entityManager.find(StoryEntity.class, saved.getId());
		assertThat(reloaded.getGenreBlend()).containsExactly("Adventure", "Comedy");
		assertThat(reloaded.getHeroes()).containsExactly("Eri", "Chuchu");
		assertThat(reloaded.getChapterEntities()).hasSize(1);
		assertThat(reloaded.getCreatedAt()).isNotNull();
	}
}
