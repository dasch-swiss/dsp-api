# Project Data Models

## Purpose and Intent

Project data models — technically realised as OWL ontologies — are the mechanism through which research projects define the structure of their data on DSP. Each project creates one or more data models that describe the types of objects it works with, the attributes and relationships it captures, and the structural constraints that ensure data consistency.

This reflects a core design principle of the platform: projects must be able to model their data according to their own domain-specific needs, while the platform provides a shared foundation (the knora-base ontology) that ensures all project data remains queryable, citable, and preservable regardless of how individual projects structure it.

The result is a two-layer architecture: knora-base defines the generic building blocks (resources, values, links, permissions, versioning), and project data models specialise these into domain-specific concepts. A musicology project and an art history project will have very different data models, but both ultimately extend the same base, which is what allows DSP to provide uniform services — search, access control, archival — across all projects.

## Core Concepts

A project data model is composed of **classes**, **properties**, and **cardinalities**.

**Classes** represent the types of research objects a project works with — the things that exist in its domain. A project studying medieval manuscripts might define classes like `Manuscript`, `Person`, and `Repository`. Each class extends one of knora-base's resource types, which determines what kind of digital representation it carries (e.g. still image, document, audio, or none).

**Properties** represent what can be said about instances of a class — their attributes and their relationships to other objects. Each property has a defined value type: text, date, number, list selection, link to another resource, and so on. These value types are provided by knora-base and cannot be extended by projects. Properties also carry constraints on where they can be used: a property can restrict which classes it may appear on (its subject type), and link properties can restrict which classes they may point to (their object type). This means the property definition itself encodes domain rules — a `wasWrittenBy` link property might only be valid on `Manuscript` and only link to `Person`.

**Cardinalities** define, for each class, which properties it has and whether they are required or optional, singular or repeatable. A `Manuscript` class might require exactly one `title` (1), allow an optional `subtitle` (0-1), and permit any number of `author` links (0-n). Cardinalities are the primary mechanism for expressing structural expectations about data completeness — they enforce at the platform level what a project considers a well-formed record.

Together, these three elements form a schema that governs how data is created, validated, and queried within a project.

## Linking to External Ontologies

### What This Means

Research data does not exist in isolation. The concepts a project models — persons, places, objects, events, dates, descriptions — often correspond to concepts that have already been formally defined in established, community-maintained vocabularies. Linking to external ontologies means declaring these correspondences explicitly: a project states that its concept is a specialisation of a concept defined in a recognised standard.

For example, a project that defines a `Person` class can declare that it is a specialisation of `foaf:Person`. A project that defines a `description` property can declare it a specialisation of `dcterms:description`. These are not loose annotations or informal labels — they are formal semantic relationships that become part of the ontology's definition.

The relationship is one of specialisation, not equivalence. A project's `Person` class may carry constraints, properties, and semantics that go beyond what `foaf:Person` defines. The declaration means "every instance of our Person is also a foaf:Person", not "our Person is the same thing as foaf:Person". This is a deliberate modelling choice: DSP uses `rdfs:subClassOf` and `rdfs:subPropertyOf` rather than equivalence constructs like `owl:sameAs` or `skos:exactMatch`.

### Why This Matters

Linking to external ontologies serves several purposes that domain experts consider central to data quality:

**Interoperability.** When a project maps its concepts to established standards, its data becomes interpretable beyond the project's own context. An external consumer encountering the data does not need to understand the project's bespoke vocabulary — they can recognise the standard concepts it maps to and integrate the data with other sources that use the same standards.

**Conceptual rigour.** The act of mapping forces a project to critically examine its own model against established domain ontologies. If a project's `Event` class cannot meaningfully be declared a specialisation of any CIDOC-CRM event type, that may reveal that the concept is poorly defined or conflates multiple concerns. The mapping process is a form of conceptual validation.

**Discoverability.** When multiple projects on DSP link their classes to the same external concept, this creates a shared semantic layer that could, in principle, enable cross-project search and comparison. Two projects that both declare a `foaf:Person` subclass are implicitly stating that their person data is comparable at that level of abstraction.

**Longevity.** Standards like CIDOC-CRM, Dublin Core, and Schema.org are maintained by international communities with governance processes designed for stability. Linking to them anchors project data to reference points that are likely to remain meaningful and maintained well beyond the lifecycle of any individual project — which aligns with DSP's mandate as a long-term preservation platform.

