---
title: "Agent-readiness baseline: triage of the open gaps"
date: 2026-11-09
author: "Raitis Veinbahs"
status: draft
repositories: []
linear: DEV-7643
---

# Agent-readiness baseline: triage of the open gaps

Source: the
[2026-10-20 dsp-api baseline assessment](https://github.com/dasch-swiss/dasch-specs/blob/main/assessments/2026-10-20-baseline/dsp-api.md)
(41 of 57, Level 3, scored at `e9a327f070e9`).

The assessment was re-checked on 2026-11-09 against `origin/main`, which is 18 commits past the scored commit.
Every file-level gap still holds: there is no hook config, `eng.yaml`, `SECURITY.md` or `.env.example`. Five production files still use RDF4J SparqlBuilder.

## A. Quick fixes (under an hour in total)

- `prose-config-coherence`: add sparql-builder to the `just test-unit` line in CLAUDE.md.
- `conventions-doc-git-history`: add one line to CONVENTIONS.md telling the agent to check `git log main` for
  commit and PR-title style, and to find reviewers from recent authors of the touched files.
- `env-documentation`: commit a `.env.example` containing `DEV_DB_PASSWORD=`.
- `security-policy`: add a short `SECURITY.md`.
- `eng-yaml`: run `/eng:eng-yaml`.
- `architecture-adherence` finding F2: change the Search `CONTEXT.md` files so the ResourceSearch port split
  reads as a target, not as something already done.

## B. Worth real work

- `architecture-adherence` finding F1: done on `feature/dev-7643-improve-ai-readiness-of-dsp-api`
  (`//tools/lint:api_imports`, a ratchet over 71 baselined files).
- `architecture-adherence` finding F4: run `/dune:dune-map` to move ARCH-MAP.md to schema 2.
- `security-scanning`: Trivy on the images, or GitHub's dependency-review action. Check first whether this
  should be a shared solution across repos, like secrets detection.
- `naming-conventions`: already settled in CONVENTIONS.md (Services: no new trait + `*Live` split for
  domain services); the assessor missed it.

## C. Push back or skip

- `migration-completeness`: the RDF4J SparqlBuilder migration is out of scope for this work.
- `blame-hygiene`: decided against setting `blame.ignoreRevsFile`; not pursued.
- `claude-md-conciseness` (rated 4/10): the inlined IRI and SPARQL rules are there on purpose. CLAUDE.md is
  always loaded, while the linked docs are only read if the agent follows the link. Shorten them to one line
  each plus a link rather than removing them.
- `pre-commit-hooks`: GitHub branch protection already blocks pushes to main, and secrets detection is waiting
  for a shared solution. A hook would only add the file-size check.
- `coverage-config`: coverage under rules_scala is painful and tells us little. Low priority.
- `dead-code-detection`: `-Wunused` already catches unused privates and imports. Finding unused public code
  would need new tooling for little return.
- `automation-scripts-tested`: `wait-for-api.sh` and `wait-for-db.sh` are simple polling loops, so tests would
  add little.

## Next steps

1. Do the quick fixes in section A as one small PR.
