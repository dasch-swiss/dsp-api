---
title: "feat: FAIR metadata and Signposting on the resource pages ARKs resolve to"
type: feat
date: 2026-10-08
author: "Raitis Veinbahs"
status: reviewed
repository: dasch-swiss/dsp-api
repositories:
  - name: dsp-api
    path: /Users/raitisveinbahs/work/2-dsp-api
  - name: dsp-app
    path: /Users/raitisveinbahs/work/2-dsp-app
prd: 01-resource-fair-landing-pages-PRD.md
linear: DEV-7420
---

# feat: FAIR metadata and Signposting on the resource pages ARKs resolve to

## Overview

Resource and value ARKs resolve to dsp-app's `/resource/{project}/{resource}`,
a client-rendered shell with no machine-readable metadata. This plan makes that
route carry the metadata the DPE project page carries:

- dsp-app's nginx forwards the route to a new dsp-api endpoint.
- That endpoint returns dsp-app's own `index.html`, with schema.org JSON-LD,
  Dublin Core `<meta>` and Signposting `<link>` elements spliced into `<head>`.
- The response also carries a `Link` header and `Vary: Accept`, or is the one
  `303` to a representation.
- dsp-api serves two representations, JSON-LD and DataCite JSON, on the API
  host.
- All facts are read as the anonymous user.
- If dsp-api fails, nginx serves the plain shell.

The PRD owns the *what*; where this plan and the PRD disagree, the PRD wins.
dsp-api is the primary repository and its PR carries Phases 1–5 and 7. dsp-app's
PR carries Phase 6. Phase 7 runs last because it needs both repositories' changes.

## Goal graph

Every point of this plan has a parent, written `↑<id>`. Following parents from
any point reaches one of the two top goals, **G0** or **G6**. The graph is
acyclic.

```
G0 ─► G1..G4 (PRD) ─► PG1..PG6 (plan goals) ─► S1..S7 (phase subgoals)
                                                ├─► TD1..TD19 (decisions) ─► deliverables
                                                ├─► deliverables
                                                └─► P, L, C, N, H, A, R, M, F (supporting points)
G6 ─► K1, K2, K3, deliverables 1.1 and 5.1
```

Each phase's closing *Phase review* checkbox is a fixed structural line that
cannot carry a tag. Its parent is that phase's subgoal.

### Top goals and PRD goals (from `01-resource-fair-landing-pages-PRD.md`)

- **G0**: Every page a DaSCH resource or value ARK resolves to is FAIR-assessable by machine.
  - **G1** ↑G0: Machines read metadata and Signposting without JavaScript.
  - **G2** ↑G0: Nothing beyond the anonymous view is published.
  - **G3** ↑G0: The F-UJI score of a resource ARK rises measurably.
  - **G4** ↑G0: A person's page is unchanged.
- **G6**: The changes are maintainable within each repository's conventions.

### Plan goals

| Id | Parent | Plan goal | Requirements |
|----|--------|-----------|--------------|
| PG1 | ↑G1 | A visible resource's landing response embeds one JSON-LD graph, Dublin Core `<meta>` tags and `<link>` elements in the served `<head>` | REQ-1.1–1.6 |
| PG2 | ↑G1 | A visible resource's landing response carries the Signposting `Link` header, identical on `GET` and `HEAD` | REQ-2.1–2.6 |
| PG3 | ↑G1 | JSON-LD and DataCite JSON are served at their own URLs on the API host. The landing route reaches them by the one `303`, and every landing response carries `Vary: Accept` | REQ-3.1–3.6 |
| PG4 | ↑G2 | Every fact comes from the anonymous view. A missing, deleted or not-visible resource yields nothing, and a file is advertised only under V or higher | REQ-4.1–4.5 |
| PG5 | ↑G4 | A person always gets the deployed dsp-app shell, unchanged except for the added `<head>` content, also when dsp-api fails or lacks the route | REQ-5.1–5.2 |
| PG6 | ↑G3 | The F-UJI score of resource pages of 0868 and 0803 is measured before and after on the same DEV targets and recorded, and FAIR Champion is run. Before ship, a local run on one stack shows it rising | REQ-6.1 |

### Phase subgoals

| Id | Parent | Phase | Subgoal |
|----|--------|-------|---------|
| S1 | ↑PG1, PG3, PG4 | 1 | One builder turns a route's shortcode and resource id into the resource's facts under the anonymous view, or into nothing |
| S2 | ↑PG1, PG3 | 2 | Pure writers render the facts as schema.org JSON-LD, Dublin Core meta tags and DataCite JSON that agree with each other |
| S3 | ↑PG2, PG3 | 3 | Pure functions produce the Signposting link set, its RFC 8288 serialisation and the `Accept` decision |
| S4 | ↑PG1, PG2, PG3, PG4, PG5 | 4 | HTTP endpoints serve the landing response and the two representations from the facts, writers and link set |
| S5 | ↑PG6 | 5 | The baseline is measured and recorded |
| S6 | ↑PG5, PG1, PG2, PG3 | 6 | dsp-app's nginx forwards the route to dsp-api and falls back to its own shell |
| S7 | ↑PG6 | 7 | A local F-UJI run of the same page, before and after the change, shows the score rising |

## Problem Statement / Motivation

↑G0. See the PRD's Context. Project ARKs land on a DPE page that F-UJI can
assess (16–21 of 24). Resource ARKs land on an SPA shell that is the same for
every route and every `Accept`, and it scores near the bottom.

## Proposed Solution

```
ARK resolver ──302──► app.<host>/resource/{sc}/{id}?…
                         │ dsp-app nginx, location for the route                       (S6)
                         ├─ proxy (GET/HEAD) ──► dsp-api /fair/resources/{sc}/{id}?…   (S4)
                         │                        ├─ facts: anonymous read              (S1)
                         │                        ├─ visible, Accept prefers a representation: 303 + Vary (S3)
                         │                        ├─ shell: <app internal>/index.html   (TD14)
                         │                        ├─ visible:   shell + head + Link + Vary (S2, S3)
                         │                        └─ otherwise: shell unchanged + Vary  (TD5)
                         └─ on any 4xx / 5xx / timeout / unreachable / no upstream: local index.html + Vary (N3)

api.<host>/fair/resources/{sc}/{id}/metadata.jsonld          → application/ld+json                     (S4)
api.<host>/fair/resources/{sc}/{id}/metadata.datacite.json   → application/vnd.datacite.datacite+json  (S4)
```

