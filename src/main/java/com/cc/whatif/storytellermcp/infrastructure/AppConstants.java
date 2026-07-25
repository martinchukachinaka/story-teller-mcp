package com.cc.whatif.storytellermcp.infrastructure;

public class AppConstants {

    private static final String STYLE_GUIDE = """
            Style:
            - Comedy first: playful narration, silly situations, a joke or gag beat in most paragraphs. Even when Tone leans dramatic or bittersweet, keep any sad or tense moment brief and gentle — never scary, never lingering.
            - Bold sound effects for physical comedy (e.g. **FLAP FLAP FLAP**)
            - Give ONE supporting character a funny quirk invented fresh for this story — a strange catchphrase, an odd walk, talking to their shoes, terrible singing, whatever fits the story. Vary it every time; don't default to "always eating something" just because that's a common example. Use it once or twice, don't overdo it.
            - Villain is misunderstood/lonely, and only if necessary, truly evil
            - PG-appropriate, suitable for ages 6-9
            """;

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

            """ + STYLE_GUIDE + """

            Structure: This is chapter 1 of a %s story. Follow "the pulse": wonder (introduce the world) → tension (introduce the problem) → choice.
            End with 2-3 selectable choice options — never free text, never a "wrong" choice.
            """;

    public static final String VIRTUE_REVEAL_TEMPLATE =
            "The virtue hidden in this story was **%s**. Did you spot it?";

    public static final String NEXT_CHAPTER_TEMPLATE = """
            The player chose: "%s"

            Continue the story from that choice.

            Hidden virtue (never name it explicitly): %s
            Virtue approach: %s

            """ + STYLE_GUIDE + """

            Keep "the pulse": wonder → tension → choice — unless this is the story's final chapter, in which case land the resolution and let the virtue click into place, with no further choice options.
            """;
}
