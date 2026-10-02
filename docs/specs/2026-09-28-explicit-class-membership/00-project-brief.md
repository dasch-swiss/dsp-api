---
title: "Project Brief: Explicit class membership for resources and values"
date: 2026-09-28
author: "Balduin Landolt"
status: draft
type: project-brief
repositories: []
---

# Project Brief: Explicit class membership for resources and values

> **What this document is.** An exploration, written to order the thinking and to start a discussion. It is not a
> decision and not a plan. Every option below is open, including doing nothing (option 0). Where it states an
> expectation, the expectation is a hypothesis to be tested, and disagreement with it is welcome. It merges two
> independent ideation sessions (2026-09-28 and 2026-09-30); where they disagreed, both views are kept.

## Vision

dsp-api stores only the concrete class of every resource and value (`?r a :Manuscript`, `?v a
knora-base:StillImageFileValue`), and the triplestore does no inference. Every question of the form "is this a
resource?", "is this a file value?" or "give me all `TextWitness`es, including `Manuscript`s and `Letter`s" is
answered by *simulating* inference, at query time or in application code:

- `rdfs:subClassOf*` / `rdfs:subPropertyOf*` property paths, evaluated per row (about 55 occurrences in about 21
  files: v2 read and search, write guards, view restrictions, ontology usage checks, export);
- Gravsearch's `OntologyInferencer`, which expands `?x a C` and every predicate into `VALUES` closures taken from
  the ontology cache;
- application-side closures passed as `VALUES` (export `FindResources`, after the SPARQL path timed out on BEOL);
- and, where those were too slow, **shortcuts that rely on observed data invariants**: "has
  `knora-base:creationDate`, so it is a resource" and "has `knora-base:valueCreationDate`, so it is a value" (the
  DEV-6833 stopgap, in `SearchQueries` and `SearchFulltextQuery`), an IRI regex instead of a class check (DEV-6885).

The idea started as a performance question and turned into a correctness and simplicity one. The shortcuts hold
only as long as the data model happens to make them hold, and a data-model change can break them without anyone
noticing. Six guards are already wrong outright (single-hop `rdfs:subClassOf`, matching only direct subclasses).

Making class membership explicit **swaps a hidden invariant for an explicit one.** "Every instance carries its
full class membership" is still an invariant that has to be kept true, but unlike "only values carry
`valueCreationDate`" it is derivable from the ontology and mechanically checkable: a query can prove it holds or list
every violation.

The hoped-for outcome: queries that ask "is X a Y" in one triple pattern, fewer places that can get it wrong,
predictable performance for the unbound "all resources of class C" queries dsp-app issues constantly, and the option
to delete simulated inference as a concept. The VRE is not DaSCH's current strategic focus, so whatever is done
should be cheap, measurable and reversible. What it has to achieve to be worth doing is itself open (see "The bar"
under Open Questions): correctness alone with no regression, or a measurable win over a no-materialisation baseline.

## Opportunity space

The options fall on three independent axes, plus a baseline.

### Option 0 (baseline): materialise nothing

- Consolidate every guard behind one or two shared helpers with correct `subClassOf*` walks (fixes the single-hop
  bugs).
- Record the data invariants the optimisations rely on and enforce them with tests or a consistency check,
  instead of replacing them (see DEV-6928).
- Keep Gravsearch's `VALUES` expansion, with the placement and ordering fixes already under way.

Plausibly gets most of the correctness win. What it cannot get is cheap unbound anchors, and the observed
invariants stay, documented and tested rather than removed. It is the baseline every other option is compared
against.

Other alternatives that avoid materialisation, listed so they are weighed rather than because they are favoured:

- **Statistics instead of data:** a generated `stats.opt`, so TDB2 orders the existing patterns better than its
  `fixed` heuristic does. Addresses speed, not the single-hop bugs or the proxies. Combinable with option 0.