- **P1** ↑S1, S2, S3, S4: dsp-api gets a new `fair` slice. Parity with DPE is
  by contract. The shapes are those of `machine-readable-metadata.md` and
  `shared-fair`, re-implemented in Scala. `shared-fair` has no record-level
  JSON-LD, link-set or meta writer (every exported writer there takes a
  project), so the resource writers adapt the project writers' shapes and the
  record DataCite and Dublin Core mappings.
- **P2** ↑S6: dsp-app changes only its nginx configuration and `Dockerfile`.
- **P3** ↑S4, S6: Deployment needs two sets of environment variables in
  ops-deploy, set after ship (H1).

### Slice layout

- **L1** ↑S1, G6: `slice/fair/ports/` holds one ADR-0011 port per foreign
  context the builder reads: resources and values, projects (legal defaults,
  custom licences, longname), ontology (class label) and assets (the sidecar).
  Each port is a trait returning fair's own boundary DTOs, never
  `ReadResourceV2`. Its `<Port>Live` adapter lives in the provider's `repo` or
  `domain/service` package, and its `<Port>InMemory` double in fair's tests.
  These are the first ports in dsp-api; there is no existing one to copy.
- **L2** ↑S1, S2, S3: `slice/fair/domain/` holds `ResourceFacts` and pure
  objects with no layer: a `ResourceFacts.derive(inputs): Option[ResourceFacts]`
  carrying the visibility, file, access, licence and creator rules, and the
  writers, link set and negotiation. The effectful
  `slice/fair/domain/service/ResourceFactsBuilder` (a `final class` with
  `ZLayer.derive`) only gathers inputs through the ports and calls `derive`, so
  the rule tests of Phase 1 are plain value tests.
- **L3** ↑S4: `slice/fair/api/` holds `FairEndpoints`, `FairServerEndpoints`
  and `FairRestService`, and a `FairApiModule` with `Dependencies` and
  `Provided` as `ExportApiModule` has.

## Technical Decisions

Each decision sits within the PRD and has a subgoal or plan goal as its parent.
Deliverables that implement a decision have that decision as their parent.

- **TD1** ↑S4: **Route paths.** The landing route is `GET`/`HEAD`
  `/fair/resources/{shortcode}/{resourceId}`, with the query string ignored
  (tapir ignores undeclared query parameters). The representations are
  `…/{resourceId}/metadata.jsonld` and `…/metadata.datacite.json`. The new
  prefix is needed because `/v2/resources/{iris…}` captures the rest of the
  path; nothing captures `/fair/`. Path segments are read as `path[String]` and
  parsed in the service, so a malformed id never becomes a tapir decode failure.
- **TD2** ↑S1: **Canonical identifiers only.** Every emitted URL is built from
  the resolved resource's IRI and the configured base URLs, never from the
  request path or the `Host` header (which is `api:3333` behind nginx). That
  covers the page URL, the representation URLs, `cite-as`, `Location` and the
  OAI links. The page URL is
  `{appExternalBaseUrl}/resource/{canonical shortcode}/{canonical id}`.
- **TD3** ↑S1: **Shortcode case.** The route's shortcode is matched
  case-insensitively (uppercased before the IRI is built), as DPE does.
  `ResourceIri.from` keeps the original casing in `.value`, so the IRI is always
  built from the uppercased shortcode, never compared to a request-cased one.
- **TD4** ↑S1: **Malformed ids** are treated as missing. The landing route
  returns the unchanged shell, and the representations return the same empty
  `404 text/plain`. Never a `400`.
- **TD5** ↑S1: **Visible** means the resource is present in a read as
  `AnonymousUser` with `withDeleted = false` (the default is `true`, so it is
  passed explicitly), and its `userPermission` is at least RV. The read uses
  `skipRetrievalChecks = false` and collapses `NotFoundException` and
  `ForbiddenException` into absent, so missing and forbidden take the same path
  without the error log `skipRetrievalChecks = true` writes for every miss. Any
  other failure stays a failure (TD14), never absent. Every fact is taken from
  the anonymous view as returned; a value anonymous cannot see is absent.
- **TD6** ↑S1: **File.** A resource has at most one file value. The selection
  keeps the `ReadValueV2`, not only its content, and considers file values
  only. It is advertised only when that value's anonymous `userPermission` is
  at least V. Its facts come from the export's `FileLink` rules: the ingest
  `/original` URL, plus MIME type, file name and size from the sidecar, each
  only when recorded. An external IIIF still image
  (`StillImageExternalFileValueContentV2`) sets the type but yields no
  `DataDownload`.
- **TD7** ↑S1: **Access level.** Full Open Access when the resource is at least
  V and either there is no file or the file is at least V. Otherwise Open
  Access with Restrictions. The COAR URIs are DPE's:
  `http://purl.org/coar/access_right/c_abf2` and
  `http://purl.org/coar/access_right/c_16ec`.
- **TD8** ↑S1: **Licence.** The chain is the file's `licenseIri`, then the
  project's `dataLicense`, then none.
  - `PLACEHOLDER` counts as none and falls through. It is the only value that
    falls through.
  - `UNKNOWN`, `AI_GENERATED` and `PUBLIC_DOMAIN` give the label only, in
    DataCite `rights`. They emit no schema.org `license` and no `license` link,
    and do not fall back.
  - A licence IRI is resolved against the built-in licence constants
    (`License.BUILT_IN`) and the project's custom licences, never against the
    project's enabled list. One that resolves to neither yields no licence and
    does not fall back.
  - The chain yields at most one licence, so the `license` link appears exactly
    when it yields a licence with a URI.
  - SPDX `rightsIdentifier` and its label come only from an explicit table of
    the Creative Commons entries, as DPE's id-to-label table.
