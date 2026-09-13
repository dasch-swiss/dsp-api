# Project Migration

Part of the dsp-api context map; see [the root index](../../../CONTEXT.md). This file lives under
`docs/contexts/` only until the context is extracted into its own module; the extraction PR moves it to
that module's root directory (see [`MODULARIZATION-PLAN.md`](../../../MODULARIZATION-PLAN.md)).

Project Migration owns VRE import and export, migration bundles, bulk graph movement, validation,
and the Data Task lifecycle. It exists as its own context because moving a whole Project in or out
of the VRE coordinates many upstream contexts at once, which places it intentionally near the top of
the dependency graph.

## Ownership notes

- Bulk named-graph movement may use the RDF platform directly.
- Reads of Projects, Users, Groups, or permissions must use their published interfaces.
- Project Migration hands data out of or into the VRE. It is not the Archive and does not own
  archival custody or long-term preservation.

## Language

**Project Migration**:
Whole-Project import, export, and handoff, including Data Model, Resources, and required
administration data.
_Avoid_: Archiving

**Migration bundle**:
A portable representation used to transfer a Project.
_Avoid_: Archival package unless it conforms to the separate Archive contract

**Data Task**:
A tracked long-running operation with a lifecycle and status.
_Avoid_: Untracked background job

**Bulk graph movement**:
An intentional whole-graph transfer using the RDF platform.
_Avoid_: General permission for cross-context SPARQL

## Relationships

- **Project Migration** transfers VRE data; it does not preserve it as the **Archive**.
- A **Migration bundle** carries exactly one **Project**, its **Data Models**, its **Resources**, and
  the required administration data.
- A **Data Task** tracks the lifecycle and status of one migration operation.
- **Project Migration** depends on **Projects**, **Identity & Access**, **Data Model**,
  **Resources & Values**, **Assets**, and the **RDF platform**.
- **Project Migration** is an **Intrinsic RDF-platform user** for **Bulk graph movement** only; all
  other cross-context reads go through published interfaces.

## Example dialogue

> **Dev:** "Export needs the Project's Groups. Can I read those triples directly while I am already
> moving the named graph?"
>
> **Domain expert:** "No. **Bulk graph movement** is the only intrinsic RDF use here. Groups come
> from the **Identity & Access** published interface."
>
> **Dev:** "Is the **Migration bundle** the archival package then?"
>
> **Domain expert:** "Only if it is explicitly built to satisfy the future **Archive** contract.
> Otherwise they are different things."

## Flagged ambiguities

- **Migration bundle** versus archival package: do not use the terms interchangeably unless a bundle
  is explicitly made to satisfy the future Archive contract.
- Export was read as archiving. Resolved: Project Migration is VRE handoff, not archival custody.
- Whether Project Migration remains one deep module once interactive export and whole-project
  migration interfaces are visible is still open; see the root index.
