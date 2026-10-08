---
title: "fix: pass an explicit Lucene hit limit in Gravsearch matchText / matchLabel (DEV-6824)"
type: fix
date: 2026-10-08
author: "Balduin Landolt"
status: draft
repository: /Users/balduinlandolt/Documents/GitHub/dasch-swiss/dsp-api/.claude/worktrees/DEV-6824
repositories: []
linear: DEV-6824
linear_project: DSP-API query performance and search correctness
---

# fix: pass an explicit Lucene hit limit in Gravsearch matchText / matchLabel (DEV-6824)

## Overview

Gravsearch's text functions emit `?x <http://jena.apache.org/text#query> "term"` with no limit. Jena then silently
caps the Lucene lookup at 10,000 hits (`TextIndexLucene.MAX_N`), and for wildcard terms the surviving subset is
arbitrary. DEV-6823 fixed the same bug for `/v2/search` and search-by-label by passing `("term" luceneHitLimit)`.

This plan fixes it for `knora-api:matchText`, `matchTextInStandoff` and `matchLabel`, which all render through
`lucenePattern`. It also makes Gravsearch timeouts a legible 503, and fixes the missing SPARQL escaping of the
term in those three functions.

`knora-api:matchFulltext` is **deliberately left capped**. Stage measurements
(`02-gravsearch-lucene-limit-measurements-design.md`) show that the limit makes its broad multi-word searches
8–30× slower, up to the 120 s timeout. Making that query fast enough comes first; it has its own ticket, DEV-7489.

## Problem Statement / Motivation

- **Wrong results, no error.** Support report (Linear comment on DEV-6824, 2026-10): dsp-app advanced search on
  `drawings-gods:DrawingPublic` (project 0105), label "matches" `ir14*`, emitted as
  `FILTER knora-api:matchLabel(?r, "ir14*")`.
    - Prod returns 1,954 results, stage returns 0, and the true count is 3,031.
    - Measured on stage: the count prequery returns 0 today and 3,031 with the limit (0.35 s → 0.8 s).
- **Non-determinism.** Results change after a reindex because Lucene doc order changes. This is the likely cause
  of "worked before, broken now" reports and of DEV-3695 (linkToClass values not found).
- **Gravsearch timeouts are a bare 500.** The docs claim a 504 (`query-language.md:71`), but in fact a
  `TriplestoreTimeoutException` falls through to `BaseEndpoints`' catch-all.
- **Escaping gap.** `lucenePattern` renders the term through `XsdLiteral.toSparql`, which concatenates without
  escaping.
    - A `"`, `\`, LF or CR in a `matchText` / `matchLabel` term breaks the generated SPARQL. That is a 500 today,
      and it lets a client alter the prequery's result set. The main query re-applies permissions, so it is result
      manipulation, not a data leak.
    - `matchFulltext` already escapes (`escapeForSparqlLiteral`, `AbstractPrequeryGenerator.scala:2050-2060`).

## Proposed Solution

1. **Phase 1, legible Gravsearch timeouts.** On the four `/v2/searchextended` endpoints, translate
   `TriplestoreTimeoutException` into `SearchTimeoutException` and a 503, with a Gravsearch-specific hedged
   message.
    - This reuses DEV-6864's exception type and endpoint variant.
    - The translation sits in `SearchRestService`, so the many internal callers of `gravsearchV2` /
      `gravsearchCountV2` keep their behaviour: incoming links, still images, regions, `ResourcesResponderV2` and
      `ValuesResponderV2`.
2. **Phase 2, the limit plus escaping for `lucenePattern`.**
    - Add a new AST entity `LuceneQueryArgs` in `SparqlQuery.scala`, rendering `("<escaped term>" <limit>)`.
    - Use it in `lucenePattern` with `OntologyConstants.Fuseki.luceneHitLimit`.
    - Move the escape helper into the entity's companion, and have `matchFulltextLuceneStatement` call it there. Its
      rendering stays a plain `XsdLiteral` with no limit, so its output is unchanged.

## Alternative Approaches Considered

All measured on stage, 2026-10-08; see the measurements doc.

- **The full limit for all four functions.** Rejected for `matchFulltext`.
    - About 20 % of prod `matchFulltext` requests hit the cap today, almost all multi-word terms that Lucene ORs.
    - With the limit their candidates grow to 10k–500k, and the prequery goes from 4–11 s to 32–115 s.
- **A modest limit (20k–50k) for `matchFulltext`.** Rejected.
    - It fully fixes only 16–31 % of today's truncated requests.
    - Cost grows roughly linearly with hits (`fortuna de ostia`: about 4 s at 10k, 13 s at 30k, 23 s at 40k).
    - The rest stays silently wrong.
- **A breadth guard for `matchFulltext`.** It doesn't help: most affected terms sit under DEV-6864's 250k probe
  cap and would still be slow.
- **Translate the timeout in `SearchResponderV2`**, as DEV-6864 did for `/v2/search`. Rejected: `gravsearchV2` and
  `gravsearchCountV2` have callers beyond `/v2/searchextended`, which would get a new error body without
  advertising the 503.

## Technical Considerations

### The AST change

`Entity` is a sealed trait in `modules/webapi/src/main/scala/org/knora/webapi/messages/util/search/SparqlQuery.scala:25`.
It extends `Expression`, whose only abstract members are `toSparql` and `getVariables` (`:204-210`), and none of
its subtypes can express an RDF collection. Add:

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

- **Rendering** matches what `/v2/search` emits (`SearchFulltextQuery.scala:66,143`): a plain literal, not
  `^^xsd:string`. Under RDF 1.1 these are the same term, and this shape is what was measured.
- **Escaping.** `escape` is `AbstractPrequeryGenerator.escapeForSparqlLiteral`, moved unchanged: backslash first,
  then `"`, LF and CR.
    - `GravsearchParser.scala:259` takes `literal.stringValue`, so the generator already receives the raw,
      SPARQL-unescaped term.
    - `matchFulltextLuceneStatement` keeps its `XsdLiteral(escape(term))`, now calling `LuceneQueryArgs.escape`.
      Its output is byte-identical.