- **TD9** ↑S1: **Creators.** The chain is `resourceAuthorship` plus the visible
  file value's authorship, deduplicated in first-seen order. Failing that, the
  project's `defaultDataAuthorship`. Failing that, the project as an
  organisation (its `longname`, else its `shortname`, `nameType`
  `Organizational`), in DataCite only.
  - `attachedToUser` is never used.
  - There is no `author` link, and `prov:wasAttributedTo` names DaSCH only.
  - The project's legal defaults are part of the public
    `/admin/projects/shortcode/…` response (`publicEndpoint`), so using them
    stays within G2.
  - The fallback is rendered into the metadata only; nothing is stored on the
    resource. This keeps the legal-metadata PRD (v4, 2026-06-19) decision that
    the project default is "display-only; the default is not asserted on the
    resource".
- **TD10** ↑S1: **Description.** The export's per-project property (0803, 081C,
  0868, 1612; a hard-coded table in `findDescriptionProperty`), read from the
  anonymous view and taken as the value's string form. Other projects get none.
- **TD11** ↑S1: **Type.** `typeOfData` as the export derives it from the
  selected file value, and `Dataset` when there is none.
  - DataCite `resourceType` names the RDF class: its `rdfs:label` in English,
    else the label with the lexicographically first language tag, else the
    class IRI.
  - schema.org `additionalType` is the class IRI in the complex schema.
- **TD12** ↑S3: **Negotiation** is a port of `shared-fair`'s `negotiate`
  decision table (deliverable 3.5). A redirect is a `303` with `Location` set to
  the absolute representation URL on the API host. `Accept` is read as
  `header[List[String]]` and the lines joined with `,`, because a repeated
  header would otherwise be a `400`.
- **TD13** ↑S4: **406 check.** dsp-api's `PassthroughAwareNotAcceptableInterceptor`
  answers an empty `406` before endpoint code runs when `Accept` matches none
  of an endpoint's declared media types. The three new endpoints must decide
  for themselves: the landing route by negotiation, and a representation by
  always answering its own type. So they carry a new opt-in attribute,
  `NegotiatesOwnAccept` (an `AttributeKey` on a dedicated case object in
  `slice/common/api`), and `leavesNegotiationToTheStore` exempts an endpoint
  that carries either it or the SPARQL `routeMarker`. The SPARQL `routeMarker`
  is not reused, because it also switches on the passthrough audit log and
  `Connection: close`.
- **TD14** ↑S4: **Shell.** It is fetched only when the answer is HTML, that is
  after negotiation, so a `303` never depends on it. It is fetched from
  `{appShellUrl}`, an internal URL ending in `/index.html`, through the existing
  `TracingHttpClient` (sttp client4), with `Accept-Encoding: identity`, a
  connect and read timeout of 500 ms each, and an outer bound of 1 s. It is
  cached in a zio-cache of capacity 1 whose lookup never fails: a success is
  kept for 10 s, a failure (non-200, timeout, or no `</head>`) for 2 s. The
  block is spliced before the first `</head>`. A shell failure on an HTML
  answer gives `502`, and nginx then serves its own shell. The 10 s TTL bounds
  R1.
- **TD15** ↑S4: **Landing headers** on every answer: `Vary: Accept` and
  `Cache-Control: no-store`; the HTML answers carry
  `Content-Type: text/html; charset=UTF-8` (as `htmlBodyUtf8` sets it).
  Representations carry `Link: <page>; rel="describes"`.
- **TD16** ↑S4: **Credentials are ignored.** The endpoints are built on a base
  with no security input at all, so they cannot read a token, and a request
  with an admin token gets the same bytes as an anonymous one. The base follows
  `V3BaseEndpoint.public`: `RequiresMethod(endpoint.errorOut(fairErrors), …)`
  with fair's own error type, not `BaseEndpoints.publicEndpoint`, whose shared
  error outputs serialise exception messages.
- **TD17** ↑S2, S3: **Escaping.**
  - JSON-LD goes into the page only through a script-safe serialiser, a
    post-pass over zio-json's output (which escapes none of these).
  - Attributes are HTML-escaped.
  - `Link` hrefs come only from IRIs and ARKs, and are percent-encoded for
    `<>";,` and space at serialisation only, as DPE's `signposting.rs`.
  - A header value the HTTP layer would reject is detected with
    `sttp.model.Header.safeApply` in the rest service and dropped with a
    warning, and the page is still served.
- **TD18** ↑S3: **OAI links** for configured shortcodes:
  `{dpeOaiBaseUrl}?verb=GetRecord&identifier=oai:dasch.swiss:ark:/72163/1/{sc}/{arkId}&metadataPrefix=oai_datacite`
  and the same with `oai_dc`, typed `application/xml`. They are never a `303`
  target. The records they point to carry the export's hard-coded facts until
  a follow-up fixes the export (5.3).
- **TD19** ↑PG5: **No caching of landing answers.** `Cache-Control: no-store` on
  every landing answer, matching dsp-app's `index.html`, so no cache replays a
  `303` to a person. DPE sends none; this is the one header that differs.

### Configuration (dsp-api `AppConfig`, `application.conf`)

A `FairConfig` section in `AppConfig`, projected in `projectAppConfigurations`
so services depend on `FairConfig` alone.

| Id | Parent | Key | Env | Example (prod) |
|----|--------|-----|-----|----------------|
| C1 | ↑TD2 | `fair.app-external-base-url` | `KNORA_WEBAPI_FAIR_APP_EXTERNAL_BASE_URL` | `https://app.dasch.swiss` |
| C2 | ↑TD14 | `fair.app-shell-url` | `KNORA_WEBAPI_FAIR_APP_SHELL_URL` | `http://app/index.html` (dsp-app listens on 80 in ops-deploy; `http://app:4200/index.html` in the local `docker-compose.yml`) |
| C3 | ↑TD18 | `fair.dpe-oai-base-url` | `KNORA_WEBAPI_FAIR_DPE_OAI_BASE_URL` | `https://repository.dasch.swiss/dpe/oai` |
| C4 | ↑TD18 | `fair.dpe-oai-shortcodes` | `KNORA_WEBAPI_FAIR_DPE_OAI_SHORTCODES` | `0803,081C,0868` |

- **C5** ↑TD1, TD2: Representation URLs use the existing
  `KnoraApi.externalKnoraApiBaseUrl`, and `cite-as` uses
  `StringFormatter.resourceIriToArkUrl` with no timestamp (unversioned).
- **C6** ↑TD18: C4 arrives from the environment as one string. It is modelled
  as a `String`, split on `,`, uppercased into a set and validated, and defaults
  to empty, so no OAI link is emitted until H1 sets it.
