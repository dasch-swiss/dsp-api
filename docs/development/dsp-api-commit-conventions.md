# Commit and PR Conventions

## Commit Organization

### Principle

Group commits by user-visible impact, not by implementation journey.

### Rules

1. Each `feat:` or `fix:` commit = one changelog entry under
   Enhancements or Bug Fixes — the sections developers deploying
   dsp-api actually read
2. Internal work (`build:`, `refactor:`, `chore:`, `test:`, `docs:`)
   still appears in the changelog, but grouped into low-visibility
   sections (Maintenances, Tests, Documentation — see `CONVENTIONS.md`
   § Commit Conventions for the full mapping). Squash aggressively so
   each one is still a meaningful standalone line, not implementation
   noise
3. Ask: "would a developer deploying dsp-api care about this change?"
   If yes → `feat:` or `fix:`. If no → an internal type
4. A debugging journey (trial-and-error, reverts, iterative fixes) belongs in a learning under `dasch-specs/learnings`, not in the commit history and not in the PR description: this repo squash-merges, so the PR body becomes the commit message

For the prefix → changelog mapping and scope convention, see
`CONVENTIONS.md` § Commit Conventions.

### Where context lives

| Layer           | Audience                      | Content                                                          |
|-----------------|-------------------------------|------------------------------------------------------------------|
| Commit messages | Release notes readers         | User-visible changes only                                        |
| PR description  | Reviewers + future developers | The code and the diff                                            |
| Learnings docs  | Future engineers + Claude     | What was tried and why it failed, structured and searchable      |
| Code comments   | Code readers                  | Invariants a reader must not break (`.claude/rules/comments.md`) |

## PR Description Format

### Template

```text
Fixes LINEAR-ID, LINEAR-ID, ...

## Motivation
Why this work was needed. What problem it solves for users.

## Summary
1-3 bullet points of user-visible changes.

## Key Changes
### [Topic]
- change details

## Gotchas
Things future developers should know. Each gotcha should be
actionable — not just "this is hard" but "do X instead of Y".

## Test Plan
- [ ] verification steps
```

### Why this format matters

The body describes the code and the diff, so a reviewer reads the change rather than the branch's history. dsp-api squash-merges, so whatever the body carries becomes the commit message for good.

The journey that produced the change goes to a learning instead. `/eng:workflows:compound` writes learnings for any dasch-swiss repo to `dasch-specs/learnings/`, where the `learnings-researcher` agent reads them during planning; it works from the session and treats a PR body as optional context, so keeping the journey out of the PR costs it nothing.

### What goes where

| Information                        | Put it in...                          |
|------------------------------------|---------------------------------------|
| New feature / breaking change      | Commit message (`feat:` / `feat!:`)   |
| Bug fix                            | Commit message (`fix:`)               |
| Build/CI/refactor details          | Commit message (hidden type)          |
| Why the work was needed            | PR Motivation section                 |
| What was tried and failed          | Learning (dasch-specs/learnings)      |
| Architecture decisions + rationale | ADR (`docs/05-internals/design/adr/`) |
| Things to watch out for            | PR Gotchas section                    |
| Structured, searchable knowledge   | Learnings doc (dasch-specs/learnings) |
