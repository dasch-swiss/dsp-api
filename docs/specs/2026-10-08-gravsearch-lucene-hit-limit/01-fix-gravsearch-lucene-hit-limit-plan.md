---
title: "fix: pass an explicit Lucene hit limit in Gravsearch text functions (DEV-6824)"
type: fix
date: 2026-10-08
author: "Balduin Landolt"
status: draft
repository: /Users/balduinlandolt/Documents/GitHub/dasch-swiss/dsp-api/.claude/worktrees/DEV-6824
repositories: []
linear: DEV-6824
linear_project: DSP-API query performance and search correctness
---

# fix: pass an explicit Lucene hit limit in Gravsearch text functions (DEV-6824)

## Overview

Gravsearch's `knora-api:matchText`, `matchTextInStandoff`, `matchLabel` and `matchFulltext` emit
`?x <http://jena.apache.org/text#query> "term"` with no limit. Jena then silently caps the Lucene lookup at
10,000 hits (`TextIndexLucene.MAX_N`), and for wildcard terms the surviving subset is arbitrary. DEV-6823 fixed
the same bug for `/v2/search` and search-by-label by passing `("term" luceneHitLimit)`. This plan does the same
for Gravsearch, in three phases:

1. Turn a Gravsearch timeout into a legible 503 instead of a bare 500. This is independent and ships either way.
2. Measure on stage whether the larger candidate sets are affordable, because Gravsearch has no breadth guard
   (unlike `/v2/search` after DEV-6864).
3. Add the limit. While touching the same rendering, fix the missing SPARQL escaping of the term in
   `matchText` / `matchTextInStandoff` / `matchLabel`.

## Problem Statement / Motivation

- **Wrong results, no error.** Support report (Linear comment, 2026-10): dsp-app advanced search on
  `drawings-gods:DrawingPublic` (project 0105), label "matches" `ir14*`, which dsp-app emits as
  `FILTER knora-api:matchLabel(?r, "ir14*")`.
    - Prod returns 1,954 results, stage returns 0, and the true count is 3,031.
    - The raw `text:query "ir14*"` returns exactly 10,000 hits without a limit and 34,306 with a limit of
    1,000,000.
    - The capped window is filled entirely by 0105's own data, so project-scoping the lookup (DEV-7377) would not
    avoid the cap here. Only the explicit limit fixes it.
- **Non-determinism.** Results change after a reindex because Lucene doc order changes. This is the likely cause
  behind "worked before, broken now" reports and DEV-3695 (linkToClass values not found).
- **The docs know about it.** `docs/03-endpoints/api-v2/query-language.md:708-712` says `matchFulltext` "still
  inherits Jena's silent ~10,000-hit Lucene cap … (DEV-6824)".
- **Gravsearch timeouts are a bare 500.** The docs claim a 504 (`query-language.md:71`), but in fact a
  `TriplestoreTimeoutException` falls through to `BaseEndpoints`' catch-all.