- **Behaviour change for `matchText` / `matchLabel`.** A balanced Lucene phrase (`"\"foo bar\""` in Gravsearch)
  breaks the SPARQL today and works afterwards. An unbalanced `"` or `\` becomes Jena's Lucene parse 400, the same
  as `matchFulltext` and `/v2/search` (`MatchFulltextE2ESpec.scala:256-278`).
- **Ordering.** `PrequeryPatternOrdering.scala:333-340` (`unitKey`) counts `IriRef` / `XsdLiteral` objects as
  restricted terms. Add `case _: LuceneQueryArgs => true`, so tier-1 tie-breaks are unchanged.
    - Tier assignment (`:229-247`) and `containsLucene` (`:208-212`) key on the predicate only.
    - The final tie-break is the rendered SPARQL string (`UnitKey.sparql`), which changes. Ordering is therefore
      guaranteed unchanged for the existing goldens. In general two tier-1 lucene units could swap, which is
      harmless since both are anchors.
- **Other matches over `Entity`** have wildcard fallbacks or match on predicates or IRIs only, with one
  exception. `SparqlTransformer.escapeEntityForVariable` (`:27-33`) throws for any unknown `Entity`. It is reached
  only for link-value and list-node statements, never for the lucene statement. Give it an explicit
  `LuceneQueryArgs` case with a clear message, so the trap is visible.
- Type inspection runs before generation (`SearchResponderV2.scala:760-761`, `835-836`) and never sees generated
  statements.
- `matchLabel` is field-less: its Lucene candidates include value objects, which the joins drop (pre-existing,
  DEV-6851). That is why `ir14*` has 34,306 raw hits for 3,031 results.

### Why the limit is safe for `lucenePattern` but not for `matchFulltext`

- The `matchText` / `matchLabel` prequery joins each Lucene hit against cheap bound-subject patterns (label, type,
  project, `isDeleted`). Measured on stage: `ir14*` costs 0.86 s at every limit from 20k to 1M.
- Broad multi-word terms in this shape are the worst case. A classless, unscoped `fortuna de ostia` (63,604
  results) goes from 0.6 s to 19 s, and LIMC-scoped from 0.6 s to 3.6 s. Prod sends almost no such queries.
- `matchFulltext` instead runs two OPTIONAL value/list-node branches and `subClassOf*` / `subPropertyOf*` walks per
  hit, so its cost scales with hits.
- Prod traffic (two weeks of traces): `matchText` / `matchLabel` are rare. Nearly all Lucene Gravsearch traffic
  is dsp-app's `matchFulltext`.

### The timeout translation

- `SearchRestService.gravsearch` / `gravsearchCount` (`slice/api/v2/search/SearchRestService.scala:60-78`) back
  exactly the four `/v2/searchextended` endpoints (`SearchServerEndpoints.scala:25-28`).
    - Catch `TriplestoreTimeoutException` there (`store.triplestore.errors.TriplestoreTimeoutException`) and fail
      with `SearchTimeoutException(SearchTimeoutException.gravsearchMessage)`.
    - This covers the prequery, the count query and the main query alike.
- **Message.** The existing `defaultMessage` ("…Try adding another word.") is fulltext-specific. Add a Gravsearch
  message along these lines: "This search could not be completed in time; it may be too broad. Try narrowing it,
  for example with a more specific search term or an additional restriction."
- **Logging.** The page prequery already logs the SPARQL on failure (`SearchResponderV2.scala:900-903`). The count
  path (`:813`) does not, so add the same `tapError(logPrequeryFailure(...))`.
- **Endpoints.** Attach `searchTimeoutVariant` to the four Gravsearch endpoints (`SearchEndpoints.scala:78-118`)
  with `errorOutVariantsPrepend`, placed as on the fulltext endpoints (`:207`, `:220`).
    - Never add it to `BaseEndpoints.errorOutputs` (`CONVENTIONS.md` § Error handling).
    - The other search endpoints stay without it.
- **OpenAPI.** This changes the OpenAPI spec of `/v2/searchextended`. dsp-app's mirrored copy is bot-synced after
  deploy, so never hand-edit it.

## Implementation Phases

### Phase 1: Gravsearch timeouts become a 503

- [x] `SearchTimeoutException.scala`: add a Gravsearch-specific hedged message next to `defaultMessage`
- [x] `SearchTimeoutException.scala`: rewrite the class doc. It covers the fulltext and Gravsearch endpoints, and
      the link points to `org.knora.webapi.store.triplestore.errors.TriplestoreTimeoutException`, not
      `dsp.errors…`.
- [x] `SearchRestService.gravsearch`: translate `TriplestoreTimeoutException` to
      `SearchTimeoutException(gravsearchMessage)`
- [x] `SearchRestService.gravsearchCount`: same translation
- [x] `SearchResponderV2.scala:813`: add `tapError(logPrequeryFailure(countSparql))` to the Gravsearch count
      execute, inside the `stageSpan`
- [x] `SearchEndpoints.scala`: `errorOutVariantsPrepend(searchTimeoutVariant)` on `postGravsearch`
- [x] `SearchEndpoints.scala`: the same on `getGravsearch`
- [x] `SearchEndpoints.scala`: the same on `postGravsearchCount`
- [x] `SearchEndpoints.scala`: the same on `getGravsearchCount`
- [x] `SearchEndpoints.scala:69-72`: update the comment ("only to the two fulltext endpoints")
- [x] `SearchEndpointsSpec.scala:62-65`: change the test so all four Gravsearch endpoints advertise a 503, while
      search-by-label, incoming links, still images and regions do not
- [x] Unit test (a spec for `SearchRestService` with a stub `SearchResponderV2`, or the existing one): a
      `TriplestoreTimeoutException` from `gravsearchV2` yields `SearchTimeoutException` with the Gravsearch message
- [x] Unit test: the same for `gravsearchCountV2`
- [x] `docs/03-endpoints/api-v2/query-language.md:71`: replace the "504 Gateway Timeout" claim with the 503 and
      its message
- [x] Grep `/Users/balduinlandolt/Documents/GitHub/dasch-swiss/dsp-app` for `searchextended`. Confirm the calls go
      through dsp-js, not the generated OpenAPI client. If any use the generated client, stop and escalate.
- [x] `bazel test //modules/webapi:test` passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 2: Lucene hit limit and term escaping for matchText, matchTextInStandoff and matchLabel