### Commonly Relevant External Ontologies

**CIDOC-CRM** (`http://www.cidoc-crm.org/cidoc-crm/`) is the international reference ontology for cultural heritage. It provides a rich model for events, actors, physical and conceptual objects, places, time-spans, and the relationships between them. Its event-centric approach — modelling what happened, involving whom, where, and when — makes it particularly relevant for humanities projects dealing with historical processes. CIDOC-CRM is an ISO standard (ISO 21127) and the most widely adopted formal ontology in the cultural heritage sector.

**Dublin Core Terms** (`http://purl.org/dc/terms/`) is a lightweight, widely-used vocabulary for describing resources with basic metadata: title, creator, date, description, subject, format, rights, and similar. Its simplicity makes it broadly applicable — almost any project will have properties that correspond to Dublin Core terms. It is especially useful as a baseline layer of interoperability even when more specialised ontologies are also in use.

**Schema.org** (`http://schema.org/`) is a broad vocabulary originally developed for web search engines, covering persons, organisations, places, creative works, events, and much more. While less specialised than CIDOC-CRM for cultural heritage, its wide adoption across the web means that mapping to Schema.org concepts can make data more accessible to general-purpose tools and services.

**FOAF** (`http://xmlns.com/foaf/0.1/`) — the Friend of a Friend vocabulary — provides terms for describing people, their names, and their social and professional connections. It is narrower in scope than the others but commonly used for person-related concepts. DSP's own `kb:User` class already extends `foaf:Person`.

These are not mutually exclusive. A project might link its person classes to both FOAF and CIDOC-CRM, its descriptive properties to Dublin Core, and its place concepts to Schema.org, depending on which standards are most relevant for each part of its model.

### How DSP Represents This

When a project declares that one of its concepts specialises an external concept, this is stored as a standard RDF/OWL relationship:

- For classes: an `rdfs:subClassOf` triple pointing from the project's class IRI to the external class IRI
- For properties: an `rdfs:subPropertyOf` triple pointing from the project's property IRI to the external property IRI

A project class or property can have multiple such links — it can simultaneously specialise a knora-base type (which is required) and one or more external concepts. For example, a `Person` class might be both a subclass of `kb:Resource` (required by the platform) and of `foaf:Person` and `crm:E21_Person` (chosen by the project).

External ontologies are referenced via their full IRIs. When creating or updating a class through the API, the external super-classes appear as `rdfs:subClassOf` entries in the JSON-LD payload alongside the required knora-base super-class. The same applies to properties via `rdfs:subPropertyOf`. The API does not resolve or validate external IRIs — it stores the declared relationship as-is.

### Current State and Limitations

The ability to link project concepts to external ontologies is fully functional at the API level. Classes and properties can declare any number of external super-classes and super-properties, and these relationships are persisted in the triplestore as part of the ontology definition.

However, several gaps limit how useful this capability is in practice:

**No UI support.** The ontology editor in DSP-APP does not expose external ontology linking. The `subClassOf` and `subPropertyOf` fields are set automatically based on the selected base type and are not user-editable. Projects that want to link to external ontologies must do so programmatically.

**Not surfaced in the frontend.** The JS library that powers the frontend filters out external ontology IRIs during ontology processing. Even when external links have been set via the API, they are not displayed anywhere in the application.

**No validation.** The API accepts any IRI as an external super-class or super-property without verifying that it resolves to a real concept in the target ontology. A typo or a reference to a non-existent term will be stored without error.

**No cross-project discovery.** Although the documentation notes that linking to external classes "can facilitate searches across projects", no such search capability is currently implemented. The external links exist as stored triples but are not indexed or queryable in a way that would enable cross-project discovery.

**Terminology gap.** The concept that domain experts refer to as "linking to external ontologies" or "mapping to external ontologies" is not named as such anywhere in the platform — not in the API, the documentation, or the codebase. The documentation describes the mechanism using RDF inheritance terminology (`rdfs:subClassOf`, `rdfs:subPropertyOf`), the dsp-tools documentation refers to "deriving from" or "referencing" external ontologies, and the code uses generic field names like `subClassOf` and `super`. Neither "linking" nor "mapping" appear as recognised terms for this concept in any layer of the system. This document is a first step toward closing that gap.
