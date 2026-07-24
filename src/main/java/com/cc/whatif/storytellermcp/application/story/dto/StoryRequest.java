package com.cc.whatif.storytellermcp.application.story.dto;

import com.cc.whatif.storytellermcp.persistence.story.model.enums.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record StoryRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("Genre blend — can combine multiple")
        List<Genre> genreBlend,
        @JsonProperty(required = true)
        @JsonPropertyDescription("Story title — make up one based off the other params provided")
        String title,
        @JsonProperty(required = true)
        @JsonPropertyDescription("Free-text setting the player describes")
        String setting,
        @JsonProperty(required = true)
        @JsonPropertyDescription("The world's power system")
        Flavor flavor,
        @JsonProperty(required = true)
        @JsonPropertyDescription("Hero descriptions, one entry per hero")
        List<String> heroes,
        @JsonProperty(defaultValue = "RANDOM")
        @JsonPropertyDescription("CUSTOM if the player describes a villain, RANDOM to invent one")
        VillainMode villainMode,
        @JsonPropertyDescription("Villain description; only used when villainMode is CUSTOM")
        String villainDescription,
        @JsonProperty(required = true)
        @JsonPropertyDescription("Story tone")
        Tone tone,
        @JsonProperty(defaultValue = "SHORT")
        @JsonPropertyDescription("SHORT (2-3 chapters) or LONG (5-10 chapters)")
        StoryLength length
) {
}