- **Escaping gap.** `lucenePattern` renders the term through `XsdLiteral.toSparql`, which concatenates without
  escaping.
    - A `"`, `\`, LF or CR in a `matchText` / `matchLabel` term breaks the generated SPARQL. That is a 500 today,
    and it lets a client alter the prequery's result set. The main query re-applies permissions, so this is
    result manipulation, not a data leak.
    - `matchFulltext` already escapes (`escapeForSparqlLiteral`, `AbstractPrequeryGenerator.scala:2050-2060`), and
    records the other functions' gap as "tracked separately".

## Proposed Solution

1. **Phase 1, legible Gravsearch timeouts.** On the four `/v2/searchextended` endpoints, translate
   `TriplestoreTimeoutException` into `SearchTimeoutException` and a 503, with a Gravsearch-specific hedged
   message.
   - This reuses DEV-6864's exception type and endpoint variant.
   - The translation sits in `SearchRestService`, so the many internal callers of `gravsearchV2` /
     `gravsearchCountV2` keep their behaviour. Those are `searchIncomingLinks`, the still-image and region
     searches, `ResourcesResponderV2`, `ValuesResponderV2`.
2. **Phase 2, measure on stage.** DEV-6864's D5 recorded that adding the limit to `matchFulltext` alone could
   reproduce the `/v2/search` timeout bug.
   - For broad terms the candidate set grows about 13× (`der` ≈ 130k hits), and Lucene is the tier-1 anchor
     every other pattern joins against.
   - Time the real prequeries with and without the limit, against a pre-set bar. If the bar is not met, stop and
     escalate rather than ship a latency regression.
3. **Phase 3, the limit plus escaping.**
   - Add one new AST entity, `LuceneQueryArgs`, in `SparqlQuery.scala`. It renders `("<escaped term>" <limit>)`.
   - Use it at both generator sites, with `OntologyConstants.Fuseki.luceneHitLimit`.
   - Keep the prequery pattern order unchanged for all existing goldens.
   - Add the missing goldens, and update the docs.

A breadth guard for Gravsearch (porting DEV-6864's LITERAL-LENGTH / PROBE to `matchFulltext`) is **out of
scope**. If Phase 2's worst-case numbers call for it, it becomes a follow-up ticket (H2).

## Alternative Approaches Considered

- **Limit only, no measurement.** Rejected. DEV-6864 shipped exactly that for `/v2/search` and caused prod 500s
  on common terms.
- **Limit plus a ported breadth guard in this plan.** Deferred. It roughly doubles the scope, touches dsp-app's
  advanced-search error handling, and may not be needed. Phase 2 decides.
- **Split by function** (limit `matchText`/`matchLabel` now, `matchFulltext` later). Rejected. All five sites
  share one rendering, and leaving one uncapped keeps the docs' "two result sets" caveat alive.
- **Translate the timeout in `SearchResponderV2`** (as DEV-6864 did for `/v2/search`). Rejected. `gravsearchV2`
  and `gravsearchCountV2` have callers beyond `/v2/searchextended`. They would get a new exception type and error
  body without advertising the 503.
- **Represent the list object without a new entity** (for example a pre-rendered string in `XsdLiteral`).
  Rejected. `XsdLiteral` would render the parentheses inside quotes.

## Technical Considerations

### The AST change

`Entity` is a sealed trait in `modules/webapi/src/main/scala/org/knora/webapi/messages/util/search/SparqlQuery.scala:25`.
It extends `Expression`, whose only abstract members are `toSparql` and `getVariables` (`:204-210`). Its
subtypes are `QueryVariable`, `GroupConcat`, `Count`, `IriRef` and `XsdLiteral`, and none can express an RDF
collection. Add the following there:

```scala
/**
 * The object of a Jena `text:query` statement: `("term" limit)`. Without the limit Jena silently caps the
 * Lucene lookup at 10,000 hits (DEV-6822). The term is escaped for a SPARQL string literal, since it is
 * user input.
 */
final case class LuceneQueryArgs(term: String, limit: Int) extends Entity {
  override def toSparql: String                  = s"(\"${LuceneQueryArgs.escape(term)}\" $limit)"
  override def getVariables: Set[QueryVariable] = Set.empty
}
```

- **Rendering** matches the shape `/v2/search` already emits (`SearchFulltextQuery.scala:66,143`:
  `($searchLiteral $luceneHitLimit)`), that is, a plain literal, not `^^xsd:string`. Under RDF 1.1 the two are
  the same term, and Phase 2 measures exactly this shape.
- **Escaping** moves from `AbstractPrequeryGenerator.escapeForSparqlLiteral` into the entity's companion,
  unchanged. Backslash goes first, then `"`, LF and CR.
    - `GravsearchParser.scala:259` takes `literal.stringValue`, so the generator already receives the raw,
    SPARQL-unescaped term. `matchFulltextLuceneStatement` must pass it on raw, or it would double-escape.
    - Lucene's own `\"` survives the round trip.