- [ ] `SparqlQuery.scala`: add `LuceneQueryArgs(term: String, limit: Int) extends Entity`, rendering
      `("<escaped>" <limit>)`
- [ ] Move `escapeForSparqlLiteral` unchanged from `AbstractPrequeryGenerator` (`:2050-2060`) into
      `LuceneQueryArgs`' companion as `escape`, and update its doc comment ("tracked separately" no longer holds)
- [ ] `AbstractPrequeryGenerator.matchFulltextLuceneStatement` (`:2104-2112`): call `LuceneQueryArgs.escape`. The
      object stays an `XsdLiteral` with no limit, with a `TODO(DEV-7489)` comment.
- [ ] `AbstractPrequeryGenerator.lucenePattern` (`:1909-1919`): object becomes
      `LuceneQueryArgs(queryString, OntologyConstants.Fuseki.luceneHitLimit)`
- [ ] `PrequeryPatternOrdering.scala:333-340` (`unitKey`): count a `LuceneQueryArgs` object as restricted
- [ ] `SparqlTransformer.escapeEntityForVariable` (`:27-33`): explicit `LuceneQueryArgs` case that throws a clear
      `GravsearchException` message
- [ ] `PrequeryPatternOrderingSpec.scala` (`:231`, `:246`): if these hand-made lucene statements model
      `matchText`, build them with `LuceneQueryArgs`; expectations stay unchanged
