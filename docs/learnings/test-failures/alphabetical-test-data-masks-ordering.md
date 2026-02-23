---
title: "Alphabetically-sorted test data masks ordering bugs"
date: 2026-02-17
issue: DEV-5869
category: test-failures
component: test_data
module: dsp-api/ValuesEndpointsE2ESpec
problem_type: test
severity: medium
symptoms: |
  The E2E test for batch-create value ordering passed despite the bug existing in production.
  Test values were alphabetically sorted, which coincidentally matched Jena's hash-based output.
root_cause: |
  Test values were ["Alpha", "Bravo", "Charlie", "Delta", "Echo"] — alphabetically sorted.
  Jena's hash-based scrambling happened to produce the same alphabetical order, so the test
  passed by coincidence.
tags:
  - testing
  - test-data
  - value-ordering
  - false-positive
  - non-determinism
related:
  - ../logic-errors/jena-jsonld-parser-loses-array-order.md
  - ../logic-errors/query-builder-refactoring-breaks-ordering.md
  - ../logic-errors/value-has-order-optional-cardinality.md
  - ../logic-errors/inject-metadata-before-lossy-parse.md
  - ../logic-errors/hashmap-loses-insertion-order-use-listmap.md
---

## Problem

The E2E test for batch-create value ordering passed despite the bug existing in production.

## Root Cause

Test values were `["Alpha", "Bravo", "Charlie", "Delta", "Echo"]` — alphabetically sorted. Jena's hash-based scrambling happened to produce the same alphabetical order, so the test passed by coincidence:

```
// Input:  ["Alpha", "Bravo", "Charlie", "Delta", "Echo"]
// Jena:   ["Alpha", "Bravo", "Charlie", "Delta", "Echo"]  <- coincidence!
// Real:   ["Delta", "Alpha", "Echo", "Bravo", "Charlie"]  <- what actually happens with non-alpha data
```

## Solution

Replaced with non-alphabetical test data and added a 10-value stress test to increase the probability of catching non-deterministic ordering.

## Prevention

- When testing ordering, always use non-alphabetical, non-sequential test data (e.g., `["Delta", "Alpha", "Echo", "Bravo", "Charlie"]`).
- Add stress tests with larger datasets (10+ items) — small datasets have higher odds of accidentally matching hash output.

## References

- `ValuesEndpointsE2ESpec.scala`
