# MCP Server Flow

How a story moves between the player, Claude Desktop and the Story Teller MCP server.

The server never calls an LLM itself. It owns the story **state** and the **prompts**; Claude (the MCP client)
does the writing. Each tool response hands Claude a generation prompt plus an instruction saying which tool to call
next, so the conversation is steered by the server.

## Components

| Layer          | Class                               | Role                                                                                  |
|----------------|-------------------------------------|---------------------------------------------------------------------------------------|
| Presentation   | `StoryMcp`                          | Exposes the MCP tools (`@McpTool`) over streamable HTTP on port `8282`                |
| Application    | `StoryServiceImpl`                  | Picks the hidden virtue, validates transitions, updates `StoryStatus`                 |
| Infrastructure | `AppConstants`                      | Prompt templates: `STORY_TEMPLATE`, `NEXT_CHAPTER_TEMPLATE`, `VIRTUE_REVEAL_TEMPLATE` |
| Persistence    | `StoryRepoImpl` → `StoryEntityRepo` | Maps `Story` ↔ `StoryEntity` and stores it via JPA (PostgreSQL)                       |

## Request flow

```mermaid
sequenceDiagram
    autonumber
    actor Player
    participant Claude as Claude Desktop<br/>(MCP client + LLM)
    participant Mcp as StoryMcp<br/>(MCP tools)
    participant Service as StoryServiceImpl
    participant Repo as StoryRepoImpl / JPA
    participant Db as PostgreSQL
    Player ->> Claude: "Let's make a story!"
    Claude ->> Player: Ask for genre, setting, flavor, heroes,<br/>villain, tone, length (one at a time)
    Player ->> Claude: Answers (or "surprise me")
    Claude ->> Mcp: start_story(StoryRequest)
    Mcp ->> Service: createStory(request)
    Note over Service: Apply defaults (villain RANDOM, length SHORT)<br/>Pick random Virtue + VirtueApproach (hidden)
    Service ->> Repo: save(story)
    Repo ->> Db: INSERT story (status IN_PROGRESS)
    Repo -->> Service: Story with id
    Service -->> Mcp: Story
    Mcp -->> Claude: StoryResponse(storyId, generationPrompt<br/>from STORY_TEMPLATE, instructions)

    loop Each chapter
        Note over Claude: Write the chapter from the generationPrompt,<br/>weaving in the virtue without naming it
        Claude ->> Mcp: save_chapter(storyId, content, choiceOptions, isFinalChapter)
        Mcp ->> Service: saveChapter(request)
        Service ->> Repo: findById(storyId)
        Note over Service: Reject if COMPLETE, or non-final with no choices.<br/>chapterNumber = chapters.size() + 1
        Service ->> Repo: save(story)
        Repo ->> Db: UPDATE story + chapters

        alt Non-final chapter
            Mcp -->> Claude: ChapterResponse(chapterNumber, AWAITING_CHOICE)
            Claude ->> Player: Read chapter aloud + show 2–3 choices
            Player ->> Claude: Picks an option
            Claude ->> Mcp: make_choice(storyId, chapterNumber, selectedOption)
            Mcp ->> Service: makeChoice(choice)
            Note over Service: Reject if not AWAITING_CHOICE, chapter missing,<br/>already answered, or option out of range
            Service ->> Repo: save(story) — status IN_PROGRESS
            Mcp -->> Claude: MakeChoiceResponse(nextChapterNumber,<br/>generationPrompt from NEXT_CHAPTER_TEMPLATE, instruction)
        else Final chapter
            Mcp -->> Claude: ChapterResponse(chapterNumber, COMPLETE,<br/>virtueReveal from VIRTUE_REVEAL_TEMPLATE)
            Claude ->> Player: Read final chapter + reveal the hidden virtue
        end
    end
```

Rejections (`StoryException`) are returned as a normal response with `rejected: true` and a `message`, not as protocol
errors, so Claude can explain the problem to the player and recover.

## Story lifecycle

```mermaid
stateDiagram-v2
    [*] --> IN_PROGRESS: start_story
    IN_PROGRESS --> AWAITING_CHOICE: save_chapter (isFinalChapter = false)
    AWAITING_CHOICE --> IN_PROGRESS: make_choice
    IN_PROGRESS --> COMPLETE: save_chapter (isFinalChapter = true)
    COMPLETE --> [*]
```

## Tools at a glance

| Tool           | Called when                          | Returns                                                          |
|----------------|--------------------------------------|------------------------------------------------------------------|
| `findStories`  | Player wants to see existing stories | Story titles (or `Story[id]`)                                    |
| `start_story`  | All parameters gathered              | `storyId`, chapter 1 prompt, next-step instructions              |
| `save_chapter` | Claude has written a chapter         | `chapterNumber`, new status, `virtueReveal` on the final chapter |
| `make_choice`  | Player picked an option              | `nextChapterNumber`, next chapter prompt, next-step instruction  |
