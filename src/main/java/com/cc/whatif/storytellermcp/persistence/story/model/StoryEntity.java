package com.cc.whatif.storytellermcp.persistence.story.model;

import com.cc.whatif.storytellermcp.domain.story.Story;
import com.cc.whatif.storytellermcp.persistence.story.model.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

@Entity
@Table(name = "stories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryEntity {

	public StoryEntity(Story story) {
		setId(story.getId());
		setTitle(story.getTitle());
		setGenreBlend(story.getGenreBlend());
		setSetting(story.getSetting());
		setFlavor(story.getFlavor());
		setHeroes(story.getHeroes());
		setVillainMode(story.getVillainMode());
		setVillainDescription(story.getVillainDescription());
		setTone(story.getTone());
		setLength(story.getLength());
		setVirtue(story.getVirtue());
		setVirtueApproach(story.getVirtueApproach());
		setStatus(story.getStatus());
		setChapterEntities(story.getChapters().stream()
				.map(chapter -> new ChapterEntity(chapter, this))
				.collect(toSet()));
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String title;

	@ElementCollection
	@CollectionTable(name = "story_genre_blend", joinColumns = @JoinColumn(name = "story_id"))
	@OrderColumn(name = "list_index")
	@Column(name = "genre", nullable = false)
	@Builder.Default
	private List<String> genreBlend = new ArrayList<>();

	@Column(columnDefinition = "TEXT")
	private String setting;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Flavor flavor;

	@ElementCollection
	@CollectionTable(name = "story_heroes", joinColumns = @JoinColumn(name = "story_id"))
	@OrderColumn(name = "list_index")
	@Column(name = "hero", nullable = false)
	@Builder.Default
	private List<String> heroes = new ArrayList<>();

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private VillainMode villainMode;

	@Column(columnDefinition = "TEXT")
	private String villainDescription;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Tone tone;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StoryLength length;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Virtue virtue;

	@Enumerated(EnumType.STRING)
	@Column(name = "virtue_approach", nullable = false)
	private VirtueApproach virtueApproach;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StoryStatus status;

	@OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private Set<ChapterEntity> chapterEntities = new HashSet<>();

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void beforeFirstSave() {
		createdAt = Instant.now();
	}

	public void addChapter(ChapterEntity chapterEntity) {
		chapterEntities.add(chapterEntity);
		chapterEntity.setStory(this);
	}

	public void removeChapter(ChapterEntity chapterEntity) {
		chapterEntities.remove(chapterEntity);
		chapterEntity.setStory(null);
	}

	public Story toStory() {
		return Story.builder()
				.id(id)
				.title(title)
				.genreBlend(genreBlend)
				.setting(setting)
				.flavor(flavor)
				.heroes(heroes)
				.villainMode(villainMode)
				.villainDescription(villainDescription)
				.tone(tone)
				.length(length)
				.virtue(virtue)
				.virtueApproach(virtueApproach)
				.status(status)
				.chapters(chapterEntities.stream().map(ChapterEntity::toChapter).collect(toSet()))
				.build();
	}
}
