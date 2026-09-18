---
title: "perf: connectivity-aware Gravsearch prequery ordering (DEV-7288 + DEV-7287, stacked PRs)"
type: perf
date: 2026-09-17
author: "Balduin Landolt"
status: implemented
repository: /Users/balduinlandolt/Documents/GitHub/dasch-swiss/dsp-api/.claude/worktrees/gravsearch-speedup
repositories: []
linear: DEV-7288
linear_project: DSP-API query performance and search correctness
---

# perf: connectivity-aware Gravsearch prequery ordering (DEV-7288 + DEV-7287)

## Overview

Two stacked PRs on the Gravsearch prequery pipeline (`modules/webapi/src/main/scala/org/knora/webapi/messages/util/search/`):

| Layer | Linear | Branch | Content |
| --- | --- | --- | --- |
| bottom (PR 1) | DEV-7288 | `feature/dev-7288-gravsearch-prequery-deterministic-inference-variable-names` | deterministic inference variable names; golden snapshots of the fully rendered prequery SPARQL for the whole E2E corpus plus the three DEV-7287 shapes |
| top (PR 2) | DEV-7287 | `feature/dev-7287-gravsearch-prequery-emits-patterns-in-dependency-order-not` | one connectivity-aware ordering pass at the end of prequery generation; delete the topological sort (`ReorderPatternsByDependency` shrinks to a statements-first partition), `TopologicalSortUtil`, `moveBindToBeginning`, `moveLuceneToBeginning`, and the `scala-graph` dependency; regenerate goldens and audit the diff |

Phases 1 to 2 are PR 1, phases 3 to 6 are PR 2 (Phase 3 is a measurement spike that produces no code). Every
phase ends with a review checkpoint that reviews the cumulative diff as if it were the finished PR (see
"Review checkpoint protocol"); the spike's checkpoint reviews the measurement write-up instead.

This plan is executed unattended by `eng:work-orchestrating`. All decisions are recorded in
"Decision record" below; the orchestrator must not re-open them. The plan lives in the repo under
`docs/specs/` following the SIPI spec convention (flat, date-prefixed `YYYY-MM-DD-NN-{topic}-{type}.md` files
sharing one stem per piece of work; see the "Specs" section of `CLAUDE.md`) and is committed together with its
journal and spike write-up at ship time (see "Shipping as a stack"); `docs/specs/` is excluded from the mkdocs
site and nothing outside `docs/specs/` may link to it.

Paths used throughout:

- `<worktree>` = `/Users/balduinlandolt/Documents/GitHub/dasch-swiss/dsp-api/.claude/worktrees/gravsearch-speedup`
- `<spec-dir>` = `<worktree>/docs/specs`; `<stem>` = `2026-09-17-01-gravsearch-prequery-ordering`
- plan = `<spec-dir>/<stem>-plan.md` (this file)
- journal = `<spec-dir>/<stem>-journal.md` (the skill derives `<plan-name>-journal.md` from the plan file name;
  the orchestrator uses this path)
- spike write-up = `<spec-dir>/<stem>-design.md` (discovery, per-case results, tier table); raw spike files
  (runner script, layout `.rq` files, `results.csv`) = `<spec-dir>/<stem>-assets/` (committed)
