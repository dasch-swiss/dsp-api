#!/usr/bin/env bash
# PreToolUse (Bash) hook.
#
# Before `gh pr create`, `gh pr merge` or `gh stack submit`, run `just commit-lint`
# on the branch and inject its result, the branch's commits and the curation rule
# into Claude's context. PRs here are rebase-merged, so every commit on the
# branch lands on main verbatim; this fires at the point where the branch must
# already be curated. See docs/development/commit-conventions.md.
#
# The base is the `--base`/`-B` of `gh pr create` when given, else the base of
# the checked-out branch's PR (via `gh`), else origin/main. In a stack that keeps
# a layer's check to its own commits.
#
# Only `gh pr merge` of the checked-out branch is refused when the check fails.
# Before create and submit it only reports, because a draft may still hold
# working commits. A merge of another PR is not checked here: the hook sees only
# the working tree, and the required `Commit hygiene` check covers that PR.
#
# Defensive by design: if jq/git are missing, this is not a repo, or there are no
# commits to show, it exits 0 silently. Without `just` or `commitlint` it reports
# that it did not check and never refuses.
set -u

command -v jq  >/dev/null 2>&1 || exit 0
command -v git >/dev/null 2>&1 || exit 0

# Network calls get a 10 s limit where `timeout` exists (macOS has none by default).
with_timeout() {
  if command -v timeout >/dev/null 2>&1; then timeout 10 "$@"; else "$@"; fi
}

input=$(cat)
cmd=$(printf '%s' "$input" | jq -r '.tool_input.command // ""' 2>/dev/null) || exit 0

action=$(printf '%s' "$cmd" \
  | grep -oE 'gh[[:space:]]+(pr[[:space:]]+(create|merge)|stack[[:space:]]+submit)' \
  | head -n1 | sed -E 's/[[:space:]]+/ /g') || exit 0
[ -n "$action" ] || exit 0

base_ref=origin/main
named_base=$(printf '%s' "$cmd" \
  | sed -nE 's/.*(--base[=[:space:]]+|-B[[:space:]]+)([^[:space:]"'\'';&|]+).*/\2/p' | head -n1)
if [ -z "$named_base" ] && command -v gh >/dev/null 2>&1; then
  named_base=$(with_timeout gh pr view --json baseRefName --jq .baseRefName 2>/dev/null) || named_base=""
fi
if [ -n "$named_base" ] && git rev-parse --verify -q "origin/$named_base" >/dev/null 2>&1; then
  base_ref="origin/$named_base"
fi

# A stale local base ref would put the base's own newer commits into the range
# and fail the check for commits that are not the branch's.
with_timeout git fetch -q --no-tags origin "${base_ref#origin/}" >/dev/null 2>&1 || true

base=$(git merge-base "$base_ref" HEAD 2>/dev/null) || exit 0
commits=$(git log --oneline "$base"..HEAD 2>/dev/null) || exit 0
[ -n "$commits" ] || exit 0

# merge_target_is_head — true when `gh pr merge` names no PR, or names the PR of
# the checked-out branch. The first word after `merge` that is not a flag is the
# target; flags that take a value consume the next word.
merge_target_is_head() {
  local rest target="" skip=0 word branch head_ref
  # Quoted flag values (`--subject "a b"`) are dropped first, so their words
  # are never taken for the target.
  rest=$(printf '%s' "$cmd" \
    | sed -E "s/.*gh[[:space:]]+pr[[:space:]]+merge//; s/\"[^\"]*\"/''/g; s/'[^']*'/''/g; s/[;&|].*//")
  set -f
  for word in $rest; do
    if [ "$skip" -eq 1 ]; then skip=0; continue; fi
    case "$word" in
      -t|--subject|-b|--body|-F|--body-file|-A|--author-email|--match-head-commit|-R|--repo) skip=1 ;;
      -*) ;;
      *) target="$word"; break ;;
    esac
  done
  [ -z "$target" ] && return 0
  branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null) || return 1
  [ "$target" = "$branch" ] && return 0
  command -v gh >/dev/null 2>&1 || return 1
  head_ref=$(with_timeout gh pr view "$target" --json headRefName --jq .headRefName 2>/dev/null) || return 1
  [ "$head_ref" = "$branch" ]
}

checked=1
if command -v just >/dev/null 2>&1 && command -v commitlint >/dev/null 2>&1; then
  lint_out=$(just commit-lint "$base_ref" 2>&1)
  lint_rc=$?
else
  checked=0
  lint_out="Not checked: \`just\` or \`commitlint\` is not on PATH (both come from the Nix dev shell)."
  lint_rc=0
fi

if [ "$checked" -eq 1 ] && [ "$lint_rc" -eq 0 ]; then
  verdict="just commit-lint passed."
elif [ "$checked" -eq 1 ]; then
  verdict="just commit-lint FAILED. Fix the commits below before the PR is merged."
else
  verdict="just commit-lint did not run."
fi

context="${verdict}

Rebase merge puts ALL of a PR's commits on main verbatim. Curate per docs/development/commit-conventions.md: one \`type(scope): subject\` commit per scope per slice (a plan phase, or a feature within it, inside one ARCH-MAP.md component), at most 100 commits, no merge commits. Fold trial-and-error and fixups into the commit they belong to, clean the comments the branch touches, and put the journey in the PR description, not in history. CI skips the check on drafts. When the base below is origin/main for a stack layer, the list also holds the lower layers' commits: curate only this layer's own.

just commit-lint ${base_ref}:
${lint_out}

Branch commits (${base:0:9}..HEAD):
${commits}"

if [ "$action" = "gh pr merge" ] && [ "$checked" -eq 1 ] && [ "$lint_rc" -ne 0 ] && merge_target_is_head; then
  jq -cn --arg ctx "$context" \
    '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: $ctx}}'
  exit 0
fi

jq -cn --arg ctx "$context" \
  '{hookSpecificOutput: {hookEventName: "PreToolUse", additionalContext: $ctx}}'
exit 0