- **Behaviour change for `matchText` / `matchLabel`.** A balanced Lucene phrase (`"\"foo bar\""` in Gravsearch)
  breaks the SPARQL today and works afterwards. An unbalanced `"` or `\` becomes Jena's Lucene parse 400, the
  same as `matchFulltext` and `/v2/search` (`MatchFulltextE2ESpec.scala:256-278`).
- **Ordering.** `PrequeryPatternOrdering.scala:333-340` (`unitKey`) counts `IriRef` / `XsdLiteral` objects as
  restricted terms. A `LuceneQueryArgs` object would count 0 and could re-break ties between tier-1 units, so add
  `case _: LuceneQueryArgs => true` there.
    - Tier assignment (`:229-247`) and `containsLucene` (`:208-212`) key on the predicate only.
    - The final tie-break is the rendered SPARQL string (`UnitKey.sparql`), which changes. Ordering is therefore
    guaranteed unchanged for the existing goldens. In general, two tier-1 lucene units could in theory swap. That
    is harmless, since both are anchors.
- **Other matches over `Entity`** have wildcard fallbacks or match on predicates or IRIs only, with one
  exception. `SparqlTransformer.escapeEntityForVariable` (`:27-33`) **throws** for any unknown `Entity`. It is
  reached via `createUniqueVariableFromStatement` (`:75-80`) for link-value and list-node statements only, never
  for the lucene statement. Leave it, but give it an explicit `LuceneQueryArgs` case that throws a clear message,
  so the trap is visible.
- Type inspection runs before generation (`SearchResponderV2.scala:760-761`, `835-836`) and never sees generated
  statements. The `XsdLiteral` matches in `GravsearchQueryChecker:79`, `InferringGravsearchTypeInspector:1402`
  and `GravsearchTypeInspectionRunner:130` run on parsed user input.
- `matchLabel` is field-less. `lucenePattern` emits `?r text:query "term"` with no `rdfs:label` argument, so its
  Lucene candidates include value objects, which the class/label joins then drop. That is pre-existing behaviour
  (see DEV-6851) and not changed here. It is why C1's raw hit count (34,306) far exceeds its result count
  (3,031).
- The ticket's `SparqlTransformer.scala:156` lucene special case no longer exists. DEV-7287 replaced it with
  `PrequeryPatternOrdering`, covered above.

### The timeout translation

- `SearchRestService.gravsearch` / `gravsearchCount` (`slice/api/v2/search/SearchRestService.scala:60-78`) back
  exactly the four `/v2/searchextended` endpoints (`SearchServerEndpoints.scala:25-28`).
    - Catch `TriplestoreTimeoutException` there (`store.triplestore.errors.TriplestoreTimeoutException`) and fail
    with `SearchTimeoutException(SearchTimeoutException.gravsearchMessage)`.
    - This covers the prequery, the count query and the main query alike.
- **Message.** The existing `defaultMessage` ("…Try adding another word.") is fulltext-specific. Add a
  Gravsearch message, along these lines: "This search could not be completed in time; it may be too broad. Try
  narrowing it, for example with a more specific search term or an additional restriction." It hedges, because a
  timeout does not prove breadth.
- **Logging.** The page prequery already logs the SPARQL on timeout (`tapError(logPrequeryFailure(...))`,
  `SearchResponderV2.scala:900-903`). The count path (`:813`) does not, so add the same `tapError` there.
- **Endpoints.** Attach `searchTimeoutVariant` to `postGravsearch`, `getGravsearch`, `postGravsearchCount` and
  `getGravsearchCount` (`SearchEndpoints.scala:78-118`) with `errorOutVariantsPrepend`. Place it as on the
  fulltext endpoints (`:207`, `:220`).
    - Never add it to `BaseEndpoints.errorOutputs` (`CONVENTIONS.md` § Error handling).
    - The other search endpoints (incoming links, still images, regions, by-label) stay without it.
- **OpenAPI.** This changes the OpenAPI spec of `/v2/searchextended`. dsp-app's mirrored copy is bot-synced after
  deploy, so never hand-edit it. Phase 1 checks that dsp-app reaches the route only through dsp-js.

### Performance

- Lucene is the tier-1 anchor of every prequery that uses a text function. More hits means proportionally more
  joins.
- Gravsearch has no breadth guard (no `raceFirst` / `FulltextBreadthGuard` on its path), the gravsearch tier is
  120 s, and Fuseki has no kill API beyond the `timeout=` parameter.
- Main real-world consumers:
    - dsp-app advanced search: `matchFulltext` and `matchLabel`.
    - The PIA harvester (about 69 % of prod API traffic, projects 0812/082A): `matchText`.

## Implementation Phases

### Phase 1: Gravsearch timeouts become a 503

- [ ] `SearchTimeoutException.scala`: add a Gravsearch-specific hedged message next to `defaultMessage`
- [ ] `SearchTimeoutException.scala`: rewrite the class doc. It covers the fulltext and Gravsearch endpoints, and
      the link points to `org.knora.webapi.store.triplestore.errors.TriplestoreTimeoutException`, not
      `dsp.errors…`.
- [ ] `SearchRestService.gravsearch`: translate `TriplestoreTimeoutException` to
      `SearchTimeoutException(gravsearchMessage)`
- [ ] `SearchRestService.gravsearchCount`: same translation
- [ ] `SearchResponderV2.scala:813`: add `tapError(logPrequeryFailure(countSparql))` to the Gravsearch count
      execute, inside the `stageSpan`
- [ ] `SearchEndpoints.scala`: `errorOutVariantsPrepend(searchTimeoutVariant)` on `postGravsearch`
- [ ] `SearchEndpoints.scala`: the same on `getGravsearch`
- [ ] `SearchEndpoints.scala`: the same on `postGravsearchCount`
- [ ] `SearchEndpoints.scala`: the same on `getGravsearchCount`
- [ ] `SearchEndpoints.scala:69-72`: update the comment ("only to the two fulltext endpoints")
- [ ] `SearchEndpointsSpec.scala:62-65`: change the test so all four Gravsearch endpoints advertise a 503, while
      search-by-label, incoming links, still images and regions do not
- [ ] Unit test in `SearchRestService`'s spec, or a new one with a stub `SearchResponderV2` if none exists:
      a `TriplestoreTimeoutException` from `gravsearchV2` yields `SearchTimeoutException` with the Gravsearch
      message
- [ ] Unit test: the same for `gravsearchCountV2`
- [ ] `docs/03-endpoints/api-v2/query-language.md:71`: replace the "504 Gateway Timeout" claim with the 503 and
      its message
- [ ] Grep `/Users/balduinlandolt/Documents/GitHub/dasch-swiss/dsp-app` for `searchextended`. Confirm the calls
      go through dsp-js, not the generated OpenAPI client. If any use the generated client, stop and escalate.
- [ ] `bazel test //modules/webapi:test` passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 2: Measure the limit on stage

