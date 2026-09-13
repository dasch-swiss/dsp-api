# Search

Part of the dsp-api context map; see [the root index](../../../CONTEXT.md). This file lives under
`docs/contexts/` only until the context is extracted into its own module; the extraction PR moves it to
that module's root directory (see [`MODULARIZATION-PLAN.md`](../../../MODULARIZATION-PLAN.md)).

Search owns the Gravsearch query language and the delivery of retrieval: the parser, the checker,
the AST, type inspection through the Data Model projection port, paging and count semantics, guards,
timeouts, tracing, and the search endpoints. It exists because a Data-Model-aware query language is
a body of logic with its own shape, distinct from both the model and the instance data it retrieves.

## Ownership notes

- Search is one provisional context. Its relationship with Resources is changing as filtering and
  faceted retrieval evolve, so its internal seams should not be hardened prematurely.
- Query translation to SPARQL and result assembly belong to Resources & Values, behind the
  `ResourceSearch` port that Search declares. That code encodes how a Resource and its Values are
  stored as triples, which is the provider's knowledge. See
  [ADR-0011](../../adr/0011-cross-context-access-ports-and-adapters.md), worked example 2.
- Search therefore holds no low-level RDF execution of its own.
- Data Model implements the **Data Model projection** port that Search declares for type
  inspection.

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

**Data Model projection**:
A Search-declared port, implemented by Data Model, offering the view of Classes and Properties that
retrieval needs without importing Data Model implementation details.
_Avoid_: Raw ontology cache

**ResourceSearch port**:
The Search-declared port through which Resources & Values answers Gravsearch, full-text, label, and
incoming-link queries.
_Avoid_: SPARQL access, triplestore service

Search remains one provisional context while full-text, Gravsearch, filtering, and faceting converge.

## Relationships

- **Gravsearch** is expressed against a **Data Model** and returns **Resources**.
- **Type inspection** resolves each **Gravsearch** variable to a **Class** or **Property** defined by
  a **Data Model**.
- A **Prequery** yields paged **Resource** IRIs, which the **main query** expands into complete
  **Resources**.
- **Search** asks **Resources & Values** for matching Resources through the **ResourceSearch port**,
  which Resources & Values implements.
- **Search** asks **Data Model** for Class and Property meaning through the **Data Model
  projection** port, which Data Model implements.
- **Search** depends on no other context and on no low-level RDF execution.

## Example dialogue

> **Dev:** "**Type inspection** needs the **Cardinalities** of a **Class**. Should Search read the
> ontology cache directly?"
>
> **Domain expert:** "No. Search declares the **Data Model projection** port and Data Model
> implements it."
>
> **Dev:** "And the SPARQL for the **prequery**?"
>
> **Domain expert:** "That is not ours. Search declares the **ResourceSearch port** and
> **Resources & Values** implements it, because how a Resource is stored as triples is its
> knowledge, not ours."

## Flagged ambiguities

- Search is stable enough to name as a context, but its internal split remains provisional. Do not
  harden the seams between full-text, Gravsearch, filtering, and faceting yet.
- Search's internal split is now fixed at the port: language and delivery stay in Search,
  representation-dependent translation moves to Resources & Values
  ([ADR-0011](../../adr/0011-cross-context-access-ports-and-adapters.md)).
- The stable **Data Model projection** that Search should consume is still an open question; see the
  root index.
- "Prequery" and "main query" have been read as two public searches. Resolved: they are one internal
  two-phase process.
