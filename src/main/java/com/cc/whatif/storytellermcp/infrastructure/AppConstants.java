package com.cc.whatif.storytellermcp.infrastructure;

public class AppConstants {

    public static final String STORY_TEMPLATE = """
            Write Chapter 1 of a short interactive children's story for a 7-year-old.
            
            Genre: %s
            Setting: %s
            Flavor: %s
            Heroes: %s
            Villain: %s
            Tone: %s
            Length: %s
            
            Hidden virtue (never name it explicitly): %s
            Virtue approach: %s — the hero's core challenge should require practicing this virtue
            
            Style:
            - Silly and absurd, bold sound effects (e.g. **FLAP FLAP FLAP**)
            - Physical comedy and dry humor, e.g. a character is always eating something — work this in naturally
            - Villain is misunderstood/lonely, not truly evil
            - Tight writing, no filler, PG-appropriate
            
            Structure: This is chapter 1 of a %s story. Follow "the pulse": wonder (introduce the world) → tension (introduce the problem) → choice.
            End with 2-3 selectable choice options — never free text, never a "wrong" choice.
            """;

    public static final String VIRTUE_REVEAL_TEMPLATE =
            "The virtue hidden in this story was **%s**. Did you spot it?";
}
