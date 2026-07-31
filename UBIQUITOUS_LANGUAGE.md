# Ubiquitous Language

Cross-context glossary for dsp-api. It complements [`CONTEXT-MAP.md`](./CONTEXT-MAP.md), which
records ownership, relationships, and the target dependency graph.

## Product context

| Term | Definition | Aliases or interpretations to avoid |
| --- | --- | --- |
| **VRE** | Virtual Research Environment in which researchers and data stewards create, edit, organise, query, and manage research data | Archive |
| **dsp-api** | Backend of the VRE and its transitional source of truth | Archive, Repository |
| **Repository** | The downstream environment for preserving and presenting published data | VRE |
| **Archive** | The separate long-term preservation system within the Repository architecture | dsp-api, Assets, Project Migration |

The VRE's transitional retention of data does not give dsp-api archival custody. Project Migration
hands data between the VRE and other systems; the Archive remains an external context.

## Projects

| Term | Definition | Aliases to avoid |
| --- | --- | --- |
| **Project** | The ownership and scoping unit for Data Models, Resources, settings, and access administration | Tenant |
| **Project settings** | Project-owned configuration such as legal defaults, licences, and Restricted View policy | User settings |
| **Shortcode** | Stable short identifier used to scope a Project's data and identifiers | Project IRI |
| **Restricted View** | Project policy for reduced-resolution or watermarked asset serving; configuration belongs to Projects and enforcement to Assets | Object-access permission |

Projects is a context separate from Identity & Access. Memberships refer to a Project but remain
owned by Identity & Access.

## Identity & Access

| Term | Definition | Aliases to avoid |
| --- | --- | --- |
| **User** | An authenticated identity that acts within Projects | Account |
| **Group** | A named set of Users used to grant permissions | Role |
| **Membership** | Association of a User with a Project or Group | Project ownership |
| **Permission profile** | A User's effective Groups and administration flags, computed by Identity & Access and carried on the authenticated request | JWT scope |
| **Administrative permission** | A Project/Group-scoped grant governing administrative actions | Object-access permission |
| **Default object-access permission** | Default access assigned to objects created in a Project, also called DOAP | Administrative permission |

## Data Model

The user-authored model that VRE data must conform to.

| Term | Definition | Aliases to avoid |
| --- | --- | --- |
| **Data Model** | A Project's Classes, Properties, Cardinalities, controlled vocabularies, and related definitions | Ontology in domain prose |
| **Class** | A user-defined type of Resource within a Data Model | Predicate |
| **Property** | A user-defined attribute or relationship a Class may carry | Field, predicate in domain prose |
| **Cardinality** | Constraint on how many Values of a Property a Resource may or must have | Multiplicity when speaking to domain experts |
| **List** | Hierarchical controlled vocabulary authored as part of a Data Model | Standalone administration tree |
| **Standoff class / mapping** | Definition face of rich text: standoff tag Classes and XML-to-standoff mappings | Standoff markup |

“Ontology” remains valid as an RDF implementation term and in existing code names such as
`OntologyIri`. Use **Data Model** for domain meaning.

## Resources & Values

Active VRE instance data conforming to a Data Model.

| Term | Definition | Aliases to avoid |
| --- | --- | --- |
| **Resource** | Instance of a Data Model Class; aggregate root and consistency scope | Record, generic entity |
| **Value** | Versioned entity holding one Property Value inside a Resource; it has no independent lifecycle | Standalone field |
| **File Value** | Value representing an Asset through metadata and an internal filename | Asset bytes |
| **Standoff markup** | Instance face of rich text: tags attached to a Text Value | Standoff definition |
| **Object-access permission** | Fine-grained access carried by a Resource or Value and evaluated against a Permission profile | Scope, administrative permission |

## Search

| Term | Definition | Aliases to avoid |
| --- | --- | --- |
| **Gravsearch** | dsp-api's Data-Model-aware query language for retrieving Resources | SPARQL public interface |
| **Full-text retrieval** | Text-oriented Resource retrieval that currently requires little Data Model knowledge | Gravsearch |
| **Type inspection** | Inferring the Data Model type of each variable in a Gravsearch query | Validation only |
| **Prequery / main query** | Two-phase internal query process: identify paged Resource IRIs, then retrieve complete Resources | Two public searches |
| **Data Model projection** | Stable published view of Classes and Properties that retrieval can consume without importing Data Model implementation details | Raw ontology cache |

Search remains one provisional context while full-text, Gravsearch, filtering, and faceting converge.

## Assets

| Term | Definition | Aliases or interpretations to avoid |
| --- | --- | --- |
| **Asset** | Binary file represented in the VRE by metadata and an identifier; bytes are handled by Sipi and ingest infrastructure | File Value, archival record |
| **Sipi** | IIIF image system used to transform and serve asset bytes | Archive |
| **dsp-ingest** | Ingest module currently handling asset ingestion and storage operations; planned to move into dsp-api | Project Migration, Archive |
| **Serving-policy enforcement** | Applying Project settings such as Restricted View when Assets are served | Permission administration |

