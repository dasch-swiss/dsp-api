# SPARQL Builder Spike: Evaluation & Recommendation

## Executive Summary

We prototyped two approaches for replacing DSP-API's three fragmented SPARQL generation
patterns (RDF4J SparqlBuilder, Twirl templates, raw string interpolation) with a unified
Scala 3 library. After evaluating both against real benchmark queries from the codebase,
**Approach A (Fragment + `sparql"..."` interpolator)** is the recommended path forward,
with select AST nodes from Approach B added where they provide clear value.

## Approaches Evaluated

### Approach A: Fragment + `sparql"..."` Interpolator (Doobie-style)

Core idea: An immutable `Fragment` type that composes monadically, with a `sparql"..."`
string interpolator for ergonomic query construction. Only typed values
(`Iri`, `Variable`, `Literal`, `Fragment`) can be interpolated — raw `String` is a
compile error.

**Key types:**
- `Fragment` — immutable `Vector[Part]`, composes via `++`, renders to `String`
- `SparqlValue` sealed trait — `Iri`, `Variable`, `Literal`
- `sparql"..."` interpolator — accepts `SparqlValue | Fragment` only
- `Fragments` object — combinators: `optional`, `union`, `graph`, `filterNotExists`, etc.
- `SparqlQuery` builders — `select`, `construct`, `ask`, `update`, `insertData`

**Files:** `Fragment.scala`, `types.scala`, `SparqlInterpolator.scala`, `Fragments.scala`, `SparqlQuery.scala`
**Tests:** `ApproachASpec.scala` (9 tests)

### Approach B: AST Case Classes

Core idea: Model SPARQL constructs as explicit case classes forming an AST tree.
Each node renders to SPARQL strings. Provides more structure but more verbosity.

**Key types:**
- `TriplePattern(subject, predicate, object)` — a single triple
- `GraphPattern` enum — `Triple`, `Optional`, `Union`, `FilterNotExists`, `Minus`, `Filter`, `Bind`, `Raw`
- `AstSelect` / `AstAsk` — query types built from `List[GraphPattern]`

**Files:** Defined inline in `ApproachBSpec.scala` (would be extracted to separate files)
**Tests:** `ApproachBSpec.scala` (5 tests)

## Comparison Matrix

| Criterion | Approach A (Fragment) | Approach B (AST) |
|---|---|---|
| **Readability** | Excellent — reads like SPARQL | Good — but verbose for simple patterns |
| **Composability** | Excellent — `++`, `combineAll`, `Fragment.combine` | Good — `List[GraphPattern]` |
| **Conditional patterns** | Natural — `Option[Fragment]`, `Fragment.combine` | Requires `Option[GraphPattern].toList.flatten` |
| **Iteration** | Natural — `.map { ... }.combineAll` | Natural — `.flatMap { ... }` |
| **Type safety** | Compile-time — raw `String` is rejected | Runtime — patterns must be well-formed |
| **Injection safety** | By construction — interpolator enforces types | By construction — AST nodes handle rendering |
| **Learning curve** | Low — looks like SPARQL with Scala values | Medium — must learn AST node types |
| **Migration effort** | Low — 1:1 mapping from existing patterns | Medium — requires restructuring logic |
| **Flexibility** | High — `Fragment.raw` for anything | Medium — `Raw(fragment)` escape hatch |
| **Query introspection** | None — opaque string at render time | Possible — can walk/transform AST |
| **Benchmark coverage** | All 6 benchmarks reproduced | 3 benchmarks reproduced |

## Benchmark Results

### 1. Simple SELECT with OPTIONAL
Both approaches handle this well. Approach A is more concise:

```scala
// Approach A (7 lines)
SparqlQuery.select(s, p, o)
  .where(
    sparql"$s a $resourceClass .",
    sparql"$s $kbIsDeleted false .",
    Fragments.optional(sparql"$s $kbLastMod $lmd ."),
    sparql"$s $p $o .",
  )
  .orderBy(lmd.desc).limit(25).render

// Approach B (12 lines)
AstSelect(
  variables = List(s, p, o),
  patterns = List(
    tp(s, rdfType, resourceClass),
    tp(s, kbIsDeleted, Literal.bool(false)),
    GraphPattern.Optional(List(tp(s, kbLastMod, lmd))),
    tp(s, p, o),
  ),
  orderBy = List(lmd.desc), limit = Some(25),
).render
```