No production code changes. All SPARQL runs read-only on stage via `dsp vre sparql query -s stage --timeout 120
--accept csv --query-file …`, following the `misc:dsp-cli` skill. Never use a local dump. Stage is a shared box,
so use the median of three runs and treat absolute values as ±50 %.

How to get real prequeries:

- **Real support case:** POST the Gravsearch to the stage API (`/v2/searchextended`, read-only), then read the
  generated prequery from the trace's root span (`recordPrequeryOnRoot`). See
  `docs/observability/gravsearch-trace-runbook.md` and `docs/observability/using-grafana.md`.
- **Prod traffic shapes** (PIA, dsp-app): take them from prod Tempo `gravsearch.query` events, then generate the
  prequery the same way on stage.
- **Limited variant:** replace the `text:query` object `"term"^^xsd:string` with `("term" 1000000)`.

Cases:

| Id | Case | Source |
| --- | --- | --- |
| C1 | 0105 `DrawingPublic`, `matchLabel(?r, "ir14*")`, count query | Linear comment on DEV-6824 |
| C2 | dsp-app advanced search, `matchFulltext` with `limitToProject`, broad terms (`der`, `und`, `brief`) in LIMC and in 0806 or 0102, page and count | DEV-7377 slow-span list |
| C3 | `matchFulltext`, classless, no project, term `der` (worst case) | golden `classlessMatchFulltext` shape |
| C4 | PIA harvester's three most frequent `matchText` query shapes (0812/082A, simple schema if that is what PIA sends) | prod Tempo |
| C5 | one broad `matchTextInStandoff` query, if any appears in prod traces; else skip and note it | prod Tempo |
| C6 | one narrow term per function (raw hit count well under 10k) | any of the above, narrowed |

Bar (proposed; adjust in review before start, H1):

- **B1:** C1 limited count = 3,031.
- **B2:** every C2 and C4 limited median ≤ 30 s.
- **B3:** every C4 limited median ≤ 2× its unlimited median. This is a latency ratio, not result parity, because
  the unlimited runs return a truncated, smaller set. PIA is most of prod load, hence the bar.
