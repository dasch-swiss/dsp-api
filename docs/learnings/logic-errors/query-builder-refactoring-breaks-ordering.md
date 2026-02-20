---
title: "Query builder migration breaks ordering semantics"
date: 2026-02-17
issue: DEV-5867
category: logic-errors
component: database_query
module: dsp-api/InsertValueQueryBuilder
problem_type: logic
severity: medium
symptoms: |
  After migrating value insert/update from Twirl templates to RDF4J's InsertValueQueryBuilder,
  editing a value moved it to the end of the list instead of preserving its position.
root_cause: |
  The old Twirl template correctly read valueHasOrder from the existing value and carried it
  forward on updates. The new builder used MAX(order) + 1 for both creates and updates,
  always appending to the end.
tags:
  - sparql
  - rdf4j
  - value-ordering
  - query-builder
  - migration
  - twirl
related:
  - ./jena-jsonld-parser-loses-array-order.md
  - ./value-has-order-optional-cardinality.md
  - ../test-failures/alphabetical-test-data-masks-ordering.md
  - ./inject-metadata-before-lossy-parse.md
  - ./hashmap-loses-insertion-order-use-listmap.md
---

## Problem

PR #3737 (October 2025) migrated value insert/update from Twirl templates to RDF4J's `InsertValueQueryBuilder`. After the migration, editing a value moved it to the end of the list instead of preserving its position.

## Root Cause

The old Twirl template (`addValueVersion.scala.txt`) correctly read `valueHasOrder` from the existing value and carried it forward on updates:

```sparql
# Old Twirl (correct) — preserves order on update
OPTIONAL { ?currentValue knora-base:valueHasOrder ?order . }
?newValue knora-base:valueHasOrder ?order .
```

The new builder used `MAX(order) + 1` for *both* creates and updates:

```scala
// New builder (buggy) — always appends to end
Expressions.max(order).as(maxOrder),
Expressions.add(maxOrder, literalOf(1)).as(nextOrder)
```

## Solution

Branch the order calculation in `buildWhereClause()` based on operation type. Updates read from the existing value; creates keep `MAX + 1`.

## Prevention

- When migrating query generation (e.g., Twirl to RDF4J), explicitly verify ordering and positional semantics.
- Unit tests that only check SPARQL syntax won't catch behavioral regressions like this — test the actual query behavior.

## References

- `InsertValueQueryBuilder.scala` (lines 632-654)
- PR #3737 — original migration
