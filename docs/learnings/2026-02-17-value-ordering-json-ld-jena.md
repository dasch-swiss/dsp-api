---
title: "Preserving JSON Array Order Through Jena JSON-LD Parsing"
date: 2026-02-17
issue: DEV-5869
category: data-integrity
component: ApiComplexV2JsonLdRequestParser
tags: [json-ld, jena, value-ordering, rdf, standoff, rich-text]
severity: high
---

# Preserving JSON Array Order Through Jena JSON-LD Parsing

## Problem

When creating a resource with multiple values for the same property via the API, the JSON array order was not preserved. Users expected values submitted as `["Alpha", "Bravo", "Charlie"]` to appear in that exact order when reading the resource back, but the order was scrambled — especially for rich text values with standoff markup.

### Symptoms

- Plain text values sometimes appeared in the correct order (by coincidence of IRI sorting)
- Rich text values with standoff/XML consistently appeared in reversed order
- Integer values appeared in arbitrary order

### Root Cause

**Apache Jena's JSON-LD parser does not preserve JSON array order.** When JSON-LD is parsed into an RDF model, array elements become unordered RDF statements (blank nodes). The original array position is lost because RDF is fundamentally a set of triples with no inherent ordering.

This affects all value types, but rich text values (with standoff markup and mapping references) were most visibly affected because their more complex blank node structure caused Jena to reorder them more aggressively than simple values.

## Solution

### Approach: Pre-parse Order Injection

Inject array position indices into the JSON **before** Jena parses it, so the index survives as a regular RDF property on each value's blank node.

**File:** `ApiComplexV2JsonLdRequestParser.scala`

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

### Why This Works for Rich Text

The injection is **type-agnostic** — it adds `orderIndex` to JSON objects at the top level of value arrays, regardless of their internal structure. Rich text values contain `textValueAsXml` (a string) and `textValueHasMapping` (an `@id` reference), but neither of these are arrays, so the injection doesn't recurse into them. The XML content passes through completely untouched.

### Map Ordering Fix

The `ZioHelper.sequence` method was also updated to use `ListMap` instead of `Map` to preserve insertion order through the ZIO effect pipeline:

```scala
def sequence[K, R, A](x: Map[K, ZIO[R, Throwable, A]]): ZIO[R, Throwable, Map[K, A]] =
  ZIO
    .foreach(Chunk.from(x.toSeq)) { case (k, v) => v.map(k -> _) }
    .map(pairs => ListMap.from(pairs))
```

## Key Lessons

1. **RDF is unordered by nature.** Never assume that JSON-LD array order will survive parsing into an RDF model. If order matters, you must encode it explicitly as data.

2. **Test with structurally distinct value types.** Plain text, booleans, typed literals (`xsd:decimal`), IRI references (`@id`), and rich text with standoff all have different JSON-LD structures. A fix that works for one may not work for another — or may appear to work by coincidence.

3. **Docker image vs local source.** When testing API fixes locally, ensure the API is running from local source (`sbt run`) not from the Docker image (`docker compose up api`). The Docker image uses the published version, not your branch.

4. **Coincidental ordering masks bugs.** Simple values (plain text, integers) happened to come back in the correct order without the fix because Jena preserved their simple blank node structure. This made it appear that only rich text was broken, when in fact ALL value types lacked ordering guarantees.

## Test Coverage Added

| Test | Type | What It Proves |
|---|---|---|
| Boolean values preserve order | Unit | JSON boolean literals work |
| Color values preserve order | Unit | String-based properties work |
| Decimal values preserve order | Unit | `xsd:decimal` typed literals work |
| URI values preserve order | Unit | `xsd:anyURI` typed literals work |
| Link values preserve order | Unit | `@id` reference objects work |
| Rich text XML round-trip | Unit | Standoff XML survives injection + Jena |
| IntValue array E2E | E2E | Full stack (API → triplestore → read back) |

## Prevention

- When adding new value types, include them in `ValueOrderingSpec` to verify ordering
- Any change to the JSON-LD parsing pipeline should re-run `ValueOrderingSpec` to catch regressions
- E2E tests for ordering should cover at least one simple and one complex value type
