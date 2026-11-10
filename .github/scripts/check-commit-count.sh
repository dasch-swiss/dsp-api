#!/usr/bin/env bash
#
# The deterministic half of the commit-hygiene gate: a PR lands as ONE commit
# unless it explicitly opts out. Message format (type allowlist + mandatory
# scope) is the other half and belongs to commitlint-rs — see .commitlintrc.yml
# and `just commit-lint`.
#
# Two rules live here, both about the shape of the branch:
#   1. the one-commit cap, liftable with the override token;
#   2. no merge commits, NOT liftable — see check_no_merges.
#
# fixup!/squash! commits need no rule here: they do not parse as Conventional
# Commits, so commitlint rejects them, including under the override.
#
# The `main` ruleset's `required_linear_history` keeps merge commits off main,
# but it says nothing about the branch, and GitHub's "Update branch" button
# defaults to merging the base in. A linear-history ruleset on all branches is
# planned for 2026-11-13; until it exists, this is the only place that catches
# such a merge.
#
# Configuration (environment variables, with defaults):
#   BASE_REF        base to diff against            (default: origin/main)
#   PR_BODY         PR description, for the override (default: empty)
#   MAX_COMMITS     commit-count cap                (default: 1)
#   OVERRIDE_TOKEN  phrase in PR_BODY that lifts the cap
#                                                   (default: allow-many-commits)
#
# The file is safe to `source` (for unit testing): it defines functions and
# only runs `main` when executed directly.

: "${BASE_REF:=origin/main}"
: "${PR_BODY:=}"
: "${MAX_COMMITS:=1}"
: "${OVERRIDE_TOKEN:=allow-many-commits}"

# has_override <body> — true if the opt-out token appears as a standalone word
# on a line that is NOT an unticked checkbox. This is deliberate: the PR template
# seeds an unticked `- [ ] allow-many-commits`, and its mere presence must not
# lift the cap — the author has to tick the box (or write the token in prose).
# Case-insensitive; a leading/trailing alphanumeric (e.g. "disallow-...") does
# not count, so the token must stand on its own.
has_override() {
  printf '%s' "$1" \
    | grep -iE "(^|[^[:alnum:]])${OVERRIDE_TOKEN}([^[:alnum:]]|$)" \
    | grep -qvE '\[[[:space:]]\]'
}

# compute_range <base_ref> — echo "<merge-base>..HEAD" so only the branch's own
# commits are considered, even if the base moved on after the branch point.
compute_range() {
  local base
  base="$(git merge-base "$1" HEAD)"
  printf '%s..HEAD' "$base"
}

# check_no_merges <count> — a branch must contain no merge commits.
#
# Deliberately not subject to the override: `allow-many-commits` says "these
# are several independent changes worth their own lines on main", which is
# never true of a merge commit.
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
    echo "  '$OVERRIDE_TOKEN' does not lift this; it is about independent"
    echo "  commits, which a merge commit is not."
  } >&2
  return 1
}

# check_count <count> <body>
check_count() {
  local count="$1" body="$2"
  if [ "$count" -le "$MAX_COMMITS" ]; then
    echo "✓ commit count: $count (max $MAX_COMMITS)"
    return 0
  fi
  if has_override "$body"; then
    echo "✓ commit count: $count > $MAX_COMMITS, but '$OVERRIDE_TOKEN' is ticked in the PR body"
    return 0
  fi
  {
    echo "✗ commit count: $count exceeds the maximum of $MAX_COMMITS."
    echo "  This repo rebase-merges, so a PR lands as one commit by default."
    echo "  Squash the branch before merging:"
    echo "      git rebase -i $BASE_REF"
    echo "  Or, if these are genuinely independent changes that each deserve"
    echo "  their own line in main's history, tick the '$OVERRIDE_TOKEN'"
    echo "  checkbox in the PR description (an unticked box does not count)."
  } >&2
  return 1
}

main() {
  local range count merges rc=0
  range="$(compute_range "$BASE_REF")"
  # The cap counts real commits only; merge commits fail check_no_merges, which
  # the override does not lift, so a merged-in base is reported as a merge and
  # not as "too many commits".
  count="$(git rev-list --count --no-merges "$range")"
  merges="$(git rev-list --count --merges "$range")"
  echo "Checking commit count over $range"
  # Both run, so a branch with both problems reports both rather than hiding
  # one behind the other.
  check_count "$count" "$PR_BODY" || rc=1
  check_no_merges "$merges" || rc=1
  return "$rc"
}

if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
  set -euo pipefail
  main "$@"
fi
