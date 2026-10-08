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
| 2 | reviewed | dsp-api@ec93ccc6b | 2 |

## Chunk queue

| id | repo | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | slice/search/SearchTimeoutException.scala | — | "Gravsearch-specific hedged message"; "rewrite the class doc" | `gravsearchMessage` exists; doc covers both endpoint families, links store-layer exception | SearchTimeoutException.scala § object + class doc | — |
| 1.2 | dsp-api | slice/api/v2/search/SearchRestService.scala, SearchEndpoints.scala, test SearchEndpointsSpec.scala, new test SearchRestServiceSpec.scala | 1.1 | "SearchRestService.gravsearch translate"; "gravsearchCount same"; 4x "errorOutVariantsPrepend"; "update the comment :69-72"; "SearchEndpointsSpec"; 2x "Unit test" | timeout from gravsearchV2 / gravsearchCountV2 -> SearchTimeoutException(gravsearchMessage); 4 gravsearch endpoints advertise 503, others do not | SearchRestService.scala § gravsearch/gravsearchCount; SearchEndpoints.scala:69-118 | — |
| 1.3 | dsp-api | responders/v2/SearchResponderV2.scala | — | "SearchResponderV2.scala:813 tapError(logPrequeryFailure(countSparql))" | count prequery failure logged with SPARQL, inside stageSpan | SearchResponderV2.scala:813, logPrequeryFailure :570 | — |
| 1.4 | dsp-api | docs/03-endpoints/api-v2/query-language.md | 1.2 | "query-language.md:71 replace 504"; "Grep dsp-app for searchextended" | docs state 503 + message; dsp-app uses dsp-js only (verified, no generated client) | query-language.md:71 | — |
| 1.5 | dsp-api | — (verification) | 1.1-1.4 | "bazel test //modules/webapi:test passes" | full webapi suite green | — | — |
| 2.1 | dsp-api | search/SparqlQuery.scala, prequery/AbstractPrequeryGenerator.scala, transformers/PrequeryPatternOrdering.scala, transformers/SparqlTransformer.scala, test PrequeryPatternOrderingSpec.scala, new test LuceneQueryArgsSpec.scala | — | "add LuceneQueryArgs"; "Move escapeForSparqlLiteral"; "matchFulltextLuceneStatement call LuceneQueryArgs.escape"; "unitKey"; "escapeEntityForVariable"; "PrequeryPatternOrderingSpec"; 2x "Unit spec for LuceneQueryArgs.toSparql" | entity renders `("<escaped>" <limit>)`; matchFulltext output unchanged; no golden changes | SparqlQuery.scala § XsdLiteral; generator :2051-2112 | — |
| 2.2 | dsp-api | prequery/AbstractPrequeryGenerator.scala, test-it prequery goldens | 2.1 | "lucenePattern object becomes LuceneQueryArgs"; "Regenerate goldens"; "Check the golden diff" | exactly 3 lines in __optional/__reorderWithUnion change; matchFulltext goldens untouched | generator :1909-1919 | — |
| 2.3 | dsp-api | test-it GravsearchToPrequeryTransformerE2ESpec, GravsearchToCountPrequeryTransformerE2ESpec + new goldens | 2.2 | 5x "New golden case" | goldens pin limit for matchText/matchLabel/matchTextInStandoff/matchLabel count and escaping | test-it prequery specs | — |
| 2.4 | dsp-api | test-e2e matchText E2E spec, MatchFulltextE2ESpec.scala | 2.2 | 3x "E2E"; "MatchFulltextE2ESpec.scala:282 comment" | unbalanced -> same status as /v2/search, never 500; phrase and LF -> 200 | MatchFulltextE2ESpec.scala:256-295 | — |
| 2.5 | dsp-api | docs/03-endpoints/api-v2/query-language.md, docs/development/dsp-api-fuseki-query-execution.md | 2.2 | 2x "query-language.md"; "Fact 9" | docs name matchFulltext as the only capped function (DEV-7489), all text functions escape | query-language.md:467-476,708-712,733; fuseki doc :153-159 | — |
| 2.5a | dsp-api | prequery/AbstractPrequeryGenerator.scala, test-it prequery spec + golden | 2.2 | "matchTextInStandoff regex FILTER escaping" (added) | regex FILTER literals escaped; golden pins it | generator :1777-1797 | — |
| 2.6 | dsp-api | — (verification) | 2.1-2.5 | "Grep text#query"; "webapi:test"; "just test-it"; "just test-e2e"; "just check" | all gates green; one unlimited emission carrying TODO(DEV-7489) | — | — |

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
| 2.1 | dsp-api | complete | 8b773a819 | LuceneQueryArgs entity + companion escape (moved unchanged); matchFulltext calls it, stays XsdLiteral with TODO(DEV-7489); unitKey + escapeEntityForVariable cases; LuceneQueryArgsSpec; ordering spec bare lucene stmt uses LuceneQueryArgs | none |
| 2.2 | dsp-api | complete | c34c71ab9 | lucenePattern emits LuceneQueryArgs(term, luceneHitLimit); goldens regenerated: 3 lines in __optional/__reorderWithUnion, text:query object only, no moves; matchFulltext goldens untouched; webapi suite green (2303) | none |
| 2.3 | dsp-api | complete | 81ca630db | new goldens: matchText (simple+complex share one), matchTextEscaped, matchLabel, matchTextInStandoff, count matchLabel; test-it needs --test_env=DOCKER_HOST --test_env=TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock locally (colima) | none |
| 2.4 | dsp-api | complete | 41c675147 | SearchEndpointPostGravsearchCountE2ESpec suite: unbalanced matchText/matchLabel = /v2/search status, never 500; phrase + LF -> 200 (all 4 fail without the fix, confirmed); MatchFulltextE2ESpec comment names LuceneQueryArgs.escape | none |
| 2.5 | dsp-api | complete | fe1b23a9b, c409de4c7 | query-language.md: matchText/matchLabel/matchTextInStandoff uncapped, matchFulltext cap -> DEV-7489, every text function escapes; fuseki doc Fact 9 lists emissions with luceneHitLimit; the plan's ":467-476" escaping notes are about regex, left unchanged | none |
| 2.5a | dsp-api | complete | 21d2b5cd3 | found during execution: matchTextInStandoff regex FILTER literals were unescaped (quote/backslash still broke SPARQL); now LuceneQueryArgs.escape; matchTextInStandoffEscaped golden | none |
| 2.6 | dsp-api | complete | — (verification at c409de4c7) | grep: one unlimited text:query emission (matchFulltextLuceneStatement, TODO(DEV-7489)); webapi 2303 green; test-it + test_gravsearch_span green; test-e2e 981 green; just check green. Docker targets run via bazel test with --test_env=DOCKER_HOST --test_env=TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock (colima; bare `just test-it` cannot find Docker here) | none |
| 2.7 | dsp-api | complete | 4f3772bd7 | review fix (round 1): escape helper moved from LuceneQueryArgs to neutral SparqlStringLiteral (simplicity/dune/consistency); LuceneQueryArgs KDoc names the matchFulltext exception (DEV-7489); standoff TODO + golden test name state regex semantics unchanged (scala-zio); docs give the 1,000,000 limit instead of "not capped" (consistency) | none |
| 2.8 | dsp-api | complete | 7bec9e099 | review fix (round 2, simplicity re-review): the two matchTextInStandoff regex TODOs folded into one constraint comment; comment-only, compiled + just check green; cap reached | none |

