---
title: "Cross-parse-boundary matching is fragile — inject metadata before lossy parse"
date: 2026-02-17
issue: DEV-5869
category: logic-errors
component: rdf_parsing
module: dsp-api/ApiComplexV2JsonLdRequestParser
problem_type: logic
severity: medium
symptoms: |
  An earlier approach tried to preserve ordering by capturing JSON array positions before
  Jena parsing and matching them back after using valueAsString. This failed because
  valueAsString only exists on TextValue and DateValue — not the other 13 value types.
root_cause: |
  Each value type uses a different identifying field in JSON-LD. Post-hoc matching would
  require per-type extraction logic — fragile and hard to maintain across 15 value types.
tags:
  - json-ld
  - jena
  - value-ordering
  - parsing
  - design-pattern
  - type-agnostic
related:
  - ./jena-jsonld-parser-loses-array-order.md
  - ./query-builder-refactoring-breaks-ordering.md
  - ./value-has-order-optional-cardinality.md
  - ../test-failures/alphabetical-test-data-masks-ordering.md
  - ./hashmap-loses-insertion-order-use-listmap.md
---

## Problem

An earlier approach (PR #3942) tried to preserve ordering by capturing JSON array positions *before* Jena parsing and then matching them back *after* using `knora-api:valueAsString`. This failed because `valueAsString` only exists on `TextValue` and `DateValue` — not on the other 13 value types.

## Root Cause

Each value type uses a different identifying field in JSON-LD:

| Value Type | Identifying Field |
| --- | --- |
| TextValue | `knora-api:valueAsString` |
| DateValue | `knora-api:valueAsString` |
| IntegerValue | `knora-api:intValueAsInt` |
| LinkValue | `knora-api:linkValueHasTargetIri` |
| FileValue | `knora-api:fileValueHasFilename` |
| ... | (13 more types) |

Post-hoc matching would require per-type extraction logic — fragile and hard to maintain.

## Solution

Replaced the match-based approach with `injectOrderIndices`, which injects an `orderIndex` property into the raw JSON *before* Jena parses it. The index survives as a regular RDF property on each blank node, so no per-type matching is needed. The `orderHint: Option[Int]` field on `CreateValueInNewResourceV2` threads the index through to the handler.

## Prevention

- Don't try to match values across a lossy parse boundary. Instead, inject the metadata you need *before* the lossy step so it comes out the other side intact.
- Prefer type-agnostic solutions over per-type extraction.

## References

- `ApiComplexV2JsonLdRequestParser.scala`
- `CreateValueInNewResourceV2`
- `CreateResourceV2Handler`
- PR #3942 — earlier failed approach
