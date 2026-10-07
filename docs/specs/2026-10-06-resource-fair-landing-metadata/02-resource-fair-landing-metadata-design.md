---
title: "Design: FAIR metadata and Signposting for resource landing pages"
date: 2026-10-06
author: "Raitis Veinbahs"
status: reviewed
repositories:
  - dsp-app
  - ops-deploy
linear: DEV-7420
---

# Design: FAIR metadata and Signposting for resource landing pages

Shared by every slice of the [plan](01-feat-resource-fair-landing-metadata-plan.md).

## Decisions taken (2026-10-06, owner)

- **`?version=`** changes `cite-as` to the versioned ARK and the metadata to that version's state. Representation
  URLs, `describedby` links and `303` targets carry the same `?version=`. A value ARK's `?version=` is read the
  same way: the resource's state at that time.
- **`?highlightValue=`** never affects the response. It is not parsed or validated, so the response does not
  reveal whether the value exists, is visible, or belongs to the resource.
- **Representations:** schema.org JSON-LD, DataCite JSON and Turtle (the same schema.org graph). All three are
  `describedby` targets and `303` candidates. No aliases: `application/json`, `application/rdf+xml` and the
  like get the page, as on DPE.
- **License, split as on DPE and per the legal-metadata PRD v4 (dasch-specs
  `specs/2026-05-08-legal-metadata-on-resources/04-legal-metadata-on-resources-PRD.md` §4):** the resource's
  `license` is the project's data license (`KnoraProject.dataLicense`), which "applies to every resource". The
  file value's own license goes only on its `DataDownload`. A project without a data license emits no root
  `license` and no `rel="license"`. This replaces the earlier decision to use the file value's license for the
  resource.
- **Visible to anonymous means public.** If the anonymous user gets the resource, it gets metadata; if not,
  nobody does. Restricted view (`RV`) on the resource still returns it to anonymous, so it gets metadata, with
  no file advertised.
- **Edge (H1):** dsp-app's nginx proxies the resource route to dsp-api over `proxyNet`. dsp-api builds the whole
  response: it fetches the shell from the app container, splices the head, and sets `Link`, `Vary`,
  `Cache-Control` and the `303` itself. nginx stays a thin proxy whose only logic is a fallback to its static
  `index.html`. Rejected: nginx keeping the shell and pulling the head and headers from dsp-api via SSI and
  `auth_request`, because the `303` and the header logic would then live in nginx config, outside dsp-api's
  tests. See Edge below.
- **A person always gets the app.** The landing endpoint never answers `4xx`. Every outcome that is not "metadata
  for a public resource" (malformed path or query, unknown, not public, deleted, graph failure) is `200` with the
  plain shell. The only non-`200` is `502` when dsp-api has no shell to serve, which nginx turns into its static
  shell.
- **Parity with DPE is by contract, not by code.** `shared-fair` is Rust; the shapes below are checked by tests
  against DPE's documented examples, not shared.

## Shape

Domain code in `slice/export/fair/`, wired in `ExportApiModule.scala`, beside `ExportService`: ARCH-MAP's
webapi-export already "straddles Resources and Values and Assets through the v3 resource CSV and OAI exporter",
and it is the only component that imports dsp-ingest's `AssetInfoService` in-process, which the sidecar read needs.
`slice/resources/` would add a second importer of that edge, outside ingest's sanctioned HTTP-only interface.
Endpoints in `slice/api/v3/resources/fair/`, registered in `ApiV3ServerEndpoints.scala` and `ApiV3Module.scala`.
Services are `final class` with `ZLayer.derive`; the shell source is a trait with a live and an in-memory
implementation so tests need no app container.

1. **Resolve.** On the representation endpoints, `{shortcode}/{resourceId}[?version]` is parsed into a
   `ResourceIri` + optional `VersionDate` (`docs/development/dsp-api-iri-handling.md`); malformed input is a `400`.
   On the landing endpoint the same parsing happens in server logic from raw `String` inputs, and failure yields
   the plain shell. `{resourceId}` is the plain id as dsp-app's URL carries it (`lklK7rVuVOmpBZYWrF8o-g`): the ARK
   resolver has already removed the check digit and un-escaped `=`. `VersionDate.from` accepts both
   `xsd:dateTimeStamp` and the ARK form `20180604T085622513Z`.
