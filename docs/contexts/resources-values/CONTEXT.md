# Resources & Values

Part of the dsp-api context map; see [the root index](../../../CONTEXT.md). This file lives under
`docs/contexts/` only until the context is extracted into its own module; the extraction PR moves it to
that module's root directory (see [`MODULARIZATION-PLAN.md`](../../../MODULARIZATION-PLAN.md)).

Resources & Values owns active VRE instance data: the Resource aggregate, Values, resource metadata,
file-value references, and standoff markup. It exists as one context because Resource is the
aggregate root and the consistency scope for everything a researcher edits.

> Code paths are relative to `modules/webapi/src/main/scala/org/knora/webapi/` unless otherwise
> prefixed.

## Ownership notes

- Current code is mostly in legacy resource, value, and standoff responders and messages, with newer
  work in `slice/resources`.
- Resource is the aggregate root. A Value is a versioned entity inside the Resource's consistency
  scope and has no independent lifecycle.
- The current responder split is a technical decomposition, not a domain seam.
- File Values remain here because they are Values. They reference Assets but do not transfer Resource
  ownership to the Assets context.
- A Resource IRI belongs to Resources & Values rather than to a global identifiers target.
- Resources & Values implements the Data Model-owned `InstanceUsage` interface through its own RDF
  adapter.

## Language

**Resource**:
An instance of a Data Model Class, and the aggregate root and consistency scope for its Values.
_Avoid_: Record, generic entity

**Value**:
A versioned entity holding one Property Value inside a Resource, with no independent lifecycle.
_Avoid_: Standalone field

**File Value**:
A Value representing an Asset through metadata and an internal filename.
_Avoid_: Asset bytes

**Standoff markup**:
The instance face of rich text: tags attached to a Text Value.
_Avoid_: Standoff definition

**Object-access permission**:
Fine-grained access carried by a Resource or Value and evaluated against a Permission profile.
_Avoid_: Scope, administrative permission

## Relationships

- A **Resource** is an instance of a **Class** and contains **Values**; each **Value** belongs to
  exactly one **Resource**.
- A **Resource** is owned by exactly one **Project** and conforms to that Project's **Data Model**.
- A **File Value** references an **Asset** but does not contain its bytes.
- **Standoff markup** attaches to a Text **Value** and uses the **Standoff classes and mappings**
  defined by **Data Model**.
- An **Object-access permission** on a **Resource** or **Value** is evaluated against a
  **Permission profile** from **Identity & Access** using the shared **Permission policy**.
- **Resources & Values** implements **InstanceUsage** for **Data Model**, so the compile-time edge
  runs from Resources & Values to Data Model and the graph stays acyclic.
- **Search** returns **Resources**.

## Example dialogue

> **Dev:** "Can I update a **Value** without going through its **Resource**?"
>
> **Domain expert:** "No. The **Resource** is the aggregate root and the consistency scope. A Value
> has no independent lifecycle."
>
> **Dev:** "What about a **File Value**? The bytes live in **Sipi**."
>
> **Domain expert:** "The File Value is still a Value here. It references an **Asset**; it does not
> hand the Resource to the Assets context."

## Flagged ambiguities

- "Standoff" named both definitions and instance markup. Resolved: **Standoff markup** belongs here,
  **Standoff class / mapping** belongs to Data Model.
- A **File Value** and an **Asset** were used interchangeably. Resolved: the File Value is a Value in
  this context; the Asset and its bytes belong to Assets.
- The responder split suggested several contexts. Resolved: Resources & Values is one context with
  **Resource** as aggregate root.
