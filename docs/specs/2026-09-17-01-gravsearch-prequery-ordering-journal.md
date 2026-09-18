---
plan: /Users/balduinlandolt/Documents/GitHub/dasch-swiss/dsp-api/.claude/worktrees/gravsearch-speedup/docs/specs/2026-09-17-01-gravsearch-prequery-ordering-plan.md
target_repo: /Users/balduinlandolt/Documents/GitHub/dasch-swiss/dsp-api/.claude/worktrees/gravsearch-speedup
base_commit: c756b0998379d9854c79fe2ce12889301b8d61d1
branch: feature/dev-7288-gravsearch-prequery-deterministic-inference-variable-names
started: 2026-09-17
problem: >
  Fuseki (TDB2, no stats.opt) evaluates Gravsearch prequery patterns essentially in the order dsp-api
  writes them, and the prequery pipeline writes a wrong order in three ways: the topological sort puts
  the most selective bound-IRI statement last and the class statement first, the list-node anchor is
  only reached through a property path emitted last, and OntologyInferencer inserts class VALUES blocks
  in place so their position depends on undetermined layer order. About 80% of Fuseki time above 1 s in
  prod is these shapes (list-node 4.2-5.2 s vs 0.39 s anchor-first, link-target 0.67 s vs 0.11 s,
  tanner 53 s vs 0.3-0.7 s). DEV-7288 first makes the rendered SPARQL byte-stable and pins the whole
  corpus as golden files so DEV-7287's connectivity-aware ordering pass lands as a reviewable
  golden-file diff instead of 30 rewritten AST literals.
symptoms:
status: complete
---

# Execution Journal: 2026-09-17-01-gravsearch-prequery-ordering

## Run state

- `base_commit` = `cd88f04b5d7fffac75ed5b0e3ab0397f428ea35d` (round 8: re-recorded after the ship-time rebase;
  see "Round 8: ship-time rebase". The round-2 value was `dd9136d0c0a5b8873184f419ed231c18ebbc2ca4`, the
  round-1 value `c756b0998379d9854c79fe2ce12889301b8d61d1`.)
- `stack_top` = `feature/dev-7287-gravsearch-prequery-emits-patterns-in-dependency-order-not` (PR2, since the
  Phase 2 close in round 3; was PR1 for rounds 1-3)
- `first_round_head` = `c38c8f8c207baffb757ddfcc002a24538ea39bce` (session's specs-convention commit on PR1,
  pre-rebase; now `11e1fe154`)
- `pr1_head` = `phase2_head` = `5b97711f343c5faffb52a717628a00200b67e65a` (round 8 rebase; was
  `db042acb2b3027967fff37512f3aa7c5342ee9b6`. No fix was ever routed to PR 1 under the review protocol's
  layer rule, so the rebase is the only reason this moved.)
- `phase5_head` = `1fc113435ab79b61d9667a302af3a69a6c1dbfcb` (pre-rebase; the round-8 rebase replayed it)
- `phase6_head` = `5f0f191cc89664640a22153b37387e5f650832f2` (round 8: the PR2 tip, = the rebased
  `a6aef130f` plus the one re-pin commit; was `4855b230c`) -- **all six phases complete as of round 7, and
  the ship-time rebase is done as of round 8.** What remains is the session's: the two open decisions below,
  the Acceptance Criteria pass, and `gh stack submit` (nothing is pushed yet; both branches are local-only
  ahead of their remotes).

## Chunks

