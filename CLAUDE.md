# Story Teller MCP — AI-Powered Interactive Story Generator

## What this project is

A Java/Spring Boot MCP server that generates interactive stories for kids (target: age 7). Claude Desktop acts as the conversational interface; this service handles storyEntity logic, prompt management, state, and LLM API calls via the Anthropic API.

Players pick storyEntity parameters (genre, setting, heroes, villain, etc.), the system generates a short "choose your own adventure" storyEntity with a hidden virtue woven into the narrative. Designed for a father-daughter experience.

## Who is building this

Chuka — an engineer learning AI. This is both a family project and a portfolio piece demonstrating MCP protocol, LLM API integration, and prompt management as a first-class service concern.
## Commands

Build tool is Gradle (via wrapper — do not assume a system-wide `gradle` install).

```bash
./gradlew build          # full build (compiles + runs tests)
./gradlew bootRun        # run the app locally (listens on port 8282)
./gradlew test           # run the test suite
./gradlew check          # run all verification tasks
```

Run a single test class or method:

```bash
./gradlew test --tests "com.cc.whatif.storytellermcp.McpserverApplicationTests"
./gradlew test --tests "com.cc.whatif.storytellermcp.McpserverApplicationTests.contextLoads"
```

## Architecture

- Java 25 (via Gradle toolchain), Spring Boot 4.1.0, built with the `webmvc` starter (servlet-based Spring MVC, not WebFlux).
- Lombok is available (`compileOnly`/`annotationProcessor`) — used already in `HelloController` via `@Slf4j`.
- `spring-boot-docker-compose` and `spring-boot-devtools` are on the `developmentOnly` classpath. `compose.yaml` at the repo root currently defines no services (`services: {}`); Spring's docker-compose support will not activate anything until a service is added there.
- Server port is fixed at `8282` in `src/main/resources/application.yaml` (not the Spring Boot default of 8080).
- Package layout under `com.cc.whatif.storytellermcp` is feature-oriented (e.g. `hello/`, `storyEntity/domain/`), not layered by technical concern (controller/service/repository) — follow this per-feature grouping when adding new functionality rather than introducing global `controllers`/`services` packages.

## Delegation principle

**"Delegate the output, own the learning."** Chuka owns MCP server code and Anthropic API integration as deliberate learning tasks. AI should scaffold and guide, not just produce finished code for these areas. Boilerplate, docs, and small well-defined logic (like virtue selection) can be delegated fully.

## Project structure

```
what-if/
├── CLAUDE.md              (this file)
├── storyEntity-engine-spec.md   (full Phase 1 spec — parameter model, chapterEntity structure, virtue system, tone guidelines)
├── phase-one-summary.md   (Phase 1 session summary and testing notes)
└── mcpserver/             (Spring Boot MCP server)
```

## Key design decisions (from Phase 1)

### MCP tools to expose

- `start_story` — takes parameters, picks random virtue, generates Chapter 1
- `make_choice` — player selects a choice, generates next chapterEntity
- `continue_story` — generates next chapterEntity (for cliffhanger-style transitions)

### Domain model

Core classes: Story, Chapter, Choice, Virtue, StorySession, StoryParameters

### Story parameters

- Genre blend: Adventure, Comedy, Thriller, Mystery (multi-select)
- Setting: free text (anything goes)
- Flavor: Magic, Sci-Fi, Superhero, Mix of all (single-select)
- Hero(es): name, species/type, personality trait (free text)
- Villain: custom description or "random" (single-select + optional free text)
- Tone: Lighthearted, Dramatic, Bittersweet (single-select)
- Length: Short (2–3 chapters) or Long (5–10 chapters) (single-select)
- Virtue: random from pool, hidden from players
- Virtue approach: random — "plot-driven" or "moment-based"
- Surprise Me mode: system picks everything

### Virtue pool

Courage, perseverance, fairness, kindness, love, patience, discernment, tolerance, forgiveness, contentment, detachment, humility.

### Chapter structure — "The Pulse"

Each chapterEntity follows: wonder → tension → choice.

- Short stories: ~300–450 words total, ~100–150 words per chapterEntity, 2–3 min read-aloud
- Long stories: ~450–750 words total, short punchy chapters, 3–5 min read-aloud
- Choices: 2–3 selectable options per choice point, never "right vs wrong"

### Story tone

Silly and absurd. Sound effects in bold. Physical comedy. Villains are misunderstood, not evil. Tight writing — every sentence earns its place. PG-appropriate for age 7.

### Canon

- The in-storyEntity character Chuchu is always eating something.

## Phase 2 tasks

| #   | Task                               | Owner                          | Status           |
| --- | ---------------------------------- | ------------------------------ | ---------------- |
| 1   | Research MCP protocol              | Chuka (AI assists)             | Not started      |
| 2   | Design domain model                | Chuka (AI assists)             | Not started      |
| 3   | Design MCP tool schema             | Collaborate                    | Not started      |
| 4   | Set up Spring Boot project         | Chuka (AI adds missing deps)   | Skeleton created |
| 5   | Implement MCP protocol layer       | Chuka (AI assists)             | Not started      |
| 6   | Implement Anthropic API client     | Chuka (AI assists)             | Not started      |
| 7   | Implement prompt management        | Collaborate (Chuka implements) | Not started      |
| 8   | Implement storyEntity state management   | Chuka                          | Not started      |
| 9   | Implement virtue selection logic   | Delegate to AI                 | Not started      |
| 10  | Write tests                        | AI scaffolds, Chuka owns cases | Not started      |
| 11  | End-to-end test via Claude Desktop | Chuka + Eri                    | Not started      |
| 12  | Document architecture              | Delegate to AI, Chuka reviews  | Not started      |

## Tech stack

- Java + Spring Boot (MCP server)
- Anthropic API (LLM calls for storyEntity generation)
- MCP protocol (Claude Desktop ↔ this server)

## Conventions

- Standard Spring Boot project structure
- Prompt templates managed as a first-class service concern (not hardcoded strings)
- Story state managed in-memory for now (persistence is Phase 3)

## Reference

See `storyEntity-engine-spec.md` for the full Phase 1 spec including the benchmark test storyEntity ("The Great Sock Catastrophe") and detailed design rationale.