- **C7** ↑TD2, TD14: C1 and C2 have local defaults in `application.conf`.
  Startup validation fails when C1 is not an absolute `http(s)` URL. A wrong C2
  only makes HTML answers `502`, which nginx turns into the plain shell.

### dsp-app nginx

- **N1** ↑S6: A top-level regex location, placed as the first regex location
  in `nginx/default.conf.template` (regex locations match in order, and the
  existing unanchored `~ /index.html|.*\.json$` would otherwise catch some ids).
  It is not a `^~ /resource/` prefix: that would stop the server-level regex
  locations from applying to every other `/resource/` path. The regex is quoted
  (an unquoted `{4}` fails to parse) and uses named captures, because the
  method check's regex `if` overwrites `$1`/`$2`:
  `location ~ "^/resource/(?<landing_shortcode>[0-9A-Fa-f]{4})/(?<landing_id>[A-Za-z0-9_=-]+)/?$"`.
  It proxies `GET`/`HEAD` to
  `$dsp_api_upstream/fair/resources/$landing_shortcode/$landing_id$is_args$args`.
  Other methods get `405`. The upstream is a variable,
  `set $dsp_api_upstream "${DSP_API_UPSTREAM}"`, filled by the image's envsubst
  of `/etc/nginx/templates`. A variable `proxy_pass` needs
  `resolver 127.0.0.11 valid=10s ipv6=off;` and `resolver_timeout 2s;` at server
  level; the resolver is consulted only when the variable is set. Anything else
  under `/resource/` keeps today's SPA behaviour. Location matching runs on the
  decoded, normalised URI, and the id class excludes `.`, `/` and `%`, so
  captures are always safe.
- **N2** ↑S6: Proxy settings:
  - `proxy_connect_timeout`, `proxy_send_timeout` and `proxy_read_timeout`
    of 2 s;
  - `proxy_set_header Accept-Encoding ""`, `Cookie ""` and
    `Authorization ""`, so no credential reaches dsp-api;
  - `proxy_redirect off`;
  - `proxy_intercept_errors on` with
    `error_page 400 401 403 404 405 406 408 413 418 429 500 502 503 504 = @shell`;
  - `include /etc/nginx/security-headers.conf`, so proxied answers keep the
    security headers.

  The landing route never answers `404` by design, so a `404` can only mean
  that dsp-api lacks the route. The fallback covers that, which makes the
  deploy order of the two repositories irrelevant. Errors nginx generates
  itself (connect refused, timeouts, an unresolvable name) go through the same
  `error_page`. `303` and 2xx responses pass through with their `Link`, `Vary`
  and `Location`.
- **N3** ↑S6: `@shell` (a server-level named location) serves
  `try_files /index.html =500` from `root /public`, with
  `add_header Vary Accept always`, `add_header Cache-Control 'no-store' always`
  and its own `include` of the security headers, since it inherits no
  `add_header`. The `=` in `error_page` makes its status `200`.
- **N4** ↑S6: When `DSP_API_UPSTREAM` is empty,
  `if ($dsp_api_upstream = "") { return 418; }` sends the request to `@shell`
  through the `418` entry, so a deployment without the variable (Cloud Run PR
  previews, `nx serve`) behaves as today.

### Repository conventions

- **K1** ↑G6: dsp-api `CONVENTIONS.md` binds every phase:
  - `final class` services with `ZLayer.derive`;
  - the `*Endpoints` / `*ServerEndpoints` / `*RestService` split;
  - per-endpoint error variants, never the shared `errorOutputs`;
  - ASCII-only production code (the `…` of the description cut is written as
    a Unicode escape), methods of at most 50 lines, SPDX headers;
  - no REQ ids or plan references in comments;
  - no "Knora" in human-readable text;
  - specs as `@RunWith(classOf[DspZTestJUnitRunner]) class XSpec extends ZIOSpecDefault`,
    as the existing specs are.
- **K2** ↑G6: Commit messages: dsp-api `feat: <subject> (DEV-7420)`; dsp-app
  per its PR-title check, `feat(nginx): <subject> (DEV-7420)`.
- **K3** ↑G6: Within each phase, the test items are written before the
  implementation items they cover; the items are listed by topic.

## Implementation Phases

#### Phase 1: Resource facts under the anonymous view

Subgoal **S1** ↑PG1, PG3, PG4.

The tests in this phase use existing 0803 incunabula test data where it covers
the case. Otherwise they use a self-contained fixture beside the spec (as
`ExportServiceSpec-1612-*.ttl`), never additions to shared datasets: those
carry item-count assertions that break unrelated specs.

### dsp-api

- [ ] 1.1 ↑G6: Read `ARCH-MAP.md` and `docs/contexts/*/CONTEXT.md`. Create the `fair` slice per L1–L3, with the four ports of L1 and their `Live` adapters and `InMemory` doubles
- [ ] 1.2 ↑TD6, TD10, TD11: Extract the pure parts of `ExportService`'s `typeOfDataOf`, `findDescriptionProperty` and `fileLinkOf` into a pure object that both `ExportService` and `ResourceFacts.derive` use. File selection returns the `ReadValueV2`. The sidecar lookup (`findAssetInfo`) is not shared: fair reads it through its assets port. `ExportServiceSpec` and its golden output stay unchanged
- [ ] 1.3 ↑S1: Define `ResourceFacts`: canonical IRI, ARK, page URL, title, description, creators with their source, licence, dates, type of data, class IRI and label, optional file, access level, project ARK and shortcode
- [ ] 1.4 ↑TD3, TD4: Identifier parsing: case-insensitive shortcode; a malformed id gives absent
- [ ] 1.5 ↑TD5: Anonymous read through the resources port and the visibility rule
- [ ] 1.6 ↑TD2: Canonical page URL and ARK from the resolved IRI (C1, C5)
- [ ] 1.7 ↑TD6: File rule
- [ ] 1.8 ↑TD7: Access-level rule
- [ ] 1.9 ↑TD8: Licence chain, with built-in and custom-licence lookup
- [ ] 1.10 ↑TD9: Creator chain
- [ ] 1.11 ↑TD10, TD11: Title, description, dates and type
- [ ] 1.12 ↑TD4, TD5: Unit tests: missing, deleted, forbidden (no permission) and malformed all yield nothing; an infrastructure failure of the read stays a failure
- [ ] 1.13 ↑TD6, TD7: Unit tests:
  - an RV-only resource is visible, with the restricted level;
  - a V resource with an RV-only file has no file, the restricted level, and a type still taken from the file;
  - V with a V file has the file and Full Open Access;
  - no file means Full Open Access;
  - a resource with a text value and a still-image file value takes its type and file from the file value;
  - an external IIIF still image gives type `Image` and no file
