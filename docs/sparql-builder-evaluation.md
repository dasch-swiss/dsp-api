# SPARQL Builder Spike: Evaluation & Recommendation

## Executive Summary

We prototyped six approaches across two dimensions — **API style** (how developers write
queries) and **implementation strategy** (what's under the hood) — for replacing DSP-API's
three fragmented SPARQL generation patterns. After evaluating all six against real benchmark
queries, **Approach A (Fragment + `sparql"..."` interpolator) with RDF4J escaping (Approach D)**
is the recommended path forward.

## Design Space

As the PRD specifies, the design space has two independent dimensions:

| Dimension | Options Explored |
|-----------|-----------------|
| **API style** | String interpolator (A, D), AST case classes (B), Fluent immutable builder (C), Template + bind (F) |
| **Implementation** | Custom escaping (A, B, C), RDF4J escaping (D), Jena ARQ wrapper (E), Jena PSS (F) |

## Approaches Evaluated

### A: String interpolator + custom escaping (Doobie-style)

`sparql"$s a $cls ."` — immutable `Fragment` type with `sparql"..."` interpolator.
Only `SparqlValue | Fragment` can be interpolated; raw `String` is a compile error.

**Tests:** `ApproachASpec.scala` (9 tests) | **Benchmarks covered:** All 6

### B: AST case classes + custom rendering

`TriplePattern(s, rdfType, cls)` — typed AST tree of query nodes. Each node renders
to SPARQL strings. More structured but more verbose.

**Tests:** `ApproachBSpec.scala` (5 tests) | **Benchmarks covered:** 3

### C: Fluent immutable builder + custom rendering

`FluentSelect().select(s, p, o).where(triple(s, p, o)).limit(25)` — method chaining
on immutable case classes, no string interpolator. Triple patterns via `triple(s, p, o)`.

**Tests:** `ApproachCSpec.scala` (6 tests) | **Benchmarks covered:** 3

### D: String interpolator + RDF4J escaping

Same API as Approach A, but delegates string literal escaping to RDF4J's
`Rdf.literalOf().getQueryString()` instead of hand-rolled `escapeSparqlString`.

**Tests:** `ApproachDSpec.scala` (12 tests) | **Focus:** Escaping comparison

### E: Thin Scala 3 wrapper over Jena ARQ QueryBuilder

Direct use of Jena's `SelectBuilder`, `AskBuilder`, `UpdateBuilder` from Scala.
Produces validated `Query` objects (real SPARQL AST), but API is mutable Java-style.

**Tests:** `ApproachESpec.scala` (8 tests) | **Benchmarks covered:** 3

### F: Template + bind via Jena ParameterizedSparqlString

Write SPARQL as a template string, bind typed values by name. Jena escapes values
at bind time. Closest to raw SPARQL but least composable.

**Tests:** `ApproachFSpec.scala` (8 tests) | **Benchmarks covered:** 2

## Comparison Matrix

| Criterion | A: Interpolator | B: AST | C: Fluent builder | D: RDF4J escaping | E: Jena wrapper | F: Template+bind |
|---|---|---|---|---|---|---|
| **Readability** | Excellent | Good | Good | Excellent (= A) | Poor (Java API) | Good (raw SPARQL) |
| **Composability** | Excellent | Good | Good | Excellent (= A) | Poor (mutable) | Poor (monolithic) |
| **Conditional patterns** | Natural | Verbose | Natural | Natural (= A) | Imperative | Requires string concat |
| **Iteration** | Natural | Natural | Natural | Natural (= A) | Imperative | Requires string concat |
| **Type safety** | Compile-time | Runtime | Runtime | Compile-time (= A) | Runtime (Object) | Runtime (bind-time) |
| **Injection safety** | By construction | By construction | By construction | By construction + battle-tested | Jena validation | "By no means foolproof" (Jena docs) |
| **Learning curve** | Low | Medium | Low | Low (= A) | Medium (Jena API) | Low |
| **Migration effort** | Low | Medium | Medium | Low (= A) | High (type mismatch) | Medium |
| **Query validation** | None (string) | None (string) | None (string) | None (string) | Yes (Jena AST) | Yes (at parse time) |
| **Mutability** | Immutable | Immutable | Immutable | Immutable | Mutable | Mutable |
| **External deps** | None (custom) | None (custom) | None (custom) | RDF4J (already present) | Jena querybuilder (new) | Jena ARQ (new) |
| **Benchmark coverage** | All 6 | 3 of 6 | 3 of 6 | N/A (= A) | 3 of 6 | 2 of 6 |

## Key Findings

### 1. API Style: String interpolator wins for readability and migration

The `sparql"..."` interpolator (A/D) reads closest to actual SPARQL, making queries
self-documenting. This matters most for a team maintaining ~70 query files.

```scala
// A: reads like SPARQL
sparql"$s a $resourceClass ."

// B: more verbose, less visual
TriplePattern(s, rdfType, resourceClass)

// C: functional but not SPARQL-like
triple(s, rdfType, resourceClass)

// E: Java-style, most verbose
builder.addWhere(s, rdfType, resourceClass)

// F: actual SPARQL, but bound values are separate
pss.setCommandText("?s a ?type ."); pss.setIri("type", "...")
```

### 2. Conditional patterns: Approaches A/C/D handle well; E/F struggle

The critical expressiveness test is conditional query fragments (Twirl `@if` equivalent).
Immutable approaches handle this naturally with `Option[Fragment]`:

```scala
// A/D: natural
val pattern = Option.when(condition)(sparql"$s $p $o .")
Fragment.combine(pattern, otherPattern)

// E: imperative mutation
maybeComment.foreach(c => builder.addWhere(s, commentIri, stringLit(c)))

// F: falls back to string concatenation of template — defeats the purpose
val clause = if (condition) "?s ?p ?o ." else ""
pss.setCommandText(s"SELECT ?s WHERE { $clause }")
```

### 3. Implementation strategy: RDF4J escaping is better than custom

Approach D revealed that RDF4J's escaping handles more edge cases than our custom
implementation:

| Character | Custom escaping | RDF4J escaping |
|-----------|----------------|----------------|
| `"` (double quote) | `\"` | `\"` |
| `\n` (newline) | `\n` | `\n` |
| `\r` (carriage return) | `\r` | `\r` |
| `\t` (tab) | `\t` | `\t` |
| `\\` (backslash) | `\\` | `\\` |
| `\f` (form feed) | **not escaped** | `\f` |
| `\b` (backspace) | **not escaped** | `\b` |
| `'` (single quote) | **not escaped** | `\'` |

RDF4J escaping is a strict superset. Since RDF4J is already a dependency, using it
internally costs nothing and provides better coverage.

### 4. Jena QueryBuilder produces validated output but fights Scala idioms

Approach E (Jena wrapper) is the only approach that produces a validated `Query` AST
rather than a string. However:
- Mutable builder pattern clashes with immutable Scala/ZIO style
- Methods accept `Object` loosely — no compile-time type safety
- Cannot compose or fork builders (mutation makes reuse unsafe)
- Adds `jena-querybuilder` dependency

### 5. Jena ParameterizedSparqlString is the weakest option

Approach F documented multiple problems:
- Templates are monolithic strings — no reusable fragments
- Conditional patterns require string concatenation of the template itself
- Iteration requires dynamically building placeholder names in the template
- The Jena docs explicitly warn injection protection is "by no means foolproof"
- If a developer uses Scala `s"..."` interpolation in the template, PSS provides zero protection

### 6. Fluent builder (C) works but interpolator (A) is more ergonomic

Approach C demonstrates that a fluent builder can work well with immutability and
composition. However, `triple(s, p, o)` is slightly less readable than `sparql"$s $p $o ."`,
and the builder adds no safety beyond what the interpolator provides.

## Injection Safety Model

Defined concretely in `InjectionSafetySpec.scala` (11 tests):

- **What types can be interpolated**: Only `SparqlValue | Fragment` via `sparql"..."` — raw `String` is a compile error
- **How are raw strings handled**: Only via `Fragment.raw("...")` — explicit, grep-able escape hatch
- **How is SPARQL injection prevented**: String escaping (quotes, newlines, backslashes), IRI wrapping in `<...>`, variable prefixing with `?`
- **How is Lucene injection prevented**: Lucene queries passed as `Literal.string()` which escapes special characters; content stays inside SPARQL string literal
- **Implementation backend**: Delegate to RDF4J's `SparqlBuilderUtils.getEscapedString()` for battle-tested coverage

## Recommendation

**Use Approach A's API (Fragment + `sparql"..."` interpolator) with RDF4J escaping (Approach D's implementation strategy).**