- **B4:** every C6 returns identical result counts with and without the limit, since the limit must not change
  anything under the cap.
- A case whose unlimited baseline times out counts as 120 s.
- C3 and C5 are recorded, not gated. A C3 timeout is expected, now surfaces as Phase 1's 503, and is the input for
  H2.

- [ ] Write C1's queries (Gravsearch, plus the unlimited and limited prequery) to
      `docs/specs/2026-10-08-gravsearch-lucene-hit-limit/assets/`
- [ ] Write C2's queries to the same folder
- [ ] Write C3's queries to the same folder
- [ ] Write C4's queries to the same folder
- [ ] Write C5's queries to the same folder, or note why C5 is skipped
- [ ] Write C6's queries to the same folder
- [ ] Run C1 unlimited and limited; record result counts
- [ ] Run C2 unlimited and limited (page and count), three runs each; record medians and result counts
- [ ] Run C3 unlimited and limited, three runs each; record medians, result counts and timeouts
- [ ] Run C4 unlimited and limited, three runs each; record medians and result counts
- [ ] Run C5 unlimited and limited, if not skipped
- [ ] Run C6 unlimited and limited; record result counts
- [ ] Write `02-gravsearch-lucene-limit-measurements-design.md` in the spec folder: a results table per case,
      B1-B4 verdicts, and the C3 worst case stated as the input for H2
- [ ] If any of B1-B4 fails, stop here and escalate with the table. Do not start Phase 3. Phase 1 can still ship
      alone.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 3: Lucene hit limit and term escaping in the Gravsearch prequery

- [ ] `SparqlQuery.scala`: add `LuceneQueryArgs(term: String, limit: Int) extends Entity`, rendering
      `("<escaped>" <limit>)`
- [ ] Move `escapeForSparqlLiteral` unchanged from `AbstractPrequeryGenerator` (`:2050-2060`) into
      `LuceneQueryArgs`' companion, and drop the generator's copy with its "tracked separately" comment
- [ ] `AbstractPrequeryGenerator.lucenePattern` (`:1909-1919`): object becomes
      `LuceneQueryArgs(queryString, OntologyConstants.Fuseki.luceneHitLimit)`
- [ ] `AbstractPrequeryGenerator.matchFulltextLuceneStatement` (`:2104-2112`): same change, passing the raw term
- [ ] `PrequeryPatternOrdering.scala:333-340` (`unitKey`): count a `LuceneQueryArgs` object as restricted
- [ ] `SparqlTransformer.escapeEntityForVariable` (`:27-33`): explicit `LuceneQueryArgs` case that throws a
      clear `GravsearchException` message
- [ ] `PrequeryPatternOrderingSpec.scala` (`:231`, `:246`): build the hand-made lucene statements with
      `LuceneQueryArgs`; its expectations stay unchanged
- [ ] Unit spec for `LuceneQueryArgs.toSparql`: a plain term, with the limit rendered
- [ ] Unit spec for `LuceneQueryArgs.toSparql`: terms containing `"`, `\`, LF and CR are escaped
- [ ] Regenerate the existing prequery and count-prequery goldens:
      `bazel test //modules/test-it:test --test_filter='.*GravsearchTo.*PrequeryTransformerE2ESpec.*' --test_env=GOLDEN_REWRITE=1`,
      then rerun without the env var
- [ ] Check the golden diff: exactly 7 lines in 6 files change, each only in the `text:query` object, and no line
      moves. The files are the prequery goldens `optional`, `matchFulltextInUnion`, `classlessMatchFulltext`,
      `classRestrictedMatchFulltext`, `reorderWithUnion` (2 lines), and the count golden
      `classlessMatchFulltext`.
- [ ] New golden case in `GravsearchToPrequeryTransformerE2ESpec`: `matchText` (complex schema). No
      `lucenePattern` golden exists today.
- [ ] New golden case: `matchText` in the simple schema
- [ ] New golden case: `matchLabel`
- [ ] New golden case: `matchTextInStandoff`
- [ ] New golden case in `GravsearchToCountPrequeryTransformerE2ESpec`: `matchLabel` count
- [ ] New golden case: `matchText` with a term containing `"` and `\`, showing the escaped rendering
- [ ] E2E: Gravsearch `matchText` and `matchLabel` with an unbalanced `"` / `\` in the term give the same status
      as `/v2/search` for that term and never 500. Mirror `MatchFulltextE2ESpec.scala:256-278`, next to the
      existing matchText E2E coverage; find that with grep first.
