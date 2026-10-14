# MCP Server Flow (PlantUML)

The same flow as [MCP server flow](mcp-server-flow.md), drawn with PlantUML.

The server never calls an LLM itself. It owns the story **state** and the **prompts**; Claude Desktop (the MCP client) does the writing. Each tool response hands Claude a generation prompt plus an instruction saying which tool to call next.

## Request flow

```plantuml
@startuml
autonumber
actor Player
participant "Claude Desktop\n(MCP client + LLM)" as Claude
participant "StoryMcp\n(MCP tools)" as Mcp
participant "StoryServiceImpl" as Service
participant "StoryRepoImpl / JPA" as Repo
database "PostgreSQL" as Db

Player -> Claude : "Let's make a story!"
Claude -> Player : Ask for genre, setting, flavor, heroes,\nvillain, tone, length (one at a time)
Player -> Claude : Answers (or "surprise me")

Claude -> Mcp : start_story(StoryRequest)
Mcp -> Service : createStory(request)
note over Service
  Apply defaults (villain RANDOM, length SHORT)
  Pick random Virtue + VirtueApproach (hidden)
end note
Service -> Repo : save(story)
Repo -> Db : INSERT story (status IN_PROGRESS)
Repo --> Service : Story with id
Service --> Mcp : Story
Mcp --> Claude : StoryResponse(storyId, generationPrompt\nfrom STORY_TEMPLATE, instructions)

loop Each chapter
  note over Claude
    Write the chapter from the generationPrompt,
    weaving in the virtue without naming it
  end note
  Claude -> Mcp : save_chapter(storyId, content, choiceOptions, isFinalChapter)
  Mcp -> Service : saveChapter(request)
  Service -> Repo : findById(storyId)
  note over Service
    Reject if COMPLETE, or non-final with no choices.
    chapterNumber = chapters.size() + 1
  end note
  Service -> Repo : save(story)
  Repo -> Db : UPDATE story + chapters

  alt Non-final chapter
    Mcp --> Claude : ChapterResponse(chapterNumber, AWAITING_CHOICE)
    Claude -> Player : Read chapter aloud + show 2–3 choices
    Player -> Claude : Picks an option
    Claude -> Mcp : make_choice(storyId, chapterNumber, selectedOption)
    Mcp -> Service : makeChoice(choice)
    note over Service
      Reject if not AWAITING_CHOICE, chapter missing,
      already answered, or option out of range
    end note
    Service -> Repo : save(story) — status IN_PROGRESS
    Mcp --> Claude : MakeChoiceResponse(nextChapterNumber,\ngenerationPrompt from NEXT_CHAPTER_TEMPLATE, instruction)
  else Final chapter
    Mcp --> Claude : ChapterResponse(chapterNumber, COMPLETE,\nvirtueReveal from VIRTUE_REVEAL_TEMPLATE)
    Claude -> Player : Read final chapter + reveal the hidden virtue
  end
end
@enduml
```

Rejections (`StoryException`) are returned as a normal response with `rejected: true` and a `message`, not as protocol errors, so Claude can explain the problem to the player and recover.

## Story lifecycle

```plantuml
@startuml
[*] --> IN_PROGRESS : start_story
IN_PROGRESS --> AWAITING_CHOICE : save_chapter\n(isFinalChapter = false)
AWAITING_CHOICE --> IN_PROGRESS : make_choice
IN_PROGRESS --> COMPLETE : save_chapter\n(isFinalChapter = true)
COMPLETE --> [*]
@enduml
```
