# Modularization Plan — dsp-api

High-level sequence for turning the Bazel-built web application into domain-oriented modules.
Companion to [`CONTEXT-MAP.md`](./CONTEXT-MAP.md) and
[`UBIQUITOUS_LANGUAGE.md`](./UBIQUITOUS_LANGUAGE.md).

Status: **draft.** This document records the implementation direction; delivery issues and detailed
designs should be created when each phase reaches the front of the work.

The fuller implementation plan, evidence, and visual review are maintained in the
[dsp-api domain modularization specification](https://github.com/dasch-swiss/dasch-specs/tree/main/specs/2026-07-30-dsp-api-domain-modularization).

## Current state

dsp-api now builds with Bazel, but `modules/webapi` remains effectively one production compilation
target and one unit-test target:

- 437 production Scala files are globbed into `//modules/webapi:webapi`;
- the target declares the complete web application's third-party dependency set;
- 176 Scala unit-test files are compiled and scheduled together;
- legacy `messages`, `responders`, `store`, root, and `slice.*` packages form coarse cycles;
- domain implementations still import delivery models and concrete RDF code.

The migration from SBT solved toolchain and packaging concerns. It did not yet establish domain
ownership or useful compilation locality.

## Core finding

The work cannot begin by translating current directories into BUILD targets. Several files form a
false foundation:

- `StringFormatter` and `SmartIri` combine validation, identifiers, and Data Model behaviour;
- `OntologyConstants` combines generic and context-specific RDF vocabulary;
- global errors import multiple domain types;
- `common` and root values depend upward while being imported broadly.

Declaring these as one shared target would hide cycles rather than remove them. The false foundation
must be reduced before extracting the first domain module.

## Principles

- Domain meaning determines ownership; current packages and RDF graphs do not.
- A domain starts as one deep module, not separate model/handler/repository targets.
- HTTP and RDF implementations become adapters only where a real seam exists.
- Context-specific identifiers normally belong to small contracts owned by their context.
- Compatibility labels and shims remain while consumers move incrementally.
- Every change must reduce a measured cycle, establish a useful interface, or extract a real
  target.
- Tests move with the behaviour they verify.
- Each pull request keeps the complete build green and avoids unrelated naming changes.

## Critical path

```text
Baseline and ownership
  → false-foundation cleanup
  → Projects vertical extraction
  → Identity & Access / Data Model / Assets in parallel
  → Resources & Values
  → Search / Project Migration / Operations in parallel
  → HTTP delivery and application composition
  → strict dependency enforcement
  → aggregate-target retirement
```

Measurements, test migration, compatibility checks, and documentation run throughout.

## Phase 0 — Establish the baseline

Outcomes:

- The context map and ownership decisions are accepted.
- Build and test behaviour is reproducibly measured.
- Dependency analysis can show whether each subsequent change improves the graph.

Work:

- Record clean, warm, no-op, implementation-change, and interface-change build measurements.
- Record compiler actions, cache behaviour, test scheduling, runtime resources, and image contents.
- Extend package analysis to every source root, including `dsp.*`.
- Add file-level analysis so coarse package cycles are not mistaken for indivisible file sets.
- Preserve current Bazel labels, images, and developer commands as compatibility requirements.

Gate: do not begin broad source movement until ownership rules and baseline commands are agreed.

## Phase 1 — Remove the false foundation

Outcomes:

- Small bottom-of-graph targets compile without domain or delivery dependencies.
- Domain extraction no longer pulls all schema, error, and RDF vocabulary machinery with it.

Work:

- Move root primitives to explicit owners and split universal errors from context-owned errors.
- Untangle `LanguageCode`, `LangString`, and similar values.
- Separate schema-invariant Data IRIs from schema-variant Definition IRIs.
- Keep context-specific Data IRIs with small contracts owned by Projects, Identity & Access,
  Resources & Values, and other owners; do not create a universal identifier dumping ground.
- Move schema conversion and ontology-aware IRI behaviour into Data Model.
- Decompose `StringFormatter`; retain `SmartIri` temporarily as a compatibility shim.
- Split `OntologyConstants` by ownership instead of extracting it intact.
- Separate generic RDF execution into the RDF platform and keep context-specific vocabulary/query
  construction in context-owned adapters.
- Introduce Bazel targets as soon as a module is independently compilable.

The primitive/error, IRI, RDF-vocabulary, and RDF-platform tracks may proceed in parallel after
ownership is settled. Changes to global files require coordination and narrow pull requests.

Gate: foundation targets have no imports from Projects, Identity & Access, Data Model, Resources,
HTTP delivery, responders, or application composition.

## Phase 2 — Extract Projects as the pilot

Projects comes first because it is a confirmed independent VRE domain, has broad consumers, and is
small enough to validate the full method.

Work:

- Introduce a small Projects contract for legitimate references and lookups.
- Move project commands/results out of HTTP request/response types.
- Put project RDF representation and queries in a Projects-owned adapter.
- Move memberships to Identity & Access.
- Move project import/export to Project Migration.
- Move cross-context erasure to Operations or explicit orchestration.
- Compose ontology-enriched HTTP responses above Projects and Data Model.
- Move tests and fixtures with the module.
- Create production, RDF-adapter, HTTP-adapter, and test targets without breaking existing routes
  or labels.

Gate: Projects builds and tests without depending on the aggregate webapi production target.

## Phase 3 — Establish the parallel upstream domains

Once the Projects contract is stable, three workstreams can proceed largely in parallel.

### Identity & Access

- Extract Users, Groups, memberships, permission administration, and permission profiles.
- Separate pure permission policy from administration and local enforcement.
- Separate Authentication/JWT from object-access authorization.
- Depend on Projects through its public contract.

### Data Model

- Consolidate ontology behaviour under the domain term Data Model.
- Move Lists into Data Model.
- Own Definition IRIs and schema conversion.
- Own standoff definitions and mappings.
- Publish the projection required by Search.
- Define the `InstanceUsage` interface required for model-evolution checks.

### Assets

- Consolidate Sipi and ingest integration.
- Define asset metadata and serving interfaces.
- Keep File Values in Resources & Values.
- Consume Restricted View configuration from Projects and enforce it in Assets.
- Prepare the seam for future ingest consolidation without assigning archival custody.

Gate: each domain has a stable public interface, intentional dependencies, and no HTTP-delivery
dependency.

## Phase 4 — Extract Resources & Values

Work:

- Consolidate Resource and Value behaviour around Resource as aggregate root.
- Rehome legacy resource/value messages and responders.
- Put standoff markup and text conversion here.
- Retain File Values while consuming the Assets interface.
- Put Resource RDF meaning and queries in a Resources-owned adapter.
- Implement Data Model's `InstanceUsage` interface.
- Localise object-access enforcement using the effective Permission profile and Permission policy.
- Move tests/fixtures and create independent Bazel targets.

This phase waits for usable Projects, Data Model, Assets, and Permission-profile interfaces.
Internal Resource/Value consolidation, the RDF adapter, and test migration may proceed in parallel
against the agreed public interface.

Gate: Resources & Values compiles independently, and new Data Model reads no longer know its RDF
representation.

## Phase 5 — Complete the upper domains

Search, Project Migration, and Operations may proceed in parallel once core interfaces stabilise.

### Search

- Keep one Search module while the retrieval redesign remains in flux.
- Consume the published Data Model projection and Resources read interfaces.
- Retain low-level RDF access only where query translation intrinsically requires it.
- Rehome Gravsearch and search-specific legacy utilities.

### Project Migration

- Consolidate VRE import/export, bundles, validation, and Data Task lifecycle.
- Replace reads of administration data with Projects and Identity & Access interfaces.
- Retain low-level RDF access only for intentional bulk graph movement.
- Remove delivery codecs from the domain implementation.
- Describe exports as VRE handoff, not archival custody.

### Operations

- Keep single-context maintenance with its owner.
- Collect only proven cross-context workflows.
- Do not reproduce `admin` as a new generic module.

Gate: no upper domain is imported by a lower domain, and each has independent production/test
targets.

## Phase 6 — Rebuild delivery and composition

Work:

- Place versioned DTOs, codecs, and endpoint translation beside the owning domain's HTTP adapter.
- Keep admin, v2, and v3 route aggregation thin.
- Replace direct repository/responder knowledge in `LayersLive` with domain and adapter entrypoints.
- Isolate configuration, observability, startup, and route assembly.
- Preserve route behaviour, generated OpenAPI, runtime resources, Java loader entries, and image
  contents.

Each domain's HTTP adapter may move when its domain interface stabilises. Final composition waits
until all production modules expose usable entrypoints.

Gate: only application composition selects concrete adapters.

## Phase 7 — Tests, enforcement, and completion

Test structure:

- Create unit-test targets per domain.
- Move fixtures with their owner.
- Split testkit by real consumers: golden-test helpers, HTTP clients, containers, and domain
  fixtures.
- Keep end-to-end tests dependent on the runnable application and HTTP contracts.

Bazel enforcement:

- Replace recursive production globs with intentional target ownership.
- Make visibility private by default and allowlist published interfaces.
- Pilot `rules_scala` `plus-one` dependency mode on extracted targets.
- Introduce strict-dependency warnings, fix declarations, then promote to errors where practical.
- Add Bazel-query checks for forbidden dependency paths.
- Ensure internal targets never depend on `//modules/webapi:webapi`.

Completion:

- Repeat the Phase 0 measurement matrix.
- Verify unrelated domain changes compile, cache, and test independently.
- Run unit, integration, end-to-end, image, and deployment-equivalent checks.
- Turn the aggregate production target into assembly/compatibility only, or retire it.
- Turn the monolithic test target into a `test_suite` or retire it.
- Remove compatibility shims and temporary visibility exceptions.

## Parallelisation summary

| Work | May begin when | Can run alongside |
| --- | --- | --- |
| Documentation, tooling, measurements | Immediately | Each other |
| Foundation tracks | Ownership accepted | Other non-overlapping foundation tracks |
| Projects | Required foundation interfaces exist | Remaining unrelated foundation cleanup |
| Identity & Access | Projects contract stable | Data Model and Assets |
| Data Model | Projects contract stable | Identity & Access and Assets |
| Assets | Projects contract stable | Identity & Access and Data Model |
| Resources & Values | Upstream interfaces usable | Tests and HTTP-adapter work |
| Search | Data Model projection and Resources read interface usable | Project Migration and Operations |
| Project Migration | Upstream contracts usable | Search and Operations |
| Test migration | Corresponding extraction starts | Production migration |
| Strict dependency enforcement | Real targets exist | Late adapter/test cleanup |
| Aggregate retirement | All consumers migrated | Final verification |

## First implementation tranche

1. Land the context map, vocabulary, guardrails, dependency analysis, and measurements.
2. Relocate one root primitive family to an explicit owner and create the first small target.
3. Split global error ownership.
4. Introduce the Data IRI / Definition IRI distinction and begin shrinking `SmartIri`.
5. Split RDF vocabulary ownership behind compatibility facades.
6. Introduce the Projects contract.
7. Complete the Projects vertical extraction and demonstrate independent caching and testing.

## Completion criteria

- Every production source belongs to an intentional Bazel target.
- The Bazel graph is acyclic and follows the documented dependency direction.
- Projects is independent of Identity & Access.
- Domain implementations contain no HTTP-delivery imports.
- Context-specific RDF meaning has locality in an owner-controlled adapter.
- `messages`, `responders`, `store`, and `common` no longer define ownership.
- Only application composition chooses concrete adapters.
- Unit tests are scheduled by domain.
- Strict dependencies and visibility prevent regression.
- Existing VRE behaviour and delivery artifacts remain compatible.
- Measurements show useful compilation and cache isolation.

## Non-goals

- Turning dsp-api into microservices.
- Redesigning the public HTTP surface.
- Moving archival responsibility into dsp-api.
- Rewriting all legacy code before extracting the first target.
- Creating per-file or per-technical-role Bazel targets.
- Freezing Search's internal design before the retrieval direction is understood.
- Renaming every legacy code symbol as part of this initiative.
