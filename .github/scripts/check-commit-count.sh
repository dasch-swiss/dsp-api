#!/usr/bin/env bash
#
# The deterministic half of the commit-hygiene gate, about the shape of the
# branch. Message format (type allowlist + mandatory scope) is the other half
# and belongs to commitlint-rs — see .commitlintrc.yml and `just commit-lint`.
#
# Two rules live here:
#   1. at most 100 commits: GitHub's "Rebase and merge" refuses a larger PR
#      with "This branch cannot be rebased due to conflicts" although nothing
#      conflicts, so the gate says what is wrong before the merge does;
#   2. no merge commits — see check_no_merges.
#
# fixup!/squash! commits need no rule here: they do not parse as Conventional
# Commits, so commitlint rejects them.
#
# The `main` ruleset's `required_linear_history` keeps merge commits off main,
# but it says nothing about the branch, and GitHub's "Update branch" button
# defaults to merging the base in. The "feature branches: linear history"
# ruleset (a GitHub repository setting, not in this repo) refuses such a merge
# on the other branches; this check is the in-repo backstop and also covers a
# fork's branch, which no ruleset here reaches.
#
# Configuration (environment variables, with defaults):
#   BASE_REF        base to diff against            (default: origin/main)
#   MAX_COMMITS     commit-count cap                (default: 100)
#
# The file is safe to `source` (for unit testing): it defines functions and
# only runs `main` when executed directly.

: "${BASE_REF:=origin/main}"
: "${MAX_COMMITS:=100}"

# compute_range <base_ref> — echo "<merge-base>..HEAD" so only the branch's own
# commits are considered, even if the base moved on after the branch point.
compute_range() {
  local base
  base="$(git merge-base "$1" HEAD)"
  printf '%s..HEAD' "$base"
}

# check_no_merges <count> — a branch must contain no merge commits.
check_no_merges() {
  local count="$1"
  if [ "$count" -eq 0 ]; then
    echo "✓ no merge commits"
    return 0
  fi
  {
    echo "✗ $count merge commit(s) on this branch."
    echo "  The branch was updated by merging $BASE_REF into it. Update it by"
    echo "  rebasing instead, so its history stays linear:"
    echo "      git fetch origin && git rebase $BASE_REF && git push --force-with-lease"
    echo "  In the GitHub UI, use the chevron next to 'Update branch' and pick"
    echo "  'Update with rebase' — the plain button makes a merge commit."
  } >&2
  return 1
}

# check_count <count>
check_count() {
  local count="$1"
  if [ "$count" -le "$MAX_COMMITS" ]; then
    echo "✓ commit count: $count (max $MAX_COMMITS)"
    return 0
  fi
  {
    echo "✗ commit count: $count exceeds the maximum of $MAX_COMMITS."
    echo "  GitHub's 'Rebase and merge' refuses more than $MAX_COMMITS commits."
    echo "  Fold the fixups into the commits they belong to (git rebase -i $BASE_REF),"
    echo "  or cut the work into a stack of pull requests at phase boundaries"
    echo "  (docs/development/commit-conventions.md, One PR or a stack)."
  } >&2
  return 1
}

main() {
  local range count merges rc=0
  range="$(compute_range "$BASE_REF")"
  # The cap counts real commits. Merge commits get their own check below, so
  # a branch whose only fault is how it was updated is told about the merge.
  count="$(git rev-list --count --no-merges "$range")"
  merges="$(git rev-list --count --merges "$range")"
  echo "Checking commit count over $range"
  # Both run, so a branch with both problems reports both rather than hiding
  # one behind the other.
  check_count "$count" || rc=1
  check_no_merges "$merges" || rc=1
  return "$rc"
}

if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
  set -euo pipefail
  main "$@"
fi
