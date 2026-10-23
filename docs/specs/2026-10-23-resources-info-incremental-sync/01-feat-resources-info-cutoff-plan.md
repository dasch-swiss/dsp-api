---
title: "feat: Incremental sync on /v2/resources/info: cutoff parameter and project-wide listing"
type: feat
date: 2026-10-23
author: "Balduin Landolt"
status: reviewed
repositories: []
linear: DEV-7632
---

# feat: Incremental sync on /v2/resources/info: cutoff parameter and project-wide listing

## Overview

`GET /v2/resources/info` lists the resources of one class in one project with their creation, last-modification
and delete dates. An external sync client (MLS, project 0807) wants to poll once a minute for "what changed since
my last sync" and then fetch those IRIs via `POST /v2/resources/batch`. Today that takes one call per class, and
each call returns every resource of the class.

Two additions:

1. Optional `cutoff` query parameter: return only resources whose last modification date (falling back to the
   creation date) is **at or after** the cutoff.
2. `resourceClass` becomes optional: without it, the call lists resources of all classes in the project named by
   the `X-Knora-Accept-Project` header. Each entry says which class it has.

## Decisions (agreed 2026-10-23)

- **At-or-after (`>=`), not strictly after.** The API stamps a write before the SPARQL update commits, so a write
  stamped just before a poll can commit after it; with `>` it would be missed for good. OAI-PMH's `from` is
  inclusive for the same reason (<https://www.openarchives.org/OAI/openarchivesprotocol.html>, sec. 2.7.1).
- **Effective date** = `lastModificationDate`, else `creationDate` (creation counts as modification). This is
  the date the response already reports as `lastModificationDate`.
- **Server time anchors the next poll.** The response gains `asOf`: the server's clock, read before the query
  runs. Clients use `cutoff = previous asOf - safety window` (recommended window: 60 s, above the
  stamp-to-commit time of long updates). Unlike "newest date seen", it advances during idle periods and never
  depends on the client's clock. Precedent: OAI-PMH `responseDate`, Salesforce `getUpdated`'s
  `latestDateCovered`. Additive field on `ListResponseDto`.
- **Timestamp format:** ISO-8601 instant with an offset (e.g. `2026-10-23T08:00:00Z`; fractional seconds
  allowed), decoded by tapir's `Instant` codec. A value without an offset, or a date only, is a 400 (tapir's
  standard decode-failure response, the same as every other malformed query parameter). Clients should use `Z`;
  a `+` offset must be URL-encoded (`%2B`).
- **Both optional, independently.** A project-wide call without a cutoff is allowed (it is the initial-sync call;
  5–6 s for 42k resources on stage). No paging: see DEV-7543.
- **`resourceClass` in every entry.** Each `ResourceInfoDto` gains `resourceClass`, the complex-schema external
  IRI. Always present, also when a class was requested: an additive JSON field.
- **Input class handling unchanged.** `resourceClass` becomes `query[Option[IriDto]]` (the convention of the
  sibling `ResourcesEndpoints`), and the rest service keeps converting it with `IriConverter.asInternalIri`, so
  every input accepted today stays accepted (no switch to the stricter `asResourceClassIriApiV2Complex`).
- **Known gaps, documented, not fixed:** erased (hard-deleted) resources leave no trace and never appear, so a
  client that must notice them needs a periodic full listing to compare against; ontology changes don't bump
  resource dates; a write that carries a client-supplied date (a custom resource `creationDate` or
  `valueCreationDate`, as dsp-tools imports may send) stamps the resource with that date
  (`CreateResourceV2Handler.scala:226`, `ValuesResponderV2.scala:436-439` feeding `InsertValueQueryBuilder.scala:191`),
  so a back-dated write is invisible to any cutoff after its date. Fixing this would need a separate server-side
  change timestamp. The endpoint stays public and returns IRIs of resources the caller may not be allowed to see,
  as it already does per class.

## Measurements (stage, project 0807, 42,040 resources, `dsp vre sparql query -s stage`)

Wall time includes about 0.1 s of client overhead; each query ran 2–3 times.

