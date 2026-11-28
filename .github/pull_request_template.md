Fixes DEV-

<!--
This repo rebase-merges: every commit on the branch lands on main verbatim, and there is no squash
safety net. Curate the branch before merge: one commit per scope per slice, at most 100. Every commit
is `type(scope): subject`. Check with `just commit-lint`.
Conventions: docs/development/commit-conventions.md
-->

## Motivation

<!-- Why this work was needed. What problem it solves for users. -->

## Summary

<!-- 1-3 bullet points of user-visible changes. -->

## Key Changes

### [Topic]

-

## Challenges and Decisions

<!-- What was tried, what failed, and key architecture decisions. Record hard-to-reverse
     ones as an ADR in docs/adr/. Delete this section if there were none. -->

### [Challenge title]

**Problem:**
**Tried:**
**Solution:**

## Gotchas

<!-- Actionable: "do X instead of Y", not "this is hard". -->

## Learnings

<!-- The learnings written for this work, or why there are none. -->

## Test Plan

<!-- Say how each line of "Before a merge" in docs/development/commit-conventions.md was met,
     or why it does not apply. -->

- [ ] verification steps

## Commit hygiene

- [ ] I have curated the branch history (fixups folded, bodies written for the agent that reads
      them, comments cleaned) so it reads well on `main`

## Review guide

<!-- Multi-phase PRs only; delete otherwise. One section per phase, in history order. -->

### Phase N: [name]

- **Review:** <!-- the files and seams to look at -->
- **Verified:** <!-- what the tests and the reviewers covered -->
- **Decision:** <!-- what was settled here and why -->