2. **Read as anonymous, always.** Through `ReadResourcesService.getResourcesWithDeletedResource` (the read that
   takes a `versionDate`) with `KnoraSystemInstances.Users.AnonymousUser`, whatever credentials the request
   carries. Never with `skipRetrievalChecks = true`, which is what `ExportService.exportResourcesOai` uses and
   which turns hidden resources into a log line. A deleted resource comes back as a `DeletedResource` with no
   values; it maps to not public. Only `ForbiddenException` and `NotFoundException` map to not public; any other
   failure (e.g. a triplestore timeout) propagates, so it is never cached as "not public".
3. **Build one graph.** `ResourceFairGraph` holds the resolved facts once. Every output is a projection of it,
   mirroring `shared-fair`'s `RecordGraph`.
4. **Project.** Pure writers produce JSON-LD, Turtle, DataCite JSON, Dublin Core meta and the link set. Each
   writer lands in the slice that first emits it. Turtle is a Jena parse of the emitted JSON-LD, with the
   `@context` inlined so the parse never fetches `https://schema.org` remotely.

## Field mapping

Source → graph, reviewed against `shared-fair` `record_datacite.rs` / `record_dublin_core.rs` and
`docs/src/dpe/machine-readable-metadata.md`:

| Graph fact | Source in dsp-api | Notes |
| --- | --- | --- |
| `ark` / `cite-as` | `StringFormatter.resourceIriToArkUrl(iri, version)` | versioned when `?version` |
| `pageUrl` | `app.dsp-app.url` + `/resource/{shortcode}/{resourceId}` | absolute |
| `title` | `ReadResourceV2.label` | |
| `creators` | `resourceAuthorship`, else the project's `defaultDataAuthorship` | data-side authorship per PRD v4; file-value authorship is asset-side and never used here. `DaSCH` → Organization, else Person; DataCite-only fallback `DaSCH` as DPE does |
| `orcids` | an authorship string that *is* an ORCID URI | no ORCID field exists; nothing is parsed out of free text |
| `dateCreated` / `dateModified` | `creationDate` / `lastModificationDate` | `publicationYear` = creation year |
| `license` | project `dataLicense` → `License.uri` | none when unset |
| `copyrightHolder` | project `dataCopyrightHolder` | none when unset |
| `generalType` | the single file value's class only | Image / Audiovisual / Sound / Text, else `Dataset`. Not `ExportService.typeOfDataOf` as is: it maps any `TextValueContentV2` to Text |
| `accessLevel` | anonymous `userPermission` on the resource and its file value | see Access |
| `file` (DataDownload) | dsp-ingest original URL + sidecar (as `ExportService.fileLinkOf`) | **only when Full Open**; never for external IIIF. Carries the file value's own `license` and `copyrightHolder` |
| `isPartOf` | project ARK (`ark:/72163/1/{shortcode}`): the DPE project page | |
| `additionalType` | resource class IRI | recorded fact |

No `description`: dsp-api has no generic description property, and `ExportService.findDescriptionProperty` is a
hardcoded map for four projects. A generic `kb:hasDescription` is separate work; until then the key is omitted.

### File-value edge cases

The file-derived facts (`file`, `generalType`) come from exactly one file value:

- **More than one file value:** "at most one file value" is an assumption in `ExportService`, not an enforced
  invariant, and `collectFirst` over a `Map` is order-dependent. The builder fails closed: it logs and omits every
  file-derived fact.
- **External IIIF (`StillImageExternalFileValueContentV2`):** its `internalFilename` is a placeholder, so
  `fileLinkOf` as written would build an invented dsp-ingest URL. No `DataDownload` is emitted; `generalType`
  (Image) still applies.
- **The sidecar read** (`fileLinkOf`) goes through dsp-ingest's `AssetInfoService` in-process, as the OAI
  export does today; ARCH-MAP records that import as outside ingest's HTTP-only interface. Keeping the code in
  webapi-export reuses that edge and adds no new one.

