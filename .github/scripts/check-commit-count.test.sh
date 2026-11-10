#!/usr/bin/env bash
#
# Tests for check-commit-count.sh.
#
# Dependency-free: needs only bash + git. The unit tests cover the two things
# that actually carry risk — tick-aware override matching and the count
# boundary; the integration tests build throwaway git repos so merge-base
# behaviour is exercised against real git.
#
# Message-format rules are commitlint's, not ours, and are not tested here.
#
# Run: bash .github/scripts/check-commit-count.test.sh   (or `just test-scripts`)

set -uo pipefail

# Fixtures build throwaway repositories with `git init`, which honours an
# inherited GIT_DIR over the directory it is run in: without this, a run under
# `git rebase --exec` or a git hook re-initializes the caller's repository.
unset GIT_DIR GIT_WORK_TREE GIT_INDEX_FILE GIT_COMMON_DIR GIT_OBJECT_DIRECTORY GIT_ALTERNATE_OBJECT_DIRECTORIES GIT_NAMESPACE

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./check-commit-count.sh disable=SC1091
source "$SCRIPT_DIR/check-commit-count.sh"

PASS=0
FAIL=0

# assert_ok "desc" cmd...   — expects the command to exit 0
assert_ok() {
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then
    PASS=$((PASS + 1))
  else
    FAIL=$((FAIL + 1))
    echo "FAIL: $desc (expected success)"
  fi
}

# assert_fail "desc" cmd... — expects the command to exit non-zero
assert_fail() {
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then
    FAIL=$((FAIL + 1))
    echo "FAIL: $desc (expected failure, got success)"
  else
    PASS=$((PASS + 1))
  fi
}

# --- Fixtures -------------------------------------------------------------

# make_repo: throwaway repo whose default branch is `base` with one commit.
# Tests pass BASE_REF=base to compute_range.
make_repo() {
  local dir
  dir="$(mktemp -d "${TMPDIR:-/tmp}/commit-count-repo.XXXXXX")"
  (
    cd "$dir" || exit 1
    git init -q -b base
    git config user.email test@example.com
    git config user.name "Test"
    # A developer's global signing or hooks must not make the fixtures prompt or fail.
    git config commit.gpgsign false
    git config core.hooksPath /dev/null
    git commit -q --allow-empty -m "chore(ci): base"
  )
  printf '%s' "$dir"
}

# --- Unit: has_override ---------------------------------------------------

# Consumed by has_override in the sourced script, not directly here.
# shellcheck disable=SC2034
OVERRIDE_TOKEN="allow-many-commits"

assert_fail "override: empty body"                 has_override ""
assert_ok   "override: exact token"                has_override "allow-many-commits"
assert_ok   "override: case-insensitive"           has_override "Allow-Many-Commits"
assert_ok   "override: token within prose"         has_override "please allow-many-commits for this PR"
assert_ok   "override: ticked checkbox"            has_override "- [x] allow-many-commits"
assert_ok   "override: ticked checkbox uppercase"  has_override "- [X] allow-many-commits"
assert_fail "override: UNticked checkbox (template default)" has_override "- [ ] allow-many-commits"
assert_ok   "override: ticked box amid template"   has_override "$(printf '## Summary\n- [ ] other thing\n- [x] allow-many-commits\n')"
assert_fail "override: only unticked box present"  has_override "$(printf '## Summary\nstuff\n- [ ] allow-many-commits\n')"
assert_fail "override: prefixed word (disallow-)"  has_override "disallow-many-commits-please"
assert_fail "override: no hyphens"                 has_override "allowmanycommits"
# The token on any line but its unticked box counts as ticked, so the template
# must spell it nowhere else, its HTML comments included.
assert_fail "override: the untouched PR template"  has_override "$(cat "$SCRIPT_DIR/../pull_request_template.md")"

# --- Unit: check_count ----------------------------------------------------

# Consumed by check_count in the sourced script, not directly here.
# shellcheck disable=SC2034
MAX_COMMITS=1

assert_ok   "count: 1 is the default maximum"      check_count 1 ""
assert_fail "count: 2 fails without override"      check_count 2 ""
assert_ok   "count: 2 with override passes"        check_count 2 "allow-many-commits"
assert_ok   "count: 20 with override passes"       check_count 20 "let's allow-many-commits"
assert_fail "count: 2 with UNticked template box still fails" check_count 2 "- [ ] allow-many-commits"
assert_ok   "count: 2 with ticked box passes"      check_count 2 "- [x] allow-many-commits"

# --- Integration: compute_range (merge-base correctness) ------------------

test_range_ignores_base_movement() {
  local repo; repo="$(make_repo)"
  (
    cd "$repo" || exit 1
    git checkout -q -b feature
    git commit -q --allow-empty -m "feat(search): f1"
    git commit -q --allow-empty -m "feat(search): f2"
    # base moves ahead after the branch point
    git checkout -q base
    git commit -q --allow-empty -m "chore(ci): base2"
    git checkout -q feature
    # range must be merge-base(base,HEAD)..HEAD = only f1,f2 (not base2)
    [ "$(git rev-list --count "$(compute_range base)")" = "2" ]
  )
  local rc=$?
  rm -rf "$repo"
  return $rc
}
assert_ok "range: excludes commits added to base after branch point" test_range_ignores_base_movement

