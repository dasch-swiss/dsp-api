# FAIR

Part of the dsp-api context map; see [the root index](../../../CONTEXT.md). This file lives under
`docs/contexts/` only until the context is extracted into its own module; the extraction PR moves it to
that module's root directory (see [`MODULARIZATION-PLAN.md`](../../../MODULARIZATION-PLAN.md)).

FAIR owns the machine-readable description of a public Resource served at the landing route its ARK
resolves to (`/fair/resources/{shortcode}/{id}`): schema.org JSON-LD, DataCite JSON, Dublin Core meta
tags and Signposting links. It exists because these descriptions are derived facts for outside readers,
not Resource data, and they are built only from what an anonymous visitor may see.

## Ownership notes

- Resources, Projects, Data Model classes and Asset sidecar data stay with their owning contexts. FAIR
  reads them through the ports it declares in `slice/fair/ports`, and each owner implements its own
  adapter.
- FAIR imports no other context. It uses only shared-kernel types such as `Shortcode`, `Permission`
  and `ResourceIri`.
- FAIR stores nothing. The only state is a process-local cache of the dsp-app shell page.

## Language

**Landing page**:
The page a Resource's ARK resolves to, answered at `/fair/resources/{shortcode}/{id}`: the dsp-app
shell with metadata added, or a redirect to a Representation.
_Avoid_: Resource page, permalink

**Representation**:
One machine-readable rendering of a Resource's description, currently schema.org JSON-LD or DataCite
JSON, each at its own URL.
_Avoid_: Export, serialization

**Resource facts**:
The values derived for one Resource from the Resource, its Project, its Class and its asset sidecar,
from which every Representation is rendered.
_Avoid_: Resource metadata, Resource data

**Signposting**:
The typed links, sent as a `Link` header and as `<link>` elements, that point from a Landing page to
its Representations and to related records.
_Avoid_: Link header

**App shell**:
The dsp-app `index.html`, fetched and briefly cached, into which the landing page's metadata is
inserted.
_Avoid_: Frontend, template

**Not visible**:
A Resource that is missing, deleted, or not viewable by the anonymous user. All three are
indistinguishable to the caller: the unchanged shell or an identical empty 404.
_Avoid_: Forbidden, hidden

**Access level**:
The COAR access right stated in the facts: Full Open Access when the anonymous user may view the
Resource and its file, Open Access with Restrictions otherwise.
_Avoid_: Permission, restricted view

## Relationships

- A **Landing page** offers one or more **Representations** and carries **Signposting** to them.
- **Resource facts** are derived from the Resource as the anonymous user sees it; a **Not visible**
  Resource has no facts.
- A **Representation** is rendered from **Resource facts** and nothing else.
- The **Landing page** is built on the **App shell**; an unavailable shell gives an empty 404, so
  nginx serves its own shell.
- **FAIR** depends on **Projects**, **Data Model**, **Resources & Values** and **Assets** only through
  ports.

## Example dialogue

> **Dev:** "A visitor asks for a Resource that was deleted last week. What does the landing route say?"
>
> **Domain expert:** "It is **Not visible**, so the plain **App shell** goes out. The same goes for a
> Resource the anonymous user may not view. The caller cannot tell the cases apart."
>
> **Dev:** "And the DataCite **Representation** for it?"
>
> **Domain expert:** "An empty 404, identical in every **Not visible** case. There are no
> **Resource facts** to render."

## Flagged ambiguities

- **Access level** was read as the Resource's permission. Resolved: it is the COAR statement in the
  **Resource facts**, derived from the anonymous user's permission on the Resource and its file.
