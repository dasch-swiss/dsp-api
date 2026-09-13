# Data Model

Part of the dsp-api context map; see [the root index](../../../CONTEXT.md). This file lives under
`docs/contexts/` only until the context is extracted into its own module; the extraction PR moves it to
that module's root directory (see [`MODULARIZATION-PLAN.md`](../../../MODULARIZATION-PLAN.md)).

Data Model owns the user-authored model that VRE data must conform to: Classes, Properties,
Cardinalities, controlled vocabularies (Lists), Definition IRIs, schema conversion, and standoff
definitions and mappings. It exists because modelling is a distinct authoring act with its own
evolution rules, separate from the instance data that conforms to it.

> Code paths are relative to `modules/webapi/src/main/scala/org/knora/webapi/` unless otherwise
> prefixed.

## Ownership notes

- Current code is spread across `slice/ontology`, `slice/lists`, `slice/admin`, legacy ontology
  responders and messages, and shared IRI machinery.
- The domain term is **Data Model**. "Ontology" is the RDF implementation term and remains valid in
  code names such as `OntologyIri`.
- Lists belong to Data Model. Defining a controlled vocabulary is a modelling act, regardless of its
  current admin route or storage in the data graph. List-node queries currently living under
  `slice/resources` do not change that ownership.
- Standoff classes and XML mappings are definitions and belong here. Standoff markup on a Text Value
  belongs to Resources & Values.
- Schema-variant Definition IRIs and their conversion belong here, not in a global identifier
  module.
- Data Model defines the consumer-owned `InstanceUsage` interface; Resources & Values implements it.

## Language

**Data Model**:
A Project's Classes, Properties, Cardinalities, controlled vocabularies, and related definitions.
_Avoid_: Ontology in domain prose

**Class**:
A user-defined type of Resource within a Data Model.
_Avoid_: Predicate

**Property**:
A user-defined attribute or relationship a Class may carry.
_Avoid_: Field, predicate in domain prose

**Cardinality**:
A constraint on how many Values of a Property a Resource may or must have.
_Avoid_: Multiplicity when speaking to domain experts

**List**:
A hierarchical controlled vocabulary authored as part of a Data Model.
_Avoid_: Standalone administration tree

**Standoff class / mapping**:
The definition face of rich text: standoff tag Classes and XML-to-standoff mappings.
_Avoid_: Standoff markup

"Ontology" remains valid as an RDF implementation term and in existing code names such as
`OntologyIri`. Use **Data Model** for domain meaning.

## Relationships

- A **Project** owns one or more **Data Models**.
- A **Data Model** defines **Classes**, **Properties**, **Cardinalities**, and **Lists**.
- A **Cardinality** constrains how many **Values** of a **Property** a **Resource** of a **Class**
  may or must have.
- A **Resource** is an instance of a **Class**; **Resources & Values** therefore depends on
  **Data Model**.
- **Data Model** owns conversion of a **Definition IRI** between **Schemas**; a **Data IRI** does not
  convert.
- **Data Model** asks **InstanceUsage** whether a **Class** or **Property** is used by any
  **Resource**; **Resources & Values** implements that interface through its own RDF adapter.
- **Search** consumes **Data Model** meaning and should eventually consume a published
  **Data Model projection**.

## Example dialogue

> **Dev:** "When a user tightens a **Cardinality** in their **Data Model**, can we update it
> directly?"
>
> **Domain expert:** "Only if existing **Resources** remain valid. Data Model asks `InstanceUsage`
> whether the **Property** or **Class** is already used."
>
> **Dev:** "Does Data Model query the Resource triples itself?"
>
> **Domain expert:** "Not for new work. **Resources & Values** implements the `InstanceUsage`
> interface through its own RDF adapter, so the Resource representation keeps locality."
>
> **Dev:** "And schema conversion belongs only to the **Definition IRI**?"
>
> **Domain expert:** "Exactly. Converting a Resource IRI or Project IRI should be a compile error."

## Flagged ambiguities

- "Ontology" was used for both the user-authored model and its RDF encoding. Resolved: **Data Model**
  is the domain term; Ontology stays an RDF implementation term and a valid code name.
- **Lists** were treated as administration data because of their admin route and data-graph storage.
  Resolved: Lists belong to Data Model.
- "Standoff" named both definitions and instance markup. Resolved: **Standoff class / mapping**
  belongs here, **Standoff markup** belongs to Resources & Values.