`ExportService.exportResourcesOai` hardcodes `accessRights = "Full Open Access"` and `LegalInfo.publicDomain`.
Those are placeholders, and this work must not copy them: parity is with DPE's *shapes*, never with the export's
placeholder values.

## schema.org shape (JSON-LD)

Following the DPE contract:

- Root `@type: Dataset` (ADR-0005: assessors test `Dataset` properties; `distribution` is a `Dataset` property),
  with `additionalType` = resource class IRI.
- `identifier`: exactly two entries, `{"@type":"PropertyValue","propertyID":"ARK","value":<ark>}` plus the page
  URL as a bare string.
- `license`: `{"@id": <uri>}`, never a string; key absent when none.
- `creator` / `prov:wasAttributedTo` (DaSCH + creators with an ORCID), `publisher` DaSCH, `isPartOf` the project,
  `includedInDataCatalog` as DPE emits it.
- `distribution`: one `DataDownload` at the root (`contentUrl`, `name`, `encodingFormat`, `contentSize`, the
  file's own `license`), each field omitted when the source lacks it, never guessed.
- `isAccessibleForFree` / `conditionsOfAccess` / `DC.accessRights` (COAR) from the access level.
- Repeated properties are always written as arrays, never collapsed to a scalar.

## Signposting

The `Link` header and the `<link>` mirror are built from one list, in DPE's order: `cite-as` (exactly once) →
`type` `https://schema.org/Dataset`, `https://schema.org/AboutPage` → `describedby` JSON-LD
(`application/ld+json`), DataCite (`application/vnd.datacite.datacite+json`), Turtle (`text/turtle`) → `license`
(0 or 1, the project's) → `author` (one per ORCID). Delimiters in hrefs are percent-encoded at serialization;
CR/LF never reach a header. Each representation answers `Link: <page>; rel="describes"`.

## Access

Nothing here is a new policy. Visibility is the existing read's (`ForbiddenException` / `NotFoundException` for
the anonymous user); whether the file may be advertised is the existing asset policy, `AssetAccess.from`, asked
through `AssetPermissionsResponder.getAssetAccess(AnonymousUser)(internalFilename)`, the same decision Sipi and
dsp-ingest enforce when the file is downloaded. The metadata only reports them:

| Anonymous gets | Access level | File advertised |
| --- | --- | --- |
| resource ≥ `V`, and no file value or `AssetAccess.original == Grant` | Full Open Access (`c_abf2`) | yes |
| resource `RV`, or the file's original is withheld | Open Access with Restrictions (`c_16ec`) | no |
| the read fails Forbidden / NotFound, or the resource is deleted or younger than `?version=` | not public | no metadata, no links at all |

A file value anonymous cannot see is not returned by the read, so it cannot be told apart from no file value;
the level then comes from the resource and the file values that are returned. `Metadata only Access` is never
emitted, because the read cannot establish it. An `RV` file value is returned with its full file details, so file
details are taken only once `AssetAccess` grants the original; with several file values none is advertised, and
the level still counts every one of them.

Only values the anonymous read returns can feed the graph, and only the allow-listed facts of the field mapping:
the read's `values` are never serialised wholesale. A version read (`?version=`) applies the permissions recorded
for that version, exactly as `/v2/resources?version=` does for the anonymous user.

## Edge

```text
browser ──► app nginx   location ~ ^/resource/([0-9A-Fa-f]{4})/([A-Za-z0-9_-]+)/?$   (first regex location)
               │  proxy_pass $dsp_api_upstream/v3/resources/$1/$2/landing$is_args$args
               │  credentials stripped; on 5xx / timeout → static /index.html
               ▼
            dsp-api ──► GET {app.dsp-app.internal-url}/index.html (cached)
               │  splice head, Link, Vary, Cache-Control, or 303
               ▼
            response
```

- **Landing endpoint:** `GET`/`HEAD` `/v3/resources/{shortcode}/{resourceId}/landing`. Raw `String` path and query
  inputs, parsed in server logic. Exempt from tapir's not-acceptable check (`PassthroughAwareNotAcceptableInterceptor`
  in `core/DspApiServer.scala`, which today exempts only the SPARQL passthrough), or it answers `406` before the
  negotiation runs.
