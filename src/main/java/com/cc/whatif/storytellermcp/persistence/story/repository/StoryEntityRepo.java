package com.cc.whatif.storytellermcp.persistence.story.repository;

import com.cc.whatif.storytellermcp.persistence.story.model.StoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoryEntityRepo extends JpaRepository<StoryEntity, Long> {

}