- [ ] 1.14 ↑TD8: Unit tests for the licence matrix:
  - file built-in;
  - file `PLACEHOLDER`, with and without a project licence;
  - `UNKNOWN`, `AI_GENERATED`, `PUBLIC_DOMAIN`, each with a project licence that is not used;
  - a custom licence of the project;
  - a file licence IRI that resolves to nothing, with a project licence that is not used;
  - no licence
- [ ] 1.15 ↑TD9: Unit tests for the creator chain:
  - resource authorship;
  - file authorship;
  - deduplication;
  - a hidden file's authorship is ignored;
  - the project default;
  - the project as organisation, by longname and by shortname
- [ ] 1.16 ↑TD2, TD3: Unit test: a lowercase request shortcode yields the canonical page URL and ARK
- [ ] 1.17 ↑S1: Run `just check` and `just test`; both pass
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 2: Writers: JSON-LD, Dublin Core meta, DataCite JSON

Subgoal **S2** ↑PG1, PG3.

### dsp-api

- [ ] 2.1 ↑S2: JSON-LD writer, built as a zio-json `Json.Obj` from an ordered field list (not `merge`, which reorders). Keys in this order, with no key for an unrecorded fact:
  - `@context` `["https://schema.org", {"prov": "http://www.w3.org/ns/prov#"}]`;
  - `@type` `Dataset`;
  - `@id` the ARK;
  - `identifier`: the ARK as a `PropertyValue` with `propertyID: "ARK"`, then the page URL;
  - `name`, `description`;
  - `license` as `{"@id"}`;
  - `isAccessibleForFree`, `conditionsOfAccess`;
  - `datePublished`, `dateCreated`, `dateModified`;
  - `creator`;
  - `publisher` (`{"@id": "https://dasch.swiss", "@type": "Organization", "name": "DaSCH", "url": "https://dasch.swiss"}`);
  - `prov:wasAttributedTo` (`{"@id": "https://dasch.swiss"}`);
  - `url`, `isPartOf`, `additionalType`;
  - `distribution` (one `DataDownload`: `contentUrl`, then `name`, `encodingFormat`, `contentSize` as a number and `license`, each when recorded)
- [ ] 2.2 ↑TD17: Script-safe JSON serialiser: `<`, `>` and `&` become JSON escapes `\u003c`, `\u003e` and `\u0026`, and U+2028 and U+2029 become `\u2028` and `\u2029`
- [ ] 2.3 ↑S2: Dublin Core meta writer. Tags `DC.title`, `DC.creator`, `DC.publisher`, `DC.identifier`, `DC.type`, `DC.date`, `DC.description`, `DC.format`, `DC.relation`, `DC.rights`, `DC.accessRights`:
  - `DC.description` cut to 1000 characters (code points, not bytes), with `…` appended only when cut;
  - `DC.accessRights` as the COAR URI;
  - creators raw, as in `record_dublin_core.rs`
- [ ] 2.4 ↑S2: DataCite JSON writer, following `record_datacite.rs` and `datacite_json.rs`:
  - kernel key order (identifiers, types, creators, titles, publisher, publicationYear, descriptions, dates, relatedIdentifiers, rightsList, formats, sizes), with `schemaVersion` `http://datacite.org/schema/kernel-4` last;
  - ARK identifier;
  - `types` per TD11;
  - creators with fallback and `nameType`;
  - titles, publisher, `publicationYear` as a string;
  - dates Created, Updated, Available;
  - Abstract;
  - `IsPartOf` the project ARK;
  - `rightsList` per TD8;
  - `formats` and `sizes`;
  - nothing empty emitted: no `null`, no `[]`, no `{}`
- [ ] 2.5 ↑S2: Add `com.networknt:json-schema-validator` (already resolved as `1.5.9`, transitively through wiremock) as a direct test dependency: coordinate in `MODULE.bazel`'s `maven.install`, re-pin with `bazel run @unpinned_maven//:pin`, and add it to the test `deps` in `modules/webapi/BUILD.bazel`. Vendor dsp-repository's `shared/fair/testdata/schemas/datacite-4.3-schema.json` as a test resource, with a note of its origin (DataCite commit `2ade77951cf2`, patched by `download-schemas.sh`: enums widened to 4.6, `uniqueItems` removed). Validate as draft-04 with format validation off, as DPE does
- [ ] 2.6 ↑S2: Unit tests mirroring `shared-fair` where a rule is shared: identifier shape, licence as a node, no placeholder, constant publisher, key order, access properties, file shape, description cut, `isPartOf` as the project ARK, `additionalType` as the class IRI
- [ ] 2.7 ↑S2: Unit test: the DataCite JSON validates for a file-carrying resource, a resource without a file, and a resource whose creator falls back to the project
- [ ] 2.8 ↑S2: Unit test: the three writers agree on title, identifier, creators, licence, type and dates for the same facts
- [ ] 2.9 ↑TD17: Unit test: hostile text in the label and description cannot leave the `<script>` or an attribute: `</script>`, `<!--`, quotes, newlines, U+2028, non-ASCII. Plus a property test: for any string, the script-safe output contains none of the five characters and decodes back to the input
- [ ] 2.10 ↑S2: Run `just check` and `just test`; both pass
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 3: Signposting and the Accept decision

Subgoal **S3** ↑PG2, PG3.

### dsp-api

- [ ] 3.1 ↑S3: Link set, in this order:
  - `cite-as` the ARK, exactly once;
  - `type` `https://schema.org/Dataset` and `https://schema.org/AboutPage`;
  - one typed `describedby` per representation;
  - `license` only when the licence chain yields a licence with a URI;
  - no `author`