- **nginx location:** the first regex location in `default.conf.template`, so ids ending in `.json`/`.js`/`.css`
  are not caught by the static blocks. The id pattern only admits safe characters, because `$1`/`$2` reach
  `proxy_pass` decoded and are not re-encoded. Anything else falls through to today's behaviour.
- **Upstream address:** env `DSP_API_UPSTREAM` (e.g. `http://api:3333`), substituted by the nginx image's template
  step like `NGINX_PORT`, with an `ENV` default in the Dockerfile (unset, the literal stays and nginx refuses to
  start). `resolver 127.0.0.11 valid=10s` resolves it per request, so nginx starts while `api` is down. `api` is
  unambiguous on `proxyNet` today; the api already reaches `ingest` and `db` by bare name.
- **Headers:** nginx clears `Authorization` and `Cookie`, passes `Accept`, and includes
  `security-headers.conf` (the server block adds none, so a location without it sends none). dsp-api sets no
  security headers, to avoid duplicates. nginx adds no `Cache-Control`. No `proxy_cache`.
- **Fallback:** `proxy_intercept_errors on`, `error_page 500 502 503 504 = /index.html`, `proxy_read_timeout 3s`.
  The internal redirect lands in the existing `/index.html` location, so a fallback looks exactly like today.
- **Shell source:** dsp-api GETs `index.html` from `app.dsp-app.internal-url` (`http://app` in ops-deploy,
  `http://localhost:4200` locally) with `Accept-Encoding: identity`, over the existing sttp backend
  (`TracingHttpClient`). Cached in a `Ref` with a TTL (`app.dsp-app.shell-cache-ttl`, default 60 s) and a
  single-flight refresh. On fetch failure the last good copy is served for at most 1 hour; older than that, or
  none at all, the landing endpoint answers `502` and nginx serves its own current shell, so a stale shell never
  points at bundles a new dsp-app release removed. No loop: `/index.html` is served statically, not by the
  `/resource/` location.
- **Splice:** before the first `</head>`, matched case-insensitively. No `</head>`: serve the shell unspliced and
  log. Response `Content-Type: text/html; charset=utf-8`, length recomputed.
- **Timeouts:** dsp-api's resource read for the landing page is bounded at 2 s, below nginx's 3 s; on timeout it
  serves the plain shell rather than letting nginx fall back.
- **Two app URLs, never interchangeable:** `app.dsp-app.url` is public and goes into the metadata;
  `app.dsp-app.internal-url` is internal and is only fetched. An internal host must never appear in output.

## Caching

- **Landing `200` and `303`:** `Cache-Control: no-cache`, plus `Vary: Accept` from S7 (before it the response does
  not vary). A permission change or deletion is visible on the next request. Today's `/resource/*` is already
  `no-store`, so nothing gets cached that wasn't.
- **Representations:** `Cache-Control: no-cache`. The `404` for anything not public: `no-store`.
- **The fallback** is today's static shell with today's headers, and carries no metadata.

## Technical Considerations

- **Representation endpoints:** `GET`/`HEAD` `/v3/resources/{shortcode}/{resourceId}/metadata.jsonld`,
  `…/metadata.datacite.json`, `…/metadata.ttl`, each with `?version=`, on the API's public host. They are public:
  `V3BaseEndpoint.public(errorOut)` with an endpoint-local error output (every v3 base needs one), never a new
  variant in the shared `BaseEndpoints.errorOutputs` (learning:
  `shared-tapir-error-envelope-leaks-exception-messages.md`). Every non-public outcome returns one identical `404`.
- **`303` and `describedby` targets** are absolute, built from `externalKnoraApiBaseUrl`, never from the request.
  nginx's `proxy_redirect` does not rewrite them with a variable `proxy_pass`, which is what we want.