| Query | Rows | Wall time |
| --- | --- | --- |
| Project-wide via `attachedToProject`, with cutoff | 3 | 0.76–0.94 s |
| Project-wide via `GRAPH <http://www.knora.org/data/0807/mls>`, with cutoff | 3 | 0.77–1.15 s |
| Project-wide via `attachedToProject`, no cutoff | 42,040 | 4.3–5.4 s |
| Project-wide via `GRAPH`, no cutoff | 42,040 | 5.2–6.0 s |

- **Graph scoping buys nothing here.** `docs/development/dsp-api-sparql-queries.md` recommends
  `GRAPH <projectDataGraph>` for project-wide reads, but the cost is in the per-resource lookups, not in finding
  the project's resources. Keep `attachedToProject`, which also spares resolving the project's data graph.
- **One row per resource.** Adding `?resource a ?resourceClass` kept the row count equal to the resource count
  on 0807, so resources have a single `rdf:type`.
- **Every poll scans the project.** The date filter can't use an index; fine at one poll per minute.

## Technical Considerations

- **Code locations.**
    - Endpoint, DTO and rest service: `modules/webapi/src/main/scala/org/knora/webapi/slice/api/v2/resources/info/`.
    - Repo: `slice/resources/domain/ResourceInfoRepo.scala` and `slice/resources/repo/ResourceInfoRepoLive.scala`.
    - Domain model: `slice/resources/domain/ResourceInfo.scala`.
    - Fake: `modules/webapi/src/test/scala/org/knora/webapi/slice/resources/repo/ResourceInfoRepoFake.scala`.
    - Nothing outside the `info` package calls the repo method, `ResourceInfo` or the rest service; there is only
      wiring in `ApiV2Module`, `ResourcesModule` and `ApiV2ServerEndpoints`.
- **SPARQL: one `sparql"..."` template.** The current query uses `s"..."` interpolation, which the repo forbids.
  Replace it with a query object in `slice/resources/repo/` that holds a single template, with the optional parts
  as holes (`opt.whenSome(x => sparql"...")`). Follow the repo's one-template convention: no local `Fragment`
  vals or defs, and `PREFIX` lines written out (`docs/development/dsp-api-sparql-queries.md`). Live models:
  `GetResourcesByClassInProjectPrequery.scala`, and `GetResourceValueVersionHistoryQuery.scala` for a `>=` date
  filter (`Literal.dateTime(instant)` renders a typed `xsd:dateTime`; no new hole type is needed).
- **Query shape.**
    - The `WHERE` block starts with `${resourceClass.whenSome(c => sparql"VALUES ?resourceClass { $c }")}`.
      `VALUES` goes first, never mid-block: the most selective pattern leads ("Selective patterns first" in
      `dsp-api-sparql-queries.md`), and a mid-block `VALUES` splits the pattern group (seen in Gravsearch).
    - Then `?resource a ?resourceClass ; knora-base:attachedToProject <p> ; knora-base:creationDate ?creationDate ;
      knora-base:isDeleted ?isDeleted`. `creationDate` has `subjectClassConstraint knora-base:Resource`, so
      without `VALUES` this selects resources only, with no class filter or inference.
    - Then the two `OPTIONAL`s, then the cutoff
      `FILTER(COALESCE(?lastModificationDate, ?creationDate) >= ${Literal.dateTime(cutoff)})` as the last element.
      A filter splits the pattern group where its variables are bound
      (`docs/development/dsp-api-fuseki-query-execution.md`), so it must come after both dates.
    - Keep `SELECT DISTINCT`. Class matching stays exact (no subclasses).
- **Row parsing.** Replace `ResourceIri.unsafeFrom` in the row mapping with `ResourceIri.from`, failing with
  `InconsistentRepositoryDataException` (`docs/development/dsp-api-iri-handling.md`); same for the class IRI.
- **Class IRI output.** The repo returns internal class IRIs. The rest service converts each *distinct* class
  once with `IriConverter.asExternalIri` (a project-wide call can return 42k rows over a handful of classes) and
  passes the external IRI into `ResourceInfoDto.from`. A conversion failure comes from stored data: map it to
  `InconsistentRepositoryDataException`, never a 400.