- [ ] 3.2 ↑TD18: The two OAI `describedby` links for listed shortcodes, after the representations, built from the canonical ARK id (C3, C4)
- [ ] 3.3 ↑TD17: RFC 8288 serialisation (`<href>; rel="x"`, plus `; type="mt"` when typed, joined with `, `) with percent-encoded hrefs, CR/LF left to the HTTP layer, and `<link>` elements rendered from the same set with the raw href
- [ ] 3.4 ↑S3: Representation link set: `<page>; rel="describes"`
- [ ] 3.5 ↑TD12: Port of `negotiate::decide`:
  - a header over 2048 UTF-8 bytes counts as absent (measure bytes, not `String.length`);
  - at most the first 20 raw comma-separated entries are read, empty and invalid ones included;
  - `q` is read per parameter whose untrimmed name is `q`; a repeated `q` is read to the last; a `q` that does not parse as Rust's `f64` would, or falls outside 0..1, drops that entry only;
  - HTML quality is the maximum over `text/html`, `text/*` and `*/*`, starting from 0;
  - only exact, ASCII case-insensitive candidate matches;
  - a candidate needs a strictly greater `q` than HTML, so a tie goes to HTML;
  - between candidates at equal `q`, header order decides;
  - no answer from `Accept` is ever a 4xx
- [ ] 3.6 ↑TD12: Unit tests mirroring `negotiate.rs`'s 21 tests, one for one, with `Accept: */*` among them
- [ ] 3.7 ↑S3: Unit tests mirroring `signposting.rs`, leaving out its project-only `author` cases:
  - the header string;
  - field syntax cannot forge a second link;
  - a newline is left to the HTTP layer;
  - describedby media types;
  - licence cardinality;
  - a representation describes its page;
  - the `<link>` elements carry the same relations as the header;
  - OAI links only for listed shortcodes, matching a literal identifier copied from dsp-repository's `docs/src/dpe/oai-pmh.md`
- [ ] 3.8 ↑S3: Run `just check` and `just test`; both pass
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 4: Endpoints

Subgoal **S4** ↑PG1, PG2, PG3, PG4, PG5.

The in-memory HTTP specs build the routes with
`ZioHttpInterpreter(DspApiServer.serverOptions(ctxStore)).toHttp(…)` and run
them with `runZIO`, as `SparqlPassthroughInterceptorSpec` does. `serverOptions`
is `private[core]`, so these specs live in package `org.knora.webapi.core`.
The shell is a stub string through the shell provider's interface.

### dsp-api

- [ ] 4.1 ↑TD2, TD14, TD18: Add `FairConfig` (C1–C4, C6, C7) to `AppConfig`, `projectAppConfigurations` and `application.conf`, each key with its `${?ENV}` override
- [ ] 4.2 ↑TD16: The fair endpoint base per TD16, with fair's error type: not visible maps to an empty `404 text/plain`, shell unavailable to `502`, anything else to `500` (`503` is not used)
- [ ] 4.3 ↑TD14: Shell provider: a layer built with `ZLayer.scoped` around the zio-cache, following `AssetPermissionsCache.makeCache`
- [ ] 4.4 ↑TD1: Landing `GET`. A visible resource gets the `303` or the shell with the head block (DC meta, `<link>`s, one JSON-LD `<script>`) and the `Link` header. Anything else gets the unchanged shell. A rejected `Link` or `Location` value drops the header with a warning
- [ ] 4.5 ↑TD15, TD19: Landing headers on every answer
- [ ] 4.6 ↑TD1: Landing `HEAD`, a second endpoint on the same path sharing the rest service method, with the same headers as `GET` and no body. Add `def head` to `RequiresMethod`, since tapir answers an undeclared `HEAD` with `404`
- [ ] 4.7 ↑TD1, TD4: Representation endpoints with their media types (a private `CodecFormat` each, as `V3ProjectsEndpoints` does) and the `describes` link. Missing, deleted, not visible and malformed all get the same empty `404 text/plain`
- [ ] 4.8 ↑TD13: Add `NegotiatesOwnAccept` and set it on the new endpoints. Every other endpoint negotiates exactly as before, the interceptor's existing tests stay unchanged, and the passthrough audit and `Connection: close` paths still key on `routeMarker` alone
- [ ] 4.9 ↑S4: Module wiring: `FairApiModule` in `ApiModule.Dependencies` and `ApiModule.layer`, a `FairServerEndpoints` parameter in `Endpoints`, and the ports' `Live` adapters and the shell provider in `LayersLive`
- [ ] 4.10 ↑TD12, TD15: In-memory HTTP tests:
  - the negotiation table of `machine-readable-metadata.md` end to end, with "unknown shortcode" read as "resource not visible";
  - `Vary: Accept` and `Cache-Control: no-store` on 200 and 303, for GET and HEAD;
  - HEAD carries the same `Link`, `Vary`, `Cache-Control`, `Content-Type` and `Location` as GET;
  - exactly one JSON-LD script and one `cite-as`;
  - the page carries the `DC.*` tags, `DC.accessRights` among them, and `<link>` elements matching the `Link` header;
  - for a visible resource, the body minus the spliced block equals the shell;
  - for `Accept` absent, `*/*` and `text/html`, the bodies are byte-identical
- [ ] 4.11 ↑S4, TD4, TD5, TD6: In-memory HTTP tests:
  - a not-visible landing answer is byte-identical to the shell and has no `Link`;
  - the four not-visible representation answers are identical in status, headers and body;
  - a V resource whose file value is RV-only: no file in the JSON-LD `distribution`, the DataCite `formats` and `sizes`, `DC.format` or the HTML
- [ ] 4.12 ↑TD16: In-memory HTTP tests: an admin token yields the same bytes as anonymous on a visible resource, and a logged-in user's token yields the unchanged shell on a resource anonymous cannot see
- [ ] 4.13 ↑TD14: In-memory HTTP tests: a shell failure gives `502` for HTML, a `303` is still served while the shell fails, and the representations do not depend on the shell
- [ ] 4.14 ↑S4: In-memory HTTP tests: the `.jsonld` body equals the graph embedded in the page; the DataCite answer is `200 application/vnd.datacite.datacite+json`; both representations carry `Link: <page>; rel="describes"`
- [ ] 4.15 ↑S4: In-memory HTTP test: `?version=` and `?highlightValue=` change nothing, also with the `highlightValue` of a value anonymous cannot see
- [ ] 4.16 ↑S4: E2E test with the shared datasets: `http://rdfh.ch/0803/0b03b0a6e6be` (resource and still-image file at least V for anonymous), `http://rdfh.ch/0803/00014b43f902` (RV-only page), `http://rdfh.ch/0001/PHbbrEsVR32q5D_ioKt6pA` (deleted) and a missing id
- [ ] 4.17 ↑TD14: Set the C1–C4 variables for the local stack on the `api` service in `docker-compose.yml`, with C2 `http://app:4200/index.html`
- [ ] 4.18 ↑S4: Run `just check`, `just test` and `just test-e2e`; all pass
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 5: Baseline measured and recorded