- **Query-time inference in the store:** a Jena RDFS dataset, or a triplestore that reasons. Jena's RDFS dataset
  assumes a vocabulary fixed at startup while project ontologies change at runtime, materialises whole type extents in
  memory, and expands below the query optimiser. A store swap is a separate, far-off question.

### Axis 1: where the closure lives

- **Instance side:** each resource or value carries its full membership.
- **Schema side:** each class carries its full superclass chain (`:Manuscript rdfs:subClassOf :TextWitness,
  knora-base:Resource`), and queries use one hop, `?r a ?c . ?c rdfs:subClassOf X`, with no property path.
    - Tiny footprint (triples per class, not per instance), one write path (class creation), the ontology cache
      already computes the chain, fixes the single-hop bugs just by existing, and works the same way for
      properties.
    - Unbound anchors ("all resources") remain a join over every class's instances, just written differently.
      The DEV-6803 audit measured exactly this query shape flipping join order (8.6 s to 73.6 s), so it needs
      benchmarking in place.
    - Ontology responses (v2, v3, dsp-tools round trips, ontology export) would suddenly show the whole chain, so
      "declared" and "entailed" superclasses have to be told apart.
- **Hybrid:** schema-side closure for correctness and hierarchies, plus instance-side membership only for the
  rungs where unbound anchors hurt (probably resource-ness).

### Axis 2: how instance-side membership is represented

Today "the `rdf:type`" *is* the class: readers take `.head`, queries project `?x a ?class`, and
`FileValuePermissionsQuery` denies asset access unless exactly one row comes back. Whatever is chosen here decides
how much of that has to change.

