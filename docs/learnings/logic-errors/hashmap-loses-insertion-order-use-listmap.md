---
title: "HashMap loses insertion order — use ListMap"
date: 2026-02-17
issue: DEV-5869
category: logic-errors
component: zio_service
module: dsp-api/ZioHelper
problem_type: logic
severity: medium
symptoms: |
  Values arrived at the handler in scrambled order despite being correctly ordered
  during parsing. The ordering was lost somewhere in the ZIO effect pipeline.
root_cause: |
  ZioHelper.sequence used HashMap internally (.toMap produces HashMap by default),
  which doesn't preserve insertion order.
tags:
  - scala
  - collections
  - hashmap
  - listmap
  - insertion-order
  - zio
related:
  - ./jena-jsonld-parser-loses-array-order.md
  - ./query-builder-refactoring-breaks-ordering.md
  - ./value-has-order-optional-cardinality.md
  - ../test-failures/alphabetical-test-data-masks-ordering.md
  - ./inject-metadata-before-lossy-parse.md
---

## Problem

Values arrived at the handler in scrambled order despite being correctly ordered during parsing. The ordering was lost somewhere in the ZIO effect pipeline.

## Root Cause

`ZioHelper.sequence` used `HashMap` internally, which doesn't preserve insertion order:

```scala
// BUG: HashMap scrambles key ordering
ZIO.foreach(map.toList) { case (k, v) => ... }.map(_.toMap)  // .toMap -> HashMap
```

## Solution

Switch to `ListMap` to preserve insertion order through the ZIO effect pipeline:

```scala
// CORRECT: ListMap preserves insertion order
ZIO.foreach(map.toList) { case (k, v) => ... }.map(results => ListMap(results: _*))
```

## Prevention

- In Scala, always verify which `Map` implementation flows through your code if ordering matters.
- `HashMap` scrambles; `ListMap` and `LinkedHashMap` preserve insertion order.
- Pay special attention to `.toMap` calls — they produce `HashMap` by default.

## References

- `ZioHelper.scala`