- [ ] E2E: Gravsearch `matchText` with a balanced phrase (`"\"foo bar\""`) returns 200
- [ ] E2E: Gravsearch `matchText` with an embedded LF returns 200
- [ ] `MatchFulltextE2ESpec.scala:282`: update the comment that names `escapeForSparqlLiteral`
- [ ] `docs/03-endpoints/api-v2/query-language.md:708-712`: replace the cap caveat. `matchFulltext` and
      `/v2/search` pass the same hit limit and return the same result set.
- [ ] `docs/03-endpoints/api-v2/query-language.md` (escaping notes around `:467-476` and `:733`): state that all
      text functions escape the term
- [ ] `docs/05-internals/design/api-v2/gravsearch.md` (`:483-552`): show the limit in the expansion description,
      and make sure any escaping note matches
- [ ] `docs/development/dsp-api-fuseki-query-execution.md` Fact 9 (`:153-159`): state that every `text:query`
      emission passes `luceneHitLimit`
- [ ] Grep `jena.apache.org/text#query` and `luceneQueryPredicate` across `modules/*/src/main`; confirm all six
      emissions pass a limit (`SearchQueries.scala:58,113`, `SearchFulltextQuery.scala:66,143`, both generator
      sites)
- [ ] `bazel test //modules/webapi:test` passes
- [ ] `just test-it` passes (needs Docker)
- [ ] `just test-e2e` passes, including `MatchFulltextE2ESpec` (needs Docker)
- [ ] `just check` passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H1 | Review and adjust Phase 2's bar (B1-B4 thresholds) and its case list | Balduin | before start | The latency budget is a product decision |
| H2 | Decide whether a Gravsearch breadth guard for `matchFulltext` is needed, from the measurements doc's C3 result, and file the ticket if so | Balduin | after ship | An org/priority decision |
| H3 | After the stage deploy, rerun the 0105 `ir14*` advanced search in dsp-app on stage and confirm 3,031 results | Balduin | after ship | Needs the deployed build and the dsp-app UI |
| H4 | Comment on DEV-3695 that DEV-6824 is the likely fix, and decide whether to close it | Balduin | after ship | Posting on Linear is the owner's call |
| H5 | Decide whether dsp-app's advanced search needs a dedicated message for the new 503 (today it shows its generic error) | Balduin | after ship | Product/UX decision in another repo |

## Acceptance Criteria

- [ ] Every `text:query` emission in `modules/*/src/main` passes an explicit limit. That is `luceneHitLimit` at
      all six sites: 2 in `SearchQueries`, 2 in `SearchFulltextQuery`, 2 in `AbstractPrequeryGenerator`.
- [ ] On stage, C1's limited count prequery returns 3,031 (Phase 2). After deploy, the dsp-app search returns
      the same (H3).
- [ ] Phase 2's bar B1-B4 is met and recorded in the measurements doc.
- [ ] Prequery pattern order is unchanged for every existing golden. The diff touches only the `text:query`
      object, on 7 lines in 6 files.
- [ ] `matchText` (simple and complex), `matchLabel`, `matchTextInStandoff` and a `matchLabel` count are each
      pinned by a golden.
- [ ] A `"`, `\`, LF or CR in any text-function term is escaped in the generated SPARQL. Covered by unit specs
      and a golden.
- [ ] An unbalanced `"` / `\` in `matchText` / `matchLabel` never yields a 500. A balanced phrase and an LF
      yield 200.
- [ ] A Gravsearch timeout on `/v2/searchextended` (page or count) returns 503 with the Gravsearch message. The
      page and count prequeries are logged with their SPARQL. No other endpoint's error surface changes.
- [ ] The docs no longer claim `matchFulltext` is capped or that Gravsearch timeouts return 504.
- [ ] `bazel test //modules/webapi:test`, `just test-it`, `just test-e2e` and `just check` pass.

## Dependencies & Risks

- **No behavioural test above 10k hits.** The fixtures are far below the cap, as they were for DEV-6823. Goldens
  plus Phase 2's stage run plus H3 are the evidence. A >10k synthetic fixture is not worth its cost to every
  test run.