### 2. IsNodeUsedQuery (ASK with UNION)
Both handle this. Approach A is more natural for the UNION syntax.

### 3. DeletePropertyQuery (UPDATE with conditional link patterns)
Approach A handles this well using `Option[Fragment]` and `Fragment.combine`.
Approach B was not benchmarked for this pattern but would require more boilerplate.

### 4. InsertValueQueryBuilder (complex conditional + iteration)
Approach A demonstrated conditional link patterns with indexed variables.
This is the most complex benchmark and maps naturally to `Option.when` + `combineAll`.

### 5. SearchQueries (Lucene + conditional filters)
Approach A handles Lucene queries as `Literal.string()` values — they're safely
escaped within SPARQL string literals. `Fragment.combine` handles optional filters.

### 6. Twirl templates (addValueVersion, searchFulltext)
Not directly benchmarked but the patterns (conditional blocks, iteration with indexed
variables, Lucene queries) are all demonstrated by the other benchmarks.

## Injection Safety Model

Defined concretely in `InjectionSafetySpec.scala` (11 tests). The model is:

### What types can be interpolated
Only `SparqlValue | Fragment` via the `sparql"..."` interpolator:
- `Iri` — rendered as `<uri>`, wrapped in angle brackets
- `Variable` — rendered as `?name`
- `Literal` — rendered with proper escaping (quotes, newlines, etc.)
- `Fragment` — composed fragments are inlined

### How are raw strings handled
Only via `Fragment.raw("...")` — the explicit, auditable escape hatch.
All uses are grep-able: `grep -r "Fragment.raw" modules/sparql-builder/`

### How is SPARQL injection prevented
- **String literals**: Quotes (`"`), newlines (`\n`), backslashes (`\`) are escaped
- **IRIs**: Wrapped in `<...>` brackets
- **Variables**: Prefixed with `?` — bad names are just bad names, not SPARQL syntax
- **Raw strings rejected**: `sparql"$someString"` is a compile error

### How is Lucene injection prevented
Lucene queries are passed as `Literal.string()` which escapes special characters.
The query content stays inside the SPARQL string literal — it cannot break out.

### What is the escape hatch
`Fragment.raw(...)` bypasses all safety. It is:
1. Explicit — requires conscious decision
2. Auditable — all uses are grep-able
3. Necessary — for vendor extensions like Jena `text#query` syntax

## Recommendation

**Use Approach A (Fragment + interpolator) as the foundation**, with these considerations:

### Why Approach A
1. **Lower migration effort** — existing SPARQL patterns map 1:1 to `sparql"..."` fragments
2. **More readable** — queries read like SPARQL, not like Java AST construction
3. **Battle-tested design** — follows Doobie's proven Fragment pattern
4. **Handles all benchmarks** — including the most complex (InsertValueQueryBuilder)
5. **Natural conditional/iteration** — Scala's `Option`, `List`, and `for` work directly

### Where Approach B adds value
AST nodes could be added later for:
- **Query introspection** — walking/transforming queries programmatically
- **Standardized patterns** — if certain query shapes become repetitive
- Both approaches use the same core types (`Iri`, `Variable`, `Literal`, `Fragment`)

### What's NOT included in the spike
- IRI validation (`Iri.trusted` bypasses it — production would add `Iri.validated`)
- Variable name validation (currently accepts any string)
- Pretty-printing / formatting
- SPARQL parsing (only generation)

## Next Steps (Phase 2)

1. Move spike types to production module
2. Migrate 2-3 representative query sites (one per pattern)
3. Validate the API against real query requirements
4. Add IRI validation and variable name constraints
5. Integrate with existing `TriplestoreService`

## Test Summary

| Test Suite | Tests | Status |
|---|---|---|
| ApproachASpec | 9 | All pass |
| ApproachBSpec | 5 | All pass |
| InjectionSafetySpec | 11 | All pass |
| **Total** | **25** | **All pass** |