Subgoal **S5** ↑PG6.

The baseline measures the DEV pages before this work is deployed there. F-UJI
is given the DEV landing page URL, not the ARK: every ARK resolves to
production (PRD Open Question 3), so an ARK target would measure production
both times.

### dsp-api

- [ ] 5.1 ↑G6: Documentation page `docs/03-endpoints/fair-landing-pages.md`, with a `mkdocs.yml` nav entry under "DSP-API Endpoints". It describes:
  - routes;
  - the facts and their rules (TD2–TD11);
  - the negotiation and Signposting tables;
  - configuration;
  - that `?version=` pages describe the current state;
  - the disagreement with DPE's OAI records;
  - a results table with columns date, assessor, version, image digest, target kind (page URL or ARK), target and per-metric result
- [ ] 5.2 ↑S5: Baseline: from a dsp-repository checkout (no change there), run `just fair-check <url>` (F-UJI 3.5.0, pinned by digest) against two DEV landing page URLs: `/resource/0803/<id>` of a resource anonymous can see at V, and `/resource/0868/<id>` of a resource whose file records a MIME type and is visible at V. Record both runs, with the two URLs, in the table
- [ ] 5.3 ↑TD18: Create the Linear follow-up issue: the OAI export hard-codes access rights, licence and authorship, so the records TD18 links contradict the page until it is fixed. Relate it to DEV-7420 and DEV-7336
- [ ] 5.4 ↑S5: Run `just check` and `just docs-build`; both pass
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 6: dsp-app forwards the route

Subgoal **S6** ↑PG5, PG1, PG2, PG3.

### dsp-app

- [ ] 6.1 ↑N1, N2, N3, N4: `nginx/default.conf.template`: the resolver lines, the landing location as the first regex location, and the `@shell` fallback
- [ ] 6.2 ↑N4: `Dockerfile`: `ENV DSP_API_UPSTREAM=""` beside `ENV NGINX_PORT`
- [ ] 6.3 ↑S6: `scripts/nginx-smoke-test.sh`, with a Makefile target `nginx-smoke-test`. It builds the image from a temporary context holding the real `Dockerfile` and `nginx/`, a stub `dist/apps/dsp-app/index.html` and `config/` (no Angular build), and runs it on a Docker network against a stub upstream (a Python `http.server` script). The upstream answers, in turn:
  - `200` with `Link` and `Vary`;
  - `303` with `Location`;
  - `404`;
  - `406`;
  - `500`;
  - a response slower than 3 s;
  - no listener;
  - an empty `DSP_API_UPSTREAM`.

  It asserts the passthroughs with the security headers, the fallback (`200`, the shell, `Vary: Accept`, `Cache-Control: no-store`, the security headers) in every other case, HEAD parity, that a `POST` is not forwarded, and that `/resource/0803/..%2F..%2Fx` and `/resource/0803/abc.json` are never forwarded
- [ ] 6.4 ↑S6: Run `./scripts/nginx-smoke-test.sh`; every assertion passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 7: Score rise confirmed locally

Subgoal **S7** ↑PG6.

F-UJI fetches the URL it is given, and `just fair-check` starts its container
with `--add-host=host.docker.internal:host-gateway`, so it reaches services
published on the host. DPE measured local pages this way
(`machine-readable-metadata.md`, "Against a local server"). Both runs use one
stack and one URL; only `DSP_API_UPSTREAM` differs, so the baseline is today's
plain shell (N4). Nothing in this phase changes the compose file: the overrides
live in a compose override file under `.claude/tmp/`.

A local score is a direction, not a DEV or production figure:

- `F1-02D` fails, because the emitted ARK points at the local resolver
  (`KNORA_WEBAPI_ARK_RESOLVER_URL`), which nothing serves;
- `KNORA_WEBAPI_KNORA_API_EXTERNAL_HOST=host.docker.internal` drops the port
  from external ontology IRIs (`externalOntologyIriHostAndPort`), so
  `additionalType` does not resolve;
- the test data's licences and authorship decide which licence and creator
  metrics can move.

### dsp-api

- [ ] 7.1 ↑S7: Build the dsp-api image from this branch with `just docker-build-dsp-api-image`, and the dsp-app image from the Phase 6 smoke-test build context (real `Dockerfile` and `nginx/`, stub shell) under a local tag. Start the stack with `just stack-init-test` plus a compose override file in `.claude/tmp/` that sets the `app` image to that tag, and on `api` sets `KNORA_WEBAPI_KNORA_API_EXTERNAL_HOST=host.docker.internal`, C1 `http://host.docker.internal:4200` and C2 `http://app:4200/index.html`
- [ ] 7.2 ↑S7: Baseline: with `DSP_API_UPSTREAM` empty on `app`, from a dsp-repository checkout run `just fair-check http://host.docker.internal:4200/resource/0803/0b03b0a6e6be`; keep the total and per-metric results
- [ ] 7.3 ↑S7: After: recreate `app` with `DSP_API_UPSTREAM=http://api:3333` and run `just fair-check http://host.docker.internal:4200/resource/0803/0b03b0a6e6be <baseline total + 1>`; it exits zero, and no metric scores lower than in 7.2
- [ ] 7.4 ↑S7: Record both runs in the results table of `docs/03-endpoints/fair-landing-pages.md`, target kind "local page URL", with the three local limitations above
- [ ] 7.5 ↑S7: Run `just check` and `just docs-build`; both pass
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Human Actions

