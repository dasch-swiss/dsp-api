---
plan: docs/specs/2026-10-08-gravsearch-lucene-hit-limit/01-fix-gravsearch-lucene-hit-limit-plan.md
target_repo: /Users/balduinlandolt/Documents/GitHub/dasch-swiss/dsp-api/.claude/worktrees/DEV-6824
base_commit: c05431ceb
branch: worktree-DEV-6824
started: 2026-10-08
problem: >
  Gravsearch's matchText, matchTextInStandoff and matchLabel emit Jena text:query without a hit limit, so the
  Lucene lookup is silently capped at 10,000 hits and, for wildcard terms, returns an arbitrary subset (support
  case: 0105 DrawingPublic label "ir14*" returns 0 or 1,954 instead of 3,031). Gravsearch timeouts surface as a
  bare 500, and the matchText/matchLabel term is not SPARQL-escaped. matchFulltext stays capped (DEV-7489).
symptoms:
  - "Advanced search label 'matches' ir14* on 0105 DrawingPublic returns 1,954 (prod) / 0 (stage) of 3,031"
  - "Results change after a Lucene reindex"
status: in-progress
---

# Execution Journal: 01-fix-gravsearch-lucene-hit-limit-plan

## Repos

| repo | base_commit | branch | merge_strategy | status | pr |
| --- | --- | --- | --- | --- | --- |
| dsp-api | c05431ceb | worktree-DEV-6824 | squash | in-progress | <https://github.com/dasch-swiss/dsp-api/pull/4386> |

## Phases

| phase | status | phase_base | review_fix_rounds |
| --- | --- | --- | --- |
| 1 | reviewed | dsp-api@fbd5e5136 | 2 |
| 2 | pending | — | 0 |

## Chunk queue

| id | repo | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | slice/search/SearchTimeoutException.scala | — | "Gravsearch-specific hedged message"; "rewrite the class doc" | `gravsearchMessage` exists; doc covers both endpoint families, links store-layer exception | SearchTimeoutException.scala § object + class doc | — |
| 1.2 | dsp-api | slice/api/v2/search/SearchRestService.scala, SearchEndpoints.scala, test SearchEndpointsSpec.scala, new test SearchRestServiceSpec.scala | 1.1 | "SearchRestService.gravsearch translate"; "gravsearchCount same"; 4x "errorOutVariantsPrepend"; "update the comment :69-72"; "SearchEndpointsSpec"; 2x "Unit test" | timeout from gravsearchV2 / gravsearchCountV2 -> SearchTimeoutException(gravsearchMessage); 4 gravsearch endpoints advertise 503, others do not | SearchRestService.scala § gravsearch/gravsearchCount; SearchEndpoints.scala:69-118 | — |
| 1.3 | dsp-api | responders/v2/SearchResponderV2.scala | — | "SearchResponderV2.scala:813 tapError(logPrequeryFailure(countSparql))" | count prequery failure logged with SPARQL, inside stageSpan | SearchResponderV2.scala:813, logPrequeryFailure :570 | — |
| 1.4 | dsp-api | docs/03-endpoints/api-v2/query-language.md | 1.2 | "query-language.md:71 replace 504"; "Grep dsp-app for searchextended" | docs state 503 + message; dsp-app uses dsp-js only (verified, no generated client) | query-language.md:71 | — |
| 1.5 | dsp-api | — (verification) | 1.1-1.4 | "bazel test //modules/webapi:test passes" | full webapi suite green | — | — |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | complete | 105aa61ad | SearchTimeoutException: add gravsearchMessage; class doc covers fulltext and Gravsearch, links store-layer exception | none |
| 1.2 | dsp-api | complete | 637508c7b | SearchRestService translates TriplestoreTimeoutException to SearchTimeoutException(gravsearchMessage) for page+count; 4 searchextended endpoints advertise 503; SearchEndpointsSpec + new SearchRestServiceSpec (fail-without-fix confirmed) | none |
| 1.3 | dsp-api | complete | cf7a06464 | Gravsearch count prequery execute gets tapError(logPrequeryFailure(countSparql)) inside its stageSpan; no unit test (logging only, no existing harness) | none |
| 1.4 | dsp-api | complete | baf188405 | query-language.md: 504 claim replaced by the 503 and its message; dsp-app grep (6c3ccc490): searchextended only via dsp-js search-endpoint-v2.ts, generated OpenAPI client unused | none |
| 1.5 | dsp-api | complete | — (verification at baf188405) | bazel test //modules/webapi:test green (2300 tests) | none |
| 1.6 | dsp-api | complete | 0e3926b81 | review fix: cross-pointer comments between the Gravsearch (SearchRestService) and fulltext (SearchResponderV2) timeout translation sites and the endpoint variant; gravsearchV2/gravsearchCountV2 trait docs state they fail untranslated | none |
| 1.7 | dsp-api | complete | c9369f9ce | review fix (round 2): SearchRestService rule scoped to the Gravsearch methods; fulltext methods must not apply it | none |

## Deferrals

- ARCH-MAP.md webapi-search/webapi-api boundary rule ("Gravsearch timeout -> 503 translation lives in SearchRestService, fulltext in SearchResponderV2; a timeout-advertising endpoint must translate"), from the phase 1 dune review. Not edited by the executor: ARCH-MAP.md is owned by `/dune:dune-map update`; for the session to run before ship.

## Side findings

- `just check` fails on this journal itself: MD034 bare URL in the `## Repos` row's `pr` cell (session-written; wrap it in `<...>`). All code gates (scalafmt, SPDX, relative imports, markdownlint elsewhere) pass.
- Phase 1 review, kept as suggestions: `logPrequeryFailure` logs a Gravsearch timeout at ERROR with a cause, and `TriplestoreServiceLive` already logs a client read timeout, so that path double-logs; the 120s main query has no SPARQL-carrying failure log. Pre-existing; now that a timeout is a handled 503, consider WARN without cause (alerting decision).
- Phase 1 review, not taken: replacing the hand-written `SearchResponderV2` stub in `SearchRestServiceSpec` with a pure translator function (the plan asked for a rest-service spec over a stub responder, which is what proves the wiring); a wire-level 503 test (the variant uses the same tapir mechanism as the fulltext endpoints, covered by `SearchEndpointsSpec`).
- Optional hardening (dune re-review, low): the rule "a handler behind searchTimeoutVariant must translate" is comment-enforced only; a spec driving each variant-carrying server endpoint with a timing-out responder and asserting 503 would make it a test.
- The `catchSome` in SearchRestService also maps timeouts from ancillary lookups (mappings, ontologies) inside `gravsearchV2` to the "may be too broad" 503; the plan intends prequery, count and main query alike, and the message hedges.