| | representation | for | against |
| -- | -- | -- | -- |
| (a) | all memberships in `rdf:type`; the concrete class is "the most specific one" | standard RDFS (what entailment rule rdfs9 produces) | every reader that treats `rdf:type` as "the class" breaks; "most specific" needs the ontology |
| (b) | `rdf:type` stays the concrete class; memberships go under a dedicated predicate (placeholder `knora-base:isInstanceOf`) | no existing query changes; can be added rung by rung, measured, and removed again | non-standard vocabulary; Gravsearch has to rewrite `?x a C` to the new predicate |
| (c) | all memberships in `rdf:type`, plus a dedicated predicate for the concrete class | standard semantics with an unambiguous concrete class; precedent for the name (`sesame:directType` in RDF4J/GraphDB, Jena's `directRDFType`) | readers still migrate, though each fix is a one-liner |

**Working hypothesis, for discussion: (b).** It was the first ideation session's starting point because it is safe
and reversible and sidesteps the value-ness hazards below, not because the others were ruled out. (c) is the
standards-aligned choice if external readers ever matter, and (a) stays on the table. Measurements may well change the
picture: whether Jena's `fixed` reorder heuristic treats a custom predicate differently from `rdf:type` is unknown.

Considered and rejected: asserting `rdfs:subClassOf` on instances. It makes each instance a class under RDFS, and
with the union default graph it would make ontology-side patterns such as `?c rdfs:subClassOf* knora-base:Resource`
enumerate millions of instances.

The v2 read path already tolerates `a knora-base:Resource`: the CONSTRUCTs fabricate it and
`ConstructResponseRdfDataParser` strips it again. So `rdf:type` for resource-ness alone is less invasive than for
anything else.

### Axis 3: scope, as a ladder

| rung | what | who asks today | notes |
| -- | -- | -- | -- |
| 1 | resource-ness (`knora-base:Resource`) | many unbound guards; dsp-app's generic anchor `?mainRes a knora-api:Resource` (resource list, class counts, advanced-search fallback, list viewer) | fewer instances (1.27 M resources) and the most performance pressure |
| 2 | value-ness (`knora-base:Value`) | write guards (delete, erase, insert), mostly on a *bound* value IRI | mainly a correctness problem; the `valueCreationDate`-style shortcuts live here |
| 3 | property hierarchies (`rdfs:subPropertyOf`: `hasValue`, `hasLinkTo`, `hasFileValue`, `isPartOf`, `seqnum`, and project subproperties) | read queries, view restrictions, version history; Gravsearch expands *every* predicate | expected to matter soon; 3,107 `hasValue` subproperties made `VALUES` closures catastrophic; DEV-6882 audits these guards |
| 4 | built-in value hierarchy (`StillImageFileValue` ⊂ `StillImageAbstractFileValue` ⊂ `FileValue` ⊂ `Value`) | view restrictions, file-value permissions, fulltext search | up to five levels deep |
| 5 | built-in resource hierarchy (`Representation`, `StillImageRepresentation`, `Segment`, ...) | no observed demand (one redundant bound-subject check in dsp-app's segment query) | these classes are almost never instantiated directly; probably skipped unless rung 6 includes it for free |
| 6 | project data-model hierarchies (`Manuscript` ⊂ `TextWitness`, across shared ontologies) | Gravsearch's class expansion; dsp-app's class-based browsing | user-facing; the "find all `TextWitness`es" case |

Off to the side: the standoff hierarchy (`StandoffTag` ⊃ `StandoffDataTypeTag` ⊃ ..., which projects extend) is the
same problem and could join the ladder later.

The ladder does not imply climbing it one rung at a time, nor stopping early. An earlier take favoured going straight
to the full closure, because deciding the markers alone "risks doing the migration twice". With an additive,
re-runnable back-fill (see Mechanics) a second pass with a wider closure is cheap, which weakens that argument, but
the choice is open.

Resource-ness and value-ness are separate rungs on purpose. They may deserve different mechanisms, e.g.
instance-side membership for resources but only a schema-side closure or option 0 for values. Materialising
value-ness in `rdf:type` has particular hazards: `FILTER(?valueObjectType != knora-base:LinkValue)` would leak link
values into search results, and `FileValuePermissionsQuery` would return two rows and deny all asset access.

Properties do not fit option (b) as neatly. Instance-side "membership" for a property means materialising the
super-property edge itself (`<r> knora-base:hasValue <v>` next to `<r> :hasTitle <v>`, RDFS rule rdfs7). That is
standard, but readers that iterate `?r ?p ?v` would see it (the v2 parser already drops generic `hasValue` triples
because Gravsearch expansion produces them). A schema-side closure avoids that question for properties.

## Mechanics shared by every instance-side option

- **Write-time chokepoint.** One function computes the membership from the ontology cache, and every write path calls
  it: v2 resource and value creation, the LinkValue side-writes (three separate queries today), v3 bulk import,
  project data import and migration import. Imports normalise foreign `rdf:type` instead of passing it through (v3
  bulk import currently keeps any extra `rdf:type` from the payload, and SHACL has no count limit on it).
- **Idempotent fixpoint maintenance action.** An additive `INSERT ... WHERE`, run in batches directly against the
  triplestore through the existing maintenance-action framework (`MaintenanceService`), repeated until a pass changes
  nothing. The same action does the initial back-fill, detects drift (a dry run reports what it would insert), and
  repairs it. Run after imports or on a schedule; "it changed something" means a write path forgot. It does *not*
  use the upgrade-plugin flow, which downloads the whole repository into memory, drops the graphs and re-uploads
  them, and no longer works at production size. It needs a single-flight guard so two runs (or two replicas) do not
  race, and it also avoids the open question whether jena-text purges its index on `DROP GRAPH`.
- **Schema-side closure** follows the same pattern: computed when a class or property is created, reconciled by the
  same action.
- **Hierarchy changes through upgrades.** Normal operation never changes a class's closure, but a knora-base or
  shared-ontology upgrade that changes the built-in hierarchy does. Such an upgrade has to re-run the action (and
  remove memberships that no longer hold), so the action should be able to delete as well as insert.

Freshness is why both are needed: without write-time computation, a resource created a minute ago would be missing
from "all resources" and "all `TextWitness`es" until the next reconciliation run. Ordering matters for the same
reason: every write path should assert the membership *before* the back-fill runs, so the back-fill converges once
instead of chasing new writes.

### What it costs in triples

Measured on stage (2026-09-30): 125.0 M triples, 12.0 M `rdf:type` triples (all typed nodes: resources, values,
standoff, admin), 1.27 M resources. Values did not finish counting within 120 s; about 10 M is an upper bound.

| rung | added triples (upper bound) |
| -- | -- |
| resource-ness | about 1.3 M (about 1 %) |
| value-ness | up to about 10 M (under 8 %) |
| full value hierarchy | up to about 20 M (about 16 %; most values are two hops deep) |
| data-model hierarchies | a few million |
| schema-side closure | negligible |

Volume is not the constraint. The real cost is keeping the invariant true (the write paths) and, only under (a) or
(c), migrating the readers.

## Milestones

Only milestone 1 is unconditional. Everything after it depends on what milestone 1 shows.

### Milestone 1: measure before committing

**Scope:** an experiment on a dev server (a prod mirror, so stage stays free). No production change, no reader
change.

Write instance-side membership (under a dedicated predicate, and under `rdf:type` too if that is cheap enough to
compare, since the reorder heuristic may treat the two differently) and a schema-side closure, using a first cut of
the fixpoint maintenance action, which also measures the back-fill's runtime. Benchmark each shape against option 0
on:

1. dsp-app's generic anchor `?mainRes a knora-api:Resource` (resource list, class counts);
2. Gravsearch for a class with data-model subclasses (the `TextWitness` case);
3. an unbound resource-ness guard, e.g. the search-by-label count query that uses the `creationDate` stopgap today;
4. export `FindResources` on BEOL;
5. a value-hierarchy query, e.g. view restrictions on `FileValue`;
6. the asset-access permission query (`FileValuePermissionsQuery`), whose optimiser hint is load-bearing.

Expected outcomes, stated as hypotheses. They assume the "beat option 0" reading of the bar; under the "correctness,
no regression" reading, the first one becomes "fall back if it regresses".

- If instance-side membership does not clearly beat option 0 on the unbound anchors (no win, or a win that needs
  fragile query shaping), fall back to option 0.
- If the schema-side closure matches option 0 and lets the property-path spellings be deleted, it can stand on
  simplicity alone.
- Instance-side membership is worth it only for the rungs that show a real gain. The guess is resource-ness and
  data-model hierarchies, with value-ness staying schema-side or at option 0.

### Milestone 2: the mechanism

**Scope:** the write-time chokepoint and the fixpoint maintenance action as production code, for whichever rungs and
representation milestone 1 supports. It includes the import paths and test fixtures (regenerated, not hand-edited).
Write paths go first, the back-fill runs after them, so it converges once. It excludes switching any reader, so up to
here everything is reversible.

### Milestone 3: adopt, rung by rung

**Scope:** switch readers to the explicit membership one rung at a time, each benchmarked in place (a simpler
pattern can be slower). Retire the corresponding shortcuts, the `creationDate` stopgap first.

### Milestone 4: delete simulated inference

**Scope:** conditional on milestone 3 covering Gravsearch. Remove `OntologyInferencer`'s `VALUES` expansion and the
property-path spellings, and add a gate against reintroducing them.

## Repositories

- **dsp-api:** all of the above.
- **dsp-app:** possibly nothing. Gravsearch keeps accepting `?x a C` and rewrites it server-side. Only affected if
  the representation is (a) or (c) and the app reads `rdf:type` from responses.
- **dsp-tools:** only if the import paths change what they accept (foreign `rdf:type`).

## Risks

- **Plan flips.** Replacing a path or `VALUES` with a plainer pattern has already made a query 8.5× slower. Every
  converted site needs an in-place benchmark. The engine facts behind this are in
  [`dsp-api-fuseki-query-execution.md`](../../development/dsp-api-fuseki-query-execution.md).
- **Hidden `rdf:type` readers** (only under (a) or (c)): `.head` readers, projected `?class` variables, exactly-one-row
  checks. Some are known (`MetadataService`, `ResourcesRepoLive.findById`, `FileValuePermissionsQuery`,
  `SearchFulltextQuery`, the view-restrictions `multiTyped` gate). The v3 code and the upgrade plugins have not been
  audited.
- **Forgotten write paths.** A new write path that skips the chokepoint silently creates instances that are invisible
  to membership queries. The reconciler catches this after the fact.
- **Declared vs. entailed superclasses** leaking into ontology responses (schema-side closure).
- **External superclasses.** v3 can add or remove `rdfs:subClassOf <external IRI>` at any time, with no instance
  check, so a closure over them would go stale.

## Constraints

- **No triplestore inference today.** The design must not assume it (see the alternatives under option 0).
- **The built-in and project class hierarchies are immutable once a class exists.** Superclasses are set only at class
  creation, and a class can only be deleted while unused. A class's closure therefore never changes while it has
  instances. External superclasses are the exception.
- **Audience is internal for now.** dsp-api's own queries are the readers. External readers (DPE, rdu-tools, the
  archive format, a SPARQL endpoint) are considered unlikely; the door is kept open, not designed for.
- **Benchmarks** run on stage (read-only) or a dev mirror (anything that writes), never on a local dump.

## Success Criteria

- A measured answer to "what does explicit membership buy over option 0, and for which rungs?", even if the answer is
  "nothing worth it".
- If adopted: no guard relies on an observed invariant; the single-hop sites return deep subclasses; "is X a Y" is
  one pattern in every query that asks it; the maintenance action reports zero drift on every environment;
  hot queries are no slower than today, and ideally unbound anchors are much faster.

## Open Questions

- [ ] **The bar.** Does correctness alone justify the work, as long as nothing regresses? Or does it have to beat
      option 0 measurably (on unbound anchors, or by deleting shortcuts option 0 can only document), since option 0
      already gets much of the correctness?
- [ ] Is there a non-performance reason that would justify instance-side membership even with a flat benchmark?
      Candidates: deleting `OntologyInferencer`, deleting the observed-invariant shortcuts.
- [ ] Is the milestone 1 benchmark set right? Candidates missing from it: the ekws.ch list-node searches, fulltext
      search, the v2 resource read path.
- [ ] Representation for properties (rung 3): schema-side closure only, or materialised super-property edges?
- [ ] Does standoff join the ladder, and when?
- [ ] External superclasses: never materialised (they are mappings, not dsp-api semantics), or materialised and kept
      in sync by the v3 mapping endpoints?
- [ ] Schema-side closure: how to tell declared from entailed superclasses (a marker, or recompute the minimal set on
      read)?
- [ ] How does this relate to the DEV-6850 scope (materialise resource-ness in `rdf:type`, consolidate the value-ness
      walk), DEV-6928 (record and enforce data invariants), and the Gravsearch pipeline simplification work? Does it
      subsume, replace or sit beside them?
- [ ] How does Jena's `fixed` reorder heuristic weight `rdf:type` against a custom predicate in these patterns?
- [ ] Link values: part of the `knora-base:Value` membership, or handled explicitly given the existing
      `!= knora-base:LinkValue` filters?
- [ ] Does explicit membership make Gravsearch's inference and type-inspection work mostly unnecessary, or only
      simpler?
- [ ] How many values are there exactly, and how long does the back-fill take at production size?
- [ ] Naming: `knora-base:isInstanceOf` is a placeholder. If (c), `directType` or something closer to the RDF4J
      precedent?

## Related

- DEV-6850: resource and value type guards. Its migration phase would be superseded by this direction if pursued;
  its guard consolidation and single-hop fixes would become consumers (or option 0).
- DEV-6882: the `rdfs:subPropertyOf*` counterpart (rung 3).
- DEV-6928: record the data invariants and keep them true by construction.
- DEV-5952, DEV-6833: earlier symptoms, and the shipped `creationDate` / `valueCreationDate` stopgap.
- Linear project "DSP-API query performance and search correctness".