| Id | Parent | Action | Who | When | Why not the agent |
|----|--------|--------|-----|------|-------------------|
| H1 | ↑S4, S6 | In ops-deploy, per environment, set `DSP_API_UPSTREAM=http://api:3333` on the `app` service (`docker-compose-svc.yml.j2`) and C1–C4 on the `api` service | DevOps | after ship | Deployment configuration and its rollout are the operators' decision |
| H2 | ↑PG6 | After DEV runs both, run `just fair-check` against the two DEV page URLs of 5.2 and FAIR Champion by hand, then add the rows to the results table | Owner | after ship | Needs the deployed environment; FAIR Champion is run by hand |

## Acceptance Criteria

- [ ] A1 ↑PG1, PG2, PG3, PG4, PG5: Every PRD requirement from REQ-1.1 to REQ-5.2 is covered by a named test in dsp-api or the dsp-app smoke test; REQ-6.1 is covered by A4
- [ ] A2 ↑PG3: DataCite JSON validates against the vendored schema in tests
- [ ] A3 ↑PG4: The not-visible cases cannot be told apart on either route
- [ ] A4 ↑PG6: The baseline is recorded, and the after-run (H2) is recorded against the same targets with assessor version and target kind
- [ ] A5 ↑PG6: The local after-run (7.3) scores above the local baseline (7.2), and no metric falls

## Dependencies & Risks

| Id | Parent | Risk | Likelihood | Impact | Mitigation |
|----|--------|------|-----------|--------|------------|
| R1 | ↑TD14 | A cached shell outlives a dsp-app deploy, so its hashed bundles 404 | M | M | 10 s TTL |
| R2 | ↑PG5 | A cold resource read exceeds nginx's 2 s timeout, so a harvester sees the plain shell for that request | M | L | Measured in H2; the fallback is by design |
| R3 | ↑PG6 | The page and the representations sit on different hosts, which an assessor may weigh | M | L | Measured in H2 |
| R4 | ↑PG4 | A restricted fact leaks through some path | L | H | One builder; byte-identity tests (4.11); credentials tests (4.12); `Cookie` and `Authorization` cleared by nginx |
| R5 | ↑TD17 | Header injection from recorded data | L | H | Hrefs only from IRIs and ARKs, percent-encoded; hostile-text tests (2.9, 3.7) |
| R6 | ↑N1 | nginx forwards a crafted path | L | M | Strict path regex on the normalised URI; traversal case in 6.3 |
| R7 | ↑TD14 | The splice breaks the SPA | L | H | Splice only before the first `</head>`; any failure gives `502` and nginx's own shell |
| R8 | ↑N1 | Another service on the shared ops-deploy network is aliased `api`, so the name resolves to it | L | M | Same exposure ingest already has with `DSP_API_URL=http://api:3333`; the fallback limits the harm to the plain shell |

## Success Metrics

- **M1** ↑PG6: Baseline (5.2) versus after-run (H2), metric by metric, for both
  DEV pages. The metrics expected to move are the metadata, identifier, licence
  and Signposting ones that moved the DPE project page. `F1-02D` is recorded as
  a residual on DEV.
- **M2** ↑PG6: Local baseline (7.2) versus local after-run (7.3), metric by
  metric, for `http://rdfh.ch/0803/0b03b0a6e6be`. The total must rise; `F1-02D`
  fails in both.

## References

- **F1** ↑S1–S6: the PRD, `01-resource-fair-landing-pages-PRD.md`.
- **F2** ↑S2, S3: dsp-repository `docs/adr/0005-fair-landing-pages-in-the-access-area.md`,
  `docs/src/dpe/machine-readable-metadata.md` (Signposting table, negotiation
  table, results ledger) and `docs/src/dpe/oai-pmh.md`.
- **F3** ↑S2, S3: dsp-repository `shared/fair/src/{negotiate,signposting,schema_org,dublin_core_meta,datacite_json,record_datacite,record_dublin_core,helpers,graph}.rs`
  and `areas/access/dpe/server/src/metadata.rs`.
- **F4** ↑TD11: *DaSCH Metadata 2.0 → DataCite 4.6 / OAI-PMH Mapping* v1.1, §2.3.
- **F5** ↑S1: dsp-api code anchors, under `modules/webapi/src/main/scala/org/knora/webapi/`:
  - `slice/export/api/service/ExportService.scala:102-197` (`exportResourcesOai` and its helpers);
  - `slice/api/v3/export/MetadataRecord.scala:47-78`;
  - `slice/resources/service/ReadResourcesServiceLive.scala:39-50`;
  - `slice/admin/domain/model/Permission.scala:13-46`;
  - `slice/admin/domain/model/LegalInfoModel.scala:90-269`;
  - `slice/admin/domain/model/KnoraProject.scala:43-45`;
  - `messages/StringFormatter.scala:1419-1425`;
  - `responders/admin/AssetPermissionsCache.scala:76-110`.
- **F6** ↑S4: dsp-api code anchors, under the same root unless noted:
  - `core/DspApiServer.scala:206-238`;
  - `slice/api/admin/SparqlPassthroughEndpoints.scala:91-146`;
  - `slice/common/api/BaseEndpoints.scala:28-82`;
  - `slice/common/api/RequiresMethod.scala:16-22`;
  - `slice/api/v3/V3BaseEndpoint.scala:35-60`;
  - `slice/api/ApiModule.scala:15-29`, `slice/api/Endpoints.scala:17-31`;
  - `slice/infrastructure/TracingHttpClient.scala:17-30`;
  - `modules/webapi/src/test/scala/org/knora/webapi/core/SparqlPassthroughInterceptorSpec.scala:49-113`.
- **F7** ↑S4: Learnings (dasch-specs):
  - `best-practices/shared-tapir-error-envelope-leaks-exception-messages.md`;
  - `design-decisions/security-logic-authenticates-body-buffering-precedes-it.md`;
  - `logic-errors/sipi-cache-auto-creation-head-request-empty-response.md`.
- **F8** ↑S1: Learnings (dasch-specs):
  - `logic-errors/resource-query-templates-enumerate-predicates.md`;
  - `best-practices/scala3-zio2-coding-mistakes-to-avoid.md`;
  - `best-practices/test-fixture-isolation-shared-data-scope.md`.
- **F9** ↑S6: dsp-app `nginx/default.conf.template`, `nginx/nginx-security-headers.conf`,
  `Dockerfile`; ops-deploy `docker-compose-svc.yml.j2` (the `app` and `api`
  services on one network).