- **Stage access** (`dsp vre sparql`, stage API, Grafana/Tempo for stage and prod) is needed for Phase 2.
- **PIA load.** If the harvester's queries get slower, prod load rises noticeably. B3 guards this.
- **DEV-6851 / DEV-7377 interplay.** Both need a Fuseki reindex and would shrink candidate sets later (label
  field, graph scoping). The limit is still required with either, as the 0105 case shows. No ordering
  dependency.
- **The noisy instrument.** Stage is a shared prod mirror, with noise up to ±50 %. Use medians of three runs and
  compare ratios.

## Risk Analysis & Mitigation

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| Broad-term Gravsearch latency grows toward the 120 s tier | M | H | Phase 2 bar gates the change; Phase 1 makes residual timeouts a legible 503; H2 decides on a guard |
| New entity changes prequery ordering | L | M | `unitKey` case; the golden diff must touch only object text |
| Double escaping in `matchFulltext` after moving escaping into the entity | M | M | Pass the raw term; unit specs plus `MatchFulltextE2ESpec`'s D7 tests (`:256-295`) |
| 503 leaks to endpoints that don't advertise it | L | M | Translate in `SearchRestService`, not the shared responder; `SearchEndpointsSpec` pins which endpoints advertise it |
| Phase 2 bar fails | M | M | Stop and escalate; Phase 1 ships alone; the guard port becomes the precondition |

## Success Metrics

- C1 on stage: unlimited count is below 3,031 (stage showed 0), limited count is 3,031. Baseline: the Linear
  comment's table (prod 1,954 / stage 0 / truth 3,031).
- C2/C4 limited medians are within B2/B3 against their unlimited baselines, and C6 is count-identical, recorded
  in `02-gravsearch-lucene-limit-measurements-design.md`.
- After deploy, the rate of `Gravsearch timed out for prequery` log lines and of 503s on `/v2/searchextended`
  stays within what C3 predicts. Compare the week before and after in Grafana.

## References

- Linear: DEV-6824 (this), DEV-6822 (parent, mechanism), DEV-6823 (`/v2/search` + search-by-label fix,
  `8351746`), DEV-6864 (`/v2/search` timeout follow-up, D5, `8cf16d9`), DEV-6715 (`matchFulltext`), DEV-7377
  (graphField scoping), DEV-6851 (label field), DEV-3695
- Constant: `modules/webapi/src/main/scala/org/knora/webapi/messages/OntologyConstants.scala:1107-1115`
- Precedent emissions: `modules/webapi/src/main/scala/org/knora/webapi/responders/v2/SearchQueries.scala:18,58,113`,
  `modules/webapi/src/main/scala/org/knora/webapi/slice/search/repo/SearchFulltextQuery.scala:22,66,143`
- Generator: `modules/webapi/src/main/scala/org/knora/webapi/messages/util/search/gravsearch/prequery/AbstractPrequeryGenerator.scala:1909-1919,2050-2060,2078-2112`
- Parser (raw term): `modules/webapi/src/main/scala/org/knora/webapi/messages/util/search/gravsearch/GravsearchParser.scala:259`
- Ordering: `modules/webapi/src/main/scala/org/knora/webapi/messages/util/search/gravsearch/transformers/PrequeryPatternOrdering.scala:202-247,333-340`
- Timeout precedent: `SearchResponderV2.scala:554-577,813,900-903`, `slice/search/SearchTimeoutException.scala`,
  `slice/api/v2/search/SearchEndpoints.scala:69-118,197-220`, `slice/api/v2/search/SearchRestService.scala:60-78`
- Goldens: `modules/test-it/src/test/resources/org/knora/webapi/messages/util/search/gravsearch/prequery/`;
  rewrite procedure in `docs/development/dsp-api-conventions.md:254-271`
- Escaping tests: `modules/test-e2e/src/test/scala/org/knora/webapi/e2e/v2/MatchFulltextE2ESpec.scala:256-295`
- Fuseki facts: `docs/development/dsp-api-fuseki-query-execution.md` (Facts 9, 10)
- Observability: `docs/observability/gravsearch-trace-runbook.md`, `docs/observability/traceql-recipes.md`
