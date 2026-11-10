# Commit and PR Conventions

The single source for how git is handled in dsp-api: the merge workflow, commit messages, commit organization
and pull requests. `CLAUDE.md`, `CONVENTIONS.md`, `REVIEW.md` and the PR template point here and do not repeat it.

## Git workflow

dsp-api merges pull requests by **rebase**. Every commit on a PR's branch lands on `main` verbatim, so the branch
history *is* the `main` history, and there is no squash-on-merge safety net: whatever is left on the branch ships.

The goal is a **meaningful history on `main`**. Every commit on `main` is a deliberate, self-contained,
Conventional Commit that an agent or a developer can read through `git log` and `git blame`. Working commits
("WIP", "fix typo", "address review") do not belong on `main`.

- **Curate before merging (mandatory).** Before a PR is merged, rewrite the branch (`git rebase -i`) so its commits
  read well on `main`: squash trial-and-error and fixups into the commit they belong to, reword messages, reorder
  as needed. Working commits while you build are fine; they make rollback easy. Push the rewritten branch with
  `git push --force-with-lease`.
- **One commit per PR by default**, enforced in CI. See
  [One PR, several commits, or a stack](#one-pr-several-commits-or-a-stack).
- **Update a branch by rebasing, never by merging `main` into it:**

      git fetch origin && git rebase origin/main && git push --force-with-lease

  In the GitHub UI, use the chevron next to **Update branch** and pick **Update with rebase**. The plain button
  creates a merge commit, which the commit hygiene check rejects.
- **At most 100 commits per PR.** GitHub's "Rebase and merge" is
  [limited to 100 commits](https://docs.github.com/en/repositories/creating-and-managing-repositories/repository-limits);
  above that it refuses with "This branch cannot be rebased due to conflicts" although nothing conflicts.
- **Every commit should build and pass its tests**, so that `git bisect` can land on any commit of `main`. This is a
  recommendation, not a check: CI tests only the head of a PR.

### Grace period (until 2026-11-13)

Until 2026-11-13, squash merge is still allowed next to rebase merge. A PR whose history is not clean, or which has
several commits without `allow-many-commits` ticked, is squash-merged instead. A squash merge takes the PR title as
the commit subject and the PR description as the body, so write the title as `type(scope): subject` too (the PR
title check does not require the scope).
From 2026-11-13 on, rebase merge is the only merge method.

## Commit message schema

Follow [Conventional Commits](https://www.conventionalcommits.org/). release-please reads the type of every commit
on `main` to sort it into a `CHANGELOG.md` section and to decide the next version, so the correct type is required,
not optional.

    type(scope): subject (DEV-1234)

    body

**Every commit has a type and a scope**; both are mandatory. There are nine types:

| Type         | Meaning                                            | Changelog section        |
|--------------|----------------------------------------------------|--------------------------|
| `feat`       | New user-visible functionality                     | Enhancements             |
| `fix`        | Bug fix (see [What `fix:` means](#what-fix-means)) | Bug Fixes                |
| `perf`       | Performance improvement                            | Performance Improvements |
| `deprecated` | Deprecation of an API or feature                   | Deprecated               |
| `docs`       | Documentation                                      | Documentation            |
| `test`       | Adding or refactoring tests                        | Tests                    |
| `refactor`   | Code restructuring, no behavior change             | Maintenances             |
| `build`      | Build system or dependencies                       | Maintenances             |
| `chore`      | Miscellaneous maintenance, CI                      | Maintenances             |

Every type is a visible changelog section. A breaking change takes a `!` suffix (or a `BREAKING CHANGE:` footer)
and bumps the major version; `feat` bumps the minor version, every other type the patch version:

    feat(api)!: remove the deprecated v2 search route

`ci`, `style` and `revert` are **not** types; the commit hygiene check rejects them:

- **CI and automation** → `chore(ci): ...`. `ci` stays a valid *scope*.
- **Formatting-only changes** → fold them into the commit they belong to. `just fmt` is a CI gate, so a standalone
  formatting commit should be rare.
- **Reverts** → `fix(<scope>): revert ...` when the reverted change shipped, otherwise `chore(<scope>): revert ...`.

### Subject

The subject is imperative and in lowercase prose (`fix(search): limit the Lucene hits`, not `Fixed ...` or
`Limit ...`). When a Linear issue exists, its id ends the subject in parentheses:
`feat(search): add the label filter (DEV-1234)`. Do not use "Knora" in a commit message; use "dsp-api".

### The body is written for an agent

The history's main reader is a coding agent: it reaches a commit through `git blame`, `git log -- <path>` and
`git log -S`, and the message is where it learns why the code is as it is. The subject says what changed; the body
carries what the diff cannot:

- the decisions taken and the alternatives rejected, with the reason;
- the invariants the code relies on and where they are enforced;
- the gotchas a later change would trip over;
- the stable sources behind them (an ADR, a `DEV-` id, a doc page).

It describes the end state of the change, never the journey to it: no "first tried", no review-round history, no
behavior that existed only on the branch. A long body is fine; an agent reads all of it.

### What `fix:` means

A `fix:` corrects behavior that exists on `main`: a bug a deployer could hit today, or that a released version
shipped. It earns a "Bug Fixes" changelog line precisely because deployers need to know.

A bug you introduced earlier in the **same branch** is not a `fix:`. Fold it into the commit that introduced it
(`git commit --fixup=<sha>`, then `git rebase -i --autosquash`) so it never lands on `main` and never generates a
changelog entry for a bug nobody ever saw. With the one-commit default this is the normal case: you are amending,
not accumulating.

Corollary: if you discover a genuine pre-existing `main` bug while doing unrelated work, give it its **own** `fix:`
commit (and usually its own PR), don't bury it inside the `feat:` or `refactor:` you happened to be writing.

## Scopes

**A scope is mandatory.** Every commit is `type(scope): subject`, so every changelog line reads `concern: subject`.

The scope names the **concern the change serves**: the component whose responsibility it belongs to, not the
directory the edited files happen to sit in. The vocabulary is the component names of
the root `ARCH-MAP.md`, with the `webapi-` prefix dropped, plus three cross-cutting scopes:

- **webapi components:** `app`, `api`, `common`, `security`, `infrastructure`, `triplestore`, `sipi-client`,
  `admin`, `ontology`, `resources`, `standoff`, `search`, `export`, `fair`
- **Modules and areas:** `ingest`, `sipi`, `fuseki`, `bagit`, `jwt`, `shacl-validator`, `sparql-builder`,
  `testkit`, `test-it`, `test-e2e`, `build-toolchain`, `docs`, `observability`
- **Cross-cutting:** `ci` (workflows, CI scripts, the commit hygiene check, agent hooks), `deps` (dependency
  bumps), `release` (release-please, versioning, publishing images)

Rules:

- Lowercase, kebab-case.
- A commit that spans several components may list them comma-separated: `refactor(admin,security): ...`.
- **Concern over location.** Documentation or a test *about* a component takes that component's scope
  (`docs(search): ...`, `test(ontology): ...`); only repo-level docs take `docs`.
- **No catch-all.** There is no `repo` or `all` scope. When no scope fits, the component is probably missing from
  `ARCH-MAP.md`: add it there deliberately rather than inventing a scope.

The check enforces only that a scope is *present*, not which one, so a new component needs no config change. The
list above is the advisory vocabulary; keep to it.

## Enforcement

The `Commit hygiene` job of `.github/workflows/commit-hygiene.yml` runs on every non-draft PR, through the
composite action `.github/actions/commit-lint`. On a draft it skips the history checks but still reports, and still
runs `just test-scripts`, the tests of the check's own scripts. It checks:

- **Messages:** [`commitlint-rs`](https://github.com/KeisukeYamashita/commitlint-rs) against
  `.commitlintrc.yml`: one of the nine types, a scope, a non-empty subject. `fixup!` and
  `squash!` commits do not parse as Conventional Commits, so they fail here too.
- **Count:** at most one commit, unless the PR description has a ticked `- [x] allow-many-commits` box. An unticked
  box does not count. The check reruns when the description is edited.
- **No merge commits on the branch.** `allow-many-commits` does not lift this; it is about independent commits,
  which a merge commit is not.

The check counts the commits between the PR's base branch and its head, so in a stack each PR is checked on its
own commits only. Run the same checks locally before pushing (`commitlint` is in the Nix dev shell):

    just commit-lint            # checks origin/main..HEAD
    just commit-lint <base-ref> # checks <base-ref>..HEAD, e.g. the layer below in a stack

Locally the PR description is empty, so the one-commit cap applies strictly; set
`PR_BODY='- [x] allow-many-commits'` to check a branch that keeps several commits.

The `main` ruleset requires a linear history, so a merge commit cannot reach `main` itself.

For agents, the Claude Code hook `.claude/hooks/pr-curation-reminder.sh` lists the branch's commits and repeats the
curation rule before `gh pr create`, `gh pr merge` and `gh stack submit`. It is advisory and never blocks.

## Commit organization

### Principle

Start from the assumption that the whole PR is **one commit**. Group commits by user-visible impact, not by
implementation journey, and ask: "would a developer deploying dsp-api care about this change?" If yes, it is a
`feat:` or `fix:`, the changelog sections a deployer reads first. If no, it is one of the other types, which land in
sections further down; squash those aggressively too, so each is a meaningful line on its own.

### One PR, several commits, or a stack

When the finished work does not fit one commit, take the first rule that matches:

1. **The parts would need different Motivation sections** (see the PR template) → **separate PRs**, each from
   `main`.
2. **The parts depend on each other but should be reviewed separately** → a **stack** of PRs (see
   [Stacked PRs](#stacked-prs)).
3. **Only then: one PR with several commits.** Each commit is independent and self-contained and deserves its own
   line in `main`'s history. Tick `allow-many-commits` and fill in the template's Review guide, one sub-section per
   commit.

A reviewer asking for a split turns case 3 into case 2. In a stack the one-commit limit applies per PR.

### Plans fold into the implementation commit

A plan, PRD or execution journal committed with a PR (under `docs/specs/`) is part of the commit that implements it,
not a separate `docs: add the plan` commit.

### History lives in git, documents hold the current state

Documents in the repository (`docs/`, ADRs, `ARCH-MAP.md`, `CONTEXT.md`, `CLAUDE.md`, `CONVENTIONS.md`, code
comments) state what the code is and does now, or, for an ADR, the target it is being moved to. They never narrate
how they got there: no "previously X, now Y", no dated amendment, no status that goes stale once a PR merges. Write
a document as though the PR that introduces it is already merged. What is still to come (a decided target, an open
question, a deferred option) is current information and stays, marked as such and linked to its Linear issue.

The history of a document is the git history of its file. When a commit changes what a document says, above all
when it changes a decision in an ADR, the commit body states what the document said before and why it changed.
`git log -p -- <file>` reads it back. A commit that only says "update ADR-0011" loses the one thing the rewritten
document no longer carries.

`docs/specs/` is the exception: PRDs, plans and journals are point-in-time records of what was intended when they
were written, and are not rewritten to follow the code.

### Where context lives

| Layer          | Audience                                | Content                                                          |
|----------------|-----------------------------------------|------------------------------------------------------------------|
| Commit subject | Release notes readers                   | What changed, as `type(scope): subject`                          |
| Commit body    | Agents and developers via blame and log | The why: decisions, invariants, gotchas; what a doc said before  |
| PR description | Reviewers, future developers            | Reviewer context, plus the journey in Challenges and Decisions   |
| Docs and ADRs  | Everyone                                | The current state, or a target state with its Linear id          |
| Learnings      | Future engineers and agents             | Optional, on top: structured, searchable knowledge               |
| Code comments  | Code readers                            | Invariants a reader must not break (`CONVENTIONS.md` § Comments) |

## Stacked PRs

Work that depends on a PR still in review goes on a stacked PR instead of waiting for the merge. Each layer is its
own PR whose base is the layer below, so it shows only its own diff and follows every rule on this page. The stack
is managed with the [`gh stack`](https://docs.github.com/en/pull-requests/reference/stacked-prs-cli-commands)
extension (`gh extension install github/gh-stack`):

| Step                                              | Command                                       |
|---------------------------------------------------|-----------------------------------------------|
| Stack on an open PR's branch                      | `gh stack init <open-pr-branch> <new-branch>` |
| Add a further layer                               | `gh stack add <new-branch>`                   |
| Push every layer and open or update its PR        | `gh stack submit`                             |
| Rebase the layers above after a lower one changes | `gh stack rebase`, then `gh stack push`       |
| After a lower layer merges                        | `gh stack sync` (fetches, rebases, pushes)    |

Both rebase merge and squash merge give the merged commits new SHAs. After a lower layer merges, restack the layers
above (`gh stack sync`, or `gh stack rebase` and `gh stack push`); otherwise the next layer's commit hygiene check
still counts the lower layer's old commits as its own. Curate each layer's commits before it merges, as for any PR.

## Pull requests

### PR description

`.github/pull_request_template.md` pre-fills the structure into every new PR; a PR opened from the CLI
(`gh pr create --body-file`) supplies the same sections:

- **Motivation:** why the work was needed, what problem it solves for users.
- **Summary:** one to three bullet points of user-visible changes.
- **Key Changes:** the changes, grouped by topic.
- **Challenges and Decisions:** what was tried, what failed, and the decisions taken. The journey that the curated
  history deliberately omits goes here, never into a commit. Record a hard-to-reverse decision as an ADR in
  `docs/adr/`. Delete the section if there were none.
- **Gotchas:** actionable, "do X instead of Y", not "this is hard".
- **Test Plan:** how the change was verified.
- **Commit hygiene:** the curation box, and the `allow-many-commits` box when the PR keeps several commits.
- **Review guide:** only with `allow-many-commits` ticked; one sub-section per commit, in history order.

Start the description with `Fixes DEV-1234`, or `Part of DEV-1234` when the PR advances an umbrella issue it does
not close. Create PRs as drafts.

### What goes where

| Information                                 | Put it in...                                                                |
|---------------------------------------------|-----------------------------------------------------------------------------|
| New feature / breaking change               | Commit message (`feat:` / `feat!:`)                                         |
| Bug fix                                     | Commit message (`fix:`)                                                     |
| Why the code is as it is                    | Commit body                                                                 |
| How a decision or document changed, and why | Commit body (the document itself is rewritten in place)                     |
| Why the work was needed                     | PR Motivation section                                                       |
| What was tried and failed                   | PR Challenges and Decisions section                                         |
| Architecture decisions and their rationale  | PR Challenges and Decisions; a hard-to-reverse one as an ADR in `docs/adr/` |
| What a reviewer should look at, per commit  | PR Review guide section (several commits only)                              |
| Things to watch out for                     | PR Gotchas section                                                          |
| Structured, searchable knowledge            | Optionally a learning, written by `/eng:workflows:compound`                 |

## Before a merge

A PR merges only when every line holds for its current head. Say in its Test Plan how each was met, or why it does
not apply.

1. **It stands on its own.** Nothing in it waits on later work.
2. **The history is curated**, and `just commit-lint` (the `Commit hygiene` check) passes.
3. **CI is green** at the head being merged.
4. **It has been reviewed**, and every finding is either fixed or answered.
5. **The description is filled in** per the template, with a Review guide when it keeps several commits.
6. **Nothing is left only in text.** Every follow-up has a Linear issue.
