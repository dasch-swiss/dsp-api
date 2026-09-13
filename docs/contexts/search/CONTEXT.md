# Search

Part of the dsp-api context map; see [the root index](../../../CONTEXT.md). This file lives under
`docs/contexts/` only until the context is extracted into its own module; the extraction PR moves it to
that module's root directory (see [`MODULARIZATION-PLAN.md`](../../../MODULARIZATION-PLAN.md)).

Search owns retrieval of Resources, including Gravsearch, full-text retrieval, type inspection,
prequery and main-query construction, inference optimisation, and result assembly. It exists because
turning a Data-Model-aware query into executable RDF is a body of logic with its own shape, distinct
from both the model and the instance data it retrieves.

## Ownership notes

- Search is one provisional context. Its relationship with Resources is changing as filtering and
  faceted retrieval evolve, so its internal seams should not be hardened prematurely.
- Data Model should eventually publish the stable projection Search needs.
- Search is an intrinsic user of low-level RDF execution because query translation is part of its
  implementation. That exception does not give Search ownership of another context's RDF meaning.

## Language

**Gravsearch**:
dsp-api's Data-Model-aware query language for retrieving Resources.
_Avoid_: SPARQL public interface

**Full-text retrieval**:
Text-oriented Resource retrieval that currently requires little Data Model knowledge.
_Avoid_: Gravsearch

**Type inspection**:
The inference of the Data Model type of each variable in a Gravsearch query.
_Avoid_: Validation only

**Prequery / main query**:
The two-phase internal query process that first identifies paged Resource IRIs and then retrieves
complete Resources.
_Avoid_: Two public searches

**Data Model projection**:
A stable published view of Classes and Properties that retrieval can consume without importing Data
Model implementation details.
_Avoid_: Raw ontology cache

Search remains one provisional context while full-text, Gravsearch, filtering, and faceting converge.

## Relationships

- **Gravsearch** is expressed against a **Data Model** and returns **Resources**.
- **Type inspection** resolves each **Gravsearch** variable to a **Class** or **Property** defined by
  a **Data Model**.
- A **Prequery** yields paged **Resource** IRIs, which the **main query** expands into complete
  **Resources**.
- **Search** depends on **Data Model**, **Resources & Values**, and the **RDF platform**.
- **Search** is an **Intrinsic RDF-platform user**: query translation legitimately uses generic RDF
  execution.
- **Search** should eventually consume a **Data Model projection** rather than Data Model
  implementation detail.

## Example dialogue

> **Dev:** "**Type inspection** needs the **Cardinalities** of a **Class**. Should Search read the
> ontology cache directly?"
>
> **Domain expert:** "Not as the target. Data Model should publish a **Data Model projection** and
> Search consumes that."
>
> **Dev:** "But Search still builds raw SPARQL for the **prequery**."
>
> **Domain expert:** "Yes. Query translation is intrinsic RDF-platform use. It is not permission to
> read another context's triples for their meaning."

## Flagged ambiguities

- Search is stable enough to name as a context, but its internal split remains provisional. Do not
  harden the seams between full-text, Gravsearch, filtering, and faceting yet.
- The stable **Data Model projection** that Search should consume is still an open question; see the
  root index.
- "Prequery" and "main query" have been read as two public searches. Resolved: they are one internal
  two-phase process.
