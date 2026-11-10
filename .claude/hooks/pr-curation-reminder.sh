#!/usr/bin/env bash
# PreToolUse (Bash) hook, advisory, never blocks.
#
# Before `gh pr create`, `gh pr merge` or `gh stack submit`, inject the branch's
# commit list plus the curation rule into Claude's context. PRs here are
# rebase-merged, so every commit on the branch lands on main verbatim; this
# fires at the exact point where the branch must already be curated. See
# docs/development/commit-conventions.md.
#
# Defensive by design: if jq/git are missing, this is not a repo, or there are no
# commits to show, it exits 0 silently.
set -u

command -v jq  >/dev/null 2>&1 || exit 0
command -v git >/dev/null 2>&1 || exit 0

input=$(cat)
cmd=$(printf '%s' "$input" | jq -r '.tool_input.command // ""' 2>/dev/null) || exit 0

# Only act on PR create/merge and stack submit; other subcommands no-op.
printf '%s' "$cmd" \
  | grep -Eq 'gh[[:space:]]+(pr[[:space:]]+(create|merge)|stack[[:space:]]+submit)' || exit 0

base=$(git merge-base origin/main HEAD 2>/dev/null) || exit 0
commits=$(git log --oneline "$base"..HEAD 2>/dev/null) || exit 0
[ -n "$commits" ] || exit 0

reminder="Rebase merge puts ALL of a PR's commits on main verbatim. A PR lands as ONE commit unless its body ticks allow-many-commits; curate before proceeding per docs/development/commit-conventions.md: squash trial-and-error and fixups into the commit they belong to, and put the journey in the PR description, not in history. Verify with \`just commit-lint\` (CI skips it on drafts). In a stack, the list below also holds the lower layers' commits: curate only this layer's own.

Branch commits (${base:0:9}..HEAD):
${commits}"

jq -cn --arg ctx "$reminder" \
  '{hookSpecificOutput: {hookEventName: "PreToolUse", additionalContext: $ctx}}'
exit 0