- **Sorting** stays in memory in `ResourceInfoRestService`, with the resource IRI as tiebreaker so equal dates
  come back in a stable order.
- **`asOf`** is read from ZIO `Clock` in the rest service before the repo call, so tests can control it.
- **What bumps `lastModificationDate`.** Every resource-level write sets it, as the code shows: value create,
  update, delete, permission change and reorder; link create, delete, target change and metadata change;
  resource metadata and authorship change; soft delete (set to the delete date, `DeleteResourceQuery.scala:47`).
  The E2E test below proves the create and delete paths end to end.
- **Unknown project** keeps returning an empty list (current behaviour).
- **OpenAPI.** `Option` query parameters are emitted as not required. The dsp-app OpenAPI spec is bot-synced from
  the dev server; don't hand-edit it, and mention the additive fields in the PR body.
- **Test data.** Use the existing `anything` (0001) data; add no shared test data. 0001 has resources of several
  classes (`anything:Thing`, `ThingPicture`, `BlueThing`, ...); most have no `lastModificationDate`; deleted ones
  include `http://rdfh.ch/0001/2qMtTWvVRXWMBcRNlduvCQ`. The E2E test creates and deletes its own resource rather
  than mutating shared ones.
- **E2E harness.** `E2EZSpec` runs the API in the test's JVM with the live clock. Requests with a project header
  follow `ResourcesRouteV2E2ESpec` (`TestApiClient.getJson[A](uri, _.header(...))`). Golden-file rewrite mode
  can't write from the Bazel sandbox: write expected output by hand. Locally, a stack already running on port
  3333 clashes with the E2E server's fixed port: stop it or override the test's API port (mechanism unverified —
  confirm during implementation).

## Acceptance Criteria

- `GET /v2/resources/info` with only the project header returns resources of all classes in the project, each
  with `resourceClass`.
- With `cutoff`, only resources whose effective date is at or after it are returned, deleted ones included.
- Every response carries `asOf`; responses for a class-restricted call without cutoff are unchanged apart from
  the two additive fields.
- Malformed `resourceClass` or `cutoff` is a 400; an unknown project or class gives an empty list.
- The query is a `sparql"..."` template with a golden test; no `s"..."` SPARQL remains in `ResourceInfoRepoLive`.
- The docs describe the endpoint and the client polling protocol, including the gaps.

## Implementation Phases

### Phases

| Phase | Status |
| --- | --- |
| 1: Query, repo, service, endpoint | pending |
| 2: E2E test and docs | pending |

#### Phase 1: Query, repo, service, endpoint

- [ ] Add `resourceClass: InternalIri` to `ResourceInfo`; update its `apply` overloads and all construction sites
- [ ] Replace `ResourceInfoRepo.findByProjectAndResourceClass` with
  `find(projectIri: ProjectIri, resourceClass: Option[InternalIri], cutoff: Option[Instant]): Task[List[ResourceInfo]]`
- [ ] New query object in `slice/resources/repo/` with the single template described under Query shape;
  `ResourceInfoRepoLive` uses it; the `s"..."` query is deleted; row parsing uses `from` plus
  `InconsistentRepositoryDataException`
- [ ] Golden spec for the query object covering the four combinations (class or not, cutoff or not), asserting
  `VALUES` first, the `FILTER` last, and the cutoff as a typed `xsd:dateTime` literal with fractional seconds kept
- [ ] Re-time the class-restricted call on stage, old query against the new rendered query, for one class of
  0807 (`dsp vre sparql query -s stage`, read-only); record both times in the journal; if the new one is
  clearly slower, fall back to `?resource a <C>` plus `BIND(<C> AS ?resourceClass)` and record why
- [ ] Update `ResourceInfoRepoFake` to the new signature: filter on class when given, and on cutoff with the
  same effective date as the query (`lastModificationDate.getOrElse(creationDate) >= cutoff`)