## Deferrals

- ARCH-MAP.md webapi-search/webapi-api boundary rule ("Gravsearch timeout -> 503 translation lives in SearchRestService, fulltext in SearchResponderV2; a timeout-advertising endpoint must translate"), from the phase 1 dune review. Not edited by the executor: ARCH-MAP.md is owned by `/dune:dune-map update`; for the session to run before ship. Phase 2 dune re-review: the search component (`messages/util/search/**`) fingerprint is stale too (new `SparqlStringLiteral` escape owner).
- `scope` Gravsearch generator still renders other user strings into SPARQL unescaped (phase 2 security review): `XsdLiteral.toSparql` (SparqlQuery.scala, every user literal via GravsearchParser.scala:259) and user `regex(?x, "pattern")` via `RegexFunction.toSparql`; a `"` breaks out of the prequery literal (prequery result manipulation; main query re-applies permissions). Fix centrally in the two `toSparql`s with `SparqlStringLiteral.escape`, then drop the call-site escapes. Beyond this plan's three text functions.
- `scope` matchTextInStandoff regex step (phase 2 scala-zio review): terms are split on spaces and used as raw regexes, so a Lucene phrase keeps its quotes and matches nothing, and `c\d` is a digit class; before this phase such terms were a 500. Pre-existing TODO; now documented in query-language.md. Needs regex-quoting / phrase handling.
- Dune DUNE-001 (phase 2): matchFulltext's text:query object is still an `XsdLiteral`, structurally indistinguishable from a forgotten limit; make the exception typed (e.g. `LuceneQueryArgs` with an optional limit rendering byte-identically) when DEV-7489 lands. DUNE-004: no static guard that every `text#query` in main sources passes a limit.

