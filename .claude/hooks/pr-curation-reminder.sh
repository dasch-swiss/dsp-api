#!/usr/bin/env bash
# PreToolUse (Bash) hook, advisory, never blocks.
#
# Before `gh pr create`, `gh pr merge` or `gh stack submit`, run the commit
# conventions check (`just commit-lint`) over the branch and inject its result,
# the commit list and the curation rule into Claude's context. PRs here are
# rebase-merged, so every commit on the branch lands on main verbatim; this
# fires at the exact point where the branch must already be curated. See
# docs/development/commit-conventions.md.
#
# The base is the `--base`/`-B` of `gh pr create` when given (the layer below,
# in a stack), otherwise origin/main.
#
# Defensive by design: if jq/git are missing, this is not a repo, or there are no
# commits to show, it exits 0 silently. Without `just` or `commitlint` (outside
# the Nix dev shell) it says the check did not run instead of failing.
set -u

command -v jq  >/dev/null 2>&1 || exit 0
command -v git >/dev/null 2>&1 || exit 0

input=$(cat)
cmd=$(printf '%s' "$input" | jq -r '.tool_input.command // ""' 2>/dev/null) || exit 0

# Only act on PR create/merge and stack submit; other subcommands no-op.
printf '%s' "$cmd" \
  | grep -Eq 'gh[[:space:]]+(pr[[:space:]]+(create|merge)|stack[[:space:]]+submit)' || exit 0

base_ref=origin/main
named_base=$(printf '%s' "$cmd" \
  | sed -nE 's/.*(--base[=[:space:]]+|-B[[:space:]]+)([^[:space:]"'\'']+).*/\2/p' | head -n1)
if [ -n "$named_base" ] && git rev-parse --verify -q "origin/$named_base" >/dev/null 2>&1; then
  base_ref="origin/$named_base"
fi

base=$(git merge-base "$base_ref" HEAD 2>/dev/null) || exit 0
commits=$(git log --oneline "$base"..HEAD 2>/dev/null) || exit 0
[ -n "$commits" ] || exit 0

if command -v just >/dev/null 2>&1 && command -v commitlint >/dev/null 2>&1; then
  if lint=$(just commit-lint "$base_ref" 2>&1); then
    verdict="\`just commit-lint $base_ref\` passed:"
  else
    verdict="\`just commit-lint $base_ref\` FAILED. Fix the history before proceeding:"
  fi
else
  verdict="\`just commit-lint\` did not run here (needs \`just\` and \`commitlint\` from the Nix dev shell); run it before proceeding."
  lint=""
fi

reminder="Rebase merge puts ALL of a PR's commits on main verbatim. Curate before proceeding per docs/development/commit-conventions.md: one commit per scope per slice, each body written for the agent that reads it later; fold fixups and in-branch bug fixes into the commit they belong to; clean the comments the branch adds or touches; put the journey in the PR description, not in history. Against origin/main, a stack layer's check and list below also hold the lower layers' commits: curate only this layer's own.

${verdict}
${lint}

Branch commits (${base:0:9}..HEAD, against ${base_ref}):
${commits}"

jq -cn --arg ctx "$reminder" \
  '{hookSpecificOutput: {hookEventName: "PreToolUse", additionalContext: $ctx}}'
exit 0
