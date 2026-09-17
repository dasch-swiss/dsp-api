# Assets

Part of the dsp-api context map; see [the root index](../../../CONTEXT.md). This file lives under
`docs/contexts/` only until the context is extracted into its own module; the extraction PR moves it to
that module's root directory (see [`MODULARIZATION-PLAN.md`](../../../MODULARIZATION-PLAN.md)).

Assets owns Sipi and ingest integration, asset metadata, byte-serving interfaces, serving-policy
enforcement, and the natural home for future ingest functionality. It exists because binary files
have their own infrastructure and serving rules, even though dsp-api currently retains only their
metadata and identifiers while Sipi and ingest handle bytes.

## Ownership notes

- File Values belong to Resources & Values, not to Assets.
- Restricted View configuration is a Project setting; enforcement belongs to Assets.
- Consolidating ingest into dsp-api expands Assets inside an existing seam. It does not make Assets
  responsible for archival custody.

## Language

**Asset**:
A binary file represented in the VRE by metadata and an identifier, whose bytes are handled by Sipi
and ingest infrastructure.
_Avoid_: File Value, archival record

**Sipi**:
The IIIF image system used to transform and serve asset bytes.
_Avoid_: Archive

**dsp-ingest**:
The ingest module currently handling asset ingestion and storage operations, planned to move into
dsp-api.
_Avoid_: Project Migration, Archive

**Serving-policy enforcement**:
The application of Project settings such as Restricted View when Assets are served.
_Avoid_: Permission administration

## Relationships

- A **File Value** references an **Asset** but does not contain its bytes.
- An **Asset** is scoped to exactly one **Project**; **Assets** therefore depends on **Projects**.
- **Restricted View** is configured as a **Project setting** and applied here through
  **Serving-policy enforcement**.
- **Sipi** and **dsp-ingest** handle **Asset** bytes; dsp-api retains **Asset** metadata and
  identifiers.
- **Project Migration** coordinates **Assets** alongside the other VRE contexts.

## Example dialogue

> **Dev:** "This **Project** has **Restricted View** on. Where do I apply it?"
>
> **Domain expert:** "Here, as **Serving-policy enforcement**. **Projects** owns the configuration;
> Assets owns what happens when bytes go out."
>
> **Dev:** "And once **dsp-ingest** moves into dsp-api, does Assets become the archive?"
>
> **Domain expert:** "No. That expands Assets inside an existing seam. Archival custody stays with
> the **Archive**."

## Flagged ambiguities

- **Asset** and **File Value** were used interchangeably. Resolved: the **File Value** is a Value in
  Resources & Values; the **Asset** and its bytes belong here.
- **Restricted View** was read as a single responsibility. Resolved: configuration belongs to
  Projects, enforcement to Assets.
- Holding asset bytes was read as archival custody. Resolved: Assets serves the VRE; the **Archive**
  is a separate system in the Repository architecture.