## Side findings

- `just check` fails on this journal itself: MD034 bare URL in the `## Repos` row's `pr` cell (session-written; wrap it in `<...>`). All code gates (scalafmt, SPDX, relative imports, markdownlint elsewhere) pass.
- Phase 1 review, kept as suggestions: `logPrequeryFailure` logs a Gravsearch timeout at ERROR with a cause, and `TriplestoreServiceLive` already logs a client read timeout, so that path double-logs; the 120s main query has no SPARQL-carrying failure log. Pre-existing; now that a timeout is a handled 503, consider WARN without cause (alerting decision).
- Phase 1 review, not taken: replacing the hand-written `SearchResponderV2` stub in `SearchRestServiceSpec` with a pure translator function (the plan asked for a rest-service spec over a stub responder, which is what proves the wiring); a wire-level 503 test (the variant uses the same tapir mechanism as the fulltext endpoints, covered by `SearchEndpointsSpec`).
- Optional hardening (dune re-review, low): the rule "a handler behind searchTimeoutVariant must translate" is comment-enforced only; a spec driving each variant-carrying server endpoint with a timing-out responder and asserting 503 would make it a test.
- The `catchSome` in SearchRestService also maps timeouts from ancillary lookups (mappings, ontologies) inside `gravsearchV2` to the "may be too broad" 503; the plan intends prequery, count and main query alike, and the message hedges.
- Phase 2 review, not taken (suggestions): drop the explicit `LuceneQueryArgs` case in `SparqlTransformer.escapeEntityForVariable` (the plan asked for it); merge the `XsdLiteral | LuceneQueryArgs` cases in `unitKey`; the `matchLabel` E2E compares against `/v2/search` (fulltext), a loose baseline but a sound never-500 guard; `GravsearchInferencePipelineTestSupport.entityKind` renders a `LuceneQueryArgs` object as `other` (no shape golden contains a text:query statement today); PrequeryPatternOrdering Scaladoc (:52, :261, :294) mentions only IriRef/XsdLiteral as bound terms; the escaping sentence appears in both the matchText and matchFulltext doc sections.
- Local Docker-backed tests on colima need `--test_env=DOCKER_HOST --test_env=TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`; bare `just test-it` / `just test-e2e` fail with "Could not find a valid Docker environment".
- Phase 2 re-review suggestions, not taken: a direct `SparqlStringLiteralSpec` (escape is covered via `LuceneQueryArgsSpec`); the numbers 1,000,000 / 10,000 in query-language.md duplicate `luceneHitLimit` and Jena's default; a pointer to `SparqlStringLiteral.escape` on `XsdLiteral` / `RegexFunction` until the `scope` deferral lands.