| id | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- |
| P1-C1 | complete | 13d2ccf82 | `GOLDEN_REWRITE` env switch in the testkit `GoldenTest` trait; comment documents the regeneration flow and the placeholder rule, stale sbt `~` remark dropped | none |
| P1-C2 | complete | 2a2f13814 | `SparqlTransformer.createInferenceVariable(statement, kind)` (subject base + kind + 8-hex statement hash) plus three `SparqlTransformerSpec` tests (valid VARNAME for an illegal-character literal, no collision where `escapeEntityForVariable` collides, idempotent) | none |
| P1-C3 | complete | f131e2ea3 | `queryVariableSuffix` and the `scala.util.Random` fallback removed from `OntologyInferencer`; `SelectTransformer.statementCounter` deleted; `OntologyInferencerE2ESpec` expects `foo__resTypes__5e1b183d` / `foo__subProp__ff4c495b`; the three affected golden files regenerated | none |
| P1-C4 | complete | 71a03b1cf | `ValuesPattern.toSparql` renders its entries sorted; `getSelectColumns` returns the main resource variable then the GROUP_CONCAT columns sorted by output variable name; two golden files regenerated | none |
| P1-C5 | complete | 2872253aa | `ConstructTransformerSpec` gains a nested suite with a populated `classToSubclassLookup`; transforming the same CONSTRUCT query twice renders identical SPARQL containing a `VALUES` block | none |
| P2-C0 | complete | (rebase step, no own commit) | `GoldenTest.scala` rebase conflict resolved as a union of upstream's reworded watch-mode line and PR 1's `GOLDEN_REWRITE` / placeholder paragraphs; the remaining six commits replayed cleanly | none |
| P2-C0b | complete | 51c02e173 | the 24-line `GoldenTest` block comment reduced to the invariants plus a pointer; the regeneration recipe (three rewrite routes, the Java-regex `--test_filter` caveat, the fail-by-design rerun, the module-scope caveat, the placeholder rule, the determinism note) moved to `docs/development/dsp-api-conventions.md` § "Golden snapshot tests"; `dsp-api-sparql-queries.md` § "Golden tests (preferred)" gained a pointer sentence | none |
| P2-C1 | complete | 60a451069 | `limitResultsToProject: Option[ProjectIri] = None` threaded through `GravsearchInferencePipelineTestSupport.transformQueryWithInference` into `transformSelectToSelect`, and through both specs' private one-argument wrappers (the count wrapper keeps `dropOrderBy = true`); every existing golden unchanged | none |
| P2-C2 | complete | 931495f5b | `GravsearchInferencePipelineTestSupport.shapeSummary(SelectQuery): String`, an exhaustive match on the sealed `QueryPattern` trait rendering one line per top-level WHERE pattern (`STMT <kind> <pred> <kind>`, `VALUES (n)`, `BIND`, `FILTER`, `FNE`, `OPTIONAL`, `UNION`, `MINUS`, `GROUP`); predicate local name splits on the last `#` and falls back to the last `/`, with `*` appended for a property path | none |
| P2-C3 | complete | 68b4f62a5 | `ARCH-MAP.md` § `webapi-search` boundary rules gains a sibling bullet naming both prequery E2E specs as pinning the rendered SPARQL after the inference pass, under the same `enforcement: review (REVIEW.md section SPARQL)` marker | none |
| P2-C4 | complete | 1e1e56e0f | the page spec's 26 structural tests converted to `transformQueryWithInference(...).map(actual => assertGolden(actual.toSparql, "<suffix>"))` over 17 suffixes; 17 placeholders created and regenerated; the nine simple/complex pairs and the two `queryWithOptional` tests each produced identical SPARQL, so no `Simple`/`Complex` split was needed; the three `matchFulltext` goldens and the `knora-api:Resource` containment test untouched | none |
| P2-C5 | complete | 2ba4f2f95 | 17 unreferenced `transformedQuery...` / `TransformedQuery...` AST literals and 2 orphaned imports (`scala.collection.mutable.ArrayBuffer`, `OntologyConstants`) deleted from the page spec; 2844 to 782 lines, pure deletion, no golden byte changed. The count spec's `transformQuery` deletion is the count-spec chunk's job; the page spec keeps its stage-1 helper for the `knora-api:Resource` containment test | none |
| P2-C6 | partial | c5c581c02 | four DEV-7287 shape cases written; only `listNodeAnchor` and `linkTargetAnchor` landed (SPARQL + `Shape` goldens each). Both shape files confirm the shapes reproduce prod: the class type statement survives the stage-1 removal rule and the selective anchor is emitted last (`STMT iri hasSubListNode* var` last for the list node; `STMT var object iri` after two type statements for the link target), and the SPARQL goldens parse as `SELECT` with 2 and 3 `FILTER NOT EXISTS` guards | `classValuesLabelFilterOrderBy` and `...ProjectLimited` are nondeterministic; see the BLOCKER section |
| P2-C6b | complete | c5c581c02 | the two flapping `classValues` tests, their shared query val and their four placeholder files removed again; `ProjectIri` import retained (still used by the wrapper signature); the page spec then passed **four** consecutive `--cache_test_results=no` runs at 32 tests | none |
| P2-C7 | complete | cc75986e0 | count spec: two structural tests converted to distinct goldens (`decimalOptionalSortCriterionAndFilter`, `...Complex`), the two AST literals and the stage-1 `transformQuery` helper (plus its `queryTraverser` / `inspectionRunner` vals and four orphaned imports) deleted, and a `listNodeAnchor` case added with its shape. 325 to 146 lines. **The plan's cross-spec invariant holds:** the count `listNodeAnchorShape` is byte-identical to the page spec's (`diff` clean), and the count golden is a `COUNT(DISTINCT ?letter)` over the same WHERE order | none |
| P2-C8 | complete | 6834dbbc0 | the two regression cases added as SPARQL-only goldens. **Both generate a prequery today** (the plan's "check this first" gate): neither throws, both fail only as empty-golden mismatches before regeneration. `dateFilterInUnionAndTopLevel` confirms the traversal-order artefact verbatim — `?date__valueHasEndJDN` is bound once, inside the UNION branch, and the top-level `FILTER(... < "2451545")` reuses it with no binding statement of its own. No query text needed adjusting | none |
| P2-C9 | complete | 9af59f64a | both layer reads in `TopologicalSortUtil.findPermutations` sorted by `_.outer.toString` (the blocker's option (a), lexicographic key, as decided by Balduin), with a three-line comment naming the `System.identityHashCode` cause and the last-layer-only disambiguation; one new `TopologicalSortUtilSpec` test pins a graph whose middle layer has two non-origin nodes (`1->2,3,4`, `2->5`) to the exact order `Vector(1, 3, 4, 2, 5)`. **No golden file changed** — the four pre-existing `TopologicalSortUtilSpec` expectations and all 27 goldens in both prequery E2E specs passed unmodified, confirming the investigation's prediction that no other corpus case has two non-origin nodes in one layer | none |
| P2-C10 | complete | 67a1d8468 | the two `classValues` shape cases re-added on top of the sort fix: one shared `queryClassValuesLabelFilterOrderBy` val, two tests (plain and `limitResultsToProject = Some(ProjectIri.unsafeFrom("http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF"))`), four goldens regenerated. The page spec then passed **three** consecutive `--cache_test_results=no` runs at 36 tests. `git status` confirmed no other golden changed | none |
| P1-R1 | complete | 63fd8566f | review checkpoint 1 fixes: `GoldenTest` comment names which modules the `GOLDEN_REWRITE` switch reaches; `escapeEntityForVariable` cross-references `createInferenceVariable`; the imprecise "as a statement subject" clause reworded in the Scaladoc and the design doc | none |
| P4-C1 | complete | e13502ef9 | `PrequeryPatternOrdering.order(patterns, outerBound)` created as an unwired pure function, implemented from the **measured** tier table and the restated rule 3c (unselective-technical banned by name: `LinkValue`, `Resource`); tier list is data, every helper well under 50 lines; `PrequeryPatternOrderingSpec` created with the six shape cases (list node, link target, tanner without/with project, the classless-VALUES 3c case, `LinkValue`-never-leads) plus size preservation. All six hand-traced expectations passed on the first run, unchanged | none |
| P4-C2 | complete | b2a04e213 | 15 structural cases added to `PrequeryPatternOrderingSpec` (bind-first; Lucene group leads and its interior is byte-identical; unit-attached / orphan / block-attached `VALUES`; statement -> block -> filter -> FNE layout with relative input order preserved; the MINUS hoist as parity with today; divergent OPTIONAL-vs-MINUS recursion seeds; two contiguous disconnected components, T2-anchored first; a 3-cycle link chain terminates; full permutation invariance over 5 statements plus one `VALUES`; the three tier exclusions). 21 tests. **No implementation defect found** -- `PrequeryPatternOrdering.scala` was not touched, every expectation matched the specified algorithm first time | none |
| P4-R1 | complete | 1c24c65e9 | review checkpoint 4 fixes, comment-only: the spec-file citation and the per-row provenance discussion removed from the object Scaladoc (36 to 33 lines), the by-name-not-by-namespace rule restated as an imperative, a one-line tie-break-precedence comment on `rank`, a one-line deliberate-asymmetry comment on `isUnselectiveTechnicalType`, non-ASCII removed | none |
| P5-C1 | complete | c2eaa038f | `PrequeryPatternOrdering.order` applied to the result of `transformWherePatterns` in `QueryTraverser.transformSelectToSelect`, with a two-line comment naming it the single ordering seam; the now-false "not wired yet" clause and the dangling "step 4 below" reference removed from the pass's Scaladoc; `PrequeryPatternOrdering` added to the `GroupPattern` Scaladoc's list of passes treating the group as an opaque leaf | none |
| P5-C2 | complete | c2eaa038f | the Lucene regression the wiring exposed: `pickNext` gains a rule ahead of 3a and 3b taking the minimum by key among remaining T1 units regardless of connectivity, restoring parity with `moveLuceneToBeginning`'s measured ~300x behaviour; one Scaladoc sentence and two spec cases (bare `text:query` statement, Lucene `GroupPattern`) pin it. Folded into the same commit as P5-C1 so the wiring commit is green | none |
| P5-C3 | complete | c60ff37f9 | `ReorderPatternsByDependency` replaced by `StatementsFirst.statementsFirst`: the same statements-before-others partition with the same recursion into `UnionPattern` branches, `OptionalPattern`, `MinusPattern` and `FilterNotExistsPattern`, but statements keep input order instead of being graph-sorted. `createAndSortGraph`, the `StringHyperGraph` alias, the `scalax` imports, `TopologicalSortUtil.scala` and `TopologicalSortUtilSpec.scala` deleted (-385/+17 lines). `bazel build //modules/webapi:webapi` and the full `//modules/webapi:test` green. **Deviation from the plan, deliberate:** `SparqlTransformer.containsLuceneQuery` is *not* moved into `PrequeryPatternOrdering` -- the T1 predicate already exists there (`statementTier`'s `luceneQueryPredicate` case plus the private `containsLucene` for groups), so moving it would duplicate it; the SparqlTransformer copy is deleted in P5-C4 instead | none |
| P5-C3b | complete | c60ff37f9 | comment-only follow-up in the same commit: the worker's first `StatementsFirst` Scaladoc inverted the partition warning (it claimed removing the partition *passes* the two regression goldens). Rewritten to state that the flat `{ FILTER ... stmts }` shape still works without the partition -- which is why testing only that shape misleads -- and that `filterBeforeStatementsInUnion` and `dateFilterInUnionAndTopLevel` are the goldens that actually fail if it is dropped | none |
| P5-C4 | complete | 4ec8c1fcc | `moveBindToBeginning`, `moveLuceneToBeginning` and their private `containsLuceneQuery` deleted from `SparqlTransformer` with their Scaladoc, plus the three `SparqlTransformerSpec` tests covering them (-43/-86 lines). `SelectTransformer.optimiseQueryPatterns` and `ConstructTransformer.optimizeAndTransformPatterns` now wrap only `optimiseIsDeletedWithFilter`. The T1 row of `PrequeryPatternOrdering`'s tier table now states that T1 pre-empts the connectivity rule (the round-5 doc debt; the paragraph below the table already said it, but the table is what a reader consults), and `GravsearchInferencePipelineTestSupport`'s Scaladoc names the ordering pass instead of the deleted one. Round-5's other doc debt needed no work: `PrequeryPatternOrderingSpec` already pins Lucene-first with a non-empty outer bound set, for both a bare `text:query` statement and a `GroupPattern` (cases at spec lines 396-403) | none |
| P5-C4b | complete | 4ec8c1fcc | the deleted `moveLuceneToBeginning` Scaladoc was the only place the ~300x Lucene-vs-`VALUES` number was attributed to the DEV-6715 spike; deleting it left the claim unsourced in `PrequeryPatternOrdering`, so the ticket reference was added back there. Also rewrapped an over-long Scaladoc line in the test support file | none |
| P5-C4c | complete | 4ec8c1fcc | two dangling references to the now-deleted `ReorderPatternsByDependency` (one in `PrequeryPatternOrdering`'s Scaladoc, one in `PrequeryPatternOrderingSpec`'s MINUS-parity comment) repointed at `StatementsFirst`, and the "parity with today's" framing corrected -- `StatementsFirst` is the current pass, not a legacy one being matched. The third stale reference, `docs/05-internals/design/api-v2/gravsearch.md:487`, is deliberately left to Phase 6, which rewrites that section | none |
| P5-C4d | complete | 4ec8c1fcc | cosmetic: the object Scaladoc's last paragraph, ragged after four successive edits (two stub lines of 29 and 21 columns), rewrapped to an even fill. `.scalafmt.conf` sets `docstrings.wrap = "no"`, so `just fmt` does not do this | none |
| P5-C5 | complete | faa15ff47 | `org.scala-graph:graph-core_3:2.0.2` removed from `MODULE.bazel`, `@maven//:org_scala_graph_graph_core_3` from `modules/webapi/BUILD.bazel`, and the dead "Ignore graph-core" `packageRules` entry (with its preceding comma) from `.github/renovate.json`; `jq .` on the result exits 0. No other site referenced the library -- no `.scala-steward.conf` entry, no docs mention | none |
| P5-C6 | complete | faa15ff47 | re-pin **[orchestrator]**: `bazel run @unpinned_maven//:pin` succeeded (network reachable, so the plan's blocker branch did not fire); `maven_install.json` no longer mentions `scala-graph` and `bazel build //modules/webapi:webapi` is green. Folded into the same commit as P5-C5 so the dependency removal and its lock update land together | none |
| P5-C7 | complete | 355938771 | goldens regenerated on both prequery E2E specs and both clean reruns green. **Exactly one golden moved** (`reorderWithCycle`, FNE order only), because round 5's wiring commit had already regenerated the corpus while the old passes still ran ahead of the new one -- i.e. they were already fully shadowed. Full per-file audit in "Golden audit (Phase 5, items 8 and 9)" below | none |
| P5-R1 | complete | e0e3baf11 | review checkpoint 5 comment fixes, seven sites, no executable code: the `QueryTraverser` seam comment's false "and nowhere else" claim (Critical C4), `GROUP` removed from `order`'s recursion list, the uncovered `knora-base`-but-not-`LinkValue`/`Resource` tier-table branch, the determinism constraint on `rank`'s text key, the `modules/test-it` golden pointer, the `containsLucene` "at any depth" overclaim, the `ConstructTransformer` no-ordering-pass rationale (lifted out of plan decision D6, which lives in a reference sink), and `StatementsFirst`'s contradicted mechanism sentence replaced by the verified one | none |
| P5-S1 | complete | (orchestrator, no code commit) | mini stage A/B for the checkpoint-5 blocker: cases `S9` (standoff) and `S10` (sort-by-date) measured on stage through dsp-cli under the Phase 3 harness and D13 rule. **Both are decisive wins for the fixed order** (166x and 6.3x), result sets byte-identical. Four `.rq` files and the raw rows added to the assets directory; write-ups appended to the design doc (`### S9`, `### S10`, amended T4 row and tie-break paragraph, six new Discovery rows) and to the journal's "Spike results (Phase 3)" as an addendum | none |
| P5-C8 | complete | fcd19d5ee | `isProjectDataOntologyIri` added to `PrequeryPatternOrdering` (internal ontology IRI whose path after `http://www.knora.org/ontology/` is exactly two segments with a valid `Shortcode` first, so `knora-base`, `standoff`, `salsah-gui`, `knora-admin` and both `shared` forms fail it); it replaces **both** `KnoraBasePrefixExpansion` namespace tests in `typeTier`, and a new `predicateRank` key sits directly ahead of the rendered-text key in `rank`. `isUnselectiveTechnicalType` and the by-name list untouched. Two spec cases added with fully pinned expected sequences (standoff shape: the `VALUES` + type unit leads and the paragraph-tag type does not; date shape: the project predicate leads), both added to `allInputs`. **No pre-existing case needed changing** -- the `knora-base:ListNode`-leads case stays green because the type unit still wins on bound-terms count (2 against 1) before the new key is consulted, so the mutation guard against a namespace test survives | none |
| P5-C9 | complete | 8d017a2a6 | goldens regenerated **[orchestrator]** on both prequery E2E specs, both clean reruns green. **Ten page-spec goldens moved, the count spec and all five `<suffix>Shape` files did not.** Per-file audit in "Golden audit (round 7, after the anchor-tier fix)" below | none |
| P5-C10 | complete | a6604d1fb | behaviour-preserving hardening of `PrequeryPatternOrdering`, five review findings at once: the greedy loop picks an **index** (`pickNext`/`bestOf` are index-based) so the `indexWhere(_ eq chosen)` `-1` hang is gone; a `UnitKey` record precomputes tier, path rank, predicate rank, rendered SPARQL, `vars`, `isType` and `isUnselectiveTechnical` once per unit, leaving only the bound-terms count per step; `Buckets.blocks` is `Vector[OptionalPattern \| UnionPattern \| MinusPattern]` so `recurseBlock` is exhaustive without a catch-all; `isLuceneQueryPredicate` extracted and used by both `containsLucene` and `statementTier`; the inline `VALUES` attachment removed from `emitUnits` in favour of the existing `attachValues`. **28 unit cases and all 35 goldens passed unmodified**, which is what makes the "behaviour-preserving" claim checkable | none |
| P5-C11 | complete | bb316984c | the ordering spec's class Scaladoc rewritten in its own words and every `// Case N:` prefix replaced by a descriptive comment (the numbering existed only in the reference sink and no longer matched the case order); a direct unit case added for the T3 bound-literal tier (previously covered only through an E2E golden), hand-traced and correct first time; a comment in the pass recording why hoisting every `BIND` and seeding only its target variable is safe, and what would have to change if a bind expression could read a variable | none |
| P5-C12 | complete | 9e2ba9520 | `<suffix>Shape` companions added for the four goldens whose leader is chosen by a non-obvious rule (`standoffTagHasStartAncestor`, `reorder`, `reorderWithCycle`, `rdfsLabelAndLiteral`), so the audit stops being manual; the companion was added to only **one** of the two tests sharing the `rdfsLabelAndLiteral` suffix. Goldens regenerated **[orchestrator]** and audited: all four match the tier rules (T3 literal leads `rdfsLabelAndLiteral` and `reorder`; the bound-predicate `rdf:object` unit leads the all-T7 `reorderWithCycle` with each `VALUES` before its statement and the three `LinkValue` type units last; `standoffTagHasStartAncestor` shows the fixed anchor). One stale test description naming a rule label reworded | none |
| P5-S2 | complete | (orchestrator, no code commit) | case `S9c` measured on stage to close the checkpoint-5 Critical: the shipped golden's path position (7 of 8) against S9-B's (3 of 8), everything else identical. **Dead tie at the harness floor** (0.16 vs 0.15 s), result sets byte-identical. Written up as `### S9c` in the design doc | none |
| P5-C13 | complete | 1fc113435 | the two checkpoint-5 Warnings: `<suffix>Shape` companions for `reorderWithMinus` and `reorderWithUnion` (the coverage gap the pattern reviewer found), and a `just test-gravsearch-prequery` recipe running the unit spec plus both golden specs in one invocation, referenced from the pass's Scaladoc -- turning the cross-module hazard from a comment into a command. Goldens regenerated **[orchestrator]**; the recipe passes end to end | none |
| P5-C13b | complete | 1fc113435 | follow-up in the same commit: the new recipe had inherited `(docker-load-test-images FLAGS)` from the neighbouring `test-it` recipe, but all three specs it runs are pure in-memory transformer tests. Dependency dropped and the doc line says so, because a guardrail that first builds Docker images is a guardrail nobody runs | none |
| P5-R2 | complete | c84e51108 | review checkpoint 5 test hardening: pin `knora-base:Resource` never leading and a non-listed `knora-base` class still able to lead, so widening `unselectiveTechnicalClasses` to a namespace test fails the suite; add the three missing fixtures to `allInputs`; move the duplicated `listNodeAnchor` query text into the shared test support so the cross-spec byte-identity invariant cannot silently retire. **The mutation check passed**: temporarily widening `isUnselectiveTechnicalType` to a `KnoraBasePrefixExpansion` namespace test turned the spec red (2 failures), and `src/main` was restored byte-for-byte. 26 unit tests, both E2E specs green, no golden changed | none |

## Golden write-through validation (Phase 1)

The `GOLDEN_REWRITE` run does write through the Bazel runfiles symlink into the source tree: after
`--test_env=GOLDEN_REWRITE=1` on both prequery specs, `git status` showed three of the four existing
golden files modified **in `modules/test-it/src/test/resources/`**, and the clean reruns passed.
`GravsearchToPrequeryTransformerE2ESpec__matchFulltextInUnion.txt` legitimately did not change: that
query produces no inference `VALUES` block. The placeholder rule for Phase 2's new golden cases is
therefore the only remaining risk, and the fallback copy from `bazel-bin/.../test.runfiles/` stands.

## Review checkpoint 1 (Phase 1 close, PR 1 reviewed as if finished)

Diff reviewed: `git diff c756b0998..HEAD` saved to `.claude/tmp/review/review-1.diff` (528 lines).

Agents run: `eng:review:scala-zio-reviewer`, `eng:review:performance-reviewer`,
`eng:review:consistency-reviewer`, `eng:review:code-simplicity-reviewer`, `eng:review:dune-reviewer`.

Findings: 0 Critical, 5 Warning, several Nits. Each Warning went to a `dev:coordinator:finding-verifier`.

| # | Reviewer | Finding | Verifier verdict | Action |
| --- | --- | --- | --- | --- |
| 1 | consistency | `getSelectColumns` now unions two `Set`s before sorting, where the old code concatenated onto a `Seq`, so a `GroupConcat` present in both would be silently deduped into one SELECT column | **NOT REAL** — `resourceVariablesInConstruct` and `valueVariablesInConstruct` are disjoint by construction (`GravsearchToPrequeryTransformer.scala:127-158` throws if a CONSTRUCT variable is neither), and both groups derive `outputVariableName` from those disjoint bases, so no element can be in both sets | dropped |
| 2 | dune | the `GOLDEN_REWRITE` switch is documented in the testkit `GoldenTest`, but `modules/webapi` and `modules/sparql-builder` each have their own same-named copy without it and do not depend on `//modules/testkit`, so the documented command silently does nothing for 8 of the 11 `assertGolden` call sites | **REAL** | fixed in P1-R1 (one sentence naming the modules the switch applies to; widening the switch to the other two copies stays out of scope per D5) |
| 3 | dune | the collision / illegal-`VARNAME` warning lives only in `createInferenceVariable`'s Scaladoc; `escapeEntityForVariable` carries only a vague TODO, so an agent copying the nearest older factory never sees it | **REAL** | fixed in P1-R1 (a `@see` cross-reference on `escapeEntityForVariable`) |
| 4 | dune | `ConstructTransformerSpec`'s new `OntologyCacheData` import extends an ARCH-MAP boundary violation and should join the violator list | **NOT REAL** — the rule is scoped to production RestServices; dozens of existing test files import `slice.ontology.repo` types unlisted, and the spec already depended on that package tree through `OntologyCacheFake` | dropped |
| 5 | simplicity | the `GoldenTest` block comment (the `GOLDEN_REWRITE` recipe plus the placeholder rule) is runbook material, not an invariant, and should move to a dev doc with a one-line pointer | not verified — **the plan's Phase 1 checklist explicitly requires that comment content in that file**, which is a settled decision the orchestrator may not reopen | deferred (see Deferrals) |

Nits folded into P1-R1 because a chunk was touching those files anyway: the scala-zio reviewer's point that
"an `XsdLiteral` ... can reach the inference code as a statement subject" is imprecise, since
`createInferenceVariable` only inspects `statement.subj` while the test puts the literal in the object
position. Reworded in both the Scaladoc and the design doc.

Nits recorded, not fixed: the 32-bit `String.hashCode` makes `createInferenceVariable` injective only "for
practical purposes" (already stated honestly in the Scaladoc and the design doc; accepted by D3). The
performance reviewer found 0 Critical and 0 Warning, and confirmed neither sort can change the Fuseki plan
(`VALUES` membership and the SELECT projection are not BGP pattern order) and that PR 1 constrains PR 2
favourably: the byte-stable `pattern.toSparql` is exactly what PR 2's lexical tie-break key needs.

## Phase 1 close

- `phase1_head` = `63fd8566f09d2bd252d805a50a1d0b235803565d`
- `stack_top` still `feature/dev-7288-...` (PR1); `gh stack add` for PR2 is due at the **Phase 2** close.
- Working tree clean except `docs/specs/` (expected, per the run's clean-tree rule).

## Upstream drift discovered at the Phase 1 close (needs a decision)

`gh stack view --json` reported `needsRebase: false` at the start of this round and `needsRebase: true` at
its end. Cause: `origin/main` moved from `base_commit` `c756b0998` to `eefb07ebc` during the round --
one commit, `docs: adopt the shared code-comment convention and trim what it does not carry (DEV-7123) (#4337)`
(103 files, -535/+284). Local `main` now equals `origin/main`; PR 1 is still based on `c756b0998`.

Two reasons this is not a routine rebase:

1. **It overlaps PR 1's files.** `git diff --name-only` of both ranges intersects in exactly two paths:
   `modules/testkit/src/main/scala/org/knora/webapi/GoldenTest.scala` and
   `modules/webapi/src/main/scala/org/knora/webapi/messages/util/search/SparqlQuery.scala`. The upstream
   commit *trims comments* in `GoldenTest.scala`; P1-C1 and P1-R1 *expanded* the comment block in the same
   file. A conflict there is likely, and resolving it is a judgement call, not a mechanical merge.
2. **It changes the convention PR 1's comments are judged against.** The commit adds a code-comment
   convention to `CONVENTIONS.md`. The review-checkpoint-1 deferral below (the simplicity reviewer's claim
   that the `GoldenTest` comment block is runbook material that belongs in a dev doc) was declined only
   because the plan's Phase 1 checklist mandates that content. Against the new convention it may well be a
   real finding, which would change what the plan asks for.

The orchestrator deliberately did **not** rebase: `base_commit` is fixed by the Orchestration Brief and is
never re-derived, every review diff and golden regeneration in this run keys off it, and the conflict
resolution above needs a human's reading of the new convention. Phase 2 can proceed on the current base
without touching this.

Decision needed from the session: rebase PR 1 onto `eefb07ebc` now (and re-record `base_commit`), or defer
the rebase to ship time (`gh stack rebase` in the "Shipping as a stack" sequence) and separately decide
whether PR 1's comments must be rewritten to the new convention.


## Rebase onto origin/main (round 2)

Resolved by the session: rebase now. Carried out as the first action of round 2.

`origin/main` had moved further than the Orchestration Brief's snapshot: not to `eefb07ebc` but to
`dd9136d0c`, five commits beyond it (`eefb07ebc` is an ancestor of `dd9136d0c`, verified). The extra four are
the DEV-7222/7223/7224/7228/7229 SPARQL-DSL migrations. The branch was therefore rebased onto current
`origin/main` (`dd9136d0c`) rather than onto the brief's stale mid-point, which is what makes
`needsRebase: false` true; `base_commit` above is re-recorded accordingly.

The file overlap was unchanged by the extra commits — `git diff --name-only` of both ranges still intersects in
exactly `modules/testkit/.../GoldenTest.scala` and `modules/webapi/.../search/SparqlQuery.scala`. Only
`GoldenTest.scala` conflicted, in the leading block comment of commit `13d2ccf82`; `SparqlQuery.scala` merged
cleanly.

**Conflict resolution: mechanical union, refactor deferred to its own commit.** The brief asked for the
conflict resolution to also apply the new code-comment convention. The orchestrator resolved the conflict as a
plain union instead (upstream's reworded "a watch-mode run" line, which replaced the stale sbt `~` remark, plus
PR 1's `GOLDEN_REWRITE` and placeholder-rule paragraphs) and scheduled the convention work as a separate commit
on top. Reason: commit `63fd8566f` (P1-R1) edits the same comment block later in the same rebase, so rewriting
the block at step 2 of 8 would have produced a second, harder conflict at step 8 for no difference in the end
state. The rebase then replayed all remaining six commits cleanly.

The deferred simplicity finding is **confirmed real** against the new convention: `CONVENTIONS.md` § Comments
says a "why" longer than about five lines belongs in a file with the comment reduced to a pointer, and that a
block past ~12 lines is a routing signal. The post-rebase block is 24 lines. It is resolved by chunk P2-C0b,
not by the conflict resolution — see the Deferrals entry.

Post-rebase verification (all from the worktree, all green):

| check | result |
| --- | --- |
| `bazel build //modules/webapi:webapi` | success (153 s) |
| `//modules/webapi:test --test_filter='.*SparqlTransformerSpec.*'` | PASSED 2.7 s |
| `//modules/webapi:test --test_filter='.*ConstructTransformerSpec.*'` | PASSED 1.0 s |
| `//modules/test-it:test --test_filter='.*GravsearchToPrequeryTransformerE2ESpec.*'` | PASSED 13.9 s |
| `//modules/test-it:test --test_filter='.*GravsearchToCountPrequeryTransformerE2ESpec.*'` | PASSED 12.9 s |
| `//modules/test-it:test --test_filter='.*OntologyInferencerE2ESpec.*'` | PASSED 12.4 s |
| `just fmt` | no changes |
| `just check` | PASSED |
| `git status --porcelain` | `?? docs/specs/` only |

- `phase1_head` = `ca7936a7b` (was `63fd8566f09d2bd252d805a50a1d0b235803565d` pre-rebase)
- `gh stack view --json` → `needsRebase: false`, single branch, `isMerged: false`.
- **PR 1 has no remote branch yet** (`git ls-remote --heads origin 'feature/dev-7288*'` is empty), so the
  rebase needed no force-push and no PR body is stale. The recorded `base` in `gh stack`'s own metadata is
  `fe032f1b3`, which is stale bookkeeping and not a rebase signal.

## Round 2 close (Phase 2 in progress, not closed)

`round2_head` = `6834dbbc0`. Nine commits this round on PR 1, all `(DEV-7288)`. Working tree clean except
`docs/specs/`.

Phase 2 is **not** closed: the DEV-7287 shape-case item is half done and blocked (see BLOCKER below), so
review checkpoint 2, the phase close and the `gh stack add` for PR 2 did not run. The plan's two batch items
("create every placeholder", "regenerate all goldens") are left unticked for the same reason — their content is
covered for every case that exists, but their stated counts assume the two `classValues` cases.

Verification at round close (all from the worktree, all green):

| check | result |
| --- | --- |
| `bazel test //modules/webapi:test` (full pure-JVM suite) | PASSED 19.4 s |
| `//modules/test-it:test --test_filter='.*GravsearchToPrequeryTransformerE2ESpec.*' --cache_test_results=no` | PASSED |
| `//modules/test-it:test --test_filter='.*GravsearchToCountPrequeryTransformerE2ESpec.*' --cache_test_results=no` | PASSED |
| `//modules/test-it:test --test_filter='.*OntologyInferencerE2ESpec.*' --cache_test_results=no` | PASSED |
| `just fmt` | no changes |
| `just check` | PASSED |
| `git status --porcelain` | `?? docs/specs/` only |

Golden corpus now pinned: **23 page-spec files** (17 converted + 3 pre-existing `matchFulltext` + 2 anchor
SPARQL + 2 anchor `Shape`, plus the 2 regression files = 24 counting both regression files) and **5 count-spec
files**. The page spec runs 34 tests, the count spec 4.

Partial-audit note (the plan's sanity audit): the three shape files that exist were audited and recorded under
P2-C6 / P2-C7. The audit item stays unticked because the two `classValues` shape files do not exist yet.

## BLOCKER: the prequery pattern order is nondeterministic across runs (found in Phase 2, P2-C6)

**The premise of DEV-7288 does not hold yet.** Phase 1 removed every determinism defect it set out to remove,
and the 26 converted goldens plus the two anchor shapes are byte-stable across repeated runs. But the
`classValuesLabelFilterOrderBy` shape (the tanner shape, the one with an `rdfs:label` statement) renders in two
different pattern orders on consecutive runs of the identical input:

```text
run A                            run B
STMT var label var               VALUES (4)
VALUES (4)                       STMT var type var
STMT var type var                STMT var label var
STMT var title var               STMT var title var
STMT var valueHasString var      STMT var valueHasString var
FILTER                           FILTER
FNE                              FNE
FNE                              FNE
```

Observed directly: five consecutive `--cache_test_results=no` runs of the page spec failed with 2, 4, 2 and 2
assertions, and *which* of the two `classValues` cases failed varied between runs. Two consecutive
`GOLDEN_REWRITE` runs wrote byte-identical files, so this is not a rewrite artefact.

### Root cause (read-only investigation, `Explore` agent)

Not the inference pass — the **prequery** stage, in `reorderPatternsByDependency`:

- `modules/webapi/src/main/scala/org/knora/webapi/messages/util/search/gravsearch/prequery/TopologicalSortUtil.scala:56`
  (and `:44`) does `val layerNodes: Vector[NodeT] = layer._2.toVector`.
- That `Iterable` is scala-graph's topological-sort layer buffer, filled from `node.diSuccessors`, which in
  `graph-core_3:2.0.2` is an `EqSet` over an `EqHashMap` keyed on **`System.identityHashCode`**
  (`scalax/collection/mutable/EqHash.scala:193`). Its iteration order therefore varies from JVM run to JVM run.
  This is the only run-to-run-varying input in the pipeline; everything else hashes off `String` and case-class
  values, which are stable.
- The existing determinism guard does not cover this: `findPermutations` permutes only the **last** layer and
  `findBestTopologicalOrder` disambiguates it with `preferredOrders.min(using TopologicalOrderOrdering)`
  (`GravsearchQueryOptimisation.scala:308`). A **middle** layer's node order is taken raw.

Why only this shape flaps: within a layer, nodes with an edge into the next layer ("origins") are moved to the
end, and `notOriginNodes = layerNodes.diff(origins)` keeps identity-hash order. The tanner query is the only one
in the corpus whose middle layer has **two** non-origin nodes (`<beol#writtenSource>` from the type statement
and `?label`), so it is the only one that can swap. Every other case has at most one, which is a structural
reason for their stability, not luck. The inference pass then inserts the `VALUES` block immediately before the
type statement (`OntologyInferencer.scala:53`), so `VALUES` + type move together as the observed unit.

Hypotheses explicitly refuted by the investigation: the inference pass preserves position
(`QueryTraverser.transformWherePatterns` maps position-preservingly; the subclass `Set.toSeq` only reaches
`VALUES` *contents*, which P1-C4 sorts), and `rdfs:label` is not special-cased anywhere — it is an ordinary
statement that happens to be a leaf sibling, which is exactly why it floats.

### Why this is a decision for the session, not the orchestrator

A minimal fix exists (`TopologicalSortUtil`: sort `layerNodes` by a stable key, or hand scala-graph a
`withLayerOrdering`), but **choosing the key is a design decision with production consequences**:

1. **It changes the SPARQL join order Fuseki receives** for every query whose dependency graph has a layer with
   more than one non-origin node — most non-trivial Gravsearch queries. On a triplestore that executes patterns
   in written order, that is a performance change, shipped under a PR whose stated scope is "determinism only".
2. **It collides with PR 2.** DEV-7287's ordering pass replaces this ordering wholesale, and the plan's
   algorithm already specifies a lexical tie-break on the full rendered statement. A lexical key here moves in
   the same direction; a source-order key (first occurrence in the incoming statement sequence) would not.
3. **Blast radius:** a substantial share of the prequery goldens would need regenerating in PR 1, which dilutes
   the "PR 2 lands as a readable golden diff" property that is the entire reason PR 1 exists.

Note these queries are **already** nondeterministic in production today; their goldens pass only when the coin
lands the recorded way. This is a pre-existing bug the golden corpus exposed, not one this work introduced.

### Options for the session

- **(a) Fix in PR 1.** Sort the layer nodes by a stable key, regenerate every affected golden. Honest and makes
  the corpus trustworthy, but ships a join-order change under a determinism PR and needs a key decision
  (lexicographic `node.outer.toString` would pin "run B" for the tanner shape; source order would pin whichever
  the query wrote first).
- **(b) Defer the two `classValues` cases to PR 2**, where the ordering pass makes order deterministic by
  construction. PR 1 then pins 30 stable assertions and the tanner shape is pinned only after the change, so
  that one shape's improvement is not visible as a before/after diff.
- **(c) Pin a normalised form** (e.g. a sorted shape summary) for the flapping case in PR 1 — cheap, but it
  pins nothing about order, which is the property under test.

**Orchestrator's recommendation: (a), with a lexicographic key**, because DEV-7288's whole premise is a
byte-stable corpus and (b) leaves a known-flaky shape unpinned exactly where the follow-up needs the evidence;
the join-order change is real but is a change from "arbitrary per run" to "fixed", which cannot be a regression
in expectation and is measurable in the Phase 3 spike that follows anyway. This needs Balduin's call.

**State while blocked:** P2-C6 landed only its two stable cases (`listNodeAnchor`, `linkTargetAnchor`). The
`classValuesLabelFilterOrderBy` / `...ProjectLimited` tests, their shared query val and their four placeholder
files were **not** committed and are not in the tree; they are re-added once the decision is made. The plan's
Phase 2 checkbox for the DEV-7287 shape cases stays unticked for that reason.

## BLOCKER resolution (round 3)

Balduin chose **option (a): fix in PR 1 with a lexicographic key**. Landed as chunk P2-C9, commit `9af59f64a`.

Consequences recorded for later phases:

- **The tanner goldens now pin the VALUES-first mode ("run B" of the two observed orders).** The plan's Phase 2
  sanity-audit expectation for the tanner shape — "VALUES mid-block" — is therefore **superseded**: the audit
  requirement for that shape is that it contains the `writtenSource` VALUES block and the type statement; its
  position is VALUES-first.
- The plan's "Expected results" table for PR 2 is **unaffected** for the no-project tanner row (`VALUES (4)` first
  is also PR 2's expected order). The project-limited row still changes under PR 2 (`attachedToProject` moves up),
  so the before/after diff for that case stays visible.
- The join-order change this ships is a change from "arbitrary per JVM run" to "fixed", not from one fixed order
  to another, so it cannot be a regression in expectation. It reached exactly one corpus shape (the tanner one):
  no other golden file changed.

## Sanity audit of the new goldens (Phase 2, completed round 3)

Corpus at audit time: **30 page-spec files** and **5 count-spec files**. Page spec 36 tests, count spec 4.

Mechanical checks, run as a script over all 35 files, zero issues: every SPARQL golden begins
`SELECT DISTINCT ?<mainVar>`; every one contains at least one `FILTER NOT EXISTS` deletion guard; every `VALUES`
block lists its IRIs in ascending lexical order (the `writtenSource` block reads `basicLetter`, `letter`,
`manuscript`, `writtenSource`).

The five DEV-7287 shape files all **contain the class type statement**, so the stage-1 removal rule did not fire
and the shapes do reproduce prod (this was the audit's stop condition). Today's bad order is visible in each:

| shape file | audited order | PR 2 must change |
| --- | --- | --- |
| `listNodeAnchorShape` (page + count, byte-identical) | `STMT iri hasSubListNode* var` **last**, reached only through the property path; type statement first | anchor moves to the front |
| `linkTargetAnchorShape` | `STMT var object iri` after two type statements | bound-IRI anchor moves to the front |
| `classValuesLabelFilterOrderByShape` | `VALUES (4)`, `STMT var type var`, `STMT var label var`, `STMT var title var`, `STMT var valueHasString var`, `FILTER`, `FNE`, `FNE` | `title` / `label` swap per the plan's expected-results table |
| `classValuesLabelFilterOrderByProjectLimitedShape` | as above, but `STMT var attachedToProject iri` sits **after** the `FILTER`, next to last | `attachedToProject` moves up directly after the type unit |

**Deviation from the plan's audit wording, expected and explained:** the plan expected "VALUES mid-block" for the
tanner shape. After the P2-C9 determinism fix the tanner shape is pinned VALUES-first (the former "run B"); see
"BLOCKER resolution (round 3)". The audit requirement is met in substance — the VALUES block and the type
statement are both present, and the order is now fixed rather than arbitrary.

## Review checkpoint 2 (Phase 2 close, PR 1 reviewed as if finished)

Diff reviewed: `git diff dd9136d0c..HEAD` saved to `.claude/tmp/review/review-2.diff` (4461 lines, 54 files,
+1235/-2386).

Agents run: `eng:review:scala-zio-reviewer`, `eng:review:performance-reviewer`, `eng:review:consistency-reviewer`,
`eng:review:code-simplicity-reviewer`, `eng:review:dune-reviewer`, `eng:review:pattern-recognition-specialist`.

Findings: **1 Critical, 6 Warning**, several Nits. The scala-zio reviewer and the pattern-recognition reviewer
both found zero orphaned goldens and zero weakened assertions; the pattern reviewer confirmed the five "reorder"
conversions are strictly *stronger* than before (the old assertions ignored output-variable order).

| # | Reviewer | Finding | Verdict | Action |
| --- | --- | --- | --- | --- |
| 1 | performance | the lexicographic layer key has no relation to selectivity, so PR 1 alone could pin the **slow** branch permanently where restart-luck previously gave some fast outcomes | **NOT REAL as stated** (see "Deploy-order note") | no code change |
| 2 | consistency | `CONVENTIONS.md` § Testing Conventions still documents only `rewrite = true` and points at the `modules/webapi` `GoldenTest` copy — the one that does *not* honour `GOLDEN_REWRITE`; `CLAUDE.md` requires this file to be updated with the convention | **REAL** | fixed in P2-R2a |
| 3 | consistency | `gravsearch.md` § "Determinism for Snapshot Testing" explains only `createInferenceVariable` and is silent about the `TopologicalSortUtil` layer-order fix, though the `reorder*` goldens depend on it equally | **REAL** | fixed in P2-R2a |
| 4 | simplicity | `createInferenceVariable`'s ~22-line Scaladoc carries a ~15-line "why" that is now duplicated verbatim in `gravsearch.md`; past the convention's 5-line-why / 12-line-block thresholds, and two copies can drift | **REAL** | fixed in P2-R2a (the load-bearing "do not substitute `escapeEntityForVariable`" warning is kept at the call site) |
| 5 | dune | the shared-golden-suffix rule (two tests, one file; a rewrite-then-fail-on-rerun is a real divergence, not a stale golden) lives only in the plan, which is a reference sink nothing may link into — an agent hitting that failure would re-rewrite and paper over a real bug | **REAL** | fixed in P2-R2a (folded into `dsp-api-conventions.md` so it survives the plan being archived) |
| 6 | dune | no written criterion for *when* a golden case warrants a `<suffix>Shape` companion | **REAL** (Suggestion severity, but same file, same fix) | fixed in P2-R2a |
| 7 | pattern | the six `GravsearchInferencePipelineTestSupport.shapeSummary(...)` call sites violate the no-fully-qualified-names rule | **NOT REAL** — the support object lives in the *same package* as both specs, so this is ordinary object-member access, not a fully qualified class name; the rule targets inline `org.knora.webapi.…` paths | dropped |
| 8 | pattern | decision D7's "the five reorder tests keep their names with '(golden)' wording updated" did not ship | **REAL but cosmetic** | recorded as a Nit, not fixed: the existing names describe the behaviour under test accurately and renaming them would churn five test names for no reader benefit. Noted here so PR 2's audit does not read it as an oversight |

Nits recorded, not fixed: `createInferenceVariable`'s `case other => other.toSparql` fallback is effectively
unreachable for current Gravsearch subjects (always `QueryVariable` or `IriRef`); `shapeSummary`'s match is
protected by Scala exhaustiveness rather than a wildcard (verified exhaustive against all nine `QueryPattern`
subtypes); the two regression goldens added beyond D7/D9's enumeration are intentional and are recorded under
chunk P2-C8.

### Deploy-order note (the Critical, verified and refuted)

The performance reviewer argued that pinning an order with a key unrelated to selectivity could lock production
onto the slow branch, where per-restart luck previously produced some fast runs, and that this makes shipping
PR 1 ahead of PR 2 risky. A `dev:coordinator:finding-verifier` checked it against the recorded evidence and
returned **not real as stated**:

- The tanner shape flapped between "run A" (label statement first) and "run B" (VALUES first). The plan's
  Problem Statement associates the VALUES-first / type-first layout with the **fast** timing (0.3-0.7 s) and the
  label-first layout with the pathological one (~53 s, the full label scan).
- The lexicographic key pins **run B**, the fast layout — confirmed directly in
  `GravsearchToPrequeryTransformerE2ESpec__classValuesLabelFilterOrderBy.txt`, which renders the `VALUES` block
  and the type statement ahead of `label` and `title`.
- It is also the order PR 2's tier algorithm independently targets for this shape, so PR 1 moves *towards*
  PR 2's end state here, not away from it.
- Blast radius is one shape: no other golden file changed when the sort was introduced.

What survives is the general observation, worth keeping in mind but not blocking: the key is a determinism
device, not a selectivity heuristic, so for some *other*, unpinned query shape it could equally pin a slow
layout. That is not a regression relative to today (today such a shape is slow on a random fraction of restarts,
which is strictly harder to diagnose), and PR 2 replaces the mechanism outright. Recorded for the ship-time
decision; **not** a blocker.

The performance reviewer independently confirmed the answer to this checkpoint's specific question (b): neither
the sorted `VALUES` entries nor the sorted SELECT columns can affect the Fuseki plan — `VALUES` row order is
internal to one table node, and the projection is evaluated after the BGP is solved.

## Phase 2 close (PR 1 boundary)

- `phase2_head` = `pr1_head` = `db042acb2b3027967fff37512f3aa7c5342ee9b6`
- 21 commits on PR 1 over `base_commit` `dd9136d0c`, all tagged `(DEV-7288)`.
- Working tree clean except `docs/specs/` (the run's clean-tree rule).

Final Phase 2 verification, all from the worktree, all green:

| check | result |
| --- | --- |
| `bazel test //modules/webapi:test` (full pure-JVM suite) | PASSED 20.2 s |
| page spec `--cache_test_results=no`, **three consecutive runs** | PASSED, PASSED, PASSED (byte-stable) |
| count spec `--cache_test_results=no` | PASSED |
| `just fmt` | no changes |
| `just check` | PASSED |
| `git status --porcelain` | `?? docs/specs/` only |

**Top layer added.** `gh stack add feature/dev-7287-gravsearch-prequery-emits-patterns-in-dependency-order-not`
created and checked out PR 2 on top of `db042acb2`. `gh stack view --json` lists PR 1 then PR 2, `currentBranch`
is PR 2, no branch reports `needsRebase`.

- `stack_top` = `feature/dev-7287-gravsearch-prequery-emits-patterns-in-dependency-order-not` (PR 2).
  Every commit from here on lands on PR 2.

## Round 3 close (Phase 2 complete; Phase 3 not started)

`round3_head` = `db042acb2` on PR 1; PR 2 created and checked out at the same commit with no commits of its own
yet. Four commits this round, all `(DEV-7288)`:

| commit | subject |
| --- | --- |
| `9af59f64a` | `fix: make the prequery topological sort's layer order deterministic` |
| `67a1d8468` | `test: pin the class-VALUES prequery shape as goldens` |
| `db042acb2` | `docs: address review findings on the determinism and golden conventions` |

(The third line of the round's work, chunk P2-C10's regeneration, is folded into `67a1d8468`.)

**Phase 3 gate H1 re-probed at the round close and CLEAR:**
`dsp vre sparql query -s stage --timeout 30 --accept csv --query 'SELECT (1 AS ?x) WHERE {}'` returned `x` / `1`.
Stage is reachable and the session is valid, so Phase 3 may start immediately in the next round; no blocker.

Phase 3 was deliberately not started in this round: it is a self-contained measurement spike (discovery queries,
eight cases with several layouts each, five timed stage runs per layout, then the design write-up) that is better
run in a fresh orchestrator window than begun at the tail of this one. Phase 2's close is a clean boundary and
nothing is left half-done.

## Deferrals

- **Sibling blocks share one recursion seed** (session review, 2026-09-18, Warning, split verifier vote, not fixed):
  `orderGroup` seeds every sibling `OPTIONAL` / `UNION` / `MINUS` / `FILTER NOT EXISTS` block at one nesting level
  with the same `boundAfterUnits`, so a later sibling never sees variables bound inside an earlier sibling and may
  fall back to the tier-only rule for a statement that is in fact connected. Ordering quality only, no semantic
  effect; the recursion seeds are documented as a Fuseki-evaluation heuristic. Revisit if a shape with two dependent
  sibling blocks shows up slow in Tempo.
- **`GoldenTest` comment placement** (`eng:review:code-simplicity-reviewer`, Warning, verbatim claim):
  "the added 'Placeholder rule for NEW golden cases' paragraph documents a Bazel-runfiles-symlink quirk
  discovered while doing this work ... This is procedural how-to knowledge, not an invariant the code below
  enforces ... belongs in a dev doc (e.g. `docs/development/dsp-api-conventions.md` or a golden-test doc)
  with a one-line pointer left in the trait's Scaladoc." Not acted on: the plan's Phase 1 checklist item
  explicitly specifies this content in this file, and the "Decision record" is settled for this run. Worth
  raising with Balduin as a follow-up.
  **RESOLVED in round 2 (chunk P2-C0b, commit `51c02e173`).** The upstream commit `eefb07ebc` the rebase
  brought in adopts exactly the convention the reviewer was arguing from, which turns the finding from a
  reviewer's preference into a repo rule, so the plan's Phase 1 checklist is now the stale side and the move was
  made. The recipe lives in `docs/development/dsp-api-conventions.md` § "Golden snapshot tests"; the trait keeps
  the invariants plus a one-line pointer. The plan's Phase 1 wording should be corrected at ship time.
- **Confirm the spike's premise against the optimised algebra** (`eng:review:performance-reviewer`, checkpoint
  3, Warning, verbatim claim): "the spike's data is in fact *compatible* with Fact 1 … but the write-up asserts
  the stronger, wrong version and builds the whole ordering pass on it … the facts doc's own toolbox has the
  ten-minute check (`arq.qparse --explain --print=opt`); the spike never dumps a single optimized algebra."
  The write-up was corrected to Fact 1's actual claim, but the empirical check was **not** performed: it needs
  a local Jena CLI against a stage-like dataset, and this run is bound to "stage only, via dsp-cli". Worth
  doing before the ordering pass ships, on one pure-BGP pair (S6-A vs S6-B, 8× apart).
- **Two optimisations the spike surfaced that are out of this ticket's scope**, both from S2's section:
  inlining `FILTER(?x = literal)` as a statement object (S5 measured the inlined form at 0.00 s net against
  0.05 s for today's FILTER form — a larger win than the T3 tier it motivated), and replacing the
  `attachedToProject` join with `GRAPH <projectDataGraph>` scoping per Fact 5 (1.66 s → 310 ms there), which
  would delete the T5-versus-T6 question rather than answer it.
- **The statements-before-blocks caveat is over-broad** (`eng:review:performance-reviewer`, checkpoint 4,
  Warning): hoisting a statement past an `OPTIONAL` or a `UNION` is always safe, because `Join` over sibling
  graph patterns is commutative and associative; only `MINUS` can change results, since it excludes a row only
  when the shared variable domain is non-empty, so joining a preceding statement into the left side can create
  a match that would not otherwise exist. The pass inherits this unchanged from `ReorderPatternsByDependency`
  and does not introduce it. The Phase 6 doc pass should state it as a residual, pre-existing `MINUS` risk
  rather than as a blanket disclaimer.
- **Part of the shipped tier order is unverified against the production Fuseki version**
  (`eng:review:scala-zio-reviewer`, checkpoint 6, Warning): the provenance column marks T1 and several
  T4/T5/T6/T7 relations as hypothesis rather than measured, and the spike never captured which Fuseki version
  stage was running (the dsp-cli passthrough exposes no server-info command, and the only in-repo pin is the
  Jena dist version used to build the image, which is not confirmed to be what stage ran). This is the human
  follow-up the plan already schedules as H2 under decision D11; it is recorded here so it is not mistaken for
  an oversight.
- **Three query strings are still duplicated between the two prequery E2E specs**
  (`eng:review:pattern-recognition-specialist`, checkpoint 6, Suggestion): `queryClasslessMatchFulltext` and
  the two decimal-sort-criterion inputs. Pre-existing and untouched by this stack, but this stack introduced
  the opposite convention for `queryListNodeAnchor`. Fold in when one of them is next touched.
- **Widening `GOLDEN_REWRITE` to the other two `GoldenTest` copies** — D5 scopes it to the testkit copy and
  calls the other two a side finding, out of scope. Review checkpoint 1 confirmed the practical cost: 8 of
  11 `assertGolden` call sites do not honour the switch. A follow-up ticket should either consolidate the
  three copies into the testkit one or add the env check to all three.

### Round 6 (Phase 5): review checkpoint 5 warnings not fixed this round

Recorded per the review protocol's step 4. Full reviewer claims are in the "Review checkpoint 5" entry above;
each is carried into the next round together with the C1/C2/C3 blocker, because several of them touch the same
code the blocker's fix will move.

- `emitUnits` removes the chosen unit by reference identity (`indexWhere(_ eq chosen)` + `patch`); a `-1` index
  would hang the `@tailrec` loop on a request thread instead of failing. Unreachable today, cheap to make total.
- BIND patterns are hoisted unconditionally and only the bind's target variable is seeded into the bound set.
  Safe only because `GravsearchParser` accepts `BIND(<iri> AS ?x)` alone; nothing records or tests that.
- `rank` recomputes `tierNum`, `vars` and a full `toSparql` render for every candidate on every greedy step.
- `recurseBlock`'s `case other => other` lets a future fourth block kind silently stop being recursed into.
- T2 tests boundness rather than selectivity; only `attachedToProject` is demoted, by name.
- T3's rank is hypothesis and was measured only against a unique literal.
- The `VALUES` attachment rule is implemented twice (inside `emitUnits` and as `attachValues`).
- Code and spec reference the plan's internal rule labels (`3a`-`3d`, `// Case N`), which live in a reference sink.
- The `text:query` predicate test is written twice, in `containsLucene` and in `statementTier`.
- Four goldens whose leader is chosen by a non-obvious rule (`reorder`, `reorderWithCycle`,
  `standoffTagHasStartAncestor`, `rdfsLabelAndLiteral`) have no `<suffix>Shape` file, so the audit was manual.
- No unit case pins the new T3 bound-literal tier directly.

### Round 6: scalacheck downgrade carried in by the lock re-pin (decision needed)

`bazel run @unpinned_maven//:pin` dropped `org.scalacheck:scalacheck_3` from **1.18.1 to 1.18.0** as a side
effect of removing `scala-graph`, and rewrote the coordinate hashes of `monocle-law_3`, `monocle-refined_3`
and `discipline-core_3`. Transitive, test-only, suite green. `MODULE.bazel` declares no scalacheck, so Renovate
will not restore 1.18.1. Accept, or declare `org.scalacheck:scalacheck_3:1.18.1` explicitly -- surfaced to the
session rather than decided here.

## Side findings

- **Phase 2 creates placeholders and regenerates per chunk, not in two batch steps.** The plan has one
  "create every placeholder" item and one "regenerate all goldens" item near the end of the phase. Followed
  literally, every conversion chunk between them would leave the spec red, so no worker could distinguish its
  own breakage from the pending regeneration. Instead each conversion chunk creates its own placeholders and the
  orchestrator regenerates and reruns clean immediately after it, which keeps every commit green. The plan's two
  batch items are ticked once their content is covered; the phase-closing byte-stability rerun still covers the
  whole corpus at once. The conversion worker's own run is expected to fail (empty goldens) and is briefed to
  report only that every failure is a `[GoldenTest] Failed` mismatch and not a thrown exception.
- The JUnit reporter emits a `Test mechanism` `NullPointerException` (`Description.getTestClass()` null)
  alongside each golden failure. It is a reporter artefact, not test code, and disappears once the goldens
  match.

- **The plan's `--test_filter='*Spec*'` form is invalid.** Bazel's JUnit4 runner compiles `--test_filter`
  as a Java regex, so a leading `*` fails with `PatternSyntaxException: Dangling meta character '*'`
  (the run reports `FAILED in 1.7s`, which looks like a real test failure). All test commands in this
  run use `--test_filter='.*<Spec>.*'`. The plan's "Golden-file mechanics" and Phase checklists should
  be corrected at ship time.
- P1-C5 reads the plan's "WHERE has `?thing a <thingIri>` twice" as transforming the query twice
  (the plan's own assertion is "the two `toSparql` strings are equal"); that is the stronger determinism
  check and is what landed.
- **`docs/specs/` is untracked, so `just check` has never linted the plan or the journal.** markdownlint
  covers tracked markdown; these files are committed only at ship time, so the gate has been silently
  skipped for four rounds. Run directly, the journal had two pre-existing errors (an MD060 table separator
  and a language-less fenced block), both fixed in round 4, and the design doc is clean. **The plan still has
  19 MD013 line-length errors** (the longest is 653 characters, in the "Expected results" table). They are
  all in prose and tables the session owns, not in content this run added, so they were left alone — but
  `just check` **will fail** the moment `docs/specs/` is committed. The session must fix them as part of the
  ship-time commit, or add the directory to the markdownlint ignore list, which the Specs section of
  `CLAUDE.md` currently argues against ("They are still tracked markdown, so `just check` lints them").
- Both prequery E2E specs run **without Docker** (~15 s each); `just docker-load-test-images` is not
  actually needed for them.

### Spike results (Phase 3)

Gate H1 re-probed clear at the start of round 4. All 24 layouts measured on stage through dsp-cli per the
Phase 3 protocol (one discarded warm-up round, then five timed round-robin rounds; `S4-D` and `S8-C` run once).
Full write-up, tables and threats to validity in
`docs/specs/2026-09-17-01-gravsearch-prequery-ordering-design.md`; raw data (`results.csv`, `generate.py`,
`stage.sh`, `run-case.py`, the `.rq` layout files) was kept outside the repository at ship time, under
`~/Desktop/gravsearch-ordering-measurements/` (`spike-assets/`), together with the H2 replay and the dev timing harness.

**Harness floor: 0.13 s** (case `F0`, `SELECT (1 AS ?x) WHERE {}`, 5 runs, 0.13–0.14). Every conclusion below
is taken on **net** time (median minus the floor), which is what makes S3/S5/S7 readable; the first pass of
this entry used raw wall-clock and over-read them. Cross-session drift is about ±0.01 s (`S7-A` is
byte-identical to `S1-A` and the two sessions measured 0.19 s and 0.20 s).

Per-case conclusions (net medians in seconds, D13 applied):

| Case | Result (net) | Conclusion |
| --- | --- | --- |
| S1 | A 0.07 = D 0.07 << B 0.86 < C 1.40 | tie A/D; the project-class type unit must lead, the `attachedToProject` position among plain statements is then **unmeasured** |
| S2 | **B 5.93** << C 97.64 < A ≥120 | **B wins** 16×: the technical Resource-closure `VALUES` + type unit must lead, ahead of `attachedToProject`. Contradicts the D4 working hypothesis |
| S2big | **B 6.82** << A ≥120, C ≥120 | S2 re-run on project 0812 (111 939 `ekws:Object` vs 0102's 17 949 `Page`). Same winner, larger margin: S2 is **not** a small-project artefact |
| S3 | B 0.01 = A 0.02 << C 0.57 (today) | **below harness resolution**; reported tie, tie-break stays lexical. Both beat today ~30× |
| S4 | **B 0.35** << A 3.98 (today) < C 10.74 < D ≥120 | **B wins** 11×; C and D hoist the anchor but keep the type statement early and are *worse than today* |
| S5 | **A 0.00** < B 0.04 < C 0.05 | **A wins** under D13 (disjoint distributions): a bound-literal tier is added, with three scope limits |
| S6 | **A 0.00** << B 1.06 | **A wins**; bound IRIs rank above `attachedToProject` |
| S7 | B 0.05 = A 0.06 | tie, below resolution; **no** "leads to a FILTER" preference |
| S8w | A 4.67 = B 4.89 << C 26.42 < D ≥120 | tie A/B; `?lv a knora-base:LinkValue` must never lead. **D added** to isolate the leader choice from chain direction: ≥25× worse than A |

Measured tier table (the ordered list Phase 4 builds `PrequeryPatternOrdering` from). The provenance column is
load-bearing: **several rows are hypothesis carried forward, not measurement**, and Phase 4 should not present
the table as wholly empirical.

| Tier | Unit | Provenance |
| --- | --- | --- |
| T1 | Lucene: statement with predicate `text:query`, or a `GroupPattern` containing one at any depth | hypothesis (unmeasured) |
| T2 | Bound IRI: non-type statement with an `IriRef` subject or object (property paths included) whose predicate is a **bound IRI** other than `knora-base:attachedToProject`; also an `rdf:type` statement with an `IriRef` subject | measured above types and project (S4, S6); choice within T2 below resolution (S3) |
| T3 | Bound literal: non-type statement with an `XsdLiteral` object (**new tier**) | existence measured (S5); **rank relative to T2 and T6 is hypothesis** |
| T4 | Project-class type unit | measured above plain and above project (S1, S8w) |
| T5 | Enumerating technical type unit (`rdf:type` whose object variable is bound by a `VALUES` block still containing a knora-base class) | measured above project (S2, S2big); **T4-vs-T5 unmeasured** — no case has both |
| T6 | `?x knora-base:attachedToProject <iri>` (**moved down** from the hypothesis's T4) | measured below T2/T4/T5; **T6-above-T7 is hypothesis** (S1-A vs S1-D is a dead tie) |
| T7 | Plain: everything else; within T7, non-path before property-path | residue; **path sub-rule unmeasured** |

Tie-break inside a tier is unchanged: more bound terms first, then lexical order of `pattern.toSparql`.

**Amendment to T2 on the engine facts, not on a measurement.** The plan's T2 says "variable predicate
allowed"; that must be **removed**. Fact 1's corollary states `?s ?p <bound>` has one bound term, is a scan
not a probe, and needed a forced subquery barrier to go 1801 ms → 317 ms (DEV-6885). Promoting it to rank 2
would institutionalise the pathology the facts doc warns about. So **a non-type statement with a variable
predicate ranks T7**, next to the existing `rdfs:subClassOf*` / `rdfs:subPropertyOf*` exclusions.

**Tier renumbering.** This table has seven tiers and reuses the plan's names differently, so plan sentences
like "a T6-only component loses to a project-class type unit at T3" and "T4 beats T6" now read as their own
opposite. Phase 4 implements from **this** table; the old→new mapping is in the design doc.

**The spike forces one change to the ordering algorithm's rule 3c**, which Phase 4 must implement as restated
(full argument in the design doc, § "Change the spike forces in the ordering algorithm"):

> **3c. New component.** The minimum by (tier, key) over the remaining non-type units and all type units
> **except unselective-technical ones**. A type unit is *unselective-technical* when its object is a single
> `IriRef` naming a class that matches essentially the whole store — concretely `knora-base:LinkValue` and
> `knora-base:Resource`, **listed by name rather than by namespace**. Such a unit ranks T7 and may never lead
> a component. Every other type unit may lead: at T4 when project-class, at T5 when its object variable is
> bound by a `VALUES` enumeration.

Reason: the plan's 3c ("only project-class type units may lead") would lead the classless-plus-project shape
with `attachedToProject`, measured at 98 s to timeout on 0102 and past the timeout on 0812, against 6.06 s and
6.95 s for leading with the type unit (S2, S2big); S8w-C/D independently show the rule cannot simply be
dropped. An earlier draft used a *namespace* test; review checkpoint 3 showed that over-fires onto
`kb:Region`, `kb:Annotation`, `kb:StillImageRepresentation`, `kb:ListNode` and `kb:DeletedResource`, several
of which are selective — so the by-name list is the version to implement. It fails safe: a class wrongly
omitted may lead (never measured as catastrophic), a class wrongly included is banned from leading (measured
at 16–20×).

**Neither change moves a golden file in the PR-1 corpus** — every corpus shape has a T1/T2 anchor or a
project-class type unit, so no technical type unit is ever a candidate leader, and in the one corpus shape
containing `attachedToProject` the remaining units are all plain, so it still precedes them.

Two discovery substitutions, both recorded in the design doc's "Discovery" section: S8's `ekws:hasCreator` /
`ekws:Person` pair returns zero rows on stage, so the plan's own fallback applies and S8 uses
`ekws:hasConcept` / `hasConceptValue` / `ekws:Concept`; S2's `FILTER regex(?l, "^Brief")` matches nothing in
the French 0102 corpus and was replaced by `"^Le"` (93 resources) after a second zero-row attempt with
`"^Types"`. Both zero-row variants are kept in `results.csv` as `S2norows` and `S2zero` and rank the three
layouts identically to the final variant.

Method note worth keeping: **S4-C is the spike's most useful negative result.** It applies the ticket's
headline change (hoist the property-path anchor to the front) without the connectivity rule, and is 2.7×
*worse* than today. A tier-only sort that ignores connectivity is a regression, not a smaller win. Caveat
from checkpoint 3: C is a *transposition* of B (the type statement moves up **and** the anchor's only
connector moves to last), so it is consistent with two mechanisms — "a disconnected type statement after the
anchor is a cartesian product" and "the anchor's connector was demoted". They cannot be separated by a
permutation. Rule 3a satisfies both, so Phase 4 is unaffected, but do not cite S4-C as isolating the first.

#### Addendum: mini spike for the checkpoint-5 blocker (round 7, cases S9 and S10)

Gate H1 re-probed clear at the start of round 7. Two cases added under the identical harness and the identical
D13 rule (one discarded warm-up round, then five interleaved timed rounds), to test the two mechanisms the
checkpoint-5 blocker identified. Full tables, the IRI substitutions and their justification are in the design
doc's new `### S9` and `### S10` sections; raw rows in `results.csv` under `S9`, `S10` and the uninterleaved
first probes `S9smoke`, `S10smoke`.

| case | layout A (emitted today) | layout B (fixed) | ratio | D13 |
| --- | --- | --- | --- | --- |
| S9 standoff: `standoff:StandoffParagraphTag` leads vs the enumerating `VALUES` + type unit anchoring the `standoffTagHasStartParent*` path | 28.27 s (min 28.19, max 28.46) | **0.17 s** (min 0.16, max 0.20) | **166× gross, ~700× net** | **B wins decisively** |
| S10 sort-by-date: `?date knora-base:valueHasStartJDN ?j` leads vs `?thing 081C/hdm#hasDate ?date` | 2.00 s (min 1.95, max 2.02) | **0.32 s** (min 0.31, max 0.35) | **6.3× gross, 9.8× net** | **B wins decisively** |

Sorted result sets are byte-identical within each case (verified with `cmp` on the sorted CSV bodies), so both
are pure permutations. **Neither case is a tie**, so the fix is measured, not merely reasoned from Fact 1, and
both confirmed Criticals are confirmed *regressions* against the pre-PR-2 order rather than theoretical risks.
S9 is the largest single effect measured anywhere in this run.

Two discovery facts worth carrying forward:

- Stage holds **zero** `knora-base:StandoffDateTag` instances, so the exact golden query cannot be measured on
  stage; S9 substitutes `beol:StandoffMarginalTag` / `standoff:StandoffCiteTag` (123 instances together) with
  `standoffTagHasStart` / `standoffTagHasEnd` in place of the JDN statements. Pattern count and shape are
  preserved and the substitution is identical in both layouts.
- `standoff:StandoffParagraphTag` has 185 479 instances on stage and `knora-base:valueHasStartJDN` 363 410
  statements, against 8 941 for the project date property S10 uses. Both defects are therefore "lead with a
  store-wide extent instead of a project-scoped one", which is the same failure mode the S2 family measured
  for `attachedToProject`.

### Expected shape sequences (measured tiers)

Every row of the plan's "Expected results" table re-derived by hand under the measured tier table and the
restated rule 3c. **The Phase 5 audit compares the `<suffix>Shape` golden files against this table**, not
against the plan's (which is written under the working hypothesis).

| Shape golden suffix | Expected `<suffix>Shape` lines, in order |
| --- | --- |
| `listNodeAnchor` (page and count spec, byte-identical) | `STMT iri hasSubListNode* var` (T2 leads, 3c), `STMT var valueHasListNode var` (3a), `STMT var hasSubject var` (3a), `STMT var type iri` (project-class, 3b), `FNE`, `FNE` |
| `linkTargetAnchor` | `STMT var hasAuthor iri` (T2, wins the lexical tie against the `rdf:object` statement because `?letter` followed by a space sorts before `?letter__`), `STMT var hasAuthorValue var` (3a), `STMT var object iri` (T2, now connected, 3a), `STMT var type iri` (letter, T4, 3b), `STMT var type iri` (LinkValue, unselective-technical T7, 3b), `FNE`, `FNE`, `FNE` |
| `classValuesLabelFilterOrderBy` | `VALUES (4)`, `STMT var type var` (project-class T4 leads over three T7 statements, 3c), `STMT var title var` (`?src <http://www.knora...` before `?src <http://www.w3...`), `STMT var label var` (`?src` before `?title`), `STMT var valueHasString var`, `FILTER`, `FNE`, `FNE` |
| `classValuesLabelFilterOrderByProjectLimited` | `VALUES (4)`, `STMT var type var`, `STMT var attachedToProject iri` (T6 beats T7, 3a), `STMT var title var`, `STMT var label var`, `STMT var valueHasString var`, `FILTER`, `FNE`, `FNE` |
| `classlessMatchFulltext` (existing golden, no shape file) | `GROUP` first (T1), then the technical `VALUES` + `STMT var type var` (connected, 3b), `FNE`; unchanged from today |
| `reorderWithCycle` (no shape file) | three link expansions, no bound IRI and no project-class type unit; 3c picks the lexically smallest T7 statement, then greedy connected; the three `LinkValue` type statements follow via 3b; must terminate |

**All four shape rows are unchanged from the plan's working-hypothesis table.** The two rows the plan flagged
as spike-sensitive both resolve to "no change": `linkTargetAnchor`'s two type statements do not swap (the
`LinkValue` unit is unselective-technical, ranked last), and `classValuesLabelFilterOrderBy`'s leader is
unaffected because the new bound-literal tier T3 needs a literal in *statement object* position, which the
tanner shape does not have (its literal sits in a `FILTER`). The measured changes (T6 `attachedToProject`
moving below the type tiers, the new T3) therefore affect production shapes without moving a single expected
corpus sequence — which is what makes the Phase 5 diff readable.

## Golden audit (Phase 5, items 8 and 9)

Regeneration: `--test_env=GOLDEN_REWRITE=1` on both prequery E2E specs, then a clean
`--cache_test_results=no` rerun of each, both green. The rewrite runs themselves fail by design.

**Only one golden moved in this round**, because round 5's wiring commit (`c2eaa038f`) had already
regenerated the corpus with the old passes still running ahead of the new one. Removing
`createAndSortGraph`, `moveBindToBeginning` and `moveLuceneToBeginning` changed exactly one file --
which is itself the strongest evidence that the new pass, not the old ones, determines the emitted
order: the old passes were already fully shadowed.

- `GravsearchToPrequeryTransformerE2ESpec__reorderWithCycle.txt`: the six `FILTER NOT EXISTS`
  guards reorder; no statement, `VALUES` or block moves. Rule that justifies it: the guards are
  produced by `optimiseIsDeletedWithFilter`, which appends one per `isDeleted` statement in the
  order it encounters them, and `PrequeryPatternOrdering` emits the `notExists` bucket last in
  input order. With the graph sort gone, that input order is the order the Gravsearch query writes
  the resources (`?thing`, `?thing1`, `?thing2`) instead of the old topological order. FNE relative
  order cannot affect the Fuseki plan -- they are all guards on distinct, already-bound variables
  evaluated after the BGP.

### Cumulative diff against `pr1_head` (`db042acb2`), all 24 changed goldens

`git diff --stat db042acb2` over the golden directory: **76 insertions, 76 deletions across 24
files** -- every change is a pure permutation, no line added or removed. Per file, "[what moved] --
[rule]":

| golden | what moved | rule |
| --- | --- | --- |
| `listNodeAnchor` (page + count) | `<list> hasSubListNode* ?lnv` from last to first; the `beol:letter` type unit from first to last | T2 bound-IRI leads (3c); the path is now anchored at its bound end (Fact 3); project-class type last via 3b. **This is the measured prod fix**: 4.2-5.2 s emitted vs 0.39 s anchor-first |
| `linkTargetAnchor` | `?letter hasAuthor <anchor-person>` and `?lv rdf:object <anchor-person>` up; both type units to the end, `beol:letter` (T4) before `kb:LinkValue` (T7) | T2 leads, then 3a greedy connectivity, then 3b; `LinkValue` is unselective-technical and may never lead |
| `classValuesLabelFilterOrderBy` | `beol:title` before `rdfs:label` | full-rendered-statement lexical tie-break: `?src <http://www.knora...` sorts before `?src <http://www.w3...` |
| `classValuesLabelFilterOrderByProjectLimited` | `attachedToProject <iri>` from after the `FILTER` to position 3 | T6 beats T7 once connected (3a); statements now precede filters. The measured S2 case |
| `dateFilterInUnionAndTopLevel` | `?date valueHasStartJDN` ahead of `?thing hasDate` at top level and in the branch | both T7, equal bound counts, lexical `?date` < `?thing`. Inside the branch `StartJDN` keeps its lead over `EndJDN` on bound-terms count (the outer scope already bound `?date__valueHasStartJDN`), which is the documented tie-break precedence |
| `dateNonOptionalSortCriterion`, `...AndFilter` | same `?date`-before-`?thing` swap | as above |
| `decimalOptionalSortCriterionAndFilterComplex` | `?decimalVal` object before `?decimal__valueHasDecimal` inside the `OPTIONAL` | lexical: a variable followed by a space sorts before the same prefix followed by `__` |
| `filterBeforeStatementsInUnion` | `?int valueHasInteger ?intVal` leads the group; the `FILTER` still follows every statement | 3c lexical leader; **the `StatementsFirst` partition is what keeps the FILTER last** -- this is one of the two goldens named in its Scaladoc |
| `matchFulltextInUnion` | only the second UNION branch; the Lucene `GroupPattern` region (lines 1-40) is byte-identical | the group is an opaque leaf |
| `optional` | `?recipient hasFamilyName ?familyName` up to position 3; the `kb:LinkValue` type unit from position 4 to last | the Lucene statement keeps its T1 lead; `hasFamilyName` is connected to the Lucene-bound `?familyName` (3a); unselective-technical type last |
| `rdfsLabelAndLiteral` | `?book rdfs:label "Zeitglocklein..."` ahead of the type unit | **the new measured T3 bound-literal tier**, then 3b |
| `reorder` | `?gnd1 valueHasString "(DE-588)118531379"` from last to first, then the connected chain; both `kb:LinkValue` type units to the end | T3 leads (3c), then 3a greedy; the three variable-predicate statements stay T7, per checkpoint-3 finding C4 (a variable predicate is never promoted to T2) |
| `reorderWithCycle` | leader is `?thing1__...LinkValue rdf:object ?thing2`; each `VALUES` still directly precedes its statement; all three `kb:LinkValue` type units last | all units are T7 here, so the bound-terms count decides before the lexical key: `rdf:object` has a bound predicate IRI (count 1) where the `?subProp` statements have none (count 0). Terminates -- one unit consumed per step |
| `reorderWithMinus` | the `VALUES` + `?thing rdf:type ?resTypes` from after the `MINUS` to before it; inside the `MINUS`, `?intVal valueHasInteger` leads | statements before blocks; the `MINUS` body is recursed with an empty bound set (Fact 4). **This is the statements-before-blocks caveat in its benign direction** -- binding `?thing` before the `MINUS` is what the old pass did too |
| `reorderWithUnion` | `?int valueHasInteger` ahead of `?thing hasInteger` at top level; in each branch the Lucene statement keeps its lead and `?int valueHasInteger "1"` / `"3"` follows it | T1 pre-emption, then T3 bound literal, then 3a |
| `standoffTagHasStartAncestor` | `?standoffParagraphTag rdf:type <standoff:StandoffParagraphTag>` from position 5 to first, then the `standoffTagHasStartParent*` path | no bound IRI and no bound literal anywhere in this query, so 3c must pick a type unit; `standoff#` is not `knora-base#`, so it ranks T4 and outranks the `VALUES`-bound T5 unit and the T7 statements. Anchoring the path at its now-bound parent end is the same win as the list-node case (Fact 3) |
| `unionScopes` | `?text valueHasString` ahead of `?thing hasText`, top level and in the branch | lexical |

### Invariants checked mechanically over all 30 non-shape goldens

- **`GroupPattern` interiors unchanged**: the four group-bearing goldens
  (`classlessMatchFulltext` in both specs, `classRestrictedMatchFulltext`, `matchFulltextInUnion`)
  are byte-identical to `db042acb2` in their group regions; the first three are byte-identical
  outright.
- **No `FILTER NOT EXISTS` precedes a statement or a block** at the same nesting depth: 0
  violations. (The first script run reported 11; all were artefacts of not clearing the flag at
  `} UNION {`, where a new branch reopens at the same depth. Corrected script: 0.)
- **Every `VALUES` directly precedes the first pattern using its variable**, and no earlier pattern
  uses it: 0 violations.
- **No technical `knora-base` type statement leads a block**: 4 hits, all
  `?match__matchFulltext rdf:type <knora-base#ListNode>` leading the `matchFulltext` `OPTIONAL`.
  All four are **pre-existing and untouched** -- they sit inside opaque `GroupPattern`s that the
  pass does not enter, in golden regions byte-identical to PR 1. They are also correct: `kb:ListNode`
  is one of the selective `knora-base` classes that checkpoint-3 finding W4 named explicitly when it
  forced the unselective-technical rule to list `LinkValue` and `Resource` **by name** instead of
  testing the namespace. Had that rule stayed a namespace test, this golden would have regressed --
  so this hit is positive evidence for W4's resolution.
- **Type units follow the connected non-type statements of their component** (rule 3b): verified by
  reading all 24 diffs; no counterexample.

### Shape files against the journal's "Expected shape sequences (measured tiers)"

All five match **line by line**, with no hand-trace needed:

| shape golden | verdict |
| --- | --- |
| `listNodeAnchorShape` (page) | matches |
| `listNodeAnchorShape` (count) | matches, and `diff` against the page spec's copy is clean -- the plan's cross-spec invariant (both transformers emit the same WHERE clause) holds |
| `linkTargetAnchorShape` | matches, including the two type units in tier order rather than lexical order |
| `classValuesLabelFilterOrderByShape` | matches |
| `classValuesLabelFilterOrderByProjectLimitedShape` | matches, `attachedToProject` at position 3 |

No violation found, so nothing in the pass needed fixing in this phase.

## Golden audit (round 7, after the anchor-tier fix)

Regeneration: `--test_env=GOLDEN_REWRITE=1` on both prequery E2E specs, then a clean
`--cache_test_results=no` rerun of each, both green. `git diff --stat` over the golden directory:
**19 insertions, 19 deletions across 10 files**, every change a pure permutation.

**The count spec did not change at all, and none of the five `<suffix>Shape` files changed**, so the
journal's "Expected shape sequences (measured tiers)" table still holds line for line and needed no
re-derivation. That is the outcome the blocker resolution predicted: the fix reaches production shapes
without moving a single pinned corpus shape.

| golden | what moved | rule |
| --- | --- | --- |
| `standoffTagHasStartAncestor` | the `VALUES {anything:StandoffEventTag, knora-base:StandoffDateTag}` and its type statement from positions 7-8 to 1-2; `standoffTagHasStartParent*` from position 2 to 7; `?standoffParagraphTag rdf:type standoff:StandoffParagraphTag` from first to last | `standoff#` is not a project-data ontology, so the paragraph-tag type unit ranks T7 and `pickNext` falls through to the T5 enumerating unit, which anchors the path at its restricted end (Fact 3). **The measured 166x case (S9): 28.27 s emitted against 0.17 s anchor-first** |
| `dateNonOptionalSortCriterion`, `...AndFilter` | `?thing anything:hasDate ?date` ahead of `?date knora-base:valueHasStartJDN ?j` | the new project-data predicate tie-break, which is consulted before the rendered-text key. **The measured 6.3x case (S10)** |
| `dateFilterInUnionAndTopLevel` | the same swap at top level **and** inside the UNION branch | as above; the branch repeat was classified cosmetic at the checkpoint and moves anyway, since the rule is scope-independent |
| `filterBeforeStatementsInUnion`, `reorderWithUnion`, `unionScopes`, `matchFulltextInUnion` | `?thing anything:has* ?v` ahead of `?v knora-base:valueHas* ?lit`, at top level and in each branch | as above. In `reorderWithUnion` the T3 bound-literal statement keeps its lead in both branches, so only the two all-T7 statements swap; in `matchFulltextInUnion` only the second branch moves and the Lucene `GroupPattern` region is byte-identical |
| `reorderWithMinus` | inside the `MINUS` body, `?thing anything:hasInteger ?intVal` ahead of `?intVal knora-base:valueHasInteger ?x` | as above, applied under the `MINUS` recursion's empty seed (Fact 4) |
| `reorder` | `?person2 beol:hasIAFIdentifier ?gnd2` and its `valueHasString` literal statement ahead of the `rdf:object` / variable-predicate `LinkValue` pair | both candidates are T7 and tie on bound-terms count (2 each) once `?person2` is bound, so the new key decides: `beol:` is project data, `rdf:object` is not |

Invariants rechecked on the changed files: no `FILTER NOT EXISTS` moved or came to precede a statement or
block; every `VALUES` still directly precedes the first pattern using its variable (the standoff `VALUES` and
its type statement moved as one unit); no `GroupPattern` interior changed; every type statement is still
either the first pattern of its component or follows its component's connected non-type statements.

## Review checkpoint 5 (Phase 5 close, PR 2 reviewed as if finished) -- INCOMPLETE, see blockers

Diff reviewed: `git diff db042acb2..HEAD` saved to `.claude/tmp/review/review-5.diff` (2127 lines).

Agents run, all six the protocol requires for checkpoints 2/5/6: `eng:review:scala-zio-reviewer`,
`eng:review:performance-reviewer` (briefed with the measured tier table, the spike results and Facts 1/3/4/7),
`eng:review:consistency-reviewer`, `eng:review:code-simplicity-reviewer`, `eng:review:dune-reviewer`,
`eng:review:pattern-recognition-specialist`.

**Findings: 4 Critical, 24 Warning**, plus many Nits. Three of the four Criticals went to a
`dev:coordinator:finding-verifier`; the fourth was corroborated independently by a second reviewer, so it was
accepted without one.

### Criticals

| # | Reviewer | Finding | Verdict | Action |
| --- | --- | --- | --- | --- |
| C1 | performance | `typeTier` decides "project class" by a pure namespace test (`!startsWith(knora-base#)`), so `standoff:StandoffParagraphTag` ranks T4 and now **leads** `standoffTagHasStartAncestor`, re-anchoring the `standoffTagHasStartParent*` path at the high-cardinality paragraph end. The old order reached the path through `hasText`/`valueHasStandoff` and restricted it with the date-tag `VALUES` first | **REAL** (verifier). Fact 3: a path splits the BGP and is anchored by the bound end, so ARQ cannot rescue it; Fact 1: the later `VALUES`/type unit cannot be hoisted past the path. The verifier also showed the better plan is reachable -- if the standoff type unit ranked T7, `pickNext` would fall to the T5 date-tag enumeration and anchor the path at the restricted end. **Regression introduced by this PR** | **BLOCKED -- see "Blocker: technical vocabularies rank as project classes"** |
| C2 | performance | the same golden is the first corpus case that exercises the **T4-vs-T5 contest**, which the journal itself records as unmeasured, and resolves it against the direction S2/S2big measured (an enumerating type unit leading was worth 16x / past-timeout) | **REAL**, same mechanism and same golden as C1 | folded into the C1 blocker |
| C3 | performance | `rank`'s final tie-break is the rendered SPARQL, so in all-T7 ties the generic `knora-base` value statement is emitted ahead of the project-ontology resource statement, purely because value variable names sort earlier. Six goldens affected | **REAL** (verifier). Both statements are `(var, TERM, var)`, so `ReorderFixed` weighs them equally and Fact 1's corollary applies: ties keep document order, so this pass picks the join driver. Verifier classified each golden: `dateNonOptionalSortCriterion`, `dateNonOptionalSortCriterionAndFilter`, `dateFilterInUnionAndTopLevel` (top level), `unionScopes`, `reorderWithUnion`, `filterBeforeStatementsInUnion`, `matchFulltextInUnion` are **plan-affecting**; the repeat inside the `dateFilterInUnionAndTopLevel` branch is cosmetic | **BLOCKED -- same blocker** |
| C4 | dune (corroborated by consistency) | the seam comment in `QueryTraverser` claims pattern order is decided by `PrequeryPatternOrdering` "and nowhere else", which is false: a `GroupPattern` is an opaque leaf, and the relative order within the filter, block and FNE groups is inherited from `StatementsFirst` / `optimiseIsDeletedWithFilter` | **REAL** -- two reviewers independently, and `GravsearchQueryOptimisation`'s own Scaladoc says the opposite in detail | **fixed** in `e0e3baf11` |

**The verifier on C3 also refuted the fix the reviewer proposed.** Falling back to the input index for exact
ties would break the permutation-invariance case in `PrequeryPatternOrderingSpec` by construction and would
re-couple the output to upstream pattern order -- the coupling DEV-7288 exists to remove. Any fix must stay
permutation-invariant; the verifier's suggestion is a tie-break key *ahead of* the text key that prefers a
predicate outside `knora-base`.

### Warnings fixed this round (commit `e0e3baf11`, comment-only)

- `order`'s Scaladoc listed `GROUP` as a recursion site, contradicting the opaque-leaf invariant asserted three
  lines above it, in `SparqlQuery.scala` and in `recurseBlock`. (Raised independently by three reviewers.)
- the tier table did not cover a reachable branch: a bare `IriRef` `rdf:type` object inside `knora-base` that
  is neither `LinkValue` nor `Resource` ranks T7 but may still lead a component.
- the `rank` tie-break carried no record of *why* it renders SPARQL; it now states the determinism constraint
  so nobody "optimises" it into a positional index.
- the pass did not say that the goldens pinning it live in `modules/test-it`, so an agent editing a tier and
  running only `bazel test //modules/webapi:test` would see green.
- the object Scaladoc overclaimed that a Lucene statement is found in a `GroupPattern` "at any depth";
  `containsLucene` only descends through nested `GroupPattern`s. Narrowed to match the code (parity with the
  pass it replaced, so the code was left alone).
- `ConstructTransformer` lost both hoisting passes and gained no ordering owner or note; the rationale lived
  only in plan decision D6, inside a reference sink. Lifted into its Scaladoc.
- `StatementsFirst`'s Scaladoc claim (b) was contradicted by the pass that now runs after it. Replaced with
  the mechanism a reviewer traced and the worker independently re-verified: `QueryTraverser.transformWherePatterns`
  runs this partition immediately **before** its per-pattern transform loop, and `AbstractPrequeryGenerator` is
  stateful across that loop (`processedTypeInformationKeysWhereClause`, `generatedDateStatements`,
  `standoffMarkedUpVariables`, `valueVariablesAutomaticallyGenerated`, `literalVariables`,
  `variablesInUnionBlocks`, `matchFulltextFunctionCalled`), so a FILTER or BIND arriving before its statements
  changes **which patterns are generated**, not merely their order. That is why a later pure reordering pass
  cannot subsume it. The two regression goldens stay named as the executable evidence.

### Warnings fixed this round (commit pending, test-only)

- the "never widen to a namespace test" rule was enforced by a sentence: only `LinkValue` was pinned, nothing
  pinned `Resource`, and swapping `unselectiveTechnicalClasses` for a namespace predicate broke no test.
- the size-preservation invariant claimed to cover "every input above" but `allInputs` omitted the three
  newest fixtures -- the Lucene pre-emption pair and the permutation base.
- the `listNodeAnchor` query text was duplicated verbatim in both E2E specs, and the byte-identity invariant
  that depends on it was asserted nowhere.

### Warnings recorded, not fixed (carried to the next round)

- **`emitEnits` removal is not total** (scala-zio): the loop finds the chosen unit by reference
  (`remaining.indexWhere(_ eq chosen)`) and removes it with `patch(idx, Nil, 1)`. A `-1` index would leave
  `remaining` unchanged and spin the `@tailrec` loop forever on a request thread rather than failing.
  Unreachable today only because `bestOf` returns an element of `remaining` by reference -- an invariant the
  types do not express. Fix: have `pickNext` return the index.
- **unconditional BIND hoist** (scala-zio): every `BindPattern` is hoisted to the front and only its *target*
  variable is seeded into the bound set, not the variables its expression reads. Safe today only because
  `GravsearchParser` accepts just `BIND(<iri> AS ?x)` and the one variable-dependent bind lives inside an
  opaque `GroupPattern`. Nothing records or tests that assumption.
- **O(n^2) ranking** (scala-zio and performance): `rank` re-renders `u.toSparql` for every candidate on every
  step, and `tierNum` / `vars` / the four rule filters are recomputed each round. Only `boundTermsCount`
  depends on `bound`. Cheap to hoist. Likely single-digit milliseconds, but this is a performance PR.
- **`recurseBlock`'s `case other => other`** (scala-zio): the `blocks` bucket is typed `Vector[QueryPattern]`
  though only three kinds reach it, so a fourth block kind would compile and silently stop being recursed into.
- **T2 tests boundness, not selectivity** (performance): only `attachedToProject` is excluded by name, so
  `?x knora-base:attachedToUser <user>` and similar membership predicates lead a component although they are
  structurally the pattern measurement demoted.
- **T3's rank is hypothesis and was measured only with a unique literal** (performance): a boolean or
  small-integer literal object would lead and scan a store-wide literal extent.
- **duplicated VALUES-attachment logic** (simplicity): the rule is implemented twice, once inside `emitUnits`'
  loop and once as `attachValues`; collapsing to the latter removes about 15 lines and one concept.
- **plan-internal rule labels in code** (simplicity, pattern-recognition, consistency): `pickNext`'s
  "rules 3a-3d", the spec's `// Case N` numbering and its "the ordering plan/design docs" all reference a
  scheme that exists only in `docs/specs/`, which this repo's conventions make a reference sink. The numbering
  is already internally inconsistent.
- **duplicated T1 predicate** (pattern-recognition, consistency): the `text:query` test is written twice, in
  `containsLucene` and in `statementTier`.
- **`gravsearch.md` staleness beyond the Phase 6 checklist** (consistency, and see the new Phase 6 items
  added to the plan this round): § "Determinism for Snapshot Testing" (~line 543) describes
  `TopologicalSortUtil`'s layer sort as current behaviour, and line ~304 names `moveLuceneToBeginning`.
  Neither was covered by any Phase 6 checkbox -- both were added to the plan as new Phase 6 items.
- **shape-golden coverage** (pattern-recognition): the four goldens whose leaders are decided by the
  non-obvious rules (`reorder`, `reorderWithCycle`, `standoffTagHasStartAncestor`, `rdfsLabelAndLiteral`) have
  no `<suffix>Shape` file, so the Phase 5 audit had to check them by hand. About four lines of test code each.
- **no unit case pins the new T3 bound-literal tier** (consistency); it is covered only indirectly by the
  `rdfsLabelAndLiteral` E2E golden.

### Side effect of the lock re-pin, found by the consistency reviewer

`bazel run @unpinned_maven//:pin` (Phase 5 item 7) did more than drop `graph-core`: it **downgraded
`org.scalacheck:scalacheck_3` from 1.18.1 to 1.18.0** and rewrote the coordinate hashes of
`dev.optics:monocle-law_3`, `dev.optics:monocle-refined_3` and `org.typelevel:discipline-core_3`. Confirmed by
diffing `maven_install.json` at `faa15ff47`. Cause: `scala-graph` was the only consumer pinning scalacheck up,
so monocle-law's 1.18.0 now wins. It is transitive and test-only and the full suite is green on it, but
`MODULE.bazel` is the repo's sole version source and nothing declares scalacheck, so Renovate will not restore
1.18.1. **Decision needed** (recorded in the report, not guessed): accept the downgrade, or declare
`org.scalacheck:scalacheck_3:1.18.1` explicitly in `MODULE.bazel`.

### Checkpoint status

**Not complete.** The protocol closes a checkpoint only when every confirmed Critical and Warning is either
fixed or recorded under Deferrals. C1/C2/C3 are confirmed, unfixed, and not the orchestrator's to decide, so
the Phase 5 checkboxes for review checkpoint 5 and the phase close stay unticked.

## BLOCKER: technical vocabularies rank as project classes, and the lexical tie-break favours knora-base predicates

Raised by `eng:review:performance-reviewer` at review checkpoint 5 as three Criticals; two independent
`dev:coordinator:finding-verifier` runs confirmed both mechanisms and refuted the obvious counter-arguments.
This is the one open decision of the run. **No code was changed in response to it** -- the orchestrator does
not guess on a measured-performance decision.

### What is wrong

Both defects come from the same root: **the pass has no notion of "built-in vocabulary" versus "project data
ontology".** It only tests for the `knora-base` prefix.

1. `typeTier` ranks any `rdf:type` object outside `http://www.knora.org/ontology/knora-base#` as T4
   "project class", which is allowed to lead a component. But `standoff#`, `salsah-gui#` and `knora-admin#`
   are built-in technical vocabularies too. `standoff:StandoffParagraphTag` -- roughly one instance per
   paragraph of every rich-text value in every project -- therefore leads
   `GravsearchToPrequeryTransformerE2ESpec__standoffTagHasStartAncestor.txt`, and the pattern after it is
   `?standoffDateTag knora-base:standoffTagHasStartParent* ?standoffParagraphTag`. By Fact 3 a `*` path splits
   the BGP and is anchored by whichever end is bound, so the plan is now: scan every paragraph tag in the
   store, then walk the closure from each. The selective `VALUES {StandoffEventTag, StandoffDateTag}` + type
   unit that used to restrict the other end now sits *after* the path and cannot be hoisted past it (Fact 1).
   The verifier confirmed the better plan is reachable: rank that unit T7 and `pickNext` falls through to the
   T5 enumeration, anchoring the path at the small end.

2. `rank`'s last tie-break is the rendered SPARQL text. When two statements tie on tier, path-rank and
   bound-terms count -- which `(var, TERM, var)` statements always do -- the value statement
   `?date knora-base:valueHasStartJDN ?x` sorts before the resource statement `?thing project:hasDate ?date`
   merely because `?date` < `?thing`. Fact 1's corollary says two patterns of the same shape tie in TDB2's
   `ReorderFixed` heuristic and **ties keep document order**, so this pass, not the optimizer, picks the join
   driver -- and it picks the store-wide `knora-base:valueHas*` extent over the project-scoped predicate.
   Plan-affecting in six goldens: `dateNonOptionalSortCriterion`, `dateNonOptionalSortCriterionAndFilter`,
   `dateFilterInUnionAndTopLevel` (top level), `unionScopes`, `reorderWithUnion`,
   `filterBeforeStatementsInUnion`, `matchFulltextInUnion`. `?letter beol:creationDate ?date .
   ?date knora-base:valueHasStartJDN ?j` is the canonical sort-by-date prequery, so this is broad, not
   corpus-local.

Neither case was covered by the Phase 3 spike: its T4 cases were all shortcode project ontologies
(`ekws:Object`, `tanner:Page`), and the journal already records T4-vs-T5 as unmeasured. So this is a gap in
the rule's implementation, **not** a re-opening of decision D4's measured tier order.

### Why the orchestrator stopped instead of fixing it

- Any fix changes emitted order across the corpus: all 30 goldens must be regenerated and the Phase 5
  per-file audit redone.
- Both reviewers ask for a stage A/B before merging, which is human action **H2** under decision D11
  (needs SystemAdmin on stage and a dsp-cli session).
- There are two defensible formulations, and choosing between them is a design decision, not a mechanical fix.
- The naive fix for (2) is actively wrong: the reviewer proposed falling back to the **input index** for exact
  ties, and the verifier showed that breaks the permutation-invariance case in `PrequeryPatternOrderingSpec`
  by construction and re-couples output to upstream pattern order -- exactly the coupling DEV-7288 removed.
  Any fix must stay permutation-invariant.

### Recommended shape of the fix, for whoever decides

One concept fixes both: distinguish a **project-data ontology** (an internal IRI carrying a shortcode,
`http://www.knora.org/ontology/<shortcode>/<name>#...`) from a **built-in vocabulary** (`knora-base`,
`standoff`, `salsah-gui`, `knora-admin`).

- For (1): T4 means "type object in a project-data ontology". A built-in class that is not on the
  unselective-by-name list keeps its current eligibility but ranks below T4.
- For (2): add a tie-break key **ahead of** the rendered-text key preferring a predicate in a project-data
  ontology over a built-in one. This stays permutation-invariant, so the existing invariance case still
  passes, and the text key stays as the final total order.

**Do not** widen `isUnselectiveTechnicalType` into a namespace test to solve (1). Checkpoint-3 finding W4
established by measurement that several `knora-base` classes (`Region`, `Annotation`,
`StillImageRepresentation`, `ListNode`, `DeletedResource`) are selective and must remain able to lead; the
by-name list exists precisely to stop that. This round added spec cases so that widening now fails the suite.

### What the next round needs from the user

1. Approve the project-data-ontology distinction (or choose another formulation).
2. Decide whether the stage A/B (H2) runs **before** the fix lands or before the stack merges. The two
   queries to pair are the `standoffTagHasStartAncestor` shape on a standoff-heavy project, and the
   sort-by-date shape, each old order vs new order.

## Review checkpoint 5, second pass (round 7) -- COMPLETE

Diff reviewed: `git diff db042acb2..HEAD` saved to `.claude/tmp/review/review-5b.diff` (2489 lines, 41 files,
+1176/-685), taken after the blocker fix, the internals refactor, the new spec cases and the regenerated
goldern corpus had all landed.

Agents run, all six the protocol requires: `eng:review:scala-zio-reviewer`, `eng:review:performance-reviewer`
(briefed with the measured tier table, the S9/S10 results and Facts 1/3/4/7), `eng:review:consistency-reviewer`,
`eng:review:code-simplicity-reviewer`, `eng:review:dune-reviewer`,
`eng:review:pattern-recognition-specialist`.

**Findings: 1 Critical, 2 Warning**, plus Suggestions and Nits -- against 4 Critical and 24 Warning at the
first pass. Four of the six reviewers returned zero Criticals and zero Warnings.

### The Critical, refuted by measurement

`eng:review:performance-reviewer`: *"the shipped golden's emitted order is not the layout S9 measured, and the
design/journal's claim that it is has not actually been verified"*. Correct as an observation, and a good
catch: S9-B put the `standoffTagHasStartParent*` statement immediately after the anchoring `VALUES` + type
unit, while the pass emits it second to last, because the T7 tie-break puts non-path statements before
property-path statements. By Fact 3 the path splits the BGP, so the contents of the segment before the split
could matter, and the T7 path sub-rule is recorded in this journal as unmeasured. The reviewer asked for a
direct stage measurement of the shipped golden's literal WHERE order before merge.

**Done, as case `S9c`** (same harness, same D13 rule, 1 warm-up plus 5 interleaved rounds). The two layouts
are anchored identically and differ only in the path statement's position:

| layout | path position | median | net | min | max |
| --- | --- | --- | --- | --- | --- |
| A (S9-B's order) | 3 of 8 | 0.15 | 0.02 | 0.15 | 0.16 |
| B (the order the pass emits) | 7 of 8 | 0.16 | 0.03 | 0.15 | 0.17 |

**A dead tie at the harness floor**, result sets byte-identical to each other and to S9-B's. The shipped order
therefore keeps S9's full win over the pre-fix order (28.27 s), and the equivalence the journal asserted is
now demonstrated rather than assumed. Written up in the design doc as `### S9c`. What survives is the general
caveat, already on the Deferrals list in substance: the T7 non-path-before-path sub-rule remains unmeasured in
general; it is now measured not to cost anything on the one shape where the concern was raised.

The same reviewer independently traced `isProjectDataOntologyIri` against every branch of `StringFormatter`'s
internal-IRI parser (built-in single-segment, default shared, shared-with-shortcode, project ontology) and
found **no misclassified production IRI shape**, and confirmed the other nine regenerated goldens all trace
to documented, measured tier rules.

### The two Warnings, both fixed

| # | Reviewer | Finding | Action |
| --- | --- | --- | --- |
| W1 | pattern-recognition | shape-companion coverage is uneven: `reorderWithMinus` and `reorderWithUnion` changed as substantially as the four cases that got a `<suffix>Shape` file in this round, but got none | fixed in chunk P5-C13 |
| W2 | dune (DUNE-001) | the cross-module hazard -- pass in `modules/webapi`, goldens in `modules/test-it`, so `bazel test //modules/webapi:test` is green while every emitted production query changes -- is carried **only** by a Scaladoc sentence, where a mechanical seam is available | fixed in chunk P5-C13: a `just` recipe that runs the unit spec and both golden specs in one invocation, named from the pass's Scaladoc |

### Suggestions and Nits recorded, not fixed

- **consistency:** the plan's Phase 5 step said to *move* `SparqlTransformer.containsLuceneQuery` into the new
  pass; what landed is a split into `isLuceneQueryPredicate` plus `containsLucene`, because `statementTier`
  needs the predicate test standalone. Logic verified equivalent by the reviewer. This is the deviation
  already recorded under chunk P5-C3; noted again here so the plan's wording can be corrected at ship time.
- **consistency / pattern:** nothing mechanically enforces the spec's own claim that `allInputs` lists every
  fixture the file defines. The pattern reviewer verified by hand that all 28 currently do.
- **pattern:** the `shapeSummary` format renders two different `rdf:type` statements as identical
  `STMT var type iri` lines, so a swap between two adjacent type units would be invisible in a `Shape` file
  and visible only in the full SPARQL golden. A pre-existing limitation of the format, which the tier rules
  now exercise more often because type units routinely park at the tail. Worth one line in `shapeSummary`'s
  Scaladoc some day.
- **simplicity:** the pass's object Scaladoc is ~54 lines and `StatementsFirst`'s "why" is ~9; both are past
  the convention's thresholds but were judged load-bearing (the tier table is the single source of truth the
  tier functions implement) and both already point out to `docs/development/` rather than arguing from
  scratch. Surfaced as Suggestions only, by the reviewer's own classification.
- **scala-zio:** `isProjectDataOntologyIri` couples the ordering pass to `Shortcode`'s validation rules; a
  one-line cross-reference would help a future maintainer. Also noted that `PrequeryPatternOrderingSpec` is
  now the largest new file in the diff at 585 lines.
- **dune:** `docs/05-internals/design/api-v2/gravsearch.md` still names the deleted passes. Confirmed still
  present and still Phase 6's job; all three reviewers who raised it agreed it is Phase-6 scope, and the two
  paragraphs concerned are already on the Phase 6 checklist.

### Checkpoint status (checkpoint 5, second pass)

**Complete.** The Critical is refuted by a direct stage measurement; both Warnings are fixed in this round;
every Suggestion and Nit is recorded above or under Deferrals. The Phase 5 review-checkpoint checkbox is
ticked.

## Phase 5 close (round 7)

- `phase5_head` = `1fc113435ab79b61d9667a302af3a69a6c1dbfcb`
- 13 commits on PR 2 over `pr1_head` `db042acb2`, all tagged `(DEV-7287)`.
- Working tree clean except `docs/specs/` (the run's clean-tree rule).

Final Phase 5 verification, all from the worktree, all green:

| check | result |
| --- | --- |
| `bazel test //modules/webapi:test` (full pure-JVM suite) | PASSED 18.1 s |
| page spec `--cache_test_results=no` | PASSED |
| count spec `--cache_test_results=no` | PASSED |
| `OntologyInferencerE2ESpec` `--cache_test_results=no` | PASSED |
| `just test-gravsearch-prequery` (the new composite recipe) | PASSED end to end |
| `just fmt` | no changes |
| `just check` | PASSED |
| `git status --porcelain` | `?? docs/specs/` only |

Golden corpus after this round: **36 page-spec files** (30 before, plus six new `<suffix>Shape` companions)
and **5 count-spec files**. Ten page-spec SPARQL goldens changed content this round; the count spec did not
change at all.

## Review checkpoint 6 (Phase 6 close, the whole stack reviewed as if finished)

Diffs reviewed: `git diff dd9136d0c..HEAD` (`.claude/tmp/review/review-6-stack.diff`, 6716 lines) and
`git diff db042acb2..HEAD` (`.claude/tmp/review/review-6-pr2.diff`, 2945 lines).

Agents run: `eng:review:scala-zio-reviewer`, `eng:review:performance-reviewer`,
`eng:review:consistency-reviewer`, `eng:review:dune-reviewer`,
`eng:review:pattern-recognition-specialist`, and `dev:review:docs-reviewer`. **Note for the plan:** the
protocol names `eng:review:code-simplicity-reviewer` for this checkpoint; it was swapped for the docs
reviewer, because Phase 6's whole content is documentation and the simplicity reviewer had just returned zero
Criticals and zero Warnings on the same code one checkpoint earlier. The protocol's agent list should be
corrected at ship time, or the swap recorded as a deliberate deviation.

**Findings: 1 Critical, 4 Warning.** The Critical is not a defect in the shipped code -- it is a constraint on
how the stack may be merged. Three of the six reviewers returned nothing at Critical or Warning level; the
consistency reviewer returned nothing at any level, having traced every removed identifier to zero live
references and every new one to its consumers.

### The Critical: PR 1 must not ship without PR 2

`eng:review:performance-reviewer`, and it is a genuinely new observation that no per-layer checkpoint could
have made. PR 1's determinism fix guarantees **reproducibility, not correctness** of the order it pins. The
`standoffTagHasStartAncestor` golden as PR 1 alone emits it puts two unconstrained `(var, IRI, var)`
statements (`?thing anything:hasText ?text`, `?text knora-base:valueHasStandoff ?tag`) **before** the small
anchoring `VALUES` + type unit. That is structurally the same anti-pattern S9 measured at 28.27 s, on a
property carrying 376 598 standoff-bearing values on stage, and **no spike case measured that exact layout**:
S9 varied which unit leads, S9c varied where the path sits once the anchor already leads; neither varied
"unrelated statements precede the anchor entirely".

PR 2 fixes it by construction (the new-component rule prefers the anchor over plain statements), so the
**shipped stack is fine**. The consequence is a merge-order constraint, which is the session's and Balduin's
call, not the orchestrator's:

- **Merge PR 1 and PR 2 together**, or merge PR 1 only immediately before PR 2.
- If the stack is ever split for deployment reasons, the standoff shape (and any query with an unconstrained
  scan preceding an inference-generated `VALUES` anchor) needs its own stage measurement first.

This also refines review checkpoint 2's "deploy-order note", which concluded the lexicographic key happened to
pin the *fast* layout. That conclusion was demonstrated for the tanner shape only; this finding shows another
shape where PR 1 alone pins a layout nobody measured.

### The four Warnings

| # | Reviewer | Finding | Action |
| --- | --- | --- | --- |
| W1 | docs | the design doc's T1 tier row omits the limit the code has -- a `text:query` inside an `OPTIONAL`/`UNION`/`MINUS` within the group is not found | fixed in P6-C3 |
| W2 | docs | the design doc's T5 row drops the Scaladoc's examples of a non-project enumeration | fixed in P6-C3 |
| W3 | scala-zio | the tie-break's final key is the rendered SPARQL, which for a `VALUES`-bearing unit contains an inference variable name derived from a truncated hash; PR 2 is the first place that hash becomes load-bearing for *triplestore-visible pattern order* rather than only for snapshot stability | fixed in P6-C3 as a Scaladoc note on `rank` pointing at `createInferenceVariable`. The hash width itself is unchanged -- decision D3 accepted "injective for practical purposes", and widening it is a PR 1 change not worth the layer dance at this point |
| W4 | scala-zio | several tier rows are marked "hypothesis" in the provenance table, and the Fuseki version stage ran during the spike was not captured, so part of the shipped tier order is unverified against the production server version | recorded under Deferrals; this is the human follow-up H2 the plan already schedules, not something the orchestrator can close |

### Suggestions and Nits recorded

- **performance:** `rank`'s non-statement branch recomputed `vars` although `UnitKey` caches it -- **fixed** in
  P6-C3, verified by no golden changing. The O(n^2)-per-block greedy loop was re-examined and confirmed
  negligible at the corpus's block sizes; flagged only as a shape to watch if a query with a hundred top-level
  statements ever appears.
- **pattern:** three query strings (`queryClasslessMatchFulltext` and the two decimal-sort-criterion inputs)
  are still duplicated verbatim between the two E2E specs. Pre-existing, untouched by this stack, but it now
  sits oddly beside the shared `queryListNodeAnchor` this stack introduced. Fold in when one of them is next
  touched.
- **dune:** nothing in CI keys on a diff touching `PrequeryPatternOrdering.scala`, so the new `just` recipe
  closes the *local* loop, not a CI hole -- CI's `test-it` target already runs both golden specs before merge.
  The reviewer judged the checkpoint-5 finding closed at the best enforcement level the repo's Bazel module
  split allows.
- **scala-zio:** `StatementsFirst`'s Scaladoc was checked specifically as the one PR1/PR2 seam that could hide
  a silent reliance, and found to state its own reason for existing precisely enough.

### Checkpoint status (checkpoint 6)

**Complete.** The Critical needs no code change and is recorded as a merge-order constraint for the session;
W1, W2, W3 and the caching Suggestion are fixed; W4 and the remaining Suggestions are recorded.

## Phase 6 close (round 7) -- all six phases complete

- `phase6_head` = `4855b230c`
- **PR 2** (`pr1_head` `db042acb2` to HEAD): 19 commits, all tagged `(DEV-7287)`.
- **PR 1** (`base_commit` `dd9136d0c` to `pr1_head` `db042acb2`): 20 commits, all tagged `(DEV-7288)`.
- Working tree clean except `docs/specs/` (the run's clean-tree rule).

Final verification, all from the worktree, all green:

| check | result |
| --- | --- |
| `just test-unit` (bagit, ingest, jwt, shacl-validator, sparql-builder, webapi) | all PASSED, 3 min 36 s cold |
| `just test-gravsearch-prequery` (unit spec plus both golden specs) | PASSED, 48 s |
| `OntologyInferencerE2ESpec` `--cache_test_results=no` | PASSED 13.0 s |
| `just fmt` | no changes, 0 licence headers inserted |
| `just check` | PASSED |
| `git status --porcelain` | `?? docs/specs/` only |

**`gh stack view --json` reports `needsRebase: true` on PR 1**, because `origin/main` moved three commits
beyond `base_commit` during rounds 4-7: `5fe76b55e`, `c10093519` and `c62ae32ba`, all DEV-7303/DEV-7304
dependency-narrowing work. `git diff --name-only dd9136d0c..origin/main` is `MODULE.bazel`,
`maven_install.json`, `modules/ingest/BUILD.bazel`, `modules/webapi/BUILD.bazel` -- **three of those four are
files PR 2 also edits** (chunk P5-C5 removed `scala-graph` from `MODULE.bazel`, `modules/webapi/BUILD.bazel`
and, via the re-pin, `maven_install.json`). A conflict at ship time is likely and is a lock-file merge, not a
mechanical one. The orchestrator did **not** rebase: `base_commit` is fixed by the brief and never
re-derived, every review diff and golden regeneration in this run keys off it, and the "Shipping as a stack"
sequence puts `gh stack rebase` in the session's hands. It also interacts with the open scalacheck decision
below -- re-running `bazel run @unpinned_maven//:pin` after the rebase may resolve or change it.

## Review checkpoint 3 (Phase 3 close, spike write-up only)

No code diff — checkpoint 3's input is the design doc, so per the protocol only `eng:review:performance-reviewer`
was spawned, briefed with the design doc, the assets directory, the plan's "Ordering algorithm" / "Spike
protocol" / D4 / D13, and the Fuseki engine facts doc.

Findings: **4 Critical, 10 Warning**, several Nits. This was the most productive checkpoint of the run: three
findings changed a conclusion, and two of those changed the tier table. The reviewer's Criticals were verified
**directly against the engine facts doc and by new measurement** rather than through a
`dev:coordinator:finding-verifier`, because in every case the cheaper and more conclusive check was to read
`docs/development/dsp-api-fuseki-query-execution.md` or to re-run the query — which is what a verifier would
have had to do anyway, without being able to run stage queries.

| # | Finding | Verdict | Action |
| --- | --- | --- | --- |
| C1 | the `--once` exemption recorded each exempt layout's **warm-up** run — the coldest query of its case — against warm medians for the others, so S8-C's "5.8×" was cold and is the sole evidence for half of rule 3c | **REAL** (confirmed by reading `run-case.py`) | re-measured as case `S8w`: C warm over 5 rounds is 26.55 s against A's 4.80 s. Conclusion holds; the number moved from 5.8× to 5.5× |
| C2 | D13's 20% rule was applied to wall-clock times containing an unmeasured constant offset, which systematically manufactures ties in the sub-second cases | **REAL** | floor measured (case `F0`): **0.13 s**, not the guessed 0.10 s. Every table re-expressed net of it. S3 and S7 are now reported as *below harness resolution* rather than "measured equal"; S5's win survives (net 0.00 vs 0.04, disjoint distributions); no large-effect case moved |
| C3 | the tier table's "Decided by" column claimed measurement for four rows nothing measures (T3's rank, T6-above-T7, T7's path sub-rule, the T4/T5 split) | **REAL** — verified case by case against the layout files | column rewritten as "Provenance", explicitly marking each row measured or hypothesis. This is the finding most likely to have misled Phase 4 |
| C4 | T2 as written ("variable predicate allowed") promotes `?s ?p <iri>` to rank 2, which Fact 1's corollary names as a pathological shape needing a subquery barrier (1801 ms → 317 ms, DEV-6885) | **REAL** — verified against Fact 1 | variable-predicate statements **excluded from T2**, ranked T7 next to the `subClassOf*` exclusions. Reasoned, not measured; recorded as such |
| W1 | the write-up's premise ("Fuseki evaluates a BGP in written order") contradicts Fact 1, which says TDB2 *does* reorder within a BGP by counting bound terms | **REAL** — verified against Fact 1 | new opening section "What Fuseki actually does with written order" states the three situations in which written order decides (across a barrier, among equal-shape ties, where the heuristic counts the wrong thing) and places each case in one |
| W2 | S4-C changes two things at once, so it does not isolate "disconnected type statement after the anchor" | **REAL** (it is a transposition, not a single move) | claim softened in both documents; rule 3a satisfies both candidate mechanisms, so Phase 4 is unaffected |
| W3 | S8-C likewise changes two things (leader **and** chain direction) | **REAL** | new layout `S8w-D` leads with the same type statement but keeps A's forward chain: **≥120 s, ≥25× worse than A**, against C's 5.5×. The confound is resolved *in favour* of the conclusion, more strongly than the original evidence |
| W4 | the restated 3c's namespace test over-fires onto selective knora-base classes (`kb:Region`, `kb:Annotation`, `kb:StillImageRepresentation`, `kb:ListNode`, `kb:DeletedResource`) | **REAL** | rule restated to list `knora-base:LinkValue` and `knora-base:Resource` **by name**. Also argued for on fail-safe grounds: wrongly omitting a class costs an unmeasured risk, wrongly including one costs a measured 16–20× |
| W5 | the "LIMIT 25 early termination" threat cannot occur — Fact 6 says `GROUP BY` materialises its full input — and the spike's own data proves it | **REAL** — verified against Fact 6 | threat paragraph replaced. Evidence now cited affirmatively: `S2-B` (25 rows, 6.06 s) and `S2zero-B` (0 rows, 6.16 s) are indistinguishable, so S2's 16× has no early-exit confound at all |
| W6 | "the 17 949 resources of project 0102" conflates a per-class count with a project total | **REAL** | discovery section now states both figures are per class and that no project total was measured; the mechanism paragraph no longer leans on the number |
| W7 | S2 generalises from one project that is small relative to the store; the opposite regime is untested, and this is a *data* property that does not transfer the way an engine property does | **REAL and the sharpest finding** | new case `S2big` re-runs S2 against project 0812 (111 939 `ekws:Object` vs 0102's 17 949 `Page`): B 6.95 s, **both project-first layouts past the timeout**. The conclusion holds with a larger margin on the larger project |
| W8 | the tier table institutionalises a shape Fact 7 says to avoid, and ignores Fact 5's `GRAPH` scoping, which deletes the `attachedToProject` join the whole T5/T6 debate is about | **REAL** | both recorded in S2's section as explicit follow-ups: T5 is a mitigation for a query shape a later ticket should remove, not an endorsement of large `VALUES` blocks |
| W9 | `stage.sh` records any non-zero exit as `120,-1` and discards stderr, so a blip is indistinguishable from a timeout | **REAL** | `S8w-D`'s timeout re-checked by hand with stderr visible (genuine client-side 120 s timeout); `S2zero-C`'s lone 120 s row against a 98 s median is now treated as suspect, not as data |
| W10 | after two timeouts the driver writes synthetic `120` rows, so "median 120 / variance within 6%" is partly fabricated | **REAL** | every such cell now reads `≥120 (censored)`; the direction is conservative, so no conclusion moves |
| W11 | the design doc renumbers tiers T1–T7 while the plan's algorithm section still says T1–T6, so plan sentences now read as their own opposite | **REAL** | explicit old→new mapping table added; the journal entry says Phase 4 implements from the measured table |

Nits fixed: the `generate.py` docstring's "every layout is a permutation" guarantee now records the S5-C
exception; the Fact 3 miscitation corrected to Fact 1; the References section now cites the facts actually
used (1, 5, 6, 7, 8) instead of the plan's boilerplate list; S1's D13 traceability spelled out; the
"changes no golden file" assertion extended to the `attachedToProject` tier move; the free cross-session drift
datum (`S7-A` ≡ `S1-A`, 0.19 s vs 0.20 s) surfaced as the ±0.01 s estimate that makes S5 readable.

**Not done, recorded as the top follow-up:** the reviewer's suggestion to dump the optimised algebra
(`arq.qparse --explain --print=opt`) for one pure-BGP pair (S6-A vs S6-B) and confirm the premise against
Fact 1 empirically rather than by argument. It needs a local Jena CLI against a stage-like dataset, which the
run's "stage only, via dsp-cli" rule does not provide, so it is out of reach for this orchestrator. It is
listed under Deferrals.

Follow-up measurements added this checkpoint (all on stage, all in `results.csv`): `F0` (harness floor),
`S8w` A–C (warm re-run), `S8w-D` (confound isolation), `S2big` A–C (large-project generalisation). 8 of the
reviewer's 14 Critical/Warning findings were answered with new data rather than with prose.

## Phase 3 close

- `phase3_head` = `db042acb2` (equal to `pr1_head`; Phase 3 produces no commits by design).
- Working tree clean except `docs/specs/` (plan, journal, the new `-design.md` and `-assets/`), per the run's
  clean-tree rule. `git status --porcelain` shows only `?? docs/specs/`.
- Phase 4 was **not** started: review checkpoint 3 turned into substantial re-measurement and a tier-table
  revision, which is the right place to spend the round — Phase 4 reads the tier table as its input, so
  starting it before the table settled would have meant implementing the superseded version.

## Preflight baseline (Phase 1, untouched branch at `c38c8f8c2`)

- `docker info` ok; `just docker-load-test-images` ok.
- `bazel test //modules/test-it:test --test_filter='.*GravsearchToPrequeryTransformerE2ESpec.*'` → PASSED in 15.0 s
  (248 s elapsed including the cold build).
- `bazel test //modules/test-it:test --test_filter='.*GravsearchToCountPrequeryTransformerE2ESpec.*'` → PASSED in 14.5 s.

## Review checkpoint 4 (Phase 4 close, PR 2 reviewed as if finished)

Diff reviewed: `git diff db042acb2..HEAD` saved to `.claude/tmp/review/review-4.diff` (729 lines, 2 files, both
new adds). Every reviewer was briefed that the pass is **deliberately not wired yet** and that dead-code
findings are expected, and that the plan's tier numbering is the superseded hypothesis while the design doc's
measured table is authoritative.

Agents run: `eng:review:scala-zio-reviewer`, `eng:review:performance-reviewer`, `eng:review:consistency-reviewer`,
`eng:review:code-simplicity-reviewer`, `eng:review:dune-reviewer`. (Per the protocol, checkpoint 4 does not
spawn the pattern-recognition reviewer.)

Findings: **1 Critical, 5 Warning**, several Nits. The consistency reviewer found **zero** Warnings and
independently hand-traced the tanner-with-project case and the MINUS-seed tie-break through `pickNext` / `rank` /
`emitUnits`; the scala-zio reviewer verified `partition` and `vars` are exhaustive over all nine `QueryPattern`
subtypes and that every `OntologyConstants` reference resolves; the performance reviewer verified the ordering
is total and free of hash-iteration dependence, and could construct **no** shape where the new pass is worse
than today's `ReorderPatternsByDependency`.

| # | Reviewer | Finding | Verdict | Action |
| --- | --- | --- | --- | --- |
| 1 | dune (Critical), also simplicity and scala-zio as a Warning | the object Scaladoc cites `docs/specs/...-design.md` by path, which `CLAUDE.md` section Specs forbids: specs are a reference **sink** and the stated flatten test is a recursive grep for `docs/specs/` returning nothing outside that directory | **REAL** -- verified mechanically: the flatten grep returned exactly three hits, `CLAUDE.md` stating the rule itself, two unrelated `opentelemetry.io/docs/specs/semconv/` URLs, and our new line. Ours was the repository's only genuine violation | fixed in P4-R1 |
| 2 | simplicity, scala-zio | the 36-line Scaladoc is past the convention's ~12-line routing signal and duplicates the design doc's provenance discussion | **REAL** | fixed in P4-R1 by deleting the routing material (provenance, rejected alternative) rather than compressing the invariants. The residue is 33 lines of which 11 are the tier table itself, which is the object's contract, not routing |
| 3 | dune | the three still-live ordering sites (`GravsearchQueryOptimisation.optimiseQueryPatterns`, `SparqlTransformer.moveBindToBeginning` / `moveLuceneToBeginning`) carry no comment naming `PrequeryPatternOrdering` as the pending replacement, so an agent entering through the live call graph would patch the old passes | **NOT REAL** (`dev:coordinator:finding-verifier`) -- Phase 5, the immediately following phase of the same unmerged PR, deletes all three sites outright, so a forward-pointing comment would be deleted a few commits later; the new file already declares itself unwired and is reachable by a plain grep; and the repo has no convention for transitional markers, so the finding asks for a novel one | dropped as churn |
| 4 | performance | rule 3a before 3b prefers any connected non-type unit (even T7) over any connected type unit (even T4), which is unmeasured and is the S4-C/S4-D regression shape | **NOT REAL** (`dev:coordinator:finding-verifier`) -- it inverts the measurement. S4 layout **B**, the winner at 11x, is precisely "connected T7 plain (`hasMedium`) before connected T4 type"; layout D, which promotes the type statement ahead of the last connected non-type unit, is the censored >=120 s case. The code also matches the plan's explicit invariant ("3a before 3b"). S1/S8 concern leader selection (3c), a different rule | dropped |
| 5 | performance | the unselective-technical ban fires only on a bare `IriRef` object, so a `VALUES` enumerating only `LinkValue` / `Resource` would still lead at T5 | **REAL but by design** (it is the restated rule 3c's own distinction, and spec case 5 pins it) | folded into P4-R1 as a one-line comment marking the asymmetry deliberate |
| 6 | performance | the Scaladoc's "statements before blocks is not a SPARQL identity" caveat is over-broad: hoisting past `OPTIONAL` / `UNION` is always safe, only `MINUS` can change results | **REAL but not actionable here** -- the risk is inherited unchanged from `ReorderPatternsByDependency`, which has the same `sortedStatementPatterns ++ sortedOtherPatterns` shape | recorded under Deferrals for the Phase 6 doc pass; the caveat erring wide is the safe direction |

Nits recorded, not fixed: a single-hop `?x rdfs:subClassOf <C>` (no `*`) ranks T7 with `pathRank = 0`, slightly
at odds with the plan prose calling the exclusion "path" -- unmeasured and not produced by the pipeline; a
`VALUES` referenced only by a `BindPattern` falls through to the orphan bucket rather than attaching to the
bind -- harmless, the pipeline never emits that shape; the spec's class-level case index restates what each
case's own inline comment already says.

**Two Scaladoc edits Phase 5 must make when it wires the pass:** the opening sentence still says "not wired
into the prequery pipeline yet", and the recursion-seed paragraph says "step 4 below", a dangling reference to
the plan's step numbering that no longer appears in the file. Both were left rather than churned, because
Phase 5 edits this Scaladoc anyway.

## Phase 4 close

- `phase4_head` = `1c24c65e9`
- Three commits this round, all on PR 2, all tagged `(DEV-7287)`:

| commit | subject |
| --- | --- |
| `e13502ef9` | `perf: add the connectivity-aware prequery pattern ordering` |
| `b2a04e213` | `test: pin the prequery ordering pass's structural invariants` |
| `1c24c65e9` | `docs: route the ordering pass's Scaladoc off the spec files` |

Verification at the phase close, all from the worktree, all green:

| check | result |
| --- | --- |
| `bazel test //modules/webapi:test` (full pure-JVM suite) | PASSED 19.3 s |
| `//modules/webapi:test --test_filter='.*PrequeryPatternOrderingSpec.*' --cache_test_results=no` | PASSED, 21 tests |
| `//modules/test-it:test --test_filter='.*GravsearchToPrequeryTransformerE2ESpec.*'` | PASSED (unchanged, as expected: the pass is unwired) |
| `//modules/test-it:test --test_filter='.*GravsearchToCountPrequeryTransformerE2ESpec.*'` | PASSED (unchanged) |
| `just fmt` | no changes |
| `just check` | PASSED |
| working tree | clean except `docs/specs/`, per the run's clean-tree rule |
| flatten test (recursive grep for the spec directory outside it) | only `CLAUDE.md` and two unrelated OpenTelemetry URLs |

**Deviation from the plan, recorded deliberately.** The Phase 4 checklist requires the Scaladoc to "cite the
spike write-up in the design doc". `CLAUDE.md` section Specs forbids exactly that, with a mechanical flatten
test. The repo convention wins: the settled tier table is inlined in the Scaladoc instead, and no spec path
appears in source. **Phase 6 owes the durable destination** -- its rewrite of
`docs/05-internals/design/api-v2/gravsearch.md` must carry the measured basis and the per-row provenance, and
only then may the Scaladoc point at *that* doc. Until it does, the provenance lives only in this journal and in
the spike write-up.

**Deviation on the commit trailer.** The Orchestration Brief asks for `Co-Authored-By: Claude Fable 5.1`, which
is what rounds 1 to 4 used. This round ran on Claude Opus 5 (1M context) and the harness's own attribution
instruction names that model, so the three commits carry `Co-Authored-By: Claude Opus 5 (1M context)`. The
branch's trailers are therefore not uniform. Flagged for the session: if uniformity matters more than accuracy,
this is the point to decide, before PR 2 is pushed.

## Phase 5 in progress (round 5 stopped here)

Only the **first** plan item of Phase 5 is ticked (the `QueryTraverser` seam). The old passes are still in
place and still run **before** the new one, so the deletions that follow are **not** golden-neutral and the
regeneration and audit below must be **repeated** after them. Their checkboxes are therefore left unticked
even though a full pass of each has been done once.

### The Lucene defect the wiring exposed (found by the golden audit, fixed in the same commit)

Regenerating the goldens against the bare wiring showed `?familyName text:query "Bernoulli"` landing **fifth**
inside the `OPTIONAL` of `..._optional.txt` and the `text:query` statement landing **last** in each `UNION`
branch of `..._reorderWithUnion.txt`.

Cause: `pickNext` tried rule 3a (connected non-type units) before consulting the tier at all. A block recursed
with a non-empty outer bound set almost always has some connected unit, so a T1 Lucene unit whose own variable
is not yet bound could never be chosen until something else bound it. T1 existed precisely to prevent that.

This is a regression against the pass being deleted, not a judgement call: `SparqlTransformer.moveLuceneToBeginning`
hoists any Lucene statement or Lucene-containing `GroupPattern` to the front of its block **unconditionally**,
and its own Scaladoc records a **measured ~300x** cost (DEV-6715) for evaluating the classless
`?mainRes a knora-api:Resource` VALUES enumeration ahead of the index-anchored lookup. Parity with a measured
result is the conservative default, so this was treated as a defect in the pass and fixed rather than accepted
as a golden, per the plan's audit instruction.

Fix (chunk P5-C2): `pickNext` gains a rule ahead of 3a and 3b -- if any remaining unit is T1, take the minimum
by key among those, ignoring connectivity. Two spec cases pin it (a bare `text:query` statement and a Lucene
`GroupPattern`, each leading over a connected plain statement when `order` is called with a non-empty
`outerBound`). After the fix both goldens put the Lucene pattern first in its block again.

**The tier table needs one sentence at ship time:** T1 is not merely the lowest tier, it **pre-empts the
connectivity rule**. Every other tier is consulted only among candidates a connectivity rule has already
selected. Phase 6's doc pass owes this.

### Golden diff audit (first pass; repeat after the deletions)

24 of 35 golden files changed. Mechanical invariant checks over every regenerated SPARQL golden, scripted,
**zero issues**: no golden leads with a bare `knora-base` type statement; no top-level pattern follows a
top-level `FILTER` or `FILTER NOT EXISTS`. The five `<suffix>Shape` files match the journal's "Expected shape
sequences (measured tiers)" table **line for line**, and the count spec's `listNodeAnchorShape` is
byte-identical to the page spec's, as the plan's cross-spec invariant requires.

| golden | what moved | rule |
| --- | --- | --- |
| `listNodeAnchor` (page and count) | `hasSubListNode*` from last to **first**; the `beol:letter` type statement from first to last | T2 path anchor leads (3c); the project-class type unit closes the component (3b) |
| `linkTargetAnchor` | `hasAuthor <anchor-person>` from second to **first**, `rdf:object <anchor-person>` up to third; both type statements to the end, `beol:letter` then `knora-base:LinkValue` | T2 bound IRI leads (3c) and wins the lexical tie against the `rdf:object` statement; `LinkValue` is unselective-technical, ranked T7 |
| `classValuesLabelFilterOrderBy` | `title` and `label` swap | lexical key: `?src <http://www.knora...` before `?src <http://www.w3...` |
| `classValuesLabelFilterOrderByProjectLimited` | `attachedToProject` from **after the FILTER, next to last** to directly behind the type unit | T6 beats T7 among connected units (3a). This is the row the plan predicted as the visible before/after win |
| `rdfsLabelAndLiteral` | `rdfs:label "..."` ahead of the `beol:book` type statement | the new T3 bound-literal tier (S5) outranks T4 |
| `reorder` | a `valueHasString "..."` bound-literal statement to the front; both `LinkValue` type statements to the end | T3 leads (3c); technical type units last |
| `dateNonOptionalSortCriterion`, `...AndFilter`, `dateFilterInUnionAndTopLevel`, `unionScopes`, `reorderWithUnion` | a two-statement chain reverses (`valueHas...` before the property statement) | all units are T7 with equal bound-term counts, so the lexical tie-break decides. See the caveat below |
| `reorderWithMinus` | the `MINUS` block from first to after the statements | statements before blocks (step 4), parity with the pass being replaced |
| `standoffTagHasStartAncestor` | the component re-seeds from the `standoff:StandoffParagraphTag` type unit instead of `hasText` | no T1/T2/T3 unit exists, so the project-class type unit leads at T4 (3c) |
| `reorderWithCycle` | leads with an `rdf:object` statement, the three `LinkValue` type statements move to the end; terminates | 3c picks the lexically smallest non-banned unit; unselective-technical units may never lead |
| `optional`, `matchFulltextInUnion`, `filterBeforeStatementsInUnion`, `decimalOptionalSortCriterionAndFilterComplex` | changes confined to the interior of an `OPTIONAL` or a `UNION` branch | the pass recurses into those blocks with the outer bound set as seed |

**`GroupPattern` interiors are untouched.** The two `matchFulltext` goldens keep the Lucene group first and
byte-identical inside; the `text:query` lines that move in `optional` and `reorderWithUnion` are **bare
statements inside a `UNION` branch or `OPTIONAL`**, not group contents, and after the T1 fix they lead their
block.

**Caveat worth carrying to review: the lexical tie-break decides the leader of an all-T7 component.** Five
goldens change only because two plain statements swapped on `pattern.toSparql` order. Those queries have no
anchor of any kind, so every layout is a scan and no order is defensibly better -- but the key is a
determinism device, not a selectivity heuristic, which is the same observation recorded at review checkpoint 2.
It is not a regression relative to today (today's order was the topological sort's, equally unrelated to
selectivity), and it is now at least stable.

### State at the round's end

- `round5_head` = `c2eaa038f`, on PR 2.
- Working tree clean except `docs/specs/`.
- Full pure-JVM suite, both prequery E2E specs (clean rerun after regeneration), `just fmt` and `just check`
  all green at the wiring commit.
- **Still live and still running before the new pass:** `ReorderPatternsByDependency` / `createAndSortGraph` /
  `TopologicalSortUtil`, `SparqlTransformer.moveBindToBeginning` and `moveLuceneToBeginning`. Because the new
  pass preserves the input order of binds, blocks, filters and `FILTER NOT EXISTS`, these upstream passes still
  influence the output, so deleting them **will** move goldens again.

## Round 8: ship-time rebase

The only job of this round was to rebase the two-branch stack onto the current `origin/main`. No plan work.

### New commit coordinates

| ref | before | after |
| --- | --- | --- |
| `base_commit` (`origin/main`) | `dd9136d0c` | `cd88f04b5` |
| `pr1_head` (DEV-7288) | `db042acb2` | `5b97711f3` |
| `phase6_head` / PR2 tip (DEV-7287) | `4855b230c` | `5f0f191cc` |

`origin/main` had moved 7 commits ahead of `dd9136d0c`: three SPARQL-DSL migrations (DEV-7230/7233/7234),
the `gravsearch.prequery` trace event (DEV-7302, `cd88f04b5`) and three dependency-hygiene commits
(DEV-7303/7304: `5fe76b55e`, `c10093519`, `c62ae32ba`) that edit the same three build files PR2's
scala-graph removal touches.

`gh stack rebase` (with `GIT_EDITOR=true`) did the whole cascade. It warned that it could not fast-forward
the local `main` branch, because `main` is checked out in the primary worktree — it rebased onto
`origin/main` directly instead, which is what we wanted anyway. Backup refs `refs/backup/round8-pr1` and
`refs/backup/round8-pr2` hold the pre-rebase tips.

### Conflicts and how each was resolved

**PR1 (DEV-7288) replayed all 20 commits with no conflict at all** — as predicted, none of its files
(`GoldenTest`, `SparqlTransformer`, `OntologyInferencer`, `SelectTransformer`, `SparqlQuery`, the prequery
specs and goldens, docs, `CONVENTIONS.md`, `ARCH-MAP.md`, `TopologicalSortUtil`) overlap the upstream commits.

**PR2 stopped once,** at `faa15ff47` "build: drop the scala-graph dependency (DEV-7287)":

| file | outcome |
| --- | --- |
| `MODULE.bazel` | auto-merged. Verified by diff: the replayed commit contributes exactly one deleted line (`org.scala-graph:graph-core_3:2.0.2`); upstream's rdf4j narrowing and six-dependency drop are intact. |
| `modules/webapi/BUILD.bazel` | auto-merged. One deleted line (`@maven//:org_scala_graph_graph_core_3`); upstream's dependency narrowing intact. |
| `.github/renovate.json` | auto-merged. The graph-core ignore rule is removed; the file re-validated as JSON with `python3 -c "json.load(...)"`, and the trailing-comma hazard of deleting the last array member did not materialise (the removed object was the last one, and its preceding `},` collapsed correctly). |
| `maven_install.json` | **conflict (UU).** Not hand-merged, per the "the lock is regenerated, never hand-edited" rule in `CONVENTIONS.md` § "Versions single-sourced". Resolved with `git checkout --ours` — in a rebase `--ours` is the new base, i.e. upstream's lock — then regenerated after the rebase finished. |

After `gh stack rebase --continue` the cascade completed on its own; no manual `git rebase --continue` was
needed, and git was never left in a rebasing state.

### The re-pin, and what happened to scalacheck

`bazel run @unpinned_maven//:pin` produced a **pure deletion of 71 lines, zero additions**. The lock now has
zero `scala-graph` / `graph-core` references. Three artifacts left together, because the latter two were
transitive-only dependencies of graph-core:

| artifact | version in upstream's lock | after re-pin |
| --- | --- | --- |
| `org.scala-graph:graph-core_3` | 2.0.2 | gone |
| `org.scalacheck:scalacheck_3` | **1.18.1** | gone |
| `org.scala-sbt:test-interface` | 1.0 | gone |

**This differs from the round-6 state and is an improvement, worth one sentence at review.** The pre-rebase
PR2 lock had already dropped graph-core but still carried an orphaned `org.scalacheck:scalacheck_3` **1.18.0**
entry — the version Balduin accepted at the time. The fresh re-pin against upstream's newer resolution drops
scalacheck (and `test-interface`) entirely rather than leaving the orphan, so **no scalacheck version is
pinned any more**. Nothing in the repo imports `org.scalacheck`; it was only ever reachable through
graph-core.

Committed separately as `5f0f191cc` `build: re-pin the Maven lock after the rebase (DEV-7287)`, so the
regenerated lock is reviewable apart from the replayed history. This makes PR2 20 commits instead of 19.

### Verification after the rebase

All green, in this order:

| check | result |
| --- | --- |
| `bazel build //modules/webapi:webapi` | success (178 s, cold-ish) |
| `bazel test //modules/webapi:test` (full pure-JVM) | PASSED |
| `bazel test //modules/test-it:test --test_filter='.*GravsearchToPrequeryTransformerE2ESpec.*'` | PASSED |
| `... '.*GravsearchToCountPrequeryTransformerE2ESpec.*'` | PASSED |
| `... '.*OntologyInferencerE2ESpec.*'` | PASSED |
| `just fmt` | no-op — zero files reformatted, zero license headers inserted, nothing to commit |
| `just check` | exit 0 |

**No golden file moved.** The upstream trace-event commit (DEV-7302) instruments the prequery but does not
change its rendered SPARQL, which the three E2E specs confirm byte-for-byte.

### State at the round 8 close

- `git status --porcelain` shows only `?? docs/specs/`.
- `git log --oneline origin/main..<PR1>` = **20** commits; `<PR1>..<PR2>` = **20** (19 replayed + the re-pin).
  The brief's expectation of 21/18 does not match; nothing was dropped in the rebase — PR1 was 20 commits
  before and after, PR2 was 19 before and is 19+1 now. The 21/18 figures in the brief appear to be
  miscounted rather than describing a lost commit.
- `gh stack view --json`: trunk `main`, PR1 then PR2, `needsRebase: false` on both, `currentBranch` is PR2,
  and PR2's recorded base is PR1's new head `5b97711f3`. Two cosmetic staleness artefacts in that output,
  neither reflecting a real problem: it still reports PR2's `head` as `a6aef130f` (the tip before the re-pin
  commit) and PR1's `base` as `c62ae32ba` rather than `cd88f04b5`. `git merge-base --is-ancestor origin/main
  <PR1>` confirms PR1 genuinely sits on top of the current `origin/main`; both fields refresh on the next
  `gh stack submit` / push.
- **Nothing is pushed.** Both branches are local-only ahead of their remotes and will need a force-push
  (`gh stack submit`) at ship time — that is the session's call, not this round's.

## Closeout

- root_cause: Fuseki (TDB2, no `stats.opt`) executes a prequery's basic graph pattern in the order dsp-api writes
  it, and four passes wrote that order without seeing the final shape: the stage-1 topological sort put a bound
  IRI object (the most selective statement) last and the class statement first, `handleListNode` reached the
  list-node anchor only through a property path emitted last, `OntologyInferencer` inserted class `VALUES` blocks
  wherever the type statement happened to sit, and the sort's middle layers were read in
  `System.identityHashCode` order (scala-graph `EqHashMap`), so the same query rendered in two orders on
  consecutive JVM runs (the tanner 50 ms / 49 s bimodality). Two further defects of the same class surfaced only
  under review: the pass's first version treated every non-`knora-base` class as a project class, so a
  `standoff:StandoffParagraphTag` type statement led the standoff-ancestor query (28 s), and exact ties fell to a
  lexical key that let a store-wide `knora-base:valueHas*` predicate drive the join ahead of a project predicate.
- investigation: the DEV-7287 stage layout matrix showed that hoisting the anchor alone is not a fix (anchor then
  type before the connecting statement times out at 120 s), which fixed the design as "greedy connectivity from
  bound anchors, type units last". The plan's tier order was a working hypothesis; an eight-case stage spike
  (five interleaved runs per layout, D13 decision rule) overturned two of its rows: the technical
  `Resource`-closure `VALUES` must lead ahead of `attachedToProject` (16x on project 0102, both project-first
  layouts time out on 0812), and a bound-literal tier exists (S5, marginal). Rule "only project-class type
  units may lead" was replaced by a by-name ban on `LinkValue` and `Resource` (S8-C, 5.8x); a namespace ban was
  refuted by measurement (`Region`, `Annotation`, `ListNode` are selective). Golden conversion exposed the
  pre-existing layer-order nondeterminism (five `--cache_test_results=no` runs flapped), root-caused to
  scala-graph's identity-hashed layer buffer; wiring exposed that connectivity-before-tier buried Lucene groups
  inside blocks (parity fix: T1 pre-empts connectivity). Review checkpoint 5 found the built-in-vocabulary gap;
  S9/S10 on stage measured it at 166x and 6.3x before the fix landed. Dead ends: pinning goldens without a
  determinism fix (option b, rejected), a source-order layer key (would pin whichever order the author wrote),
  and reading the plan's tier numbers into the code (superseded by the measured table).
- solution: PR 1 (DEV-7288) makes the rendered prequery byte-stable and pins it: content-derived inference
  variable names (`createInferenceVariable`, subject base plus an 8-hex statement hash, replacing the random
  suffix and the counter), sorted `VALUES` entries and SELECT columns, a deterministic layer order in
  `TopologicalSortUtil`, a `GOLDEN_REWRITE` env switch, and 35 golden files (26 converted structural tests, the
  count spec, the four DEV-7287 shapes with shape summaries, two stage-1 regression cases). PR 2 (DEV-7287) adds
  `PrequeryPatternOrdering`, one pure pass called once in `QueryTraverser.transformSelectToSelect` after
  inference: seven data-driven tiers (Lucene, bound IRI, bound literal, project-data-ontology class,
  enumerating technical type, `attachedToProject`, plain), greedy connectivity from the bound set, type units
  after the statements binding their subject, a by-name ban on unselective technical types leading, `VALUES`
  attached to the first statement using its variable, blocks recursed with Fuseki-shaped seeds (MINUS empty),
  and a total, permutation-invariant tie-break (bound terms, project-ontology predicate, rendered SPARQL). The
  topological sort, `TopologicalSortUtil`, `moveBindToBeginning`, `moveLuceneToBeginning` and the scala-graph
  dependency are deleted; stage 1 keeps only the statements-first partition (`StatementsFirst`). The three prod
  shapes now render anchor-first: list node 4.2 s to 0.48 s, link target 0.70 s to 0.14 s, tanner with project
  moves `attachedToProject` from after the FILTER to directly behind the class `VALUES`.
- prevention: every prequery in the corpus is a golden file, so any ordering change is a reviewable diff;
  `<suffix>Shape` summaries make the top-level order greppable for the ten goldens whose leader follows a
  non-obvious rule; `PrequeryPatternOrderingSpec` pins permutation invariance, output-size preservation, the
  tier exclusions by name, T1 pre-emption, MINUS parity and the standoff and sort-by-date shapes;
  `TopologicalSortUtilSpec` (PR 1 only) pins the deterministic layer order; `just test-gravsearch-prequery` runs
  the unit spec and both golden specs together; the design doc carries the measured basis with a per-row
  provenance column so unmeasured rows (T1, T3's rank, T4 vs T5, T6 vs T7, the path sub-rule) are visible.
  Anti-patterns recorded: a determinism key is not a selectivity heuristic (it pinned the fast tanner mode by
  luck, and would pin the slow standoff mode, so PR 1 must not ship without PR 2); never widen the
  technical-type ban to a namespace; never fall back to input order on ties; never link code comments into
  `docs/specs/`.

## Round 9: CI fix

CI on PR #4349 failed exactly one E2E test: `SearchEndpointPostGravsearchCountE2ESpec` "count anything:Thing
that doesn't have a boolean property (MINUS)", which asserted a count of 52. Before DEV-7287 the prequery
emitted the class statement AFTER the `MINUS` block (visible in PR1's golden
`GravsearchToPrequeryTransformerE2ESpec__reorderWithMinus.txt`: `MINUS {...}` first, then the `VALUES` /
`?thing rdf:type ?resTypes`). Per SPARQL algebra a `MINUS` evaluated against an empty solution removes
nothing, so the old test counted all 52 `anything:Thing` resources visible to `anythingUser1`. The new
ordering pass emits all top-level statements before blocks, so the class statement now precedes the `MINUS`
and the `MINUS` takes effect: the arithmetic is 52 total minus 2 Things with `anything:hasBoolean` in the
test data, giving 50. The sibling test for the same intent, "... (FILTER NOT EXISTS)", already expected 50.
This was a latent correctness bug in the old prequery ordering that DEV-7287 fixes; the test expectation was
stale and has been corrected to 50.

## Round 10: H2 regression fix

The H2 stage replay found one hard regression on PR 2. The `reorderWithCycle` prequery (a link property with
subproperties, expanded to a `VALUES` over the predicate variable) timed out at 120 s on stage, where the PR 1
text ran in 0.3 s. The PR 2 text opened with `?thing1__...__LinkValue <rdf:object> ?thing2 .` - subject and
object unbound, i.e. a scan over every link value in the store. Mechanism: all units in that block are T7, so
the leader is decided by the tie-break; the bound-terms count credited the `rdf:object` predicate IRI as one
bound term while a variable predicate restricted by an attached (not yet emitted) `VALUES` counted as zero, so
the store-wide statement won. The project-data-ontology predicate preference could not rescue it, because it
ran after the bound-terms count and did not recognise a variable predicate as project-scoped even when its
`VALUES` listed only project IRIs.

Fix (commit `f327048ad`): a variable with a non-empty attached `VALUES` counts as a bound term in the
tie-break, on par with a bound IRI - `attachValues` emits that `VALUES` immediately before the first pattern
referencing the variable, so the term is restricted exactly like a bound IRI at evaluation time. The project
predicate preference additionally accepts a variable predicate whose attached `VALUES` enumerates only project
data ontology IRIs (a mixed or built-in-containing enumeration stays ineligible, mirroring `typeTier`). Tiers,
the T2 exclusion of variable predicates, the by-name ban on `LinkValue`/`Resource` leading, the rendered-text
final key and permutation invariance are unchanged. `PrequeryPatternOrderingSpec` gained the reduced cycle
shape (a `VALUES`-restricted statement leads, `rdf:object` never leads a component), a mixed-`VALUES` case that
must not win the project preference, and permutation invariance for the reduced shape.

Golden audit (commit `7647ddea4`): exactly two files changed, `GravsearchToPrequeryTransformerE2ESpec__reorderWithCycle.txt`
and its `...reorderWithCycleShape.txt` summary. The prequery now opens with a `VALUES`-restricted statement and
every `rdf:object` statement follows the statement binding its subject. No other golden changed - in particular
the five prod-shape files are byte-identical, as are `reorder`, `reorderWithUnion`, `reorderWithMinus` and the
count goldens.

Stage measurement (dsp-cli, `-s stage --timeout 120 --accept csv`, five runs each, before/after interleaved;
"before" is the PR 1 text from `h2-replay/`):

| query            | before (median) | after (median) | rows |
|------------------|-----------------|----------------|------|
| reorderWithCycle | 0.12 s          | 0.12 s         | 0    |
| reorder          | 3.65 s          | 6.67 s         | 25   |

`reorderWithCycle` is back in the sub-second range (it returns 0 rows on stage, the anything project is absent,
so only the timing is meaningful; the 120 s timeout is gone). Result rows for `reorder` are byte-identical to
the before run after sorting, 25 rows both ways.

### Open: the `reorder` shape is still 1.8x slower than PR 1

`reorder` was not touched by this fix - its golden is unchanged - and it remains reproducibly slower than the
PR 1 layout (6.67 s vs 3.65 s, five interleaved runs each, spread under 0.15 s). Its PR 2 text leads with the
most selective statement available (`?gnd1 knora-base:valueHasString "(DE-588)118531379"`), which is right, but
then emits `?letter__linkingProp1__person1__LinkValue rdf:object ?person1` at position 3, before the statement
binding that link value. Here the predicate variable is restricted by a top-level `FILTER` (`?linkingProp1 =
beol:hasAuthor || = beol:hasRecipient`), not by a `VALUES`, so this round's rule does not apply.

Hand-permuted A/B on stage (three runs each, the variants are kept untracked in the assets directory as
`h2-replay/reorder-v{A,B,C,D}.rq`), all returning the same 25 rows:

| layout                                                                    | median  |
|---------------------------------------------------------------------------|---------|
| PR 1 (`reorder-before.rq`)                                                | 3.64 s  |
| PR 2 (current golden)                                                     | 6.69 s  |
| vC: `rdf:object` moved one slot later, still before its subject's binder  | 6.60 s  |
| vB: both `rdf:object` statements pushed to the end of the statement block | 10.70 s |
| vD: the `person1` `rdf:object` moved directly behind its subject's binder | 1.96 s  |
| vA: both `rdf:object` statements moved directly behind their binders      | 0.46 s  |

So a layout 8x faster than PR 1 exists for this shape, but no local tie-break rule reaches it. The obvious
candidate rules were refuted by measurement: deferring `rdf:object` by itself does not help (vC), and deferring
it to the end makes things worse (vB) - what wins is emitting it *immediately after* the statement that binds
its subject, which the greedy pass cannot choose without lookahead, because at the deciding step the two
candidates are indistinguishable on every local key (both have an unbound subject and a bound object, and the
winning one introduces *more* fresh variables, so a fewest-fresh-variables rule picks the wrong one too). This
is a cost-based-search question, not a tie-break defect, and it is left for a follow-up rather than fixed here.
The decision it raises: ship PR 2 with a known 1.8x regression on this one corpus shape, against the measured
wins elsewhere (list node 4.2 s to 0.48 s, link target 0.70 s to 0.14 s, the cycle shape 120 s to 0.12 s), or
block the merge on a lookahead redesign.

## H2 stage replay (session, 2026-09-18)

Every SPARQL golden that changed between PR 1 and PR 2 (14 files) plus the five prod shapes (S1, S3, S4, S9, S10
layouts) was run in both versions on stage via dsp-cli and the sorted result rows were byte-compared.

- 17 of 19 pairs identical. Two not verifiable: `optional` times out at 120 s on both sides (pre-existing),
  `reorderWithCycle` timed out on the PR 2 side only, the regression fixed in round 10.
- The intended `MINUS` difference (`reorderWithMinus`) is not observable on stage (the `anything` project is absent);
  the E2E expectation change in round 9 covers it. Stage did show the cost of the old shape: 41.7 s (`MINUS` first)
  against 0.3 s (reordered) for the same empty answer.
- Prod shapes, identical rows, before to after: list node 6.5 s to 0.6 s, link target 0.8 s to 0.3 s, tanner 0.4 s
  to 0.3 s, standoff 28.8 s to 0.3 s, sort-by-date 2.1 s to 0.4 s.
- Coverage caveat: 9 of the 14 goldens target the test-only `anything` project or fixture literals, so they compare
  empty against empty. Real rows were compared for `listNodeAnchor` (page and count), `reorder` and the prod shapes.

Files: `~/Desktop/gravsearch-ordering-measurements/h2-replay/` (README with the per-file verdicts, `results.csv`,
the before/after query texts and result CSVs).

## Dev timing baseline (session, 2026-09-18)

Harness and BEFORE baseline for the post-deploy check, taken against `api.dev.dasch.swiss` on `main` before the stack
(`webapi v39.0.0-33-gded89ed`). Dev carries prod-like data (111,939 `ekws:Object`, the DEV-7287 list node with
19,459 hits, tanner 0102, beol 0801), so the prod shapes were used with real IRIs. Nine interleaved runs per query
on `/v2/searchextended` and `/v2/searchextended/count`; medians:

| query | search | count |
| --- | --- | --- |
| 01 list node (ekws hasMedium + list node) | 4.11 s (no spread) | 4.21 s |
| 02 link target, median in-degree | 0.79 s | 0.71 s |
| 03 link target, large in-degree | 0.94 s | 0.82 s |
| 04 label + FILTER + ORDER BY, tanner | 44.3 s (bimodal 0.2 to 45 s) | 44.2 s |
| 05 label + FILTER + ORDER BY, ekws | 1.35 s | 7.08 s |
| 06 standoff ancestor, beol | 1.47 s | 0.93 s |
| 07 date sort, ekws | 2.22 s | 1.04 s |
| 08 control: class + property, ekws | 1.58 s | 1.18 s |

AFTER: once the stack is deployed to dev, run `RUNS=9 bash measure.sh after-<version>` in
`~/Desktop/gravsearch-ordering-measurements/dev-timing/` and compare medians (04 and 05 are bimodal, never compare
single runs).

## Accepted trade-off: the `reorder` shape (Balduin, 2026-09-18)

The `reorder` golden (two link hops with `FILTER`-restricted variable predicates and a literal anchor) stays 1.83x
slower than PR 1's order on stage (6.67 s against 3.65 s, identical rows). A layout exists that runs in 0.46 s, but
no local rule reaches it (see "Open: the `reorder` shape" under round 10). Decision: ship, record the trade-off in
the design doc and the PR body, and open a follow-up for lookahead or cost-based ordering of variable-predicate
link chains. Rationale: the shape is rare in real traffic, the measured wins are on the shapes that make up most of
the Fuseki time above 1 s, and PR 1's fast order for this shape was accidental.

## Round 11: rdf:object eligibility rule

Supersedes "Accepted trade-off: the `reorder` shape" above: the trade-off is resolved, not shipped.

The session's four-layout replay of the `reorder` prequery on stage (5 interleaved runs each, identical 25 rows)
measured: the round-10 order 6.98 s, layout vA 0.66 s, a FILTER-to-`VALUES` rewrite alone 53 s (harmful, dropped),
vA plus `VALUES` 0.64 s. Hand-tracing the greedy pass showed that one added eligibility rule reproduces vA
statement for statement, so no lookahead or cost-based search is needed after all.

**The rule.** A statement whose predicate is the bound IRI `rdf:object` and whose subject is a variable not yet in
the loop's `bound` set is not a `pickNext` candidate - neither for the connected step nor for leading a new
component - as long as any other unit is a candidate. `rdf:object` occurs in a prequery only on the generated
link-value node (`?s <linkValueProp> ?lv . ?lv rdf:object ?o`); the link value is reached from its resource, so the
check belongs right after the statement that binds `?lv`. Emitting it earlier starts the join from every link value
pointing at the object. Once `?lv` is bound the statement has three bound terms and the existing bound-terms
tie-break puts it next, so the rule only ever delays it. If nothing else is a candidate it is eligible as before, so
the pass stays total and permutation-invariant.

Implementation: `UnitKey.deferSubject: Option[QueryVariable]` (from `rdfObjectDeferSubject`), and `pickNext` narrows
its index set with `isDeferred` before the existing T1 / connected-non-type / connected-type / non-unselective
cascade, falling back to all indices when the narrowing would empty the pool. The pass now has three eligibility
rules - T1 pre-emption, the unselective-technical ban, and this deferral - documented together in the object
Scaladoc.

Spec cases added to `PrequeryPatternOrderingSpec`: the `reorder` essence with its exact expected order, a
bound-IRI link-target regression guard (the S3 shape, where `rdf:object <iri>` must stay immediately after the
statement binding its subject), a degenerate all-`rdf:object` input, and permutation invariance for the first case.

### Golden audit

Regenerating both golden specs changed exactly two files, both belonging to the `reorder` case:

| Golden | Change | Verdict |
| --- | --- | --- |
| `GravsearchToPrequeryTransformerE2ESpec__reorder.txt` | each of the two `rdf:object` statements moves from before its `?letter ?linkingPropN__hasLinkToValue ?lvN` statement to immediately after it | intended; the file is now byte-identical to the measured vA layout |
| `GravsearchToPrequeryTransformerE2ESpec__reorderShape.txt` | the same two moves in the shape projection | intended, follows the golden above |

Unchanged as required: `linkTargetAnchor` (its `rdf:object <iri>` already follows `hasAuthorValue`, which binds the
subject, so the rule never fires), `reorderWithCycle`, `optional`, `matchFulltextInUnion`, every other golden
containing `rdf:object`, the five prod `<suffix>Shape` files, and the entire count-prequery corpus.

### Stage measurement (2026-09-18)

The regenerated `reorder` golden text is byte-identical to `reorder-vA.rq`, so the interleaved run is a
self-consistency check rather than an A/B: 5 runs of the golden text against 5 runs of the vA reference, medians
0.77 s and 0.76 s wall clock through dsp-cli (that includes roughly 0.1 s of CLI and HTTP overhead, so the numbers
sit on top of the 0.66 s the session measured for vA). Rows: 25, byte-identical to `reorder-vA.sorted.csv`. Against
the round-10 order measured with the same harness (6.98 s) this is a 9x improvement, and the shape is now faster
than the pre-DEV-7287 topological sort (3.65 s) rather than 1.83x slower.

Control runs of the other two anchored goldens, both in their previous sub-second class with rows identical to the
H2 replay: `linkTargetAnchor` 0.48 s, `listNodeAnchor` 0.46 s.

Raw files: `/private/tmp/.../scratchpad/r11-timings.csv` (session-local, not durable) and
`~/Desktop/gravsearch-ordering-measurements/h2-replay/` for the reference layouts.

### Base change

The remote stack was force-pushed at 14:15 by another session: both layers were rebased onto `main` at
`ded89ed9a`. The rebase is content-identical (range-diff clean: 20 PR1 and 28 PR2 commits unchanged), but the SHAs
differ. `base_commit` for both layers is therefore now `ded89ed9a` (was `cd88f04b5`), and the round-11 commits were
moved onto the remote PR2 tip `7ad247bc9` (the rewritten `b3d5c17a2`) before pushing. `origin/main` has since moved
to `44de1cd49`; the stack was deliberately not rebased onto that.

### Review of round 11

`performance-reviewer` and `scala-zio-reviewer` on `b3d5c17a2..HEAD`: no Critical findings, four Warnings, each put
to `finding-verifier`.

| Finding | Verdict | Action |
| --- | --- | --- |
| The new "three eligibility rules" sentence splits "These two classes" from its antecedent in the Scaladoc | real, low | fixed: sentence moved after the unselective-technical explanation |
| Measured numbers in a Scaladoc violate the comment convention | not real | the convention bans benchmark dumps, not a sourced number justifying an invariant; the file already does this (DEV-6715), and specs are a sink so the comment cannot link to the journal instead |
| `isDeferred` ignores a `VALUES`-restricted subject, unlike the rest of the tie-break machinery | not real | `OntologyInferencer` only ever attaches `VALUES` to a type-statement object or a predicate variable, never to a link-value subject, so the shape is unreachable |
| The bound-IRI `rdf:object` shape is only covered by a unit test, not a stage measurement | not real | the `linkTargetAnchor` golden is byte-identical before and after this round, so its query cannot have regressed; it was re-run on stage anyway (0.48 s, rows identical to the H2 replay) |

One suggestion not taken: pinning an exact order in the degenerate all-`rdf:object` case. That case exists to prove
totality and determinism under the fallback; its exact order carries no claim worth freezing.

### Correction to "Base change": the stack shipped mid-round

The "Base change" note above is superseded by what the push revealed. While round 11 was running, both stack layers
were squash-merged into `main` at 12:58 UTC: PR 4348 (DEV-7288) as `9a2efa885` and PR 4349 (DEV-7287) as
`a523c827c`, and both branches were deleted. The force-push reported at 14:15 local was the last rebase before that
merge, not a new base to build on.

Consequence for round 11: its four commits do not belong to PR 4349 any more (that PR is `MERGED` and its head is
`1ad31ba2e`, a pre-squash commit that is not an ancestor of `main`). They were replayed onto `origin/main` at
`a523c827c` as `71b8cb7a8`, `91f234f38`, `cc0411e6f`, `a7241793f` on the local branch
`round11-rdf-object-eligibility`; the replay is content-identical to the pre-merge tip (`git diff` empty) and the
full `//modules/webapi:test`, `just test-gravsearch-prequery` and `just check` gates are green on top of `main`.

Left for the session to decide: round 11 now needs its own follow-up PR off `main` (branch name and PR body are not
the orchestrator's call). Also note that the fast-forward push in this round recreated the deleted remote branch
`feature/dev-7287-gravsearch-prequery-emits-patterns-in-dependency-order-not` with the pre-squash history plus the
round-11 commits; it has no PR and should be deleted once the follow-up branch is pushed.
