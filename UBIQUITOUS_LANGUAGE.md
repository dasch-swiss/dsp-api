# Ubiquitous Language

Consolidated cross-context glossary for dsp-api. **Complements — does not replace —
[`CONTEXT-MAP.md`](./CONTEXT-MAP.md)**, which holds the full context map, boundaries, and
target dependency structure. This file rolls up the terms that matter across contexts, with
ambiguities and synonyms flagged.

## Data Model

The user-authored schema — the reason dsp-api is not just a triplestore with a REST wrapper.

| Term | Definition | Aliases to avoid |
| --- | --- | --- |
| **Data Model** | A project's user-authored schema: the classes, properties, cardinalities, and controlled vocabularies its data must conform to | Ontology (RDF-implementation word) |
| **Class** | A user-defined type of resource within a data model | Resource class¹ |
| **Property** | A user-defined attribute or relationship a class may carry | Predicate |
| **Cardinality** | A constraint on how many values of a property a resource may/must have | |
| **List** | A hierarchical controlled vocabulary authored as part of a data model; defining one is a *modelling* act | Taxonomy, node tree |
| **Standoff class / mapping** | The schema face of rich text: standoff tag classes (ontology entities) and the XML↔standoff mapping that structures markup — practically near-static (one encouraged standard mapping) | |

¹ *"Resource class" is fine informally, but the modelled type is a **Class** in the data model.*

## Resources & Values

The instance data — resources and their values, conforming to a data model.

| Term | Definition | Aliases to avoid |
| --- | --- | --- |
| **Resource** | An instance of a data-model class; the **aggregate root** and consistency boundary | Record, entity, object |
| **Value** | A versioned entity holding one property value *inside* a resource; never exists without its resource | Property value (when it implies independence) |
| **File value** | A value type representing an asset (image, document, audio, …) by metadata + internal filename — the bytes live in Assets/Media, not here | |
| **Standoff markup** | The instance face of rich text: standoff tags attached to a text value | Annotation (overloaded) |

## Search

Retrieval of resources, including dsp-api's own query language.

| Term | Definition | Aliases to avoid |
| --- | --- | --- |
| **Gravsearch** | dsp-api's custom query language; data-model-aware retrieval of resources | |
| **Full-text search** | Text-only retrieval with zero data-model knowledge (converging with Gravsearch in a redesign) | |
| **Type inspection** | Inferring the data-model type of each variable in a Gravsearch query | |
| **Prequery / main query** | The two-phase SPARQL Gravsearch generates (paged IRIs, then full data) | |

## Assets / Media

A thin integration context: the ports to the external **Sipi** and **dsp-ingest** services.

| Term | Definition | Aliases to avoid |
| --- | --- | --- |
| **Asset** | A binary file (image, document, AV) whose bytes live in Sipi/dsp-ingest; dsp-api stores only its metadata | File (when bytes are implied) |
| **Sipi** | External IIIF media server that stores and serves asset bytes | |
| **dsp-ingest** | External service that ingests assets; planned to merge into dsp-api | Ingest service |
| **Restricted View** | A per-project policy for serving reduced-resolution/watermarked images (setting owned by Identity, enforcement by Assets) | |

## Identity & Access

| Term | Definition | Aliases to avoid |
| --- | --- | --- |
| **Project** | The ownership and scoping unit; owns data models, resources, and settings | |
| **User** | An authentication identity that acts within projects | Account |
| **Group** | A named set of users used to grant permissions | |
| **Permission profile** | A user's effective groups + admin flags, computed by Identity and carried on the authenticated request | |
| **Administrative permission** | A project/group-scoped grant of what a user may do by default | |
| **Default object-access permission** | The default access granted on objects created in a project (a.k.a. DOAP) | |

## Export / Migration

| Term | Definition | Aliases to avoid |
| --- | --- | --- |
| **Migration** | Bulk export/import of a whole project (data model + resources + admin + permissions) at the RDF named-graph level | |
| **Data Task** | A tracked long-running async operation with a status lifecycle | Job (informal) |

## Shared Kernel & cross-cutting

Genuine cross-context vocabulary (see `CONTEXT-MAP.md` § Shared Kernel).