- [ ] Unit spec for `LuceneQueryArgs.toSparql`: a plain term, with the limit rendered
- [ ] Unit spec for `LuceneQueryArgs.toSparql`: terms containing `"`, `\`, LF and CR are escaped
- [ ] Regenerate the existing prequery and count-prequery goldens:
      `bazel test //modules/test-it:test --test_filter='.*GravsearchTo.*PrequeryTransformerE2ESpec.*' --test_env=GOLDEN_REWRITE=1`,
      then rerun without the env var
- [ ] Check the golden diff: exactly 3 lines in 2 files change, each only in the `text:query` object, and no line
      moves. The files are `GravsearchToPrequeryTransformerE2ESpec__optional` (1 line) and `__reorderWithUnion`
      (2 lines). The `matchFulltext` goldens must not change.
- [ ] New golden case in `GravsearchToPrequeryTransformerE2ESpec`: `matchText` in the simple schema
- [ ] New golden case: `matchLabel`
- [ ] New golden case: `matchTextInStandoff`
- [ ] New golden case in `GravsearchToCountPrequeryTransformerE2ESpec`: `matchLabel` count
- [ ] New golden case: `matchText` with a term containing `"` and `\`, showing the escaped rendering
- [ ] E2E: Gravsearch `matchText` and `matchLabel` with an unbalanced `"` / `\` in the term give the same status
      as `/v2/search` for that term, and never 500. Mirror `MatchFulltextE2ESpec.scala:256-278`, next to the
      existing matchText E2E coverage; find that with grep first.
- [ ] E2E: Gravsearch `matchText` with a balanced phrase (`"\"foo bar\""`) returns 200
- [ ] E2E: Gravsearch `matchText` with an embedded LF returns 200
- [ ] `MatchFulltextE2ESpec.scala:282`: update the comment that names `escapeForSparqlLiteral`
- [ ] `docs/03-endpoints/api-v2/query-language.md:708-712`: keep the `matchFulltext` cap caveat, but replace the
      DEV-6824 reference with DEV-7489, and state that `matchText` / `matchLabel` /
      `matchTextInStandoff` are not capped
- [ ] `docs/03-endpoints/api-v2/query-language.md` (escaping notes around `:467-476` and `:733`): state that all
      text functions escape the term
- [ ] `docs/development/dsp-api-fuseki-query-execution.md` Fact 9 (`:153-159`): list which `text:query`
      emissions pass `luceneHitLimit`, and name `matchFulltext` as the remaining capped one (DEV-7489)
- [ ] Grep `jena.apache.org/text#query` and `luceneQueryPredicate` across `modules/*/src/main`. Exactly one
      emission (`matchFulltextLuceneStatement`) passes no limit, and it carries the `TODO(DEV-7489)` comment.
- [ ] `bazel test //modules/webapi:test` passes
- [ ] `just test-it` passes (needs Docker)
- [ ] `just test-e2e` passes, including `MatchFulltextE2ESpec` (needs Docker)
- [ ] `just check` passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H1 | After the stage deploy, rerun the 0105 `ir14*` advanced search in dsp-app on stage and confirm 3,031 results | Balduin | after ship | Needs the deployed build and the dsp-app UI |
| H2 | Comment on DEV-3695 that DEV-6824 is the likely fix, and decide whether to close it | Balduin | after ship | Posting on Linear is the owner's call |
| H3 | Decide whether dsp-app's advanced search needs a dedicated message for the new 503 (today it shows its generic error) | Balduin | after ship | Product/UX decision in another repo |

