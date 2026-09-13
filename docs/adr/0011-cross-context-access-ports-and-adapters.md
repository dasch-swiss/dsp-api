# ADR-0011: Cross-context access through consumer-defined ports

Date: 2026-09-13

Relates to: [`CONTEXT.md`](https://github.com/dasch-swiss/dsp-api/blob/main/CONTEXT.md) (context map and
guardrails), [`MODULARIZATION-PLAN.md`](https://github.com/dasch-swiss/dsp-api/blob/main/MODULARIZATION-PLAN.md)
(extraction sequence), [`ARCH-MAP.md`](https://github.com/dasch-swiss/dsp-api/blob/main/ARCH-MAP.md)
(component topology). These live at the repository root, outside the documentation site. Adopts the contract of
dasch-ops-platform ADR-0013 ("Cross-capability access, the Modulith contract") for dsp-api, with the
deviations listed below.

## Status

Accepted

## Context

dsp-api is one deployable holding several bounded contexts: Projects, Identity & Access, Data Model,
Resources & Values, Search, Assets, Project Migration and Operations. They refer to each other
constantly. A Resource belongs to a Project and conforms to a Data Model; Search returns Resources;
Project Migration moves a whole Project; Data Model must know whether a Class is still used by any
Resource before it may be changed.

Today those references are served by reading the other context's RDF directly. The architecture map
records the result: `ListsResponder` imports thirteen query classes from `slice/resources/repo`, the
export services run raw SPARQL against the admin and permissions graphs, `OntologyResponderV2` reaches
into resource data through five `Is*Used*` queries, and three named graphs each have several writers
from different contexts. `webapi` is a single Bazel target with public visibility, so nothing stops the
next change from copying the nearest pattern.

The modularization plan turns each context into a deep Bazel module. That requires deciding, before
the first extraction, how contexts may talk to each other. dasch-ops-platform settled the same question
for its Rust modulith in ADR-0013: consumer-defined ports, provider-owned adapters, a composition root
that only wires, and data sovereignty over storage. dsp-api already uses that shape in one place, the
planned `InstanceUsage` interface. This ADR generalises it.

## Decision

1. **Graph sovereignty.** Each context owns its named graphs and is their sole reader and writer. No
   other context issues SPARQL against them, even though all graphs live in one Fuseki dataset. The
   admin graph belongs to Projects and Identity & Access, the permissions graph to Identity & Access,
   the ontology graphs to Data Model, the project data graphs to Resources & Values. Enforced by
   review today and by Bazel visibility once per-context targets exist.

2. **Consumer-defined ports live in the consumer's `ports` package.** When context A needs something
   from context B, A declares a Scala trait that says exactly what A needs, together with its small
   boundary DTOs, in `slice/<a>/ports`. That package depends on nothing but the shared kernel
   (Foundation primitives and Permission policy). Ports are never shared between consumers: two
   contexts needing similar data from one provider each declare their own port.

3. **The provider implements the adapter next to its data.** B provides a `<Port>Live` class in
   `slice/<b>/repo` (or `domain/service` when no persistence is involved) that implements A's port. B
   depends on `slice/<a>/ports` and on nothing else of A. The adapter is a role at the seam, not a
   size limit: behind a small port the provider may keep a deep implementation, because the knowledge
   of how its data is represented belongs to the provider.

4. **The composition root only wires.** `LayersLive` constructs B's adapter and provides it where A
   requires the port. No adapter logic lives in `core/`.

5. **A context may depend on another context's `ports` package, and on nothing else of it.** This is
   the one permitted context-to-context dependency. `domain`, `repo` and `api` of a context are
   visible only to that context and to the composition root. The top-level arrow stays
   `app -> api -> domain contexts -> platform -> libraries`, with the single narrow exception of a
   provider depending on a consumer's `ports` package.

6. **References are opaque identifiers; details come through the port.** A context stores only the
   foreign IRI (a `ProjectIri`, `UserIri`, list-node IRI). Labels, settings, rates and other details are
   fetched through a port, never by reading the owner's graph. Identifier value types are shared
   concepts and live in the shared kernel; a boundary DTO returned by a port (for example a
   `ProjectRef` with shortcode and name) is defined in the consumer's `ports` package and is not shared.

7. **Share concepts, never shapes.** A value object that means the same thing everywhere (`ProjectIri`,
   `LanguageCode`, permission levels) belongs to the shared kernel. A DTO that one consumer needs
   from one provider does not, even when two consumers need something similar.

8. **No cross-context transaction.** Each context commits its own SPARQL update. A workflow spanning
   contexts is a sequence of per-context updates coordinated by identifiers and port calls; consistency
   across contexts is eventual. `IriLocker` remains a per-context concurrency guard, not a cross-context
   transaction.

9. **Ratchet.** Existing cross-context SPARQL may stay until the owning context is extracted. New
   cross-context reads and writes go through a port from the day this ADR is accepted.

10. **One platform exception: whole-graph movement.** Project Migration may copy, upload or drop whole
    named graphs through the RDF platform because it operates on graphs as opaque units without
    interpreting their triples. Reading admin, permission or resource *meaning* out of those graphs is
    not covered and goes through ports.

## Worked examples

**Data Model asking Resources & Values about instance use (lookup style).** Data Model declares
`InstanceUsage` in `slice/ontology/ports` (is this class used by any resource, is this property used
with this class). Resources & Values implements `InstanceUsageLive` in `slice/resources/repo`, replacing
the `IsClassUsedInDataQuery`, `IsPropertyUsedInResourcesQuery` and `CountPropertyUsedWithClassQuery`
reads that Data Model runs today. `LayersLive` wires it. The compile-time edge is
Resources & Values -> Data Model ports, so the graph stays acyclic.

**Search asking Resources & Values for matching resources (deep read model).** Search declares
`ResourceSearch` in `slice/search/ports`: run a parsed Gravsearch query for a user with paging, count
it, full-text and label search, incoming links. Resources & Values implements `ResourceSearchLive`
behind which the prequery and main-query generators, the SPARQL transformers, `OntologyInferencer`,
`MainQueryResultProcessor` and the Lucene full-text queries move, because they encode how a Resource
and its Values are stored as triples. Search keeps the Gravsearch language (parser, checker, AST),
type inspection through the Data Model projection port, paging and count semantics, guards, timeouts,
tracing and the HTTP endpoints. Search therefore stops being an "intrinsic RDF-platform user"; the
RDF access moves to the context whose representation it reads. The port is small and the
implementation behind it is large, which is the intended shape of a deep module.

**Project Migration reading administration data.** Export declares the ports it needs in
`slice/export/ports` (project snapshot, users referenced by a project, permissions of a project).
Projects and Identity & Access implement them next to the admin and permissions graphs, replacing
`AdminDataQuery`, `AdminUsersQuery`, `PermissionDataQuery` and `ReferencedUserIrisQuery`. Moving the
project data graph itself stays a whole-graph platform operation under decision 10.

## Deviations from dasch-ops-platform ADR-0013

- **Naming.** dsp-api keeps its suffix convention, `<Port>Live` for the production adapter and
  `<Port>InMemory` for the test double, instead of `Live<Port>` and `Test<Port>`. Repo-internal
  consistency with dozens of existing classes wins over cross-repo uniformity.
- **Storage unit.** Sovereignty is over named graphs in one Fuseki dataset instead of tables in one
  Turso file. The consequence is the same: no cross-owner queries.
- **Whole-graph exception.** ADR-0013 has no equivalent of decision 10. It exists because Project
  Migration's job is moving graphs, which has no table analogue in the ops platform.
- **Enforcement today is `review`.** ADR-0013 enforces with Bazel visibility from the start. dsp-api
  can only do so after the modularization plan's Phase 7 creates per-context targets; until then
  the rule lives in `REVIEW.md` and the architecture map's banned-constructs table.

## Consequences

**Positive.** Contexts become independently testable by providing an in-memory port implementation;
extraction of a context to its own target or service changes only the adapters wired in
`LayersLive`; the open question "small published identifier contract or dependency on the whole
module" is closed (identifiers go to the shared kernel, everything else through ports); Search's RDF
exemption disappears and its context boundary becomes explicit; multi-writer graphs get a single
owner to converge on.

**Costs.** One port and one adapter per cross-context need; boundary DTOs are declared per consumer;
reads that were one SPARQL join become a port call plus in-memory composition; Search shrinks to a
small context owning the query language and delivery, with most of today's search code moving into
Resources & Values; the rule carries one deliberate exception (a `ports` package) and one platform
exception (whole-graph movement), both of which must be checked in review until Bazel enforces them.

## Alternatives rejected

- **Keep reading other contexts' graphs.** Simplest today; couples every context to every other
  context's RDF representation and makes extraction impossible.
- **Provider-published contracts** (the "small Projects contract" in earlier plan drafts). Points the
  dependency consumer -> provider and lets the provider dictate what consumers may see. Replaced by
  shared-kernel identifiers plus consumer-defined ports.
- **One shared `ports` or `contracts` module for all cross-context traits.** Becomes a god module that
  every context depends on and strips each port of its consumer's authorship.
- **Adapters in the composition root.** Keeps `core/` free of context dependencies but makes it accrete
  storage knowledge from every provider.
- **Folding Search into Resources & Values.** Matches how the code reads today but freezes the Search
  boundary while the retrieval design is still moving. The port keeps the option open either way.
