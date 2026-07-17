# Modularization Plan — dsp-api

How to move from the domain model to a domain-driven module structure (and, downstream, small
Bazel compilation units). Companion to [`CONTEXT-MAP.md`](./CONTEXT-MAP.md) (the bounded contexts
and target dependency DAG) and [`UBIQUITOUS_LANGUAGE.md`](./UBIQUITOUS_LANGUAGE.md) (the glossary).

Status: **draft** — outcome of a domain-modelling session. This is a sequencing plan, not an
implementation plan; the first concrete design artifact is described under Phase 0.

> Code paths in these documents are relative to
> `modules/webapi/src/main/scala/org/knora/webapi/` unless prefixed otherwise (post-#4183 the
> `webapi` sources live under `modules/webapi/`).

## Where this fits

The `modules/` restructure already underway (`#4183` moved `webapi`, `sipi`, `ingest`, `fuseki`
under `modules/`; Sipi already builds with **Bazel** via a Nix dev shell) is the *physical
packaging* groundwork. This plan supplies the missing half: the **domain-driven target** for what
those modules should be and *why the current code can't yet be split into small ones.* Goal
ordering is fixed: **domain meaning → code structure → small Bazel units**, never the reverse.

## Core finding

**The domain boundaries are already close to right. The blocker is the foundation, not the
carving.** `StringFormatter` and `SmartIri` (both in `messages/StringFormatter.scala`, ~1453
lines), plus `OntologyConstants` (~1067 lines), collapse kernel primitives, Data Model semantics,
string validation, and RDF vocabulary into a single ~2500-line floor that ~a quarter of the codebase
imports. Every context that merely touches an IRI implicitly pulls in Data Model's schema
machinery. **No downstream context can become a small compilation unit until that floor is
decomposed** — regardless of how clean its boundary is.

## Recommendation: apply before more modelling

Further broad domain modelling now hits diminishing returns. The context map is a *hypothesis*
("boundaries close to right; floor is the blocker"); the cheapest way to confirm or falsify it is
to execute the one thing that must happen first anyway. Modelling more contexts in depth risks
polishing a map that first contact with the code will redraw.

**Sequence:** design the foundation seam → apply it incrementally → let what you learn reshape the
next extraction.

## Phase 0 — Decompose the foundation floor (prerequisite)

The highest-leverage, lowest-domain-risk move. Deliverable: a focused **decomposition design**
(bounded, ~1 day) before code changes, covering:

- **Split the IRI type into two families** so the type system encodes schema-variance:
    - `DataIri` — schema-invariant identifiers (resource, value, project, user, list). No schema
    conversion (converting one should be a *compile error*). → shared-identifiers kernel.
    - `DefinitionIri` — schema-variant data-model entity IRIs (class, property, ontology); carry
    internal↔API conversion. → Data Model context.
- **Relocate `SmartIri`'s ~30 methods:** pure identity/string ops → kernel; schema conversion +
  ontology/entity parsing → a Data Model schema service. Stop backing typed IRIs with `SmartIri`.
- **Split `StringFormatter`** along its three tangled jobs: string validation, IRI handling, and
  ontology-schema logic.
- **Extract `OntologyConstants`** — separate genuinely shared RDF vocabulary from Data-Model-only
  constants.
- **Split the kernel into small units** (`identifiers` / `rdf-access` / `permission-policy` /
  `primitives`) so a context can depend on `identifiers` without pulling in `rdf-access`. This
  split is the single highest-leverage step for small Bazel units.

**Strangler strategy (not big-bang — touches ~25% of files):**

1. First bite: extract `OntologyConstants` (pure constants, low risk) and introduce the two IRI
   family types *alongside* `SmartIri`.
2. Migrate call sites incrementally toward the new types; shrink `SmartIri` to a deprecated shim.
3. Move schema-conversion logic into the Data Model schema service; delete the shim when unused.

## Phase 1+ — Extract contexts along the DAG

Once the floor is decomposed, extract/solidify contexts guided by the target DAG in
`CONTEXT-MAP.md`, bottom-up: Identity & Access → Data Model / Assets → Resources & Values → Search
→ Export. Let each extraction's learnings reshape the next; do **not** pre-plan the full sequence
now (speculative until validated).

Known non-obvious move: the Data Model → Resources "is this entity used?" check becomes the
`InstanceUsage` capability via **Dependency Inversion** — interface owned by Data Model, implemented
by Resources — to keep the graph acyclic (see `CONTEXT-MAP.md` § Target dependency structure).

## Guardrails (apply continuously)

- **The ratchet:** existing cross-context SPARQL stays; **no *new* cross-context SPARQL** — new
  cross-boundary reads go through a published capability. Exempt: intrinsic substrate users
  (Search, Export bulk graph moves).
- **Identity vs behavior:** typed IRIs are identifiers; embedded ontology/schema *behavior* does
  not belong in a value object or the kernel.
- **Domicile:** cross-context reference IDs live in the shared-identifiers kernel; entity behavior
  lives in the owning context.
- **Meaning wins over storage:** boundary assignment follows domain meaning, not the RDF graph an
  entity happens to live in (e.g. list nodes in the data graph are still Data Model).

## Non-goals / explicitly deferred

- More strategic context modelling (diminishing returns until validated by code).
- Tactical internals of contexts not yet being touched.
- Full migration sequencing beyond Phase 0 — depends on Phase 0 learnings.
- Consolidating mislocated code (Lists/ingest in `admin`) — do it opportunistically as each
  context is extracted, not as a standalone sweep.

## Success signal

Contexts compile as independent units; the kernel is a few tiny units at the bottom; a change to
Assets or Identity no longer forces recompilation of the ontology-schema machinery. That is the
point at which small Bazel compilation units become achievable.