- review diffs = `<worktree>/.claude/tmp/review/review-<n>.diff` (git-ignored via `/.claude/tmp`, never committed)
- `PR1` = `feature/dev-7288-gravsearch-prequery-deterministic-inference-variable-names` (bottom layer, cut
  from `origin/main` by the session's git setup and adopted with `gh stack init` before Phase 1)
- `PR2` = `feature/dev-7287-gravsearch-prequery-emits-patterns-in-dependency-order-not` (top layer, created
  with `gh stack add` at the Phase 2 close; phases 3 to 6 commit here)
- the stack is `(main) <- PR1 <- PR2`, managed by `gh stack` from the first commit on; the worktree is checked
  out on the stack's current layer throughout (this deviates from the "stay on the worktree's own branch" rule
  and is confirmed by Balduin in the session's git-setup question before the unattended part starts)
- `stack_top` = the branch the orchestrator commits to: `PR1` until the Phase 2 close, `PR2` afterwards
  (journalled; on every respawn the orchestrator checks out `stack_top`, which overrides the brief's `branch`)
- `base_commit` = SHA of `origin/main` the run started from (from the Orchestration Brief, journalled in
  Phase 1); `pr1_head` = tip of `PR1`, recorded at the Phase 2 close and re-recorded after any later PR-1 fix;
  `phase<n>_head` = SHA recorded at the end of each phase

## Problem Statement / Motivation

Fuseki (TDB2, no `stats.opt`) evaluates prequery patterns essentially in the order dsp-api writes them
(engine Facts 1, 3, 7 in `docs/development/dsp-api-fuseki-query-execution.md`). The prequery pipeline
produces a wrong order in three ways (DEV-7287): the topological sort puts a bound-IRI object (the most
selective statement) last and `?mainRes a <Class>` first; `handleListNode` reaches the list-node anchor only
through a property path emitted last; `OntologyInferencer` inserts class `VALUES` blocks in place, so the
position (and the resulting 50 ms vs 49 s bimodality of the tanner query) depends on undetermined layer order
in `TopologicalSortUtil`. Measured on stage: list-node shape 4.2 to 5.2 s emitted vs 0.39 s anchor-first;
link-target shape 0.67 s vs 0.11 s; tanner 53 s vs 0.3 to 0.7 s. About 80% of Fuseki time above 1 s in prod is
these shapes.

Changing the order touches every prequery, and plan flips go both ways. DEV-7288 therefore first makes the
rendered SPARQL byte-stable and pins the whole corpus as golden files, so DEV-7287 lands as a reviewable
golden-file diff instead of 30 rewritten AST literals.

## Proposed Solution

**PR 1 (DEV-7288).**

1. Remove the `scala.util.Random` fallback in `OntologyInferencer` and derive the `VALUES` variable name from the
   statement itself (decision D3), so both prequery and main query render identically on every run and PR 2's
   reordering does not renumber variables.
2. Sort `VALUES` entries and prequery SELECT columns when rendering (decision D5) so golden files are readable
   and independent of hash-trie layout.
3. Add a `GOLDEN_REWRITE` environment switch to the testkit `GoldenTest` (decision D5) so regeneration is
   `bazel test ... --test_env=GOLDEN_REWRITE=1` instead of editing spec source.
4. Convert every structural `assertTrue(actual == expectedSelectQuery)` case in
   `GravsearchToPrequeryTransformerE2ESpec` and `GravsearchToCountPrequeryTransformerE2ESpec` into a golden
   snapshot of the fully rendered prequery (through the full two-stage pipeline in
   `GravsearchInferencePipelineTestSupport`), delete the AST literals, and add golden cases for the three
   DEV-7287 shapes expressed in the `anything` test ontology, including a project-limited tanner variant.

**PR 2 (DEV-7287).**

1. Implement `PrequeryPatternOrdering` (new pure object in `gravsearch/transformers/`) per the algorithm
   in "Ordering algorithm" below, with a pure unit spec in `modules/webapi`.
2. Call it once in `QueryTraverser.transformSelectToSelect` on the post-inference patterns, just before the
   `WhereClause` is built. This covers page and count prequery; `AbstractPrequeryGenerator` is untouched.
3. Delete the topological sort in `ReorderPatternsByDependency`, `TopologicalSortUtil` (+ spec),
   `moveBindToBeginning`, `moveLuceneToBeginning` (+ their `SparqlTransformerSpec` tests), the `scala-graph`
   artifact in `MODULE.bazel` / `modules/webapi/BUILD.bazel`, and re-pin `maven_install.json`.
   `optimiseIsDeletedWithFilter` stays (it is a rewrite, not an ordering). **One piece of stage 1 ordering
   stays as well**: the partition "statements first, then the other patterns, recursively per block" that
   `reorderPatternsByDependency` performs on top of the sort (`GravsearchQueryOptimisation.scala:345-366`).
   `AbstractPrequeryGenerator` is stateful and depends on it in two precise cases: (a) a FILTER that the parser
   places before the statements binding its variables, which only happens for a FILTER inside a **nested braced
   group** written before those statements (the parser already puts a group's own FILTERs last), throws
   `GravsearchException("One or more variables used in a filter have not been bound in the same UNION block")`
   inside a UNION branch (`AbstractPrequeryGenerator.scala:2394-2403`, `recordVariablesInUnionBlock` line 644);
   (b) `BIND` and block patterns written before statements change what `generatedDateStatements` (line 249) and
   the per-block generated-variable frames see. The partition is kept as `StatementsFirst` (renamed from
   `ReorderPatternsByDependency`, sort removed) so stage 1 behaves exactly as today except for the order among
   statements, which stage 2 now owns. No stage-1 state depends on the relative order among statements (all
   such state is set- or map-valued, `useInference` is read after traversal, ORDER BY resolves against the
   top-level frame), which is what makes deleting the sort safe.
4. Regenerate the goldens and audit every changed file against the ordering rules; rewrite the design doc.

No runtime fallback flag (ticket decision). Stage replay is a human action after the code is complete.

## Alternative Approaches Considered

- **Tune `ReorderPatternsByDependency` instead of replacing it.** Rejected in the ticket: four passes touch
  order and none sees the final shape; the last one wins. One final pass is the only seam that sees
  inference output and the project-limit statement.
- **List-node shape only: expand the subtree from the list cache and emit `VALUES ?lnv {...}`.** Not
  pursued now; the general pass fixes all three shapes. Can be added later if the path anchor proves slow.
- **`stats.opt` on the triplestore (DEV-6834).** Ops-side, unverified whether Jena's weighted reorder would
  pick the right plan. Independent of this work.
- **Per-transformation counter for variable suffixes (ticket text).** See D3.

## Technical Considerations

### Pipeline facts the implementation relies on (verified 2026-09-16 on `main` at `fe032f1b3`)

- Call chain (`SearchResponderV2.gravsearchV2` lines 832 to 874, `gravsearchCountV2` lines 755 to 792):
  `transformConstructToSelect` (stage 1, `AbstractPrequeryGenerator`, runs `GravsearchQueryOptimisation`
  = remove redundant `knora-api:Resource`, remove entities inferred from property, topological reorder) then
  `transformSelectToSelect` (stage 2, `SelectTransformer`: `moveLuceneToBeginning`, `optimiseIsDeletedWithFilter`,
  `moveBindToBeginning`, then `OntologyInferencer` expansion). `transformSelectToSelect` appends the
  `attachedToProject` statement at the end before traversal (`QueryTraverser.scala:311-315`) and builds
  `WhereClause(patterns)` at line 323. **The new pass goes between those two lines' results: after
  `transformWherePatterns`, before `WhereClause`.**
- `GravsearchInferencePipelineTestSupport.transformQueryWithInference` mirrors that composition for tests
  but hard-codes `limitResultsToProject = None`; it needs an optional project parameter for the tanner variant.
- `SmartIri.hashCode = toString.hashCode` (`StringFormatter.scala:493`), so `Set[IriRef]` iteration and
  the SELECT-column `Set`s are already stable across JVM runs. Sorting (D5) is hardening plus readability,
  not a correctness fix.
- `ValuesPattern` renders from a `Set[IriRef]` (`SparqlQuery.scala:428-430`). `GravsearchToPrequeryTransformer.getSelectColumns`
  returns `Seq(mainResourceVariable) ++ dependentResourceGroupConcat ++ valueObjectGroupConcat` (two `Set`s).
- `ConstructTransformer.transform` has two call sites in `SearchResponderV2` (line 690 for
  `FullTextMainQueryGenerator.createMainQuery`, line 958 for `GravsearchMainQueryGenerator.createMainQuery`);
  neither generator emits a `BindPattern`, a Lucene statement or a `GroupPattern` (grep over
  `messages/util/search/` main sources), so `moveBindToBeginning` / `moveLuceneToBeginning` are no-ops for
  `ConstructTransformer` and can be deleted; `ConstructTransformer.optimizeAndTransformPatterns` keeps only
  `optimiseIsDeletedWithFilter`. The ordering pass is **not** applied to the main queries (out of ticket scope;
  their `VALUES ?mainRes {page IRIs}` anchor is already first).
- Gravsearch `BIND` is restricted by the parser to `BIND(<knora data IRI> AS ?var)`
  (`GravsearchParser.scala:475-492`), so a `BindPattern` always binds a constant and is safe first.
- `GroupPattern` is opaque: never reorder inside it. It provides the variables it mentions (the `matchFulltext`
  expansion binds the main resource variable via its inner `BIND(COALESCE(...))`).
- Only `TopologicalSortUtil.scala`, `GravsearchQueryOptimisation.scala` and `TopologicalSortUtilSpec.scala`
  import `scalax`. Dependency: `MODULE.bazel:196` (`org.scala-graph:graph-core_3:2.0.2`),
  `modules/webapi/BUILD.bazel:115` (`@maven//:org_scala_graph_graph_core_3`). Re-pin:
  `bazel run @unpinned_maven//:pin` (CONVENTIONS.md "Versions single-sourced"). Check `.github/renovate.json`
  for a scala-graph-specific rule and remove it if present.
- `docs/05-internals/design/api-v2/gravsearch.md` lines 307 to 466 document the topological sort (with
  figure `figures/query_graph.png`), lines 480 to 494 discuss `moveBindToBeginning` + reorder as passes the
  opaque group must survive, lines 505 to 515 discuss `moveLuceneToBeginning`. All three sections change in PR 2.
- `ARCH-MAP.md` (component `webapi-search`, around line 924 to 952) lists the golden specs that pin query
  output; the two E2E specs are missing from that list and should be added in PR 1.

### Golden-file mechanics under Bazel (load-bearing for unattended execution)

- `modules/test-it` depends on `//modules/testkit` and `//modules/webapi` (main), so it uses
  **`modules/testkit/src/main/scala/org/knora/webapi/GoldenTest.scala`** (filesystem lookup only), not the
  webapi test copy with the classpath fallback.
- The golden path is derived at compile time from the spec's source position, `/src/test/scala/` replaced by
  `/src/test/resources/`, file `<SpecName>__<suffix>.txt`, resolved at test time relative to the runfiles root
  (`modules/test-it/BUILD.bazel:21-28` explains the dual `data` + `resources` glob).
- Runfiles entries are symlinks to the source files, so a rewrite of an **existing** file lands in the source
  tree. A rewrite of a **missing** file creates a plain file in runfiles and is lost. Therefore: **create an
  empty placeholder file in `src/test/resources/...` for every new golden case before the rewrite run**, and
  verify with `git status` that the source files changed. The symptom of a forgotten placeholder is a clean
  rerun failing with `[GoldenTest] File not found: modules/test-it/src/test/resources/...` while the rewrite run
  reported `Rewritten`. Mandatory fallback whenever `git status` shows fewer changed golden files than expected:

  ```bash
  R=modules/test-it/src/test/resources/org/knora/webapi/messages/util/search/gravsearch/prequery
  cp "bazel-bin/modules/test-it/test.runfiles/_main/$R/<SpecName>__<suffix>.txt" "$R/<SpecName>__<suffix>.txt"
  ```

  then rerun the spec without the env to confirm it passes.
- Two tests asserting on the same suffix share one golden file; use this deliberately for the
  simple-schema / complex-schema pairs whose outputs must be identical (it preserves the equivalence claim
  the current AST literals encode). In a `GOLDEN_REWRITE` run both tests write the file and the last writer
  wins, so a divergence only surfaces in the clean rerun as a failure of one of the pair. **Such a failure is a
  real equivalence break, not a rewrite artifact**: do not split the suffix to make it pass; investigate (in
  PR 1 it means the simple and complex schema no longer render identically; in PR 2 it means the ordering pass
  is not a pure function of the pattern set) and fix the code.
- Test targets: `bazel test //modules/test-it:test --test_filter='.*GravsearchToPrequeryTransformerE2ESpec.*'`
  (Docker testcontainers, `exclusive`, `no-remote`; needs `just docker-load-test-images` once). Pure unit
  specs run with `bazel test //modules/webapi:test --test_filter='.*<SpecName>.*'`. Regeneration:
  same command plus `--test_env=GOLDEN_REWRITE=1`; the run **fails by design** ("Rewritten"), then rerun
  without the env and it must pass. Bazel caches test results per env, so both runs execute.

### Ordering algorithm (PR 2, `PrequeryPatternOrdering.order(patterns, outerBound)`)

*Written under the D4 working hypothesis.* The Phase 3 spike changed the tier table (a bound-literal tier was
added, `attachedToProject` dropped below both type tiers, only project-data-ontology classes count as
project classes, and the built-in-versus-project distinction also became a tie-break key); the measured table
and the restated rules are in the spike write-up (`<stem>-design.md`) and the journal, and the shipped code
follows them. The procedure below is otherwise as implemented.

Pure function `Seq[QueryPattern] => Seq[QueryPattern]`, recursive, deterministic. Terminology:

- `vars(p)`: variables of a pattern. Statement: subject, predicate, object variables. `ValuesPattern`: its
  variable. `BindPattern`: its variable. `FilterPattern`: expression variables. Block patterns
  (`OptionalPattern`, `UnionPattern` over all branches, `MinusPattern`, `FilterNotExistsPattern`,
  `GroupPattern`): union of inner `vars`, recursively.
- **Unit**: a `StatementPattern` or a `GroupPattern`. A `ValuesPattern` is not a unit: it is *attached
  dynamically* to whichever statement using its variable is **emitted first** (step 3e), so the `VALUES`
  always lands immediately before the first statement that references its variable, whatever the tier and
  connectivity choices were. A `VALUES` whose variable no unit in the group uses but some block or filter of
  the group references is *block-attached*: it is emitted immediately before the first such block or filter
  (step 4/5), never hoisted to the front. A `VALUES` nothing in the group references is an *orphan*.
- **Type unit**: a unit whose statement predicate is `rdf:type` and whose subject is a variable.
  *Project-class* if its object IRI, or (when the object is a variable bound by a `VALUES` in the group) every
  IRI in that `VALUES`, lies outside the `http://www.knora.org/ontology/knora-base#` namespace; otherwise
  *technical* (this covers `knora-base:LinkValue` generated for every link property, `knora-base:Resource` and
  its full closure, `*Representation` closures, `ListNode`, and any VALUES still containing a knora-base class
  after `limitInferenceToOntologies` filtering). Only project-class type units may lead a component (step 3c);
  technical type units are always emitted after the statements that bind their subject.
- **Tier** of a unit (lower is better):
    - T1 Lucene: statement with predicate `text:query`, or a `GroupPattern` containing one at any depth.
    - T2 Bound IRI: non-type statement with an `IriRef` subject or object (property-path statements
        included, variable predicate allowed), predicate not `knora-base:attachedToProject`; also an `rdf:type`
        statement whose subject is an `IriRef`.
    - T3 to T5, **fixed by the Phase 3 spike (D4)**. Working hypothesis: project-class type unit (T3) above
        `attachedToProject <iri>` (T4) above technical type unit (T5). The spike may also introduce a tier for
        non-type statements with a bound `XsdLiteral` object (case S5) between T2 and T3.
    - T6 Plain: everything else. Within T6, non-path statements before property-path statements.
- **Key** for tie-breaking within a tier: more bound terms first (IRIs, literals, and variables already in
  the bound set), then lexical order of `pattern.toSparql`. Every choice among units ends in this key, so the
  order of the emitted **units and their VALUES** is a pure function of the input *set* of units
  (permutation-invariant over statements, group patterns and VALUES), which removes the tanner
  nondeterminism. `binds`, `blocks`, `filters` and `notExists` keep their input order (steps 2, 4, 5); the
  permutation-invariance unit test therefore permutes only statements, group patterns and VALUES. The spike's
  case S7 decides whether a "leads to a FILTER" preference is inserted before the lexical step for T6; the
  default is no.
- **T2 exclusions**: TBox paths `rdfs:subClassOf*` / `rdfs:subPropertyOf*` (any statement whose predicate is
  `rdfs:subClassOf` or `rdfs:subPropertyOf`) are never T2, even with a bound object; they rank T6 (path) so
  they follow the statement that binds their subject. Outside the opaque `matchFulltext` group they occur only
  in hand-written queries; no spike case measures them.

The tier order and any extra tier are data (an ordered list in `PrequeryPatternOrdering`), so the spike result
plugs in without touching the procedure.

Procedure for one group with `outerBound: Set[QueryVariable]`:

1. Partition the input into `binds`, `values` (all `ValuesPattern`s as a list, grouped by variable into a
   multimap, each tracked as emitted or not by identity so that two `VALUES` on one variable are both kept;
   classified unit-attached, block-attached or orphan per the definitions above), `units` (statements, group
   patterns), `blocks` (`OptionalPattern`, `UnionPattern`, `MinusPattern`), `filters`, `notExists`. The output
   has exactly as many patterns as the input (unit-tested).
2. `emitted = binds (input order)`; `bound = outerBound ++ vars(emitted)`. Orphan `VALUES` (referenced by
   nothing) are appended **after all units** in step 3 completes, in input order: a `VALUES` nothing joins
   with multiplies whatever precedes it (Fact 1), so it belongs last. The current pipeline never produces one
   (inference attaches every `VALUES` to the statement it rewrote); this is cheap insurance.
3. While units remain, emit the next unit, trying these rules in order and taking the first that applies:
   a. **Connected non-type**: the min by (tier, key) among non-type units sharing a variable with `bound`.
   b. **Connected type** (closes the current component): the min by (tier, key) among type units sharing a
      variable with `bound`.
   c. **New component**: the min by (tier, key) over the remaining non-type units **and project-class type
      units**. A technical type unit can never lead. This single comparison encodes the "type VALUES must lead
      when there is no anchor" fallback (a T6-only component loses to a project-class type unit at T3) and the
      spike's answer to "project vs class" (whichever tier is lower leads).
   d. **Leftover technical type units** whose subject nothing binds (degenerate input): the min by (tier, key).
   e. Emit: for every variable of the chosen unit that has a not-yet-emitted `VALUES`, emit that `VALUES`
      first (in variable order, then input order), then the unit; `bound ++= vars(unit)`.
   Invariant: a type unit is either the leader of its component (3c, project-class only) or follows every
   non-type unit of its component that is connected at the time (3a before 3b). The `?lv a knora-base:LinkValue`
   statement generated for every link property is technical and therefore never leads an anchorless query.
4. Append `blocks` in input order (a block-attached `VALUES` immediately before the first block referencing
   its variable), each recursed with the following seed:
   - `OptionalPattern` → `bound`
   - each `UnionPattern` branch → `bound`
   - `FilterNotExistsPattern` (step 5) → `bound`
   - `MinusPattern` → **empty set**
   The seeds are an
   execution-plan heuristic that mirrors how Fuseki evaluates these operators (Fact 4: the MINUS right side is
   materialised without the outer bindings), not a SPARQL-semantics claim; the Scaladoc and the design doc must
   say so. `GroupPattern` contents are never touched. Placing all statements before all blocks is **parity
   with today** (`ReorderPatternsByDependency` already returns `sortedStatementPatterns ++ sortedOtherPatterns`),
   not a SPARQL identity: `(A MINUS B) JOIN C` differs from `(A JOIN C) MINUS B` when `C` binds a variable that
   also occurs in `B`, and likewise for OPTIONAL. The pass accepts parity with today (the only statement it
   newly hoists past blocks is `attachedToProject`, which binds no new variable); the Scaladoc states this and a
   unit test pins the parity behaviour so a future change is deliberate.
5. Append `filters` in input order (a block-attached `VALUES` referenced only by a filter goes immediately
   before that filter), then `notExists` (recursed as in 4) in input order.

Expected results the goldens must show (used in Phase 5's audit). The audit is mechanical through the
**shape summaries** (D14): `GravsearchInferencePipelineTestSupport.shapeSummary(query: SelectQuery): String`
renders one line per **top-level** pattern of the WHERE clause: `STMT <subj-kind> <pred> <obj-kind>` (kind is
`var`, `iri` or `lit`; `pred` is the predicate IRI's local name, `?var` for a variable predicate, with `*`
appended for a property path), `VALUES (n)` (no variable name, so the D3 hash never enters the summary),
`BIND`, `FILTER`, `FNE`, `OPTIONAL`, `UNION`, `MINUS`, `GROUP`. Nested contents are not listed. The five shape
cases assert `assertGolden(shapeSummary(actual), "<suffix>Shape")` next to the SPARQL golden. The audit compares
each `<suffix>Shape` file with the second column below line by line; a mismatch fails the audit. The general audit
rules (type unit after connected non-type statements, VALUES
adjacent to its statement, FNE last, GroupPattern interior unchanged) are checked by the orchestrator reading
each changed SPARQL golden, not by grep.

The sequences below are derived by hand from the rules and the exact statement sets stage 1 emits (link
properties expand to `?s <linkValueProp> ?lv`, `?lv a knora-base:LinkValue`, `?lv isDeleted false` (rewritten
to an FNE), `?lv rdf:object <target>`; list nodes to `?v valueHasListNode ?lnv`, `<node> hasSubListNode* ?lnv`;
every resource and value variable gets an FNE). Lexical ties use the full rendered statement, so
`?letter <http://www.knora.org/...>` sorts before `?letter <http://www.w3.org/...>` and `?letter` followed by a space before
`?letter__...`. If a regenerated shape file differs, the orchestrator re-traces the algorithm by hand for that
input in the journal before deciding whether the pass or this table is wrong; the table is corrected only when
the trace shows the table misapplied a rule.

| Shape (beol ontology, complex schema) and golden suffix | Expected top-level order (`<suffix>Shape` lines) |
| --- | --- |
| list node, `listNodeAnchor` (page and count spec): `?letter a beol:letter ; beol:hasSubject ?subj . ?subj knora-api:listValueAsListNode <http://rdfh.ch/lists/0801/logarithmic_curves>` | `STMT iri hasSubListNode* var` (T2 leads), `STMT var valueHasListNode var`, `STMT var hasSubject var`, `STMT var type iri` (project-class, connected, 3b), `FNE`, `FNE` |
| link target, `linkTargetAnchor`: `?letter a beol:letter ; beol:hasAuthor <http://rdfh.ch/0801/anchor-person>` | `STMT var hasAuthor iri` (T2; wins the lexical tie against `?letter__...` `rdf:object`), `STMT var hasAuthorValue var`, `STMT var object iri` (T2, now connected), `STMT var type iri` (letter, project-class T3, 3b), `STMT var type iri` (LinkValue, technical T5, 3b; the tier decides, not the lexical key), `FNE`, `FNE`, `FNE` (three: `?letter`, the link value, and the typed object IRI `<anchor-person>` each get an `isDeleted` guard). **Spike-sensitive**: if the Phase 3 tier table ranks T5 above T3 the two type statements swap. |
| tanner without project, `classValuesLabelFilterOrderBy` (full text in D9) | `VALUES (4)`, `STMT var type var` (project-class leads, 3c), `STMT var title var` (`?src <http://www.knora...` sorts before `?src <http://www.w3...`), `STMT var label var` (`?src` sorts before `?title`), `STMT var valueHasString var`, `FILTER`, `FNE`, `FNE`. **Spike-sensitive**: the leader depends on T3 ranking above T6 (S8) and on no literal tier (S5). |
| tanner with project, `classValuesLabelFilterOrderByProjectLimited` (page spec only) | per the spike's D4 result; under the working hypothesis: `VALUES (4)`, `STMT var type var`, `STMT var attachedToProject iri` (T4 beats T6), then `STMT var title var`, `STMT var label var`, `STMT var valueHasString var`, `FILTER`, `FNE`, `FNE` |
| classless `matchFulltext`, `classlessMatchFulltext` (existing golden, no shape file) | `GROUP` first (T1), then technical `VALUES` + `STMT var type var` (T5, connected, 3b), `FNE`; unchanged from today |
| `reorderWithCycle` (`?thing hasOtherThing ?thing1 . ?thing1 hasOtherThing ?thing2 . ?thing2 hasOtherThing ?thing`, no shape file) | three link expansions, no bound IRI, no project-class type unit (the three `LinkValue` type statements are technical): 3c picks the lexically smallest T6 statement, then greedy connected; `LinkValue` type statements follow via 3b; must terminate (no graph, no cycle handling needed) |

Derivation method for the tanner row, as an example for the audit: after the type unit binds `?src`, the
connected non-type candidates `?src beol:title ?title` and `?src rdfs:label ?label` tie on bound terms (subject
variable bound, predicate IRI) and `title` wins lexically (`?src <http://www.knora.org/...` before
`?src <http://www.w3.org/...`); after `?title` is bound, `label` (rendered `?src ...`) sorts before
`valueHasString` (rendered `?title ...`) because `s` < `t`.

### DEV-7287 shape queries (PR 1, D9; used verbatim as test inputs)

The CONSTRUCT clauses mirror the prod queries (the matched value is returned), so the prequery carries one
`GROUP_CONCAT` column per returned value variable; for `linkTargetAnchor` the CONSTRUCT object is an IRI, so
its column comes from the generated link-value variable (`letter__...__LinkValue__Concat`,
`GravsearchToPrequeryTransformer.scala:165-197`), not from a query variable. Prefix block for all four:

```sparql
PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
```

`listNodeAnchor` (page spec; the count spec uses the same text with `dropOrderBy = true`):

```sparql
CONSTRUCT {
  ?letter knora-api:isMainResource true .
  ?letter beol:hasSubject ?subj .
} WHERE {
  ?letter a beol:letter .
  ?letter beol:hasSubject ?subj .
  ?subj knora-api:listValueAsListNode <http://rdfh.ch/lists/0801/logarithmic_curves> .
}
```

`linkTargetAnchor`:

```sparql
CONSTRUCT {
  ?letter knora-api:isMainResource true .
  ?letter beol:hasAuthor <http://rdfh.ch/0801/anchor-person> .
} WHERE {
  ?letter a beol:letter .
  ?letter beol:hasAuthor <http://rdfh.ch/0801/anchor-person> .
}
```

`classValuesLabelFilterOrderBy` (and `classValuesLabelFilterOrderByProjectLimited`, same text, with
`limitResultsToProject = Some(ProjectIri.unsafeFrom("http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF"))`):

```sparql
CONSTRUCT {
  ?src knora-api:isMainResource true .
  ?src beol:title ?title .
} WHERE {
  ?src a beol:writtenSource .
  ?src rdfs:label ?label .
  ?src beol:title ?title .
  ?title knora-api:valueAsString ?titleStr .
  FILTER(?titleStr = "Basel"^^xsd:string)
} ORDER BY ASC(?label)
```

(The literal is ASCII on purpose so the repo's punctuation rule never tempts a reviewer to touch four goldens.)

### Determinism design (PR 1, D3)

`OntologyInferencer.transformStatementInWhere` drops the `queryVariableSuffix` parameter. Variable names come
from a new `SparqlTransformer.createInferenceVariable(statement: StatementPattern, kind: String): QueryVariable`:

- `base` = the subject's variable name, or for an IRI subject its local name (text after the last `#` or `/`),
  sanitised to `[A-Za-z0-9_]` (every other character dropped);
- `hash` = `f"${statement.toSparql.hashCode}%08x"` (always 8 hex digits, negative values render in two's
  complement without a sign; `String.hashCode` is specified by the JVM, so it is identical across runs and
  machines);
- name = `s"${base}__${kind}__$hash"`, e.g. `?thing__resTypes__3fa2b91c`, `?letter__subProp__0c11e9d4`.

`escapeEntityForVariable` is **not** reused for this: it is lossy (`.../ab#cd` and `.../abc#d` escape alike, which
would merge two `VALUES` blocks and intersect their class sets to empty results) and passes characters that are
illegal in a SPARQL `VARNAME` (an `XsdLiteral` object such as `"(DE-588)118531379"` reaches `inferSubproperties`).
The hash makes the name injective for practical purposes; identical statements share a variable (harmless,
identical constraint). The result is always a valid SPARQL `VARNAME` (`(PN_CHARS_U | [0-9]) ...`, so a leading
digit or an empty `base` are both legal). `SelectTransformer.statementCounter` and its comment go away; `ConstructTransformer`
needs no threading. `OntologyInferencerE2ESpec` expectations (`resTypes5432`, `subProp5432`) change to the
derived names, and `SparqlTransformerSpec` gets tests for a literal-object statement (valid `VARNAME`) and two
statements whose IRIs collide under the old escape (different variables).

### Conventions to follow

- `CLAUDE.md`, `CONVENTIONS.md`, `docs/development/dsp-api-conventions.md` § Testing (ZIO Test suite
  structure, `GoldenTest` preferred for query builders), `REVIEW.md` § SPARQL (emitted-SPARQL changes must show
  up as golden diffs). Methods in `src/main` at most 50 lines, immutable style, no fully qualified names in
  code (import at top), ASCII-only source, no "Knora" in commit messages or docs prose.
- Commits: Conventional Commits, `<type>: <description> (DEV-NNNN)`, one per landed chunk. PR 1 commits use
  `(DEV-7288)`, PR 2 commits `(DEV-7287)`. Types: `refactor:`/`test:` for PR 1, `perf:` for the ordering pass,
  `refactor:` for deletions, `docs:` for docs, `build:` for the dependency removal.
- Markdown: run `just check` after editing any `.md`.
- Never call `unsafeFrom` in new production code; the test support may construct `ProjectIri` via `unsafeFrom`.

## Decision record

Decisions the orchestrator must treat as settled. D1 to D4 were put to Balduin on 2026-09-17; D5 to D12 are
planner calls he can veto by editing this section before the run.

| Id | Decision | Choice | Why |
| --- | --- | --- | --- |
| D1 | Stack topology under `eng:work-orchestrating` | **Confirmed 2026-09-17, revised the same day after Balduin's review of the git section: one orchestrator run on a `gh stack` that exists from the start.** `PR1` is cut from `origin/main` and `gh stack init`ed before Phase 1; `gh stack add PR2` runs at the Phase 2 close; phases 3 to 6 commit on `PR2`; `gh stack submit --auto` ships both drafts. No retroactive split of one branch. | matches the gh-stack skill ("create the stack before writing files, do not implement everything on one branch and split later"); a PR-1 fix discovered later uses the skill's lower-layer edit loop instead of cherry-pick surgery. See "Shipping as a stack". |
| D2 | Per-phase review mechanism | **Confirmed 2026-09-17: orchestrator-run review fan-out at every phase end (tier 2 spawns read-only reviewer agents, verifies findings, dispatches fixes as chunks)** | unattended and multi-perspective; the session's own adversarial review still runs once at the end. See "Review checkpoint protocol". |
| D3 | Deterministic suffix scheme | **Confirmed 2026-09-17: content-derived names via a new `SparqlTransformer.createInferenceVariable` (subject base plus statement hash, see "Determinism design")**; the existing `createUniqueVariableFromStatement` (used for `__LinkValue` and `__listNodeVar` variables) is not touched (deviates from the ticket's counter) | a counter is assigned in pre-ordering traversal order, so PR 2's reorder would renumber every `VALUES` variable and bury the real diff. |
| D4 | Rank of `attachedToProject <iri>`, technical (knora-base) type units, bound literals, T2 tie-break, T6 tie-break | **Decided 2026-09-17: measured in Phase 3 (spike) on stage through dsp-cli (`dsp vre sparql query -s stage`); the implementation phase takes the tier table from the spike's journal entry.** Working hypothesis until then: project-class type (T3) above project (T4) above technical type (T5); bound literals are not anchors; T2 ties and T6 ties broken lexically. **Only if the spike cannot run at all (stage unreachable or session invalid) does the orchestrator return `run_status: blocked` naming H1; in that one case Phase 4 does not start and PR 1 is not shipped separately. Ties within a spike that ran are resolved by D13.** | Balduin asked for a spike instead of choosing from one data point (tanner: VALUES+type first 0.3 to 0.7 s vs project first 1.9 s). Stage is a prod copy on the prod Fuseki version; Balduin's standing rule is that every SPARQL measurement runs on stage via dsp-cli, never on a local dump. See "Spike protocol". |
| D13 | Spike decision rule | a layout wins a case when its median over 5 stage runs is at least 20% faster than the runner-up and its minimum is not slower than the runner-up's median; otherwise the case is a **tie and resolves to the D4 working-hypothesis rank for that case** (S1: T3 project-class type above T4 project; S2: T4 project above T5 technical; S5: no literal tier; S7: no FILTER preference; S8: technical type never leads; S3/S6: lexical key, anchors above project). A tie is a normal outcome, never a blocker. | keeps the spike from over-fitting noise; stage shares the host with other traffic, so single runs vary. |
| D15 | Orchestrator round cap | the session may reset the skill's 5-round cap **without asking** as long as the last round made progress (new commits or ticked checkboxes); expected shape is about one orchestrator round per phase plus review-fix rounds | six phases with E2E regenerations and review fan-outs will exceed five rounds; asking at the cap would break the unattended premise Balduin asked for. |
| D5 | PR 1 hardening extras | include all three: `GOLDEN_REWRITE` env switch in testkit `GoldenTest`; sort `ValuesPattern` entries by rendered IRI; sort prequery SELECT columns (main resource first, then GROUP_CONCAT columns by output variable name) | one-liners; make goldens readable and regeneration a flag, not a source edit. Not applied to the other two `GoldenTest` copies (side finding, out of scope). |
| D6 | Main query ordering | untouched; `ConstructTransformer` keeps only `optimiseIsDeletedWithFilter` | no BIND/Lucene/GroupPattern can occur in the main query; ticket scope is the prequery. |
| D7 | Which specs become golden | all 26 structural cases in the page spec (lines 2694 to 2813 of the suite, including the two tests that both assert `TransformedQueryWithOptional`) and both in the count spec; AST literals deleted; the five "reorder" tests keep their names with "(golden)" wording updated | ticket: replace, do not duplicate. |
| D8 | Golden pipeline | every golden case runs the full two-stage pipeline (`transformQueryWithInference`), i.e. pins the text sent to Fuseki | ticket acceptance. |
| D9 | Where the three DEV-7287 shapes live | as new cases in `GravsearchToPrequeryTransformerE2ESpec` using the **`beol`** test ontology (`http://0.0.0.0:3333/ontology/0801/beol/v2#`, loaded by `DefaultRdfData`), whose properties `hasSubject` (ListValue), `hasAuthor` (link to `beol:person`) and `title` (TextValue) have **no `subjectClassConstraint`**, so `RemoveEntitiesInferredFromProperty` keeps the class statement exactly as for the ekws/tanner properties in prod. Shapes (complete query texts under "DEV-7287 shape queries" below): list node (`letter` has no subclasses: single type statement like `ekws:Object`), link target to `<http://rdfh.ch/0801/anchor-person>` (any syntactically valid Knora data IRI; no data needed), tanner on `writtenSource` (subclasses `manuscript`, `basicLetter`, `letter` → a 4-class VALUES), plus the tanner variant limited to the beol project `http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF` (shortcode `0801`, `test_data/project_data/admin-data.ttl:332`); the count spec gets the list-node shape too | the `anything` properties all carry `subjectClassConstraint anything:Thing`, so stage 1 would delete `?thing a anything:Thing` and the shapes would not reproduce the prod problem (round-2 correctness finding); no fixtures added to shared test data; prequery generation needs no matching data. |
| D10 | Unit tests for the ordering pass | pure `ZIOSpecDefault` in `modules/webapi/src/test/.../transformers/PrequeryPatternOrderingSpec.scala`, AST built by hand, including a permutation-invariance test | runs without Docker on RBE; fast feedback for workers. |
| D11 | Stage replay / prod verification | human action H2 after PR 2 is complete, not a plan deliverable | needs SystemAdmin on stage and Grafana access. |
| D12 | Plan location | `docs/specs/` in the repo under the SIPI spec convention (Balduin, 2026-09-17: "move the plan to the repo", "how does sipi handle this?"); journal (`-journal.md`), spike write-up (`-design.md`) and raw spike files (`-assets/`) share the plan's stem and are committed at ship time; review diffs stay under `.claude/tmp` | keeps the measured basis and the execution journal with the code; `docs/specs/` is excluded from the mkdocs site, passes `just check` (markdownlint), and is a reference sink (no inbound links). |
| D14 | Golden shape summaries | the five DEV-7287 shape cases additionally golden a one-line-per-top-level-pattern summary (`<suffix>Shape`) rendered by a small helper in `GravsearchInferencePipelineTestSupport`; the Phase 5 audit compares those summaries with the expected-results table | rendered SPARQL is flat (nested patterns start at column 0) and variable predicates exist, so a text grep cannot tell top-level order; the summary makes the audit mechanical and diffable. |

## Implementation Phases

Phase checklists own their scope. Items marked **[orchestrator]** are executed by the orchestrator itself
(git, test runs, review fan-out), never delegated to a worker. Test commands assume the repo root of the
worktree and the Nix dev shell (`direnv` loads it).

### Phase 1: Deterministic rendering (PR 1, part 1)

**Gate: H1** — resolve before starting this phase.

- [x] Stack setup **[session, before the first orchestrator round; recorded here so the orchestrator can
      verify it]**: in the skill's git-setup question (1.4) choose "Create new feature branch" named `PR1`, cut
      from `origin/main` in this worktree (`git fetch origin && git checkout -b <PR1> origin/main`), then
      `git config rerere.enabled true && git config remote.pushDefault origin && gh stack init <PR1>` (adopts
      the existing branch, trunk `main`). Put `base_commit = $(git rev-parse origin/main)` and `branch = PR1`
      into the Orchestration Brief.
- [x] Branch verification **[orchestrator]**: journal the brief's `base_commit` and `stack_top = PR1` (never
      re-derive `base_commit`). On later rounds the journal's `stack_top` wins over the brief's `branch`: run
      `git checkout <stack_top>` if `git branch --show-current` differs. Then verify: `git status --porcelain`
      is empty; `gh stack view --json` shows `trunk == "main"`, `currentBranch == stack_top` and no branch with
      `needsRebase: true`; on the first round `git rev-parse HEAD` equals `base_commit`. On any mismatch return
      `run_status: blocked` instead of resetting anything.
- [x] Preflight **[orchestrator]**: `docker info` succeeds; run `just docker-load-test-images`; run
      `bazel test //modules/test-it:test --test_filter='.*GravsearchToPrequeryTransformerE2ESpec.*'` and
      `--test_filter='.*GravsearchToCountPrequeryTransformerE2ESpec.*'` on the untouched branch and record the
      pass result and wall-clock time in the journal (baseline).
- [x] Add a `GOLDEN_REWRITE` switch to `modules/testkit/src/main/scala/org/knora/webapi/GoldenTest.scala`:
      rewrite when `rewrite || rewriteAll || sys.env.get("GOLDEN_REWRITE").exists(_.nonEmpty)`; update the trait
      comment to describe `--test_env=GOLDEN_REWRITE=1` and the placeholder-file rule for new goldens; drop the
      stale sbt `~` remark. *Superseded in execution (round 2):* the repo adopted the shared code-comment
      convention (#4337) mid-run, so the regeneration recipe and the placeholder rule moved to
      `docs/development/dsp-api-conventions.md` and the trait keeps a one-line pointer.
- [x] Add `SparqlTransformer.createInferenceVariable(statement, kind)` as specified under "Determinism
      design", with unit tests in `SparqlTransformerSpec` (literal-object statement yields a valid `VARNAME`;
      `.../0001/ab#cd` vs `.../0001/abc#d` yield different variables; same statement twice yields the same one).
- [x] In `OntologyInferencer`, remove the `queryVariableSuffix` parameter from `transformStatementInWhere`,
      `inferSubclasses` and `inferSubproperties`; name the variables with
      `SparqlTransformer.createInferenceVariable(statementPattern, "resTypes" | "subProp")`; remove
      the `scala.util.Random` usage entirely.
- [x] In `SelectTransformer`, remove `statementCounter`, its comment, and the suffix argument; the method
      becomes a direct delegation to the inferencer.
- [x] In `SparqlQuery.scala`, render `ValuesPattern` entries sorted by `toSparql`
      (`values.toSeq.map(_.toSparql).sorted.mkString(" ")`).
- [x] In `GravsearchToPrequeryTransformer.getSelectColumns`, return the main resource variable first followed
      by the GROUP_CONCAT columns sorted by `outputVariable.variableName`.
- [x] Update `OntologyInferencerE2ESpec` (`modules/test-it/.../transformers/`): remove the `queryVariableSuffix`
      arguments and expect the derived variable names.
- [x] Add a determinism test to `ConstructTransformerSpec` (`modules/webapi`): the suite-level layer is
      `OntologyCacheFake.emptyCache` (no subclasses, so no `VALUES` would be produced), so give this test its
      own layer via `.provide(ConstructTransformer.layer, IriConverter.layer, StringFormatter.test,
      OntologyInferencer.layer, OntologyCacheFake.withCache(data))` with
      `data = OntologyCacheFake.emptyData.copy(classToSubclassLookup = Map(thingIri -> Set(thingIri, blueThingIri,
      thingWithSeqnumIri)))` (`OntologyCacheData` fields: `ontologies, classToSuperClassLookup,
      classToSubclassLookup, subPropertyOfRelations, superPropertyOfRelations, classDefinedInOntology,
      propertyDefinedInOntology, entityDefinedInOntology, standoffProperties`;
      `limitInferenceToOntologies = None` so `classDefinedInOntology` may stay empty); transform a
      `ConstructQuery` whose WHERE has `?thing a <thingIri>` twice and assert the two `toSparql` strings are
      equal and contain `VALUES` (proves the main-query path is deterministic without a suffix).
- [x] Rewrite `docs/05-internals/design/api-v2/gravsearch.md` § "Determinism for Snapshot Testing"
      (lines 517 to 526; it describes the `Random.nextInt` fallback and the `SelectTransformer` counter) to the
      content-derived scheme: subject base plus a hash of the rendered statement via `createInferenceVariable`,
      why identical statements sharing a variable is harmless, why the lossy `escapeEntityForVariable` was not
      reused (keep the section's existing warning about a constant suffix intersecting `VALUES` blocks). Run
      `just check`.
- [x] Regenerate the four existing golden files **[orchestrator]** (they change because of VALUES sorting and
      the new variable names; *in execution only three changed*: `matchFulltextInUnion` emits no inference
      `VALUES`, so it is unaffected by both) (original wording follows:
      the new variable names): run both E2E specs with `--test_env=GOLDEN_REWRITE=1`, confirm via `git status`
      that exactly the four files under `modules/test-it/src/test/resources/.../prequery/` changed, then rerun
      without the env and confirm green. Record in the journal that the write-through to the source tree works
      (this validates the placeholder rule for Phase 2).
- [x] Run `bazel test //modules/webapi:test --test_filter='.*ConstructTransformerSpec.*'`,
      `--test_filter='.*SparqlTransformerSpec.*'`, `bazel test //modules/test-it:test --test_filter='.*OntologyInferencerE2ESpec.*'`,
      and `bazel build //modules/webapi:webapi` green; run `just fmt`.
- [x] Review checkpoint 1 **[orchestrator]** per the protocol, scope: PR 1 as if finished.
- [x] Phase close **[orchestrator]**: `git status --porcelain` is empty (every chunk and review fix is
      committed with `(DEV-7288)`); record `phase1_head = $(git rev-parse HEAD)` in the journal.

### Phase 2: Golden corpus (PR 1, part 2)

- [x] Extend `GravsearchInferencePipelineTestSupport.transformQueryWithInference` with
      `limitResultsToProject: Option[ProjectIri] = None`, passed through to `transformSelectToSelect`, and add
      the same optional parameter to the two private one-argument wrappers named `transformQueryWithInference`
      in `GravsearchToPrequeryTransformerE2ESpec` (lines 60 to 74) and `GravsearchToCountPrequeryTransformerE2ESpec`
      (lines 57 to 72; keeps `dropOrderBy = true`).
- [x] Add `GravsearchInferencePipelineTestSupport.shapeSummary(query: SelectQuery): String` per D14 (one line
      per top-level WHERE pattern, format defined under "Expected results"; property-path predicates get `*`,
      variable predicates render as `?var`, IRIs as their local name after the last `#` or `/`).
- [x] Add to `ARCH-MAP.md` (component `webapi-search`, golden-spec list) the two E2E specs as golden-pinned
      prequery output; run `just check`.
- [x] In `GravsearchToPrequeryTransformerE2ESpec`, convert the 26 structural tests (suite lines 2694 to 2813)
      to `transformQueryWithInference(input).map(actual => assertGolden(actual.toSparql, "<suffix>"))`, using
      one shared suffix per group of tests whose expected AST is currently the same value:
      `dateNonOptionalSortCriterion` (2), `dateNonOptionalSortCriterionAndFilter` (2), `dateOptionalSortCriterion` (2),
      `dateOptionalSortCriterionAndFilter` (2), `decimalOptionalSortCriterion` (2), `rdfsLabelAndLiteral` (2),
      `rdfsLabelAndVariable` (2), `rdfsLabelAndRegex` (2), `optional` (2: the tests at lines 2694 and 2802 both
      assert `TransformedQueryWithOptional`); and distinct suffixes otherwise: `decimalOptionalSortCriterionAndFilter`,
      `decimalOptionalSortCriterionAndFilterComplex`, `unionScopes`, `standoffTagHasStartAncestor`, `reorder`,
      `reorderWithUnion`, `reorderWithMinus`, `reorderWithCycle` (8). Total 17 golden files for 26 tests. The
      `knora-api:Resource` containment test (line 2828) keeps its structural assertion. The pairs were equal at
      stage 1; they are expected to stay equal after inference (same ontology, so the same VALUES), but if the
      clean rerun fails on a pair, compare the two renderings: a difference confined to `VALUES` membership means
      `getOntologiesRelevantForInference` differs between the simple and complex input, in which case use
      `<suffix>Simple` / `<suffix>Complex` and note it in the journal; any structural difference is a bug to fix.
- [x] Delete every `val transformedQuery...: SelectQuery` / `val TransformedQuery...` AST literal and the now
      unused imports from `GravsearchToPrequeryTransformerE2ESpec`; the page spec **keeps** its stage-1-only
      `transformQuery` helper because the `knora-api:Resource` containment test (line 2828) still uses it; only
      the count spec's `transformQuery` helper is deleted (its two callers become golden tests).
- [x] Add golden cases to `GravsearchToPrequeryTransformerE2ESpec` for the DEV-7287 shapes (complex schema,
      `beol` ontology, exact Gravsearch texts in D9): `listNodeAnchor`, `linkTargetAnchor`,
      `classValuesLabelFilterOrderBy`, `classValuesLabelFilterOrderByProjectLimited` (limited to the beol
      project `http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF`); each test asserts both
      `assertGolden(actual.toSparql, "<suffix>")` and `assertGolden(shapeSummary(actual), "<suffix>Shape")`
      (combine with `&&`). Before regenerating, confirm by reading the stage-1 removal rule
      (`GravsearchQueryOptimisation.scala:107-176`) that the class statement survives: the three beol properties
      have no `subjectClassConstraint`, so the subject is not in `entitiesInferredFromProperties`.
- [x] Add two regression golden cases to `GravsearchToPrequeryTransformerE2ESpec` that pin the stage-1
      "statements first" behaviour PR 2 must preserve. **The parser normalises FILTER position within one
      group** (`GravsearchParser.scala:706-733` visits the FILTER's argument first and appends the
      `FilterPattern` after the group's statements), so a FILTER simply written before its statements does
      **not** exercise the partition; only a FILTER inside a nested braced group written before the binding
      statements does (nested plain groups are flattened, `GravsearchParser.scala:817-819`). Use these texts:
      `filterBeforeStatementsInUnion` (complex schema, `anything` ontology; WHERE = `?thing a anything:Thing .`
      followed by
      `{ { ?thing anything:hasText ?text . FILTER(?intVal > 1) } ?thing anything:hasInteger ?int .`
      `?int knora-api:intValueAsInt ?intVal . } UNION { ?thing anything:hasRichtext ?richtext . }`;
      parsed branch order is `hasText, FILTER, hasInteger, intValueAsInt`, and without the partition the
      FILTER hits the UNION bound-variable check with `?intVal` unbound and throws) and
      `dateFilterInUnionAndTopLevel` (simple schema, prefix `<http://api.knora.org/ontology/knora-api/simple/v2#>`,
      `anything` ontology: `?thing a anything:Thing . ?thing anything:hasDate ?date .` at top level with
      `FILTER(?date < "GREGORIAN:2000"^^knora-api:Date)` and `ORDER BY ?date`, plus a UNION whose first branch is
      `?thing anything:hasDate ?date . FILTER(?date < "GREGORIAN:1900"^^knora-api:Date)` and whose second branch
      is `?thing anything:hasInteger ?int`; the **same operator** at both levels makes the branch, traversed
      first, consume `?date__valueHasEndJDN` in `generatedDateStatements`, so the golden pins the traversal-order
      artefact, a pre-existing latent behaviour that PR 2 must not change; note this in the journal). Both must
      generate a prequery today; check this first, before
      converting anything else: create their two empty placeholder files, run the page spec with
      `--test_env=GOLDEN_REWRITE=1`, and inspect: a placeholder that is now non-empty means the query generates;
      a placeholder still empty together with a `GravsearchException` for that test in the Bazel test log means
      the query throws today, in which case adjust the query minimally (keep its purpose: a FILTER written
      before its binding statements inside a UNION branch; the same date variable filtered in a UNION branch
      and at top level) and record the change in the journal. SPARQL goldens only (no shape file).
- [x] In `GravsearchToCountPrequeryTransformerE2ESpec`, convert the two structural tests to golden
      (`decimalOptionalSortCriterionAndFilter`, `decimalOptionalSortCriterionAndFilterComplex`), delete the AST
      literals and the count spec's `transformQuery` helper, and add a `listNodeAnchor` golden case with its
      `listNodeAnchorShape` (same query text as the page spec; its shape summary must be byte-identical to
      the page spec's).
- [x] Create empty placeholder files for every new golden suffix under
      `modules/test-it/src/test/resources/org/knora/webapi/messages/util/search/gravsearch/prequery/`
      (`<SpecName>__<suffix>.txt`): 17 converted + 4 shape SPARQL + 4 `Shape` summaries + 2 regression = 27 new
      files for the page spec, 2 + 1 + 1 = 4 new files for the count spec (the four matchFulltext files already
      exist).
- [x] Regenerate all goldens **[orchestrator]**: both E2E specs with `--test_env=GOLDEN_REWRITE=1`; confirm via
      `git status` that every placeholder is now non-empty in the source tree (fallback: copy from runfiles);
      rerun without the env and confirm green.
- [x] Sanity-audit the new golden files **[orchestrator]**, recording in the journal: each file parses as a
      `SELECT`, contains `FILTER NOT EXISTS` deletion guards, the five DEV-7287 shape files (four in the page
      spec, one in the count spec) **contain the class type statement** (`beol:letter` or the `writtenSource`
      VALUES) and show today's (bad) order (type first, anchor last / VALUES mid-block; *in execution* the tanner
      shape is pinned VALUES-first because Phase 2 also fixed the nondeterministic layer order in
      `TopologicalSortUtil`, see the journal) so PR 2's diff will be
      visible, and the `writtenSource` VALUES list is alphabetically sorted. A shape file without the type
      statement means the removal rule fired; stop and record a blocker (the shape does not reproduce prod).
- [x] Run `just check` and `just fmt`; run `bazel test //modules/webapi:test` (full pure-JVM webapi suite) green.
- [x] Byte-stability check **[orchestrator]**: run both E2E specs once more with `--cache_test_results=no`
      (forces re-execution) and confirm green.
- [x] Review checkpoint 2 **[orchestrator]** per the protocol, scope: PR 1 as if finished.
- [x] Phase close **[orchestrator]**: `git status --porcelain` is empty (all commits `(DEV-7288)`); record
      `phase2_head` **and** `pr1_head = $(git rev-parse HEAD)` in the journal. This is the PR-1 boundary.
- [x] Add the top layer **[orchestrator]**: on `PR1` (the stack's top so far; `gh stack top` if unsure) run
      `gh stack add <PR2>`; it creates `PR2` from `PR1` and checks it out. Verify `gh stack view --json` lists
      `PR1` then `PR2` with `currentBranch == PR2`; journal `stack_top = PR2`. Every commit from here on lands
      on `PR2`.

### Phase 3: Spike, measure the anchor tiers (PR 2, part 1, no production code)

**Gate: H1** — the stage session must be valid (see H1); if
`dsp vre sparql query -s stage --timeout 30 --accept csv --query 'SELECT (1 AS ?x) WHERE {}'` fails, the
orchestrator returns `run_status: blocked` with the blocker "H1: stage session invalid or stage unreachable"
and does not start Phase 4 (D4); the session resolves it with Balduin and respawns. **All queries run on stage
through dsp-cli; no local Fuseki or dump is used for any measurement or discovery.** This phase produces no
commits; its artifacts live in `<spec-dir>/<stem>-assets/` and `<spec-dir>/<stem>-design.md` and are committed
with the plan at ship time.

- [x] Create `<spec-dir>/<stem>-assets/` with a runner script `stage.sh` that wraps
      `dsp vre sparql query -s stage --timeout 120 --accept csv --query-file <f>`, prints wall-clock seconds
      and row count, and appends one CSV line `case,layout,run,seconds,rows` to `<spec-dir>/<stem>-assets/results.csv`
      (timeouts recorded as `120`).
- [x] Run the discovery queries from "Spike protocol" on stage via `stage.sh` and record the chosen IRIs and
      counts (project IRIs for 0102 and 0812, a `hasConcept` target of median in-degree, the list node
      `<http://rdfh.ch/lists/0812/JS06AyiJR-qRce2laMtF1w>`, the full `knora-base:Resource` subclass list, a
      `hasShelfNumber` literal with exactly one hit) as the "Discovery" section of `<spec-dir>/<stem>-design.md`
      (frontmatter per the Specs convention: `title`, `date`, `author`, `status: draft`).
- [x] Write the layout files for cases S1 to S8 (`<spec-dir>/<stem>-assets/S<n>-<layout>.rq`) exactly as
      "Spike protocol" specifies, using the discovered IRIs; every file is a complete prequery
      (`SELECT DISTINCT ?mainRes ... GROUP BY ?mainRes ORDER BY ASC(?mainRes) LIMIT 25`) in the shape the
      current pipeline emits, differing between layouts only in pattern order.
- [x] Run every layout on stage: one warm-up round over all layouts of the case (discarded), then 5 timed
      rounds, each round running the case's layouts round-robin in letter order (A, B, C, D, then again), so load
      drift on stage affects all layouts alike; exemptions: S4-D runs exactly once with `--timeout 120` and is
      recorded as `120` if it times out; any other layout that times out twice is recorded as `120` and skipped
      in later rounds; verify that all layouts of one case return the same sorted result set (byte-compare the
      sorted CSV bodies) and record any mismatch as a defect in the layout file.
- [x] Write the "Results" section of `<spec-dir>/<stem>-design.md`: per case a table (layout, median, min, max,
      rows), the winner per
      D13, and the resulting **tier table** (T1 to T6 plus any literal tier) and **tie-break rules**; copy the
      tier table and the per-case one-line conclusions into the journal under `## Side findings` as
      "Spike results (Phase 3)". This journal entry is the input for Phase 4.
- [x] Restate every row of the "Expected results" table under the **measured** tier table (re-run the
      algorithm by hand for each shape with the final tiers and any literal tier) and append the restated
      rows to the same journal entry as "Expected shape sequences (measured tiers)". The Phase 5 audit compares
      the `<suffix>Shape` files against this journal restatement, not against the plan's table (which is
      written under the working hypothesis).
- [x] Review checkpoint 3 **[orchestrator]** per the protocol, scope: the spike write-up only (spawn the
      performance reviewer with `<stem>-design.md` and the Fuseki facts doc; the other reviewers are
      skipped since there is no code diff).
- [x] Phase close **[orchestrator]**: `git status --porcelain` shows only untracked files under `docs/specs/`
      (plan, journal, design, assets; they are committed at ship time, not per phase); record
      `phase3_head = $(git rev-parse HEAD)` (equal to `pr1_head`) in the journal.

### Phase 4: Ordering pass, pure implementation (PR 2, part 2)

- [x] Create `modules/webapi/src/main/scala/org/knora/webapi/messages/util/search/gravsearch/transformers/PrequeryPatternOrdering.scala`
      implementing "Ordering algorithm" as `object PrequeryPatternOrdering { def order(patterns: Seq[QueryPattern],
      outerBound: Set[QueryVariable] = Set.empty): Seq[QueryPattern] }`
      with small private helpers (`vars`, `tier`, `key`, `attachValues`, `orderGroup`), each under 50 lines,
      immutable, tail-recursive or fold-based; the tier order is an ordered list taken from the Phase 3
      journal entry; Scaladoc states the engine facts it relies on (Facts 1, 3, 4, 7), the tier table, and cites
      the spike write-up in the design doc.
- [x] Create `modules/webapi/src/test/scala/org/knora/webapi/messages/util/search/gravsearch/transformers/PrequeryPatternOrderingSpec.scala`
      (`ZIOSpecDefault`, hand-built AST) covering: list-node shape (path anchor first, type last); link-target
      shape (T2 first, connected chain, `LinkValue` and class type last); tanner without project (project-class
      type unit leads); tanner with `attachedToProject` and classless `attachedToProject` with the Resource
      closure VALUES (expected orders **derived from the Phase 3 tier table in the journal entry "Spike results",
      not copied from the working hypothesis**); an anchorless link
      query (`?a hasAuthor ?p . ?p hasFamilyName ?n`, expanded form) where the technical `LinkValue` type
      statement must not lead; output size equals input size for every case; a statement binding a variable
      that also occurs in a MINUS body is still hoisted before the MINUS (parity with today, see step 4);
      Lucene `GroupPattern` first
      and never reordered inside; `BindPattern` first; orphan `VALUES` after all units; attached `VALUES`
      immediately before its statement; FILTER after statements and blocks, `FILTER NOT EXISTS` last; MINUS
      recursed with an empty seed, OPTIONAL/UNION/FNE recursed with the outer bound set; two disconnected
      components stay contiguous and are ordered by anchor tier; the cycle case terminates; a
      permutation-invariance test (every permutation of an input of 5 statements plus one VALUES yields the
      same output; binds, blocks and filters are excluded from the permuted set by design); a
      type-with-IriRef-subject statement ranks T2; `rdfs:subClassOf*` with a bound object ranks T6 (path);
      a `VALUES` referenced only inside an OPTIONAL is emitted immediately before that OPTIONAL, not first.
- [x] Run `bazel test //modules/webapi:test --test_filter='.*PrequeryPatternOrderingSpec.*'` green;
      `just fmt`.
- [x] Review checkpoint 4 **[orchestrator]** per the protocol, scope: PR 2 as if finished (the pass exists but is
      not wired yet; reviewers must flag that as expected, not as a defect).
- [x] Phase close **[orchestrator]**: `git status --porcelain` is empty (all commits `(DEV-7287)`); record
      `phase4_head` in the journal.

### Phase 5: Wire the pass, delete the old passes, regenerate goldens (PR 2, part 3)

- [x] In `QueryTraverser.transformSelectToSelect`, apply `PrequeryPatternOrdering.order(patterns)` to the
      result of `transformWherePatterns` before building the `WhereClause`; add a comment naming this as the
      single ordering seam.
- [x] In `GravsearchQueryOptimisation`, replace `ReorderPatternsByDependency` by a private object
      `StatementsFirst` whose single method `statementsFirst(patterns)` keeps exactly the partition logic of
      today's `reorderPatternsByDependency` (lines 345 to 366: statements in input order, then the other
      patterns in input order, recursing into `UnionPattern` branches, `OptionalPattern`, `MinusPattern` and
      `FilterNotExistsPattern`) **without** `createAndSortGraph`; delete `createAndSortGraph`, the
      `StringHyperGraph` type alias and the `scalax` imports; `optimiseQueryPatterns` becomes the two removal
      passes followed by `statementsFirst`; its Scaladoc states the two exact cases that need the partition
      (a FILTER inside a nested braced group written before its binding statements; BIND/blocks written before
      statements) and names the regression goldens `filterBeforeStatementsInUnion` and
      `dateFilterInUnionAndTopLevel`, so nobody later tests the obvious `{ FILTER ... stmts }` shape, sees it
      pass, and drops the partition.
- [x] Delete `TopologicalSortUtil.scala` and `TopologicalSortUtilSpec.scala`.
- [x] In `SparqlTransformer`, delete `moveBindToBeginning` and `moveLuceneToBeginning` and their Scaladoc;
      **move** the private `containsLuceneQuery` (`SparqlTransformer.scala:155-159`, exactly the T1 predicate)
      into `PrequeryPatternOrdering` instead of reimplementing it; in `SparqlTransformerSpec` delete the three
      tests covering the deleted methods; update the Scaladoc
      of `GravsearchInferencePipelineTestSupport` (lines 26 to 33, which names `moveLuceneToBeginning`) to say
      that `PrequeryPatternOrdering` places the Lucene `GroupPattern` first (tier T1); add
      `PrequeryPatternOrdering` to the list of passes in the `GroupPattern` Scaladoc
      (`SparqlQuery.scala:484-492`) that treat the group as an opaque leaf.
- [x] `SelectTransformer.optimiseQueryPatterns` becomes
      `override def optimiseQueryPatterns(patterns: Seq[QueryPattern]): Task[Seq[QueryPattern]] = ZIO.attempt(optimiseIsDeletedWithFilter(patterns))`
      (`optimiseIsDeletedWithFilter` is `Seq[QueryPattern] => Seq[QueryPattern]`, imported via
      `SparqlTransformer.*`); `ConstructTransformer.optimizeAndTransformPatterns` likewise wraps only
      `SparqlTransformer.optimiseIsDeletedWithFilter(patterns)` in its existing `ZIO.attempt`.
- [x] Remove `"org.scala-graph:graph-core_3:2.0.2"` from `MODULE.bazel` and
      `"@maven//:org_scala_graph_graph_core_3"` from `modules/webapi/BUILD.bazel`; in `.github/renovate.json`
      remove the whole `packageRules` entry (lines 53 to 59) `{ "description": "Ignore graph-core (breaking changes,
      from .scala-steward.conf)", "matchPackageNames": ["org.scala-graph:graph-core_3"], "enabled": false }`
      together with the comma that precedes it, keeping the JSON valid (`jq . .github/renovate.json` succeeds).
- [x] Re-pin the lock **[orchestrator]**: `bazel run @unpinned_maven//:pin`; confirm `maven_install.json` no longer
      mentions `scala-graph`; `bazel build //modules/webapi:webapi` green.
- [x] Regenerate all goldens **[orchestrator]** (`--test_env=GOLDEN_REWRITE=1` on both E2E specs, then a clean
      rerun green).
- [x] Audit the golden diff **[orchestrator]**: for every changed golden file write one journal line
      "[file]: [what moved] — [rule that justifies it]"; verify the five `<suffix>Shape` files match the
      journal's "Expected shape sequences (measured tiers)" line by line (the count spec's
      `listNodeAnchorShape` must be byte-identical to the page spec's: both transformers emit the same WHERE
      clause and only the SELECT / ORDER BY differ; on a mismatch, hand-trace the algorithm for that input in
      the journal first); read every changed SPARQL golden and verify every type statement is either the first
      pattern of its component or follows all non-type statements of its component that were connected at that
      point, no technical type statement (`knora-base` class) is the first pattern of a component, every
      `VALUES` directly precedes the first pattern using its variable, no `FILTER NOT EXISTS` precedes a statement
      or block, and no `GroupPattern` interior changed (compare against PR 1's version with
      `git show <pr1_head>:<golden path>`, e.g. `git diff <pr1_head> -- modules/test-it/src/test/resources/.../GravsearchToPrequeryTransformerE2ESpec__classlessMatchFulltext.txt`,
      and confirm the diff touches only lines outside the `{ ... }` group); any violation is a defect in the pass
      to fix in this phase, not a golden to accept.
- [x] Run `bazel test //modules/webapi:test` (full pure-JVM webapi suite) green; `just fmt`.
- [x] Review checkpoint 5 **[orchestrator]** per the protocol, scope: PR 2 as if finished.
- [x] Phase close **[orchestrator]**: `git status --porcelain` is empty (all commits `(DEV-7287)`); record
      `phase5_head` in the journal.

### Phase 6: Docs and final verification (PR 2, part 4)

- [x] Rewrite `docs/05-internals/design/api-v2/gravsearch.md` § "Query Optimization by Topological Sorting of
      Statements" (lines 307 to 466) into a section describing the connectivity-aware ordering pass (tiers,
      greedy connectivity, type-last, no-anchor fallback, block recursion, determinism), referencing
      `docs/development/dsp-api-fuseki-query-execution.md` Facts 1, 3, 4, 7 and DEV-7287; the "Cyclic Graphs"
      subsection (lines 447 to 466) is replaced by one sentence stating that the greedy pass consumes one unit
      per step and therefore terminates on any input, cycles included; delete the `figures/query_graph.png`
      reference (and the file if nothing else references it).
- [x] Add to that new section a "Measured basis" subsection: the spike's per-case result table and the tier
      table copied (not linked: specs are a reference sink) from `<spec-dir>/<stem>-design.md` (stage, prod copy,
      date of the run, Fuseki version from
      `dsp vre sparql query -s stage` server info if available), so the tier order is traceable to data.
- [x] Rewrite § "Determinism for Snapshot Testing" (around line 543): it still says `TopologicalSortUtil`
      "now sorts each layer's nodes by their string form (`sortBy(_.outer.toString)`)", describing a class this
      stack deletes. Determinism now comes from `PrequeryPatternOrdering`'s permutation-invariant rendered-SPARQL
      tie-break key, so the paragraph shrinks to one sentence saying that. **Added in round 6 from review
      checkpoint 5 (consistency reviewer): this paragraph was added by PR 1 itself and no other Phase 6 item
      covered it.**
- [x] In § "Optimisation of generated SPARQL" (around line 304), remove the sentence "For example,
      `moveLuceneToBeginning` moves Lucene queries to the beginning of the block in which they occur" and say
      that `optimiseIsDeletedWithFilter` is now the only pass left in `optimiseQueryPatterns`. **Added in round 6
      from review checkpoint 5; it sits above the deferred topological-sort section and no Phase 6 item named it.**
- [x] Update the same doc's § "Why the Expansion Needs an Opaque Group" and § "Why the Expansion Must Be
      Hoisted" to name `PrequeryPatternOrdering` (T1 Lucene tier) instead of `moveBindToBeginning` /
      `reorderPatternsByDependency` / `moveLuceneToBeginning`.
- [x] Add a short pointer paragraph to `docs/development/dsp-api-sparql-queries.md` § "Pattern Order and Query
      Performance" stating that Gravsearch prequeries get their order from `PrequeryPatternOrdering` and that
      shape changes show up as golden diffs in the two E2E specs.
- [x] Update `ARCH-MAP.md` component `webapi-search`: mention `PrequeryPatternOrdering` as the single ordering
      seam, remove any mention of `TopologicalSortUtil` / scala-graph if present.
- [x] Run `just check` (markdownlint, format, licence headers) green.
- [x] Run the complete pure-JVM suite `just test-unit`, both prequery E2E specs and
      `bazel test //modules/test-it:test --test_filter='.*OntologyInferencerE2ESpec.*'` green **[orchestrator]**;
      record wall-clock in the journal.
- [x] Review checkpoint 6 **[orchestrator]** per the protocol, scope: the whole stack (PR 1 and PR 2) as if
      finished.
- [x] Phase close **[orchestrator]**: `git status --porcelain` is empty (all commits `(DEV-7287)`); record
      `phase6_head` in the journal; list `git log --oneline <pr1_head>..HEAD` (the PR-2 commits) and
      `git log --oneline <base_commit>..<pr1_head>` (the PR-1 commits) in the journal; confirm
      `gh stack view --json` shows no `needsRebase: true` (a PR-1 fix routed per the review protocol's layer
      rule has already been rebased upstack).

## Spike protocol (Phase 3)

Environment: **stage only**, via `dsp vre sparql query -s stage --timeout 120 --accept csv --query-file <f>`
(dsp-cli 0.2.1, session in `~/.config/dsp-cli/auth.toml`, valid until 2026-10-01 at planning time; an expired
session is a plan-staleness signal, H1 re-checks it right before Phase 3; the passthrough hits the
stage triplestore directly, so internal ontology IRIs `http://www.knora.org/ontology/...` are used as-is).
Stage is a copy of prod on the prod Fuseki version. Responses slower than about 45 s may hang at the stage
ingress until the client timeout (tanner handoff § 5); treat a `--timeout 120` expiry as "120 s or worse".
Reference numbers from earlier stage runs: list-node emitted layout 4.2 to 5.2 s vs anchor-first 0.39 s;
hasConcept emitted 0.67 s vs anchor-first 0.11 s; tanner VALUES-first 0.3 to 0.7 s vs label-first 53 s.

Discovery queries (stage):

```sparql
# project IRIs
SELECT ?p (COUNT(*) AS ?n) WHERE { ?s a <http://www.knora.org/ontology/0812/ekws#Object> ;
  <http://www.knora.org/ontology/knora-base#attachedToProject> ?p } GROUP BY ?p
SELECT ?p (COUNT(*) AS ?n) WHERE { ?s a <http://www.knora.org/ontology/0102/scenario-tanner#Page> ;
  <http://www.knora.org/ontology/knora-base#attachedToProject> ?p } GROUP BY ?p
# hasConcept target with median in-degree (take the row at OFFSET = half the distinct-target count)
SELECT ?o (COUNT(*) AS ?n) WHERE { ?s <http://www.knora.org/ontology/0812/ekws#hasConcept> ?o } GROUP BY ?o ORDER BY DESC(?n)
# Document subclasses (tanner VALUES) and the full Resource closure (classless VALUES)
SELECT DISTINCT ?c WHERE { ?c <http://www.w3.org/2000/01/rdf-schema#subClassOf>*
  <http://www.knora.org/ontology/0102/scenario-tanner#Document> }
SELECT DISTINCT ?c WHERE { ?c <http://www.w3.org/2000/01/rdf-schema#subClassOf>*
  <http://www.knora.org/ontology/knora-base#Resource> }
# one hasShelfNumber literal with exactly one hit
SELECT ?s (COUNT(*) AS ?n) WHERE { ?v <http://www.knora.org/ontology/knora-base#valueHasString> ?s .
  ?d <http://www.knora.org/ontology/0102/scenario-tanner#hasShelfNumber> ?v }
  GROUP BY ?s HAVING (COUNT(*) = 1) LIMIT 1
# S8 predicates exist and match (fallback: pick the ekws link property with the largest count from the second query)
SELECT (COUNT(*) AS ?n) WHERE { ?s <http://www.knora.org/ontology/0812/ekws#hasCreator> ?p .
  ?p a <http://www.knora.org/ontology/0812/ekws#Person> }
SELECT ?p (COUNT(*) AS ?n) WHERE { ?s ?p ?o . ?s a <http://www.knora.org/ontology/0812/ekws#Object> . ?o a ?c .
  FILTER(STRSTARTS(STR(?p), "http://www.knora.org/ontology/0812/ekws#")) }
  GROUP BY ?p ORDER BY DESC(?n) LIMIT 5
```

If the S8 count is zero, substitute the top ekws link property (and its `Value` counterpart and object class)
from the second query and record the substitution in the design doc's "Discovery" section.

Cases. Each layout is the full prequery the pipeline emits today for that Gravsearch (see the PR-1 goldens for
the exact statement set: type statement or `VALUES + ?mainRes a ?resTypes`, value statements,
`FILTER NOT EXISTS { ?x knora-base:isDeleted true }` per resource/value variable, `GROUP BY ?mainRes ORDER BY
ASC(?mainRes) LIMIT 25`), differing only in order. Names: `S<n>-<letter>.rq`.

| Case | Question it answers | Layouts (pattern order; FILTER and FNEs always last) |
| --- | --- | --- |
| S1 tanner + project 0102 (`?mainRes a Document` VALUES of 7 classes, `rdfs:label ?l`, `hasShelfNumber ?v`, `?v valueHasString ?s`, `FILTER(?s = "<literal>")`, `attachedToProject <0102>`) | project-class type vs project as leading anchor (D4); only the project position varies | A: VALUES+type, project, label, hasShelfNumber, valueHasString. B: project, VALUES+type, label, hasShelfNumber, valueHasString. C: project, label, hasShelfNumber, valueHasString, VALUES+type. D: VALUES+type, label, hasShelfNumber, valueHasString, project |
| S2 classless + project 0102 (`VALUES` = full Resource closure, `rdfs:label ?l`, `FILTER regex(?l, "^Brief", "i")`, `attachedToProject <0102>`) | technical (Resource closure) VALUES vs project | A: project, label, VALUES+type. B: VALUES+type, project, label. C: project, VALUES+type, label |
| S3 link target 0812 (`?mainRes a ekws:Object`, `?mainRes hasConcept <iri>`, `?mainRes hasConceptValue ?lv`, `?lv a knora-base:LinkValue`, `?lv rdf:object <iri>`) | which T2 leads; type-last confirmation | A: hasConcept, hasConceptValue, rdf:object, type LinkValue, type Object. B: rdf:object, hasConceptValue, hasConcept, type LinkValue, type Object. C (today): type Object, hasConcept, hasConceptValue, type LinkValue, rdf:object |
| S4 list node 0812 (`?mainRes a ekws:Object`, `?mainRes hasMedium ?li`, `?li valueHasListNode ?lnv`, `<node> hasSubListNode* ?lnv`) | re-confirm the DEV-7287 stage matrix with the same harness (sanity check of the method) | A (today): type, hasMedium, vln, path. B: path, vln, hasMedium, type. C: path, type, hasMedium, vln. D: path, vln, type, hasMedium (expected timeout; run once only) |
| S5 bound literal as anchor (S1 without project, literal inlined as `?v valueHasString "<literal>"`) | does a bound `XsdLiteral` object deserve a tier above types | A: literal statement, hasShelfNumber, VALUES+type, label. B: VALUES+type, hasShelfNumber, literal statement, label. C: VALUES+type, label, hasShelfNumber, valueHasString + FILTER (today's form) |
| S6 T2 anchor + project (S3 layout A plus `attachedToProject <0812>`) | anchors beat project | A: hasConcept, ..., project last before types. B: project, hasConcept, ... |
| S7 T6 tie-break (S1 statement set, project fixed in second position) | is a "leads to a FILTER" preference worth adding; only the T6 order varies | A: VALUES+type, project, label, hasShelfNumber, valueHasString (same text as S1-A, but re-run inside S7's own round-robin so both layouts see the same load). B: VALUES+type, project, hasShelfNumber, valueHasString, label. Default "no preference" unless B wins per D13 |
| S8 anchorless link query 0812 (`?mainRes a ekws:Object ; ekws:hasCreator ?p . ?p a ekws:Person`, expanded: `hasCreator`, `hasCreatorValue`, `?lv a LinkValue`, `?lv rdf:object ?p`, two type statements) | confirms that the technical `LinkValue` type statement must not lead and how a project-class type unit compares with plain statements when nothing is bound | A: type Object, hasCreator, hasCreatorValue, rdf:object, type Person, type LinkValue. B: hasCreator, hasCreatorValue, rdf:object, type Object, type Person, type LinkValue. C: type LinkValue, rdf:object, hasCreatorValue, hasCreator, type Object, type Person (expected worst; run once only) |

Result rule: D13. The write-up must state the tier table explicitly, e.g.
`T1 Lucene > T2 bound IRI > T3 project-class type > T4 attachedToProject > T5 technical type > T6 plain`, and
whether a literal tier or a FILTER preference is added. Cases S1 to S8 are the complete list; the spike adds
no others.

## Review checkpoint protocol

Executed by the orchestrator (tier 2) at the end of every phase; it may spawn read-only agents at depth 3.

1. Capture the cumulative diff of the PR under review (`base_commit` and `pr1_head` are the journal values):
   for checkpoints 1 and 2 `git diff <base_commit>..HEAD`; for 4 to 6 `git diff <pr1_head>..HEAD` plus, for
   checkpoint 6, `git diff <base_commit>..HEAD` as a second input. Save as
   `<worktree>/.claude/tmp/review/review-<n>.diff` (create the directory on first use; `/.claude/tmp` is
   git-ignored). Checkpoint 3 (spike) has no diff; its input is `<spec-dir>/<stem>-design.md`.
2. Spawn in parallel, each with the diff path, the plan path, and the instruction to report findings as
   `file:line — severity (Critical | Warning | Nit) — claim — why`: `eng:review:scala-zio-reviewer`,
   `eng:review:performance-reviewer` (brief it with the "Ordering algorithm" section, the spike results and
   Facts 1, 3, 4, 7), `eng:review:consistency-reviewer`, `eng:review:code-simplicity-reviewer`,
   `eng:review:dune-reviewer`, and for checkpoints 2, 5, 6 also `eng:review:pattern-recognition-specialist`
   (golden-file and test-structure consistency). Checkpoint 3 spawns only the performance reviewer. *In
   execution*, checkpoint 6 used `dev:review:docs-reviewer` in place of `eng:review:code-simplicity-reviewer`
   because Phase 6 is documentation only and the simplicity reviewer had returned nothing on the same code one
   checkpoint earlier.
3. For every Critical or Warning finding spawn one `dev:coordinator:finding-verifier` (read-only) to refute it;
   keep only findings it confirms.
4. Fix every confirmed Critical and Warning in this phase as ordinary worker chunks (one chunk per finding or
   per file), re-run the affected tests, commit; loop back to step 2 at most twice; then record any survivors
   under `## Deferrals` in the journal with the reviewer's claim verbatim.
   **Layer rule (checkpoints 4 to 6):** a fix that belongs to PR 1 (it touches only files changed in
   `<base_commit>..<pr1_head>` and is not caused by the ordering pass) is committed on `PR1`, not on `PR2`:
   `gh stack down` (now on `PR1`), dispatch the worker, run the PR-1 tests, commit with `(DEV-7288)`, then
   `gh stack rebase --upstack --no-trunk` to replay `PR2` on the new `PR1` tip, `gh stack top`, re-record
   `pr1_head = $(git rev-parse <PR1>)` in the journal, and re-run the PR-2 tests on `PR2`. If the rebase exits 3
   (conflict), run `gh stack rebase --abort`, leave the fix on `PR2` instead, and record why in the journal.
   Anything else is committed on `PR2`.
5. Nits: record in the journal; fix only if a chunk in the same file is being dispatched anyway.
6. A checkpoint is complete when steps 1 to 5 are done and the journal has a "Review checkpoint n" entry
   listing agents run, findings by severity, and the fix commits. Tick the checkbox only then.

The session's own end-of-run adversarial review (skill Phase 5) still happens once after Phase 6.

## Shipping as a stack (session, tier 1; replaces the single `gh pr create` in skill Phase 6)

The stack `(main) <- PR1 <- PR2` has existed since the Phase 1 stack setup and the Phase 2 `gh stack add`; at
this point `PR2` is checked out and every phase is committed. Shipping is one linear sequence; shell
invocations do not share variables, so the snippet defines what it uses.

```bash
cd /Users/balduinlandolt/Documents/GitHub/dasch-swiss/dsp-api/.claude/worktrees/gravsearch-speedup
PR2=feature/dev-7287-gravsearch-prequery-emits-patterns-in-dependency-order-not
STEM=docs/specs/2026-09-17-01-gravsearch-prequery-ordering
git checkout "$PR2"                                   # gh stack top, spelled out
if git status --porcelain | grep -qv '^?? docs/specs/'; then echo "BLOCKER: dirty tree"; exit 1; fi
# skill Phase 6 step 3, plan-repo state (lands in PR 2): set the plan's frontmatter status to implemented first
git add "$STEM-plan.md" "$STEM-journal.md" "$STEM-design.md" "$STEM-assets"
git commit -m "docs: add Gravsearch prequery ordering plan, journal and spike write-up (DEV-7287)"
gh stack view --json      # expect trunk "main", branches [PR1, PR2], currentBranch PR2, no needsRebase
gh stack submit --auto    # pushes both layers (--force-with-lease), opens two DRAFT PRs, links them as a stack
gh stack view --json      # expect a pr.number on both branches
```

`gh stack submit` exits 9 if stacked PRs are not enabled on the repository; that is a blocker for Balduin
(the fallback would be two ordinary draft PRs with `gh pr create --base main` and `--base <PR1>`, but only on
his say-so). Review diffs under `.claude/tmp/review/` are never added. The worktree stays on `PR2`.

Then `gh pr edit` each PR: title
`refactor: deterministic Gravsearch inference variable names and golden prequery snapshots (DEV-7288)`
and `perf: order Gravsearch prequery patterns by connectivity from bound anchors (DEV-7287)`; body per
`eng:working` 4.3 with the Linear link on top, the `## Human Actions` table copied verbatim, and for PR 2 the
golden-diff audit table from the journal; `gh pr edit --add-assignee balduinlandolt`. Verify CI per
`eng:working` 4.3b on both PRs. Move DEV-7288 and DEV-7287 to In Review in Linear.

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H1 | Docker Desktop is running, the dsp-cli stage session is valid (`dsp vre sparql query -s stage --timeout 30 --accept csv --query 'SELECT (1 AS ?x) WHERE {}'` succeeds; re-login with `dsp auth login` against `https://api.stage.dasch.swiss` if not), and `gh auth status` succeeds on the machine that runs the orchestrator | Balduin | before Phase 1 (stage is first needed in Phase 3) | starting Docker Desktop, logging in to stage and GitHub are outside the agent's reach; every E2E golden run, the spike and the final `gh stack submit` depend on them |
| H2 | Stage replay and prod verification: replay each emitted prequery from the DEV-7287 golden diff (and the three prod shapes with prod ontology IRIs) via `dsp vre sparql query -s stage --timeout 120 --accept csv`, byte-compare sorted results against the pre-change layout, then after deploy check `{ span:name = "gravsearch" && span:duration > 2s } \| count_over_time() by (span.gravsearch.schema_predicates)` drops the `listValueAsListNode` and single-link-property rows to about zero and loop the tanner query 20 times, all under 1 s | Balduin | after PR 2 is complete, before merging PR 2 | needs a SystemAdmin login on stage and Grafana access; the ticket names this replay as the insurance in place of a runtime flag |

## Acceptance Criteria

PR 1 (DEV-7288):

- [x] Rendering the same `ConstructQuery` twice yields identical SPARQL text for the main query
      (`ConstructTransformerSpec`), and the prequery path has no random or counter-based names.
- [x] `OntologyInferencer` contains no reference to `scala.util.Random`; `SelectTransformer` has no counter.
- [x] In every golden file, each `VALUES` block lists its IRIs in ascending lexical order, and each page-spec
      `SELECT` line names the main resource variable first followed by the `GROUP_CONCAT` columns in ascending
      order of their `AS ?name` output variable (D5).
- [x] Every case in both E2E specs that pinned a `SelectQuery` AST now pins the fully rendered prequery via
      `assertGolden`; no `val transformedQuery...: SelectQuery` literal remains in either spec.
- [x] Golden files exist for all converted cases plus `listNodeAnchor`, `linkTargetAnchor`,
      `classValuesLabelFilterOrderBy`, `classValuesLabelFilterOrderByProjectLimited` (page spec) and
      `listNodeAnchor` (count spec), each shape case with its `<suffix>Shape` companion.
- [x] `docs/05-internals/design/api-v2/gravsearch.md` § "Determinism for Snapshot Testing" describes the
      content-derived scheme and no longer mentions `Random` or a counter.
- [x] `bazel test //modules/test-it:test --test_filter='.*Gravsearch.*PrequeryTransformerE2ESpec.*' --cache_test_results=no`
      passes after the regular green run (byte stability across two real executions).
- [x] `GOLDEN_REWRITE=1` regenerates goldens without editing spec source.

PR 2 (DEV-7287):

- [x] The spike write-up (`<spec-dir>/<stem>-design.md`) exists with all eight cases measured on stage via
      dsp-cli (5 interleaved runs per layout, exempted layouts once), identical result sets across layouts,
      and an explicit tier table; the same table is in the design doc's "Measured basis".
- [x] The tier order in `PrequeryPatternOrdering` equals the spike's tier table.
- [x] `PrequeryPatternOrdering` is the only code path that orders statements relative to each other;
      stage 1 keeps only the statements-first partition (`StatementsFirst`); `TopologicalSortUtil`,
      `ReorderPatternsByDependency`, `createAndSortGraph`, `moveBindToBeginning`, `moveLuceneToBeginning` and
      the scala-graph artifact are gone from source, build files and lock file.
- [x] The two stage-1 regression goldens (`filterBeforeStatementsInUnion`, `dateFilterInUnionAndTopLevel`)
      still pass after PR 2 (their statement order may change, but they must still generate a prequery).
- [x] The five `<suffix>Shape` golden files match the journal's "Expected shape sequences (measured tiers)"
      (count and page `listNodeAnchorShape` byte-identical); the audit line for every changed golden is in the
      journal and none violates the audit rules.
- [x] `PrequeryPatternOrderingSpec` passes, including permutation invariance.
- [x] Full `bazel test //modules/webapi:test`, both E2E specs, and `just check` are green.
- [x] `docs/05-internals/design/api-v2/gravsearch.md` no longer describes topological sorting or scala-graph.
- [ ] Two draft PRs exist as a `gh stack` (`main <- PR1 <- PR2`), assigned to Balduin, CI green on both.

## Dependencies & Risks

- **Docker availability** is the hard runtime dependency for every golden regeneration (H1).
- **Golden write-through under Bazel** is validated in Phase 1 before Phase 2 depends on it; the fallback (copy
  from runfiles) is documented above.
- **Plan flips in the wild**: the pass changes the order of every prequery. Mitigation: goldens make every change
  visible; the audit rules reject regressions of the known kinds; H2 replays on stage before merge.
- **Heuristic gaps**: the tier order comes from an eight-case spike on stage (a prod copy); shapes outside
  those cases fall back to the connectivity rule. Cheap to revisit because the tiers are data in one object and
  every change shows up as a golden diff.
- **Spike environment**: stage shares its host with other traffic and its ingress may hang on responses over
  about 45 s; D13's 20% margin, interleaved runs and the 120 s timeout guard against reading noise as a ranking.
  If the stage session is invalid the spike blocks (gate H1) rather than falling back to a local dump.
- **Bazel re-pin** requires network access to Maven Central; if `bazel run @unpinned_maven//:pin` fails, the
  orchestrator records a blocker rather than hand-editing `maven_install.json`.
- **E2E runtime**: each E2E spec run boots Fuseki via testcontainers (minutes). Phases regenerate goldens at most
  twice each; do not run the whole `//modules/test-it:test` target, only the filtered specs.

## Risk Analysis & Mitigation

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| New golden written to runfiles instead of the source tree | M | M | placeholder files + `git status` check + runfiles copy fallback; validated in Phase 1 on existing files |
| Pass emits a worse plan for a corpus shape not covered by the three prod shapes | M | H | golden audit rules in Phase 5; unit spec on disconnected components; H2 stage replay of the full diff before merge |
| `MINUS` block reordered against outer bindings that Jena does not provide | L | M | empty seed for MINUS recursion (Fact 4), unit-tested |
| Content-derived variable names collide or are syntactically invalid | L | H | sanitised base plus an 8-hex hash of the rendered statement; identical statements share a variable harmlessly; unit tests in `SparqlTransformerSpec` for a literal object and for IRIs that collide under the old escape |
| Technical knora-base classes that projects instantiate (`Region`, `Annotation`, `LinkObj`, `*Representation`) are ranked as technical and never lead | M | L | anchorless queries on such classes start from a plain statement instead of the type, roughly today's cost; revisit with data if Tempo shows such shapes above 2 s after deploy |
| Orchestrator context exhaustion during review checkpoints | M | L | checkpoints are self-contained journal entries; a fresh orchestrator resumes from checkboxes |
| Removing scala-graph breaks an unexpected transitive user | L | L | grep shows three files only; `bazel build //...` after re-pin |

## Success Metrics

- Prod, one week after deploy: Tempo query `{ span:name = "gravsearch" && span:duration > 2s }` grouped by
  `span.gravsearch.schema_predicates` shows the `listValueAsListNode` and single-link-property rows at about zero
  (baseline 290 to 450 and 200 to 1300 slow runs per day).
- Tanner `hasShelfNumber` query: 20 consecutive runs all under 1 s (baseline bimodal 50 ms / 49 s).
- Review cost: DEV-7287 is reviewable as a golden-file diff plus one new object and one unit spec.

## References

- Linear: [DEV-7288](https://linear.app/dasch/issue/DEV-7288), [DEV-7287](https://linear.app/dasch/issue/DEV-7287),
  related DEV-6803 (audit), DEV-6924 (ReorderFixed cartesian trap), DEV-6819 (query-shape gate).
- Pipeline: `messages/util/search/QueryTraverser.scala:305-325` (insertion point),
  `gravsearch/transformers/OntologyInferencer.scala:50,75` (random suffix), `SelectTransformer.scala:29-54`,
  `SparqlTransformer.scala:116-177`, `gravsearch/prequery/GravsearchQueryOptimisation.scala:181-367`,
  `TopologicalSortUtil.scala`, `AbstractPrequeryGenerator.scala:333-394` (link value statements) and `:422-447`
  (`handleListNode`), `responders/v2/SearchResponderV2.scala:755-792,832-874`.
- Tests: `modules/test-it/.../prequery/GravsearchToPrequeryTransformerE2ESpec.scala` (suite at 2693),
  `GravsearchToCountPrequeryTransformerE2ESpec.scala`, `GravsearchInferencePipelineTestSupport.scala`,
  `modules/testkit/.../GoldenTest.scala`, `modules/test-it/BUILD.bazel:21-28,81-101`,
  `modules/webapi/src/test/.../transformers/ConstructTransformerSpec.scala`, `OntologyInferencerE2ESpec.scala`,
  `SparqlTransformerSpec.scala`, `TopologicalSortUtilSpec.scala`, `slice/ontology/repo/service/OntologyCacheFake.scala`.
- Build: `MODULE.bazel:196`, `modules/webapi/BUILD.bazel:115`, `maven_install.json`, `CONVENTIONS.md`
  "Versions single-sourced".
- Docs to change: `docs/05-internals/design/api-v2/gravsearch.md:307-515`,
  `docs/development/dsp-api-sparql-queries.md` § Pattern Order, `ARCH-MAP.md` component `webapi-search`.
- Engine facts: `docs/development/dsp-api-fuseki-query-execution.md` Facts 1, 3, 4, 7.
- Measurements: tanner handoff `~/Desktop/handoff-gravsearch-values-split-bgp.md` (layouts q0 to q13);
  DEV-7287 stage layout matrix (list node); Tempo traces `eba89507b77ff49517ce46387b78294a` (list node, 8.5 s),
  `737abda549f877beca84a60009f87d99` (hasConcept), `ae7d78f9698869c6ffc098ba6e1963c5` /
  `3e304e0e50e5a6c8239f857fc59078c3` (tanner slow / fast).
- Institutional learnings (dasch-specs `learnings/`): `test-failures/alphabetical-test-data-masks-ordering.md`,
  `logic-errors/query-builder-refactoring-breaks-ordering.md`, `performance/sparql-filter-not-exists-for-rare-predicates.md`,
  `best-practices/test-fixture-isolation-shared-data-scope.md`.
- Tooling: `~/.claude/skills/gh-stack/SKILL.md` (non-interactive rules), eng `work-orchestrating` skill and
  `work-orchestrator` agent definition (tier model, gates, status report).
