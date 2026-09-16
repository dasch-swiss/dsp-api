# C4 Model and ADRs

## Architecture decision records

ADRs live in `docs/adr/` (start with [ADR-0001](../adr/0001-record-architecture-decisions.md)) as
`NNNN-slug.md`, numbered sequentially and append-only. No tooling
is required: copy the shape of the latest file, take the next number, and add the file to the
`Decisions (ADR)` section of `mkdocs.yml`. Active ADRs keep their concrete references current; deprecated or
superseded ADRs stay frozen apart from a `superseded by ADR-NNNN` status line. The `/dune:grill` skill can
draft one for you after a design discussion.

## C4 model

Run the following command from the root directory to start the C4 model browser:

```bash
just structurizer
```
