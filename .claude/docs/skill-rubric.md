# Skill rubric — auditlogic/module

One-page checklist every skill in `.claude/skills/` must pass. Rules come from Anthropic's published
guidance as fetched on 2026-11-16; each rule links its source. `R` rules are Anthropic's, `M` rules are
this repo's own (from the 10 Nov skills audit).

Sources: [CC] https://code.claude.com/docs/en/skills ·
[SPEC] https://agentskills.io/specification ·
[BP] https://platform.claude.com/docs/en/agents-and-tools/agent-skills/best-practices ·
[OV] https://platform.claude.com/docs/en/agents-and-tools/agent-skills/overview ·
[PROMPT] https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-prompting-best-practices ·
[FABLE] https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/prompting-claude-fable-5-2 ·
[SC] Anthropic `skill-creator` skill (bundled with Claude, read 2026-11-16)

## What changed versus how this repo's skills are written

The file format is unchanged for Opus 5.5 / Fable 5.2. What changed is how the text is read: Fable 5.2
treats a "never" as absolute and keeps obeying caution written for earlier models, so the old skills'
blanket `MUST` / `NEVER` / `CRITICAL` rules now cost accuracy instead of adding it (rules R9, R17–R19).
Also different from this repo's habits: commands are now skills, the skill listing has a hard character
budget, and only the first 5,000 tokens of a skill survive compaction.

## Frontmatter

| # | Rule | Source |
|---|---|---|
| R1 | `---` is line 1; `name` and `description` are present. Claude Code treats both as optional, the open spec requires them; we require them. | [CC#frontmatter-reference](https://code.claude.com/docs/en/skills#frontmatter-reference), [SPEC#frontmatter](https://agentskills.io/specification#frontmatter) |
| R2 | `name`: 1–64 chars, lowercase letters, digits, single hyphens, equal to the directory name; no `claude` / `anthropic`. | [SPEC#name-field](https://agentskills.io/specification#name-field), [OV#skill-structure](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/overview#skill-structure) |
| R3 | `description`: third person, says what the skill does and when to use it, with the words a user would actually type. Max 1,024 chars. | [BP#writing-effective-descriptions](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/best-practices#writing-effective-descriptions), [SPEC#description-field](https://agentskills.io/specification#description-field) |
| R4 | Put the trigger in the first sentence: `description` + `when_to_use` is cut at 1,536 chars and all listings share a budget of 1% of the context window. Fewer, sharper skills trigger better. | [CC#skill-descriptions-are-cut-short](https://code.claude.com/docs/en/skills#skill-descriptions-are-cut-short) |
| R5 | A skill that only a person should start (publishes, migrates, changes many files) sets `disable-model-invocation: true` and an `argument-hint`. Background knowledge nobody types sets `user-invocable: false`. | [CC#frontmatter-reference](https://code.claude.com/docs/en/skills#frontmatter-reference) |
| R6 | Use only documented fields; unknown ones are ignored silently, so a typo disables the setting without an error. | [CC#frontmatter-reference](https://code.claude.com/docs/en/skills#frontmatter-reference) |

## Body

| # | Rule | Source |
|---|---|---|
| R7 | `SKILL.md` stays under 500 lines and about 5,000 tokens. | [SPEC#progressive-disclosure](https://agentskills.io/specification#progressive-disclosure), [BP#progressive-disclosure-patterns](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/best-practices#progressive-disclosure-patterns) |
| R8 | The instructions that matter most come first: after compaction only the first 5,000 tokens of a skill are re-attached, and the file is not re-read on later turns. | [CC#skill-content-lifecycle](https://code.claude.com/docs/en/skills#skill-content-lifecycle) |
| R9 | Plain imperative wording with the reason for each rule. No all-caps emphasis and no "if in doubt, use X"; caps are a last resort for a rule that tests show is ignored. | [PROMPT#tool-usage](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-prompting-best-practices#tool-usage), [PROMPT#add-context-to-improve-performance](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-prompting-best-practices#add-context-to-improve-performance), [SC] |
| R10 | Say only what Claude does not already know; one term per concept; no dated or time-sensitive statements. | [BP](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/best-practices) |
| R11 | A rule that must hold every time is a `hooks` entry or a script, not a sentence. | [CC#claude-stops-following-a-skill](https://code.claude.com/docs/en/skills#claude-stops-following-a-skill) |

## Wording for the current models

| # | Rule | Source |
|---|---|---|
| R17 | No bare absolutes. Write each hard rule with its scope, its reason and its exception, and prefer the action to take ("ask the owner before deleting") over a ban ("never delete"), because a "never" is followed with no exceptions. | [FABLE#keep-caution-from-piling-up](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/prompting-claude-fable-5-2#keep-caution-from-piling-up), [FABLE#review-instructions-written-for-earlier-models](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/prompting-claude-fable-5-2#review-instructions-written-for-earlier-models) |
| R18 | Remove caution written for earlier models (retry bans, repeated "double check", stop-and-confirm at every step, word-count limits) unless a current failure justifies it; such rules raise cost or cause side effects. | [FABLE#review-instructions-written-for-earlier-models](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/prompting-claude-fable-5-2#review-instructions-written-for-earlier-models) |
| R19 | Do not ask the model to write out its reasoning; ask for the evidence instead (command output, file and line). | [FABLE](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/prompting-claude-fable-5-2) |

## Bundled files

| # | Rule | Source |
|---|---|---|
| R12 | Detail moves to `references/`, runnable checks to `scripts/`, templates to `assets/`. `SKILL.md` says when to read or run each one. | [SPEC#optional-directories](https://agentskills.io/specification#optional-directories), [SC] |
| R13 | References are one level deep from `SKILL.md`; a reference file over 100 lines opens with a table of contents. | [SPEC#file-references](https://agentskills.io/specification#file-references), [BP#structure-longer-reference-files-with-a-table-of-contents](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/best-practices#structure-longer-reference-files-with-a-table-of-contents) |
| R14 | Repeated deterministic work (the gate greps) is one script, and the text says "Run" or "See". Refer to it as `${CLAUDE_SKILL_DIR}/scripts/…`. | [BP#utility-scripts](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/best-practices#utility-scripts), [CC#available-string-substitutions](https://code.claude.com/docs/en/skills#available-string-substitutions) |

## Commands and testing

| # | Rule | Source |
|---|---|---|
| R15 | New workflows are skills, not `.claude/commands/` files (commands were merged into skills and still work; a skill wins a name clash). | [CC](https://code.claude.com/docs/en/skills) |
| R16 | Each kept skill has at least two realistic test prompts, compared with and without the skill in a fresh session, on every model the team uses. | [CC#evaluate-and-iterate-on-a-skill](https://code.claude.com/docs/en/skills#evaluate-and-iterate-on-a-skill), [BP#build-evaluations-first](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/best-practices#build-evaluations-first) |

## Repo rules (audit of 10 Nov)

| # | Rule |
|---|---|
| M1 | Every command, path, version and file name in a skill was run or read in this repo on `dev`; a claim that cannot be checked is removed. |
| M2 | One skill owns each topic. Other skills link to it and never restate it. |
| M3 | Every path a skill, agent or command points at exists. No `.claude/rules/…` (the directory does not exist). |
| M4 | Connector-only steps are marked as such; each skill says what a profile-less module does instead. |
| M5 | No `.env`, no credentials, no personal e-mail addresses in examples. |

## Not documented (checked, not found)

A maximum number of skills; sub-folders that group skills; how a non-directory file such as this one is
treated inside `.claude/skills/`. If this file ever shows up as a skill, move it to `.claude/docs/`.