### Why this combination

1. **Best readability** — queries read like SPARQL with typed holes
2. **Best composability** — `Fragment.combine`, `combineAll`, `++` for immutable composition
3. **Lowest migration effort** — existing SPARQL patterns map 1:1 to `sparql"..."` fragments
4. **Battle-tested escaping** — RDF4J's escaping handles more edge cases than custom code
5. **Compile-time safety** — raw `String` cannot be interpolated; only `SparqlValue | Fragment`
6. **No new dependencies** — RDF4J is already present; no need for Jena querybuilder or extras
7. **Handles all benchmarks** — only approach that covered all 6 benchmark queries

### What to add later

- **AST nodes from Approach B** — for query introspection/transformation if needed
- **IRI validation** — `Iri.trusted` bypasses it; production would add `Iri.validated`
- **Variable name validation** — currently accepts any string
- **Query validation** — optionally parse rendered SPARQL via Jena to catch structural errors

### What to explicitly avoid

- **Jena QueryBuilder wrapping** — mutable API fights Scala idioms; adds dependency
- **ParameterizedSparqlString** — template approach is fundamentally incompatible with composability
- **Custom escaping** — RDF4J does it better and is already available

## Next Steps (Phase 2)

1. Swap custom `escapeSparqlString` for RDF4J's escaping internally
2. Move spike types to production module
3. Migrate 2-3 representative query sites (one per pattern)
4. Validate the API against real query requirements
5. Add IRI validation and variable name constraints
6. Integrate with existing `TriplestoreService`

## Test Summary

| Test Suite | Tests | Focus |
|---|---|---|
| ApproachASpec | 9 | String interpolator + all 6 benchmarks |
| ApproachBSpec | 5 | AST case classes |
| ApproachCSpec | 6 | Fluent immutable builder |
| ApproachDSpec | 12 | RDF4J vs custom escaping comparison |
| ApproachESpec | 8 | Jena ARQ QueryBuilder wrapper |
| ApproachFSpec | 8 | Jena ParameterizedSparqlString |
| InjectionSafetySpec | 11 | Injection safety definitions |
| **Total** | **59** | **All pass** |
