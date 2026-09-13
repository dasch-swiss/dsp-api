# Operations

Part of the dsp-api context map; see [the root index](../../../CONTEXT.md). This file lives under
`docs/contexts/` only until the context is extracted into its own module; the extraction PR moves it to
that module's root directory (see [`MODULARIZATION-PLAN.md`](../../../MODULARIZATION-PLAN.md)).

Operations owns only genuine cross-context maintenance workflows. It exists to hold repair and
migration work that no single context can own, and it is deliberately narrow so that it does not
become a replacement dumping ground.

## Ownership notes

- Maintenance that concerns one context stays with that owner.
- Operations must not become a replacement dumping ground for the current `admin`, `store`, or root
  packages.
- Whether a dedicated Operations module is needed remains conditional on identifying enough real
  cross-context workflows.

## Language

**Operations**:
Genuine cross-context maintenance workflows that cannot remain with one owning context.
_Avoid_: Admin dumping ground

**Maintenance workflow**:
A deliberate repair or migration operation, which belongs to Operations only when it coordinates
multiple contexts.
_Avoid_: Any admin endpoint

## Relationships

- **Operations** depends on the published interfaces of the contexts it coordinates, currently
  **Projects**, **Identity & Access**, **Data Model**, and **Resources & Values**.
- A **Maintenance workflow** that touches one context belongs to that context, not to **Operations**.
- **Operations** defines no domain meaning of its own; it composes meaning owned elsewhere.

## Example dialogue

> **Dev:** "This repair endpoint rewrites bad **Values** in one **Project**. Does it belong to
> **Operations**?"
>
> **Domain expert:** "No. It touches one context, so it stays with **Resources & Values**."
>
> **Dev:** "And if it also had to rebuild **Permission profiles** and a **Data Model** cache?"
>
> **Domain expert:** "Then it is a real cross-context **Maintenance workflow**, and Operations
> coordinates it through published interfaces."

## Flagged ambiguities

- "Admin" was read as a home for any maintenance endpoint. Resolved: create the Operations module
  only for proven cross-context workflows, and leave single-context maintenance with its owner.
- Which maintenance workflows justify a dedicated Operations module is still open; see the root
  index.
