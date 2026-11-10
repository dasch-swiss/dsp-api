Fixes DEV-

<!--
This repo rebase-merges: every commit on the branch lands on main verbatim, and there is no squash
safety net. Curate the branch before merge. A PR lands as ONE commit unless the many-commits box
below is ticked. Every commit is `type(scope): subject`. Check with `just commit-lint`.
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

## Test Plan

- [ ] verification steps

## Commit hygiene

- [ ] I have reviewed and cleaned up the branch history (working commits squashed, clear messages)
      so it reads well on `main`
- [ ] allow-many-commits — tick only if this PR is genuinely several independent, self-contained
      changes that each deserve their own line in `main`'s history

## Review guide

<!-- Only when the many-commits box is ticked; delete otherwise. One sub-section per commit,
     in history order. -->

### `type(scope): subject`

- **Review:** <!-- the files and seams to look at -->
- **Verified:** <!-- what tests / checks cover it -->
- **Decision:** <!-- what was settled here and why -->