## Project Migration

| Term | Definition | Aliases or interpretations to avoid |
| --- | --- | --- |
| **Project Migration** | Whole-Project import/export and handoff, including Data Model, Resources, and required administration data | Archiving |
| **Migration bundle** | Portable representation used to transfer a Project | Archival package unless it conforms to the separate Archive contract |
| **Data Task** | Tracked long-running operation with a lifecycle and status | Untracked background job |
| **Bulk graph movement** | Intentional whole-graph transfer using the RDF platform | General permission for cross-context SPARQL |

## Operations

| Term | Definition | Aliases to avoid |
| --- | --- | --- |
| **Operations** | Genuine cross-context maintenance workflows that cannot remain with one owning context | Admin dumping ground |
| **Maintenance workflow** | Deliberate repair or migration operation; it belongs to Operations only when it coordinates multiple contexts | Any admin endpoint |

## Technical language

| Term | Definition | Aliases to avoid |
| --- | --- | --- |
| **Data IRI** | Schema-invariant identifier such as a Resource, Value, Project, User, Group, List, or Permission IRI | Definition IRI |
| **Definition IRI** | Schema-variant identifier for a Data Model entity, with internal and public forms | Generic Data IRI |
| **Schema** | Representation of a Definition IRI or entity: internal, v2 Complex, or v2 Simple | Data Model |
| **Foundation primitive** | Universal validation or value with no domain dependency | Every cross-context identifier |
| **Permission policy** | Pure permission levels plus `hasPermissions` parsing and comparison | Permission administration |
| **RDF platform** | Generic triplestore execution, transactions, and RDF library integration | Shared domain model, shared kernel |
| **Context-owned RDF adapter** | Adapter containing the triples and queries whose meaning belongs to one context | Global repository layer |
| **Authentication** | Credential, JWT, Scope, and request-authentication concerns | Object-access authorization |
| **Scope** | Coarse JWT grant used to gate an endpoint | Object-access permission |
| **InstanceUsage** | Data Model-owned interface for asking whether a Class or Property is used by Resources | Raw cross-context SPARQL |
| **Ratchet** | Existing cross-context SPARQL may remain temporarily; new cross-context reads use published interfaces | Big-bang rewrite |
| **Intrinsic RDF-platform user** | Search query translation or Project Migration bulk movement where low-level RDF execution is part of the implementation | Any context that can reach the triplestore |

Data IRIs are not automatically globally owned. A Project IRI normally belongs to a small Projects
contract, a User IRI to Identity & Access, and a Resource IRI to Resources & Values. The Data IRI /
Definition IRI distinction describes behaviour; context ownership describes dependency direction.

## Relationships

- A **Project** owns one or more **Data Models**, the **Resources** conforming to them, and Project
  settings.
- **Identity & Access** owns Project and Group **Memberships** and computes a User's **Permission
  profile**.
- A **Data Model** defines **Classes**, **Properties**, **Cardinalities**, and **Lists**.
- A **Resource** is an instance of a **Class** and contains **Values**; each Value belongs to exactly
  one Resource.
- A **File Value** references an **Asset** but does not contain its bytes.
- **Gravsearch** is expressed against a Data Model and returns Resources.
- **Project Migration** transfers VRE data; it does not preserve it as the Archive.
- A **Definition IRI** may convert between Schemas; a **Data IRI** may not.
- Context-specific RDF meaning remains in a **context-owned RDF adapter** even when several adapters
  use the same **RDF platform**.

## Example dialogue

> **Developer:** “When a user tightens a Cardinality in their Data Model, can we update it directly?”
>
> **Domain expert:** “Only if existing Resources remain valid. Data Model asks `InstanceUsage`
> whether the Property or Class is already used.”
>
> **Developer:** “Does Data Model query the Resource triples itself?”
>
> **Domain expert:** “Not for new work. Resources & Values implements the `InstanceUsage` interface
> through its own RDF adapter, so the Resource representation keeps locality.”
>
> **Developer:** “And schema conversion belongs only to the Definition IRI?”
>
> **Domain expert:** “Exactly. Converting a Resource IRI or Project IRI should be a compile error.”

## Flagged ambiguities

- **Search:** the context is stable enough to name, but its internal split remains provisional.
- **Operations:** create the module only for proven cross-context workflows.
- **Published identifier contracts:** decide case by case whether a consumer needs a tiny contract
  or a dependency on the owning domain module.
- **Migration bundle versus archival package:** do not use the terms interchangeably unless a
  bundle is explicitly made to satisfy the future Archive contract.
