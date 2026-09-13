# Projects

Part of the dsp-api context map; see [the root index](../../../CONTEXT.md). This file lives under
`docs/contexts/` only until the context is extracted into its own module; the extraction PR moves it to
that module's root directory (see [`MODULARIZATION-PLAN.md`](../../../MODULARIZATION-PLAN.md)).

Projects owns project identity, metadata, lifecycle, legal defaults, licences, and project-level
settings. It exists because a Project is the ownership and scoping unit every other VRE context
hangs off, and that scoping is a domain concern rather than an administrative delivery concern.

> Code paths are relative to `modules/webapi/src/main/scala/org/knora/webapi/` unless otherwise
> prefixed.

## Ownership notes

- Today this is mixed into `slice/admin`, but administrative delivery is not a domain seam.
- Projects is separate from Identity & Access. Memberships refer to a Project but remain owned by
  Identity & Access.
- Enriched HTTP responses that combine project data with users or data-model IRIs are query
  composition above the domain modules, not evidence that Projects owns those concepts.
- Restricted View configuration is a Project setting; enforcement belongs to Assets.
- `ProjectIri` is a shared-kernel value type. Project details cross a context boundary only through
  a port the consuming context declares, and Projects implements those adapters, for example the
  project snapshot Project Migration needs
  ([ADR-0011](../../adr/0011-cross-context-access-ports-and-adapters.md)).

## Language

**Project**:
The ownership and scoping unit for Data Models, Resources, settings, and access administration.
_Avoid_: Tenant

**Project settings**:
Project-owned configuration such as legal defaults, licences, and Restricted View policy.
_Avoid_: User settings

**Shortcode**:
A stable short identifier used to scope a Project's data and identifiers.
_Avoid_: Project IRI

**Restricted View**:
A Project policy for reduced-resolution or watermarked asset serving, configured by Projects and
enforced by Assets.
_Avoid_: Object-access permission

## Relationships

- A **Project** owns one or more **Data Models**, the **Resources** conforming to them, and
  **Project settings**.
- A **Shortcode** scopes exactly one **Project**.
- **Identity & Access** depends on **Projects**: **Memberships** and administrative permissions are
  project-scoped.
- **Assets** depends on **Projects**: asset settings and policies are project-scoped.
- **Resources & Values** depends on **Projects**: every **Resource** is project-owned.
- **Project Migration** and **Operations** read **Projects** through ports they declare and
  **Projects** implements.
- **Restricted View** is configured as a **Project setting** and enforced by **Assets**.

## Example dialogue

> **Dev:** "A **Project** response also lists its members. Does that make **Membership** a Project
> concept?"
>
> **Domain expert:** "No. That response is composed above the domain. **Identity & Access** owns
> **Membership**; **Projects** only owns the scope it points at."
>
> **Dev:** "And **Restricted View**?"
>
> **Domain expert:** "Configured here as a **Project setting**. Enforced by **Assets** when bytes
> are served."

## Flagged ambiguities

- "Project" was used both for the scoping unit and for its administration data. Resolved: Projects
  is a context separate from Identity & Access, and Memberships name a Project without being owned
  by it.
- "Tenant" appeared as a synonym for Project. Resolved: use **Project**.
