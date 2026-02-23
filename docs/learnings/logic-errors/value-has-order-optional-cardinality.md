---
title: "valueHasOrder is optional — file values may not have it"
date: 2026-02-17
issue: DEV-5867
category: logic-errors
component: database_query
module: dsp-api/InsertValueQueryBuilder
problem_type: logic
severity: medium
symptoms: |
  The edit-ordering fix failed on file values with a SPARQL binding error because
  valueHasOrder was assumed to always exist.
root_cause: |
  The ontology defines valueHasOrder with owl:maxCardinality 1 (optional, not required).
  File values may not have this property set at all.
tags:
  - sparql
  - ontology
  - cardinality
  - value-ordering
  - optional-property
related:
  - ./jena-jsonld-parser-loses-array-order.md
  - ./query-builder-refactoring-breaks-ordering.md
  - ../test-failures/alphabetical-test-data-masks-ordering.md
  - ./inject-metadata-before-lossy-parse.md
  - ./hashmap-loses-insertion-order-use-listmap.md
---

## Problem

The edit-ordering fix failed on file values with a SPARQL binding error. The fix assumed `valueHasOrder` would always be present on a value.

## Root Cause

The ontology defines `valueHasOrder` with `owl:maxCardinality 1` (optional, not required). File values may not have this property set at all.

## Solution

Use `OPTIONAL` binding with a fallback to 0, matching the old Twirl template's behavior:

```sparql
OPTIONAL { <currentValueIri> knora-base:valueHasOrder ?existingOrder . }
BIND(IF(BOUND(?existingOrder), ?existingOrder, 0) AS ?nextOrder)
```

## Prevention

- Always check the ontology's cardinality constraints (`minCardinality` / `maxCardinality`) before assuming a property exists on all instances.
- Use `OPTIONAL` + `BIND(IF(BOUND(...)))` for properties that may be absent.

## References

- `InsertValueQueryBuilder.scala`
- `knora-ontologies/knora-base.ttl` — cardinality definition