| Term | Definition | Aliases to avoid |
| --- | --- | --- |
| **Data IRI** | A **schema-invariant** identifier — same string in every schema (resource, value, project, user, list). Lives in the shared-identifiers kernel | |
| **Definition IRI** | A **schema-variant** identifier for a data-model entity (class, property, ontology); has internal + API forms and genuinely converts. Owned by Data Model | Entity IRI (acceptable synonym) |
| **Schema** | Which representation an IRI/entity is in — *internal* vs *API v2 Complex/Simple* | |
| **Permission policy primitive** | The pure permission-level model (RV < V < M < D < CR) + `hasPermissions` parse/compare; a kernel value object | |
| **Object-access permission** | Fine-grained per-object access stored as a literal on the data and enforced at point of use | |
| **Scope** | Coarse API-layer JWT authorization gating endpoints (distinct from object-access permission) | |
| **Shared kernel / substrate** | The RDF graph + triplestore access + identifiers + policy primitive that contexts share — *not* a per-context store | |
| **InstanceUsage** | Target published capability by which Data Model asks Resources "is this class/property used in data?" instead of raw cross-context SPARQL | |
| **Ratchet** | The rule: existing cross-context SPARQL stays, but no *new* cross-context SPARQL — new cross-boundary reads go through a published capability | |
| **Intrinsic substrate user** | A context (Search, Export) whose job legitimately requires operating directly on the RDF substrate; exempt from the ratchet | |

## Relationships

- A **Project** owns one or more **Data Models** and the **Resources** conforming to them.
- A **Data Model** defines **Classes**, **Properties** (with **Cardinalities**), and **Lists**.
- A **Resource** is an instance of a **Class** and contains **Values**; a **Value** belongs to exactly one **Resource**.
- A **Value** may be a **File value** referencing an **Asset**; the **Asset**'s bytes live in **Sipi/dsp-ingest**.
- A **Resource** references a **List** node through a list **Value**.
- **Gravsearch** queries are expressed against the **Data Model** and return **Resources**.
- Every **Resource**/**Value** carries an **object-access permission**, evaluated against the requesting user's **permission profile**.
- A **Definition IRI** converts between **Schemas**; a **Data IRI** does not.

## Example dialogue

> **Dev:** "When a user tightens a **Cardinality** in their **Data Model**, do we just update the schema?"
>
> **Domain expert:** "Only if no **Resource** already violates it. The **Data Model** has to ask whether the **Property** is used in data first."
>
> **Dev:** "And today it does that with SPARQL straight against the resource data?"
>
> **Domain expert:** "Right — that's the accidental coupling we're ratcheting away. The target is **InstanceUsage**: Data Model asks Resources through a published capability, so it never touches a **Data IRI**'s storage directly. Note that's a **Definition IRI** it's constraining, but the resources it checks are addressed by **Data IRIs**."
>
> **Dev:** "So `toInternalSchema` makes sense on the **Property**'s IRI but not on the **Resource**'s?"
>
> **Domain expert:** "Exactly. A **Definition IRI** is schema-variant; a **Data IRI** is schema-invariant. Converting a **Data IRI** should be a compile error, not a silent no-op."

## Flagged ambiguities

- **"Ontology" vs "Data Model"** — the same concept. *Data model* is the domain term (use in prose, docs, APIs); *ontology* is the RDF-implementation word (acceptable in code: package names, `OntologyIri`). Don't use "ontology" when speaking to domain experts.
- **"IRI" is not one thing** — a **Data IRI** (schema-invariant, kernel) and a **Definition IRI** (schema-variant, Data Model) behave differently. The legacy `SmartIri` conflates them and hands schema-conversion to both; the type system should keep them distinct.
- **"Permission" spans two layers** — coarse API-layer **Scope** (JWT, endpoint gating) vs. fine-grained **object-access permission** (per-object, stored on data). Always say which you mean.
- **"List"** — refers to the *definition* (a controlled vocabulary, part of the Data Model), while a list **Value** is the instance reference from a resource. Don't call the value "a list."
- **"Value"** — a domain entity inside a **Resource**, not a generic "field" or standalone object; it has identity, versioning, and its own permission, but no independent lifecycle.
- **"Standoff"** — has a Data Model face (classes/mappings) and a Resources face (markup on a text value); it is a cross-cutting *feature*, not a context.