# --- Integration: merge commits are rejected ------------------------------

# make_merged_repo <n>: a feature branch with <n> real commits that has had an
# advanced `base` merged into it — what GitHub's "Update branch" button
# produces when you use the plain button instead of "Update with rebase".
make_merged_repo() {
  local n="$1" dir i
  dir="$(make_repo)"
  (
    cd "$dir" || exit 1
    git checkout -q -b feature
    for i in $(seq 1 "$n"); do
      git commit -q --allow-empty -m "feat(search): change $i"
    done
    git checkout -q base
    git commit -q --allow-empty -m "chore(ci): base moved on"
    git checkout -q feature
    git merge -q --no-ff base -m "Merge branch 'base' into feature"
  )
  printf '%s' "$dir"
}

# --- Unit: check_no_merges ------------------------------------------------

assert_ok   "no-merges: a linear branch passes"    check_no_merges 0
assert_fail "no-merges: one merge commit fails"    check_no_merges 1
assert_fail "no-merges: several merge commits fail" check_no_merges 3

# --- Integration: the branch shape the Update-branch button produces ------

test_merge_commit_fails() {
  local repo; repo="$(make_merged_repo 1)"
  ( cd "$repo" || exit 1; BASE_REF=base PR_BODY="" MAX_COMMITS=1 main )
  local rc=$?; rm -rf "$repo"; return $rc
}
# The override says "these are several independent changes worth their own
# lines on main". That is never true of a merge commit, so it must not lift this.
test_override_does_not_excuse_a_merge() {
  local repo; repo="$(make_merged_repo 1)"
  ( cd "$repo" || exit 1; BASE_REF=base PR_BODY="- [x] allow-many-commits" MAX_COMMITS=1 main )
  local rc=$?; rm -rf "$repo"; return $rc
}
# A rebased branch is what the guidance asks for, and it has to pass.
test_rebased_branch_passes() {
  local repo; repo="$(make_merged_repo 1)"
  (
    cd "$repo" || exit 1
    git rebase -q base >/dev/null 2>&1
    BASE_REF=base PR_BODY="" MAX_COMMITS=1 main
  )
  local rc=$?; rm -rf "$repo"; return $rc
}
# The cap must not also fire: the branch has one real commit, and blaming it
# for "2 commits" would send the developer to the wrong remedy.
test_merge_does_not_trip_the_cap() {
  local repo out
  repo="$(make_merged_repo 1)"
  out="$( cd "$repo" && BASE_REF=base PR_BODY="" MAX_COMMITS=1 main 2>/dev/null )"
  rm -rf "$repo"
  case "$out" in *"commit count: 1 (max 1)"*) return 0 ;; *) return 1 ;; esac
}
# Two real commits plus a merge must still trip the cap.
test_cap_still_applies_alongside_a_merge() {
  local repo; repo="$(make_merged_repo 2)"
  ( cd "$repo" || exit 1; BASE_REF=base PR_BODY="" MAX_COMMITS=1 check_count \
      "$(git rev-list --count --no-merges "$(compute_range base)")" "" )
  local rc=$?; rm -rf "$repo"; return $rc
}
assert_fail "merge: a merged-in base fails the gate"                test_merge_commit_fails
assert_ok   "merge: the cap reports the real commit count, not the merge" test_merge_does_not_trip_the_cap
assert_fail "merge: two real commits plus a merge still trip the cap"     test_cap_still_applies_alongside_a_merge
assert_fail "merge: 'allow-many-commits' does not excuse a merge"   test_override_does_not_excuse_a_merge
assert_ok   "merge: rebasing the same branch makes it pass"         test_rebased_branch_passes

# --- Integration: main (end to end) --------------------------------------

test_main_single_commit() {
  local repo; repo="$(make_repo)"
  (
    cd "$repo" || exit 1
    git checkout -q -b feature
    git commit -q --allow-empty -m "feat(search): a"
    BASE_REF=base PR_BODY="" MAX_COMMITS=1 main
  )
  local rc=$?; rm -rf "$repo"; return $rc
}
test_main_two_commits() {
  local repo; repo="$(make_repo)"
  (
    cd "$repo" || exit 1
    git checkout -q -b feature
    git commit -q --allow-empty -m "feat(search): a"
    git commit -q --allow-empty -m "fix(search): b"
    BASE_REF=base PR_BODY="" MAX_COMMITS=1 main
  )
  local rc=$?; rm -rf "$repo"; return $rc
}
test_main_two_commits_override() {
  local repo; repo="$(make_repo)"
  (
    cd "$repo" || exit 1
    git checkout -q -b feature
    git commit -q --allow-empty -m "feat(search): a"
    git commit -q --allow-empty -m "fix(search): b"
    BASE_REF=base PR_BODY="- [x] allow-many-commits" MAX_COMMITS=1 main
  )
  local rc=$?; rm -rf "$repo"; return $rc
}
assert_ok   "main: single-commit branch passes"          test_main_single_commit
assert_fail "main: 2 commits, no override, fails"        test_main_two_commits
assert_ok   "main: 2 commits with ticked override passes" test_main_two_commits_override

# --- Summary --------------------------------------------------------------

echo
echo "commit-count tests: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
