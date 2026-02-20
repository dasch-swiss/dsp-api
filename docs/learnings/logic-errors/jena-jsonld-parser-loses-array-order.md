---
title: "Jena's JSON-LD parser loses array order"
date: 2026-02-17
issue: DEV-5869
category: logic-errors
component: zio_service
module: dsp-api/ApiComplexV2JsonLdRequestParser
problem_type: logic
severity: high
symptoms: |
  Creating a resource with multiple values for the same property scrambled their positions.
  Rich text values with standoff/XML consistently appeared in reversed order.
  Plain text values sometimes appeared in correct order by coincidence of IRI sorting.
root_cause: |
  Apache Jena's JSON-LD parser does not preserve JSON array order. Array elements become
  unordered RDF statements (blank nodes) because RDF is fundamentally a set of triples
  with no inherent ordering.
tags:
  - json-ld
  - jena
  - value-ordering
  - rdf
  - array-order
  - parsing
related:
  - ./query-builder-refactoring-breaks-ordering.md
  - ./value-has-order-optional-cardinality.md
  - ../test-failures/alphabetical-test-data-masks-ordering.md
  - ./inject-metadata-before-lossy-parse.md
  - ./hashmap-loses-insertion-order-use-listmap.md
---

## Problem

When creating a resource with multiple values for the same property via the API, the JSON array order was not preserved. Users expected values submitted as `["Alpha", "Bravo", "Charlie"]` to appear in that exact order when reading the resource back, but the order was scrambled — especially for rich text values with standoff markup.

### Symptoms

- Plain text values sometimes appeared in the correct order (by coincidence of IRI sorting)
- Rich text values with standoff/XML consistently appeared in reversed order
- Integer values appeared in arbitrary order

Rich text values were most visibly affected because their more complex blank node structure caused Jena to reorder them more aggressively than simple values.

## Root Cause

Apache Jena's JSON-LD parser uses hash-based `GraphMem2` internally. JSON array order is silently lost after parsing:

```
// Input JSON-LD array order:  [Value-A, Value-B, Value-C]
// After Jena parse:           [Value-C, Value-A, Value-B]  (hash-dependent)
```

This affects all value types, but rich text values (with standoff markup and mapping references) were most visibly affected because their more complex blank node structure caused Jena to reorder them more aggressively than simple values.

## Solution

### Pre-parse Order Injection

Inject array position indices into the JSON **before** Jena parses it, so the index survives as a regular RDF property on each value's blank node.

```scala
private val OrderIndexProperty = "http://knora.org/internal/orderIndex"

private[common] def injectOrderIndices(rawJson: String): String =
  rawJson.fromJson[Json.Obj].toOption match
    case None      => rawJson
    case Some(obj) =>
      val modified = obj.fields.map {
        case (key, Json.Arr(values)) if !key.startsWith("@") =>
          val indexed = values.zipWithIndex.map {
            case (Json.Obj(fields), idx) =>
              Json.Obj(fields :+ (OrderIndexProperty -> Json.Num(idx)))
            case (other, _) => other
          }
          (key, Json.Arr(indexed))
        case other => other
      }
      Json.Obj(Chunk.from(modified)).toJson
```

The injection happens in `RootResource.fromJsonLd` before Jena parsing. After Jena parses the JSON-LD, `extractValues` reads the `orderIndex` back and uses it to sort values before passing them to the creation handler.

This approach is **type-agnostic**: it works on all 15 value types without needing to know their internal JSON-LD structure.

### Map Ordering Fix

`ZioHelper.sequence` was also updated to use `ListMap` instead of `HashMap` to preserve insertion order through the ZIO effect pipeline:

```scala
def sequence[K, R, A](x: Map[K, ZIO[R, Throwable, A]]): ZIO[R, Throwable, Map[K, A]] =
  ZIO
    .foreach(Chunk.from(x.toSeq)) { case (k, v) => v.map(k -> _) }
    .map(pairs => ListMap.from(pairs))
```

### Test Coverage Added

| Test | Type | What It Proves |
| --- | --- | --- |
| Boolean values preserve order | Unit | JSON boolean literals work |
| Color values preserve order | Unit | String-based properties work |
| Decimal values preserve order | Unit | `xsd:decimal` typed literals work |
| URI values preserve order | Unit | `xsd:anyURI` typed literals work |
| Link values preserve order | Unit | `@id` reference objects work |
| Rich text XML round-trip | Unit | Standoff XML survives injection + Jena |
| IntValue array E2E | E2E | Full stack (API -> triplestore -> read back) |

## Prevention

- Never assume JSON array order survives a Jena parse round-trip. If array order matters, encode it explicitly as data *before* handing JSON to Jena — don't try to reconstruct it after.
- Test with structurally distinct value types: plain text, booleans, typed literals (`xsd:decimal`), IRI references (`@id`), and rich text with standoff all have different JSON-LD structures.
- When adding new value types, include them in `ValueOrderingSpec` to verify ordering.
- Any change to the JSON-LD parsing pipeline should re-run `ValueOrderingSpec` to catch regressions.
- E2E tests for ordering should cover at least one simple and one complex value type.

## References

- `ApiComplexV2JsonLdRequestParser.scala` — injection logic
- `ZioHelper.scala` — ListMap fix
- `ValueOrderingSpec` — unit tests for ordering
- `ValuesEndpointsE2ESpec` — E2E ordering tests