- [ ] `ResourceInfoDto` gains `resourceClass: IRI`; `ListResponseDto` gains `asOf: Instant`
- [ ] `ResourceInfoRestService`: one method taking `(ProjectIri, Option[IriDto], Option[Instant], Order, OrderBy)`;
  reads `asOf` from `Clock` first; converts the class in with `asInternalIri` (failure: `BadRequestException`) and
  each distinct class out with `asExternalIri` (failure: `InconsistentRepositoryDataException`); sorts with the
  IRI as tiebreaker; Scaladoc states the at-or-after semantics and the effective date
- [ ] `ResourceInfoEndpoints`: `resourceClass` becomes `query[Option[IriDto]]`; add `query[Option[Instant]]("cutoff")`
  with a description (at-or-after, ISO-8601 with offset, effective date); update the endpoint description
- [ ] `ResourceInfoServerEndpoints` wired to the new method
- [ ] `ResourceInfoRestServiceSpec`: existing cases adapted, plus:
    - no class: all classes returned, each with its external `resourceClass`
    - cutoff excludes resources whose effective date is older
    - cutoff exactly equal to a resource's effective date includes it
    - a resource with no `lastModificationDate` is judged by its creation date
    - `lastModificationDate` at or after the cutoff with creation before it: included; both before it: excluded
    - equal dates come back ordered by IRI
    - `asOf` equals the `TestClock` time
    - use resources with distinct, non-alphabetical dates so ordering assertions can't pass by accident
- [ ] Run `bazel test //modules/webapi:test`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 2: E2E test and docs

- [ ] New E2E spec `ResourcesInfoE2ESpec` under `modules/test-e2e/src/test/scala/org/knora/webapi/e2e/v2/`, against
  project 0001:
    - a project-wide call returns entries of more than one class, each with `resourceClass` set, and at least as
      many entries as a single-class call
    - a single-class call returns only that class
    - a cutoff in the future returns an empty list
    - create a resource, take its `lastModificationDate` (effective date) from a listing, poll with exactly that
      instant as cutoff: it is listed
    - add a value to that resource, poll with a cutoff equal to the date from before the write: listed, with a
      newer `lastModificationDate`
    - delete that resource (soft delete), poll with a cutoff before the delete: listed with `isDeleted = true` and
      a `deleteDate`
    - `asOf` is present and not before the request was sent
    - 400 for: cutoff without offset, date-only cutoff, malformed `resourceClass`, missing `X-Knora-Accept-Project`
      (confirm the current behaviour for the header and assert it)
    - a cutoff with a URL-encoded `+02:00` offset is accepted
    - an unknown project and an unknown but well-formed class both give an empty list
- [ ] Check what `POST /v2/resources/batch` returns for a soft-deleted IRI (code or a quick E2E assertion), for the
  docs
- [ ] Run the new spec: `just docker-load-test-images`, then
  `bazel test //modules/test-e2e:test --test_filter='.*ResourcesInfoE2ESpec.*'`; it passes
- [ ] Docs: new section in `docs/03-endpoints/api-v2/reading-and-searching-resources.md`, after "Reading Resources
  by Class from a Project", for `GET /v2/resources/info`. Cover the header, parameters, response shape
  (`resourceClass`, `asOf`), at-or-after semantics and effective date, and timestamp format and precision. Add a
  "Polling for changes" subsection:
    - first sync: project-wide, no cutoff, remember `asOf`
    - each poll: `cutoff = previous asOf - 60 s`, remember the new `asOf`; overlap is deliberate, so fetching a
      resource twice must be harmless
    - entries with `isDeleted = true` are deletions; say what `POST /v2/resources/batch` returns for them
    - fetch changed IRIs with `POST /v2/resources/batch`
    - gaps: erased resources never appear (compare a full listing now and then), ontology changes don't count,
      back-dated writes (client-supplied creation dates, e.g. imports) are missed, no permission filtering, each
      poll scans the whole project, no paging (large first sync)
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Out of scope

- Detecting erased resources (would need a tombstone or change log).
- Detecting back-dated writes (would need a server-side change timestamp separate from `lastModificationDate`).
- Permission filtering on the endpoint.
- Paging or keyset cursors (DEV-7543).