- **New config**, nested like `DspIngestConfig` (internal vs external URL) and validated in `AppConfig`:
    - `app.dsp-app.url` (env `KNORA_WEBAPI_DSP_APP_URL`): the public origin of dsp-app, scheme + host, no path,
      no trailing slash: `https://app.dasch.swiss` on production, `https://{{ DSP_APP_HOST }}` in ops-deploy. It
      must be the host the ARK resolver sends resources to, or the page URL names a different deployment than the
      ARK (DPE's `F1-02D` defect). Configured, not taken from the request's `Host`, because identifiers are never
      derived from the request (ADR-0005).
    - `app.dsp-app.internal-url` (env `KNORA_WEBAPI_DSP_APP_INTERNAL_URL`): dsp-app as reached from dsp-api's
      network; see Edge.
    - `app.dsp-app.shell-cache-ttl`.
    - **Empty means off.** Both URLs default to empty in `application.conf`. With `url` empty, the representation
      endpoints answer `404` and the landing endpoint serves the plain shell, and startup logs a warning. A
      missing setting therefore publishes nothing, never a `localhost` identifier. Tests and local runs set them.
- **New HTTP infrastructure:** no `Link`, `Vary`, `303`, HTML-serving or `HEAD` endpoint exists in dsp-api today.
  `RequiresMethod` gets `head`. Headers go through tapir `header[String]`. `HEAD` must return the same status and
  headers as `GET`, tested explicitly (learning: `sipi-cache-auto-creation-head-request-empty-response.md`).
- **Head splicing:** S4 introduces one splice point. S5 and S6 each contribute their own part through it, so
  neither depends on the other; the second to land adds a golden test of the combined head.
- **JSON-LD in `<script>`:** `<`, `>` and `&` are written as JSON unicode escapes before splicing (ADR-0005
  consequence). Built as a zio-json AST, never by string concatenation. A JSON-LD data block is not executed, so
  a future CSP does not block it.
- **Observability:** per `docs/observability/instrumentation-recipe.md`: bounded span names, no IRIs as span
  attributes, an `exit_reason` for shell / metadata / not-public / fallback, and a counter for shell-fetch
  failures.
- **Size:** a resource has at most one file, so no byte budget like DPE's 4 MB `hasPart` cap is needed.
- **Performance:** S4 puts dsp-api on the hot path of every resource deep link, without a resource read. S5 and
  S6 add one single-resource read plus one sidecar lookup per request.
- **SPARQL:** none new expected. If any is needed, use the `sparql"…"` interpolator.

## Alternative Approaches Considered

- **A Traefik router on the app host instead of nginx**: rejected (H1). It routes per path just as well, but puts
  the fallback to the static shell in Traefik, which cannot serve one, and moves the routing out of the repo that
  owns the route.
- **nginx keeps the shell (SSI + `auth_request`)**: rejected (H1); see Decisions.
- **The file value's license as the resource's license**: rejected (2026-10-06, replacing the earlier decision).
  It covers the asset, not the record; the PRD v4 makes the data license project-wide, and DPE separates the two.
- **Angular SSR in dsp-app**: rejected. dsp-app is pure CSR with no SSR scaffolding, and the headers and `303`
  would still need server code.
- **Point resource ARKs at a DPE record page**: rejected. It would give code-level parity with `shared-fair`, but
  DPE knows only the corpus export (3 of 85 committed projects carry records), is as fresh as its last deploy,
  and has no dsp-api permission model.
- **Read with the requester's credentials**: rejected. The output would vary by viewer, making shared caching
  unsafe and the "leaks nothing" guarantee depend on every caller.
- **Reuse the existing `/v2/resources` JSON-LD as the JSON-LD representation**: rejected as the primary
  representation, because it is knora-api vocabulary, not schema.org, and assessors do not read it.
- **Render representations inline on the page URL by `Accept`**: rejected by ADR-0005. Representations get their
  own URLs; the page negotiates only via `303`.
- **Ship the landing response as one phase**: rejected. Bundling the edge plumbing with all metadata puts the
  riskiest change (dsp-api on every resource page view) and every visible behaviour in one release, and nothing
  moves an assessor score until all of it lands.