## Acceptance Criteria

- [ ] `matchText` (simple and complex), `matchTextInStandoff` and `matchLabel` pass `luceneHitLimit`. Pinned by
      the regenerated goldens and new ones for each, including a `matchLabel` count.
- [ ] `matchFulltext`'s generated SPARQL is byte-identical to today's (its goldens are unchanged).
- [ ] After deploy, the 0105 `ir14*` advanced search returns 3,031 (H1). On stage, the limited prequery already
      does (measurements doc).
- [ ] Prequery pattern order is unchanged for every existing golden.
- [ ] A `"`, `\`, LF or CR in any text-function term is escaped in the generated SPARQL. Covered by unit specs and
      a golden.
- [ ] An unbalanced `"` / `\` in `matchText` / `matchLabel` never yields a 500. A balanced phrase and an LF yield
      200.
- [ ] A Gravsearch timeout on `/v2/searchextended` (page or count) returns 503 with the Gravsearch message, and
      both prequeries are logged with their SPARQL. No other endpoint's error surface changes.
- [ ] The docs say which text functions are capped and no longer claim a 504.
- [ ] `bazel test //modules/webapi:test`, `just test-it`, `just test-e2e` and `just check` pass.

## Dependencies & Risks

- **No behavioural test above 10k hits.** The fixtures are far below the cap, as they were for DEV-6823. The
  goldens, the stage measurements and H1 are the evidence.
- **A broad `matchText` / `matchLabel` term** gets more candidates. Per-hit cost in this shape is small (see the
  measurements doc), and Phase 1 turns a residual timeout into a 503.
- **`matchFulltext` stays wrong** for about 20 % of its prod requests until DEV-7489 lands. The docs
  keep saying so.

## Success Metrics

- C1 on stage: today's count is 0, the limited count is 3,031 (measurements doc). After deploy the dsp-app search
  shows 3,031 (H1).
- After deploy, Gravsearch prequery latency for `matchText` / `matchLabel` requests stays within the
  measurements doc's numbers. Check in Grafana with the `gravsearch.query` event filter.

## References

- Measurements: `02-gravsearch-lucene-limit-measurements-design.md` (this folder)
- Linear: DEV-6824 (this), DEV-7489 (`matchFulltext` speedup + limit), DEV-6822 (parent), DEV-6823 (`8351746`),
  DEV-6864 (REWRITE / HONEST-TIMEOUT, `8cf16d9`), DEV-6715, DEV-7377, DEV-6851, DEV-3695
- Constant: `modules/webapi/src/main/scala/org/knora/webapi/messages/OntologyConstants.scala:1107-1115`
- Precedent emissions: `modules/webapi/src/main/scala/org/knora/webapi/responders/v2/SearchQueries.scala:18,58,113`,
  `modules/webapi/src/main/scala/org/knora/webapi/slice/search/repo/SearchFulltextQuery.scala:22,66,143`
- Generator: `modules/webapi/src/main/scala/org/knora/webapi/messages/util/search/gravsearch/prequery/AbstractPrequeryGenerator.scala:1909-1919,2050-2060,2104-2112`
- Ordering: `modules/webapi/src/main/scala/org/knora/webapi/messages/util/search/gravsearch/transformers/PrequeryPatternOrdering.scala:202-247,333-340`
- Timeout precedent: `SearchResponderV2.scala:554-577,813,900-903`, `slice/search/SearchTimeoutException.scala`,
  `slice/api/v2/search/SearchEndpoints.scala:69-118,197-220`, `slice/api/v2/search/SearchRestService.scala:60-78`
- Goldens: `modules/test-it/src/test/resources/org/knora/webapi/messages/util/search/gravsearch/prequery/`;
  rewrite procedure in `docs/development/dsp-api-conventions.md:254-271`
- Escaping tests: `modules/test-e2e/src/test/scala/org/knora/webapi/e2e/v2/MatchFulltextE2ESpec.scala:256-295`
