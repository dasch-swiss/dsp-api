# Recommended Approach: Interpolated Template

The chosen API. Write SPARQL as `sparql"..."` templates with typed interpolation; compose
dynamic parts as `Fragment` values. Reads like raw SPARQL, safe by construction.

## Philosophy

One foundation, two surfaces:

- **Template style (primary)** — write whole queries as `sparql"""..."""` templates with
  typed holes. This is the Doobie/Skunk pattern (`sql"..."` for entire queries).
- **Builder style (alternative)** — construct queries programmatically via
  `Sparql.select(...).where(...).render`, where each slot accepts a `Fragment`. Useful
  when the query *shape* itself is dynamic.

Both share the same core — `Fragment` (a monoid), the `sparql"..."` interpolator, and the
typed values `Iri`, `Variable`, `Literal`, `Prefix`. They render identical output; the
choice is ergonomic, not semantic. Exposing both mirrors `sqlx` (`query!` macro +
`QueryBuilder`) and JOOQ (DSL + plain SQL).

## Shared vocabulary

```scala
import org.knora.sparqlbuilder.{Fragment, Iri, Variable, Literal, Prefix, Sparql}
import org.knora.sparqlbuilder.Fragment.{sparql, sp}  // sp is a short alias for sparql
import org.knora.sparqlbuilder.Fragments

// Prefixes — constructed with unsafeFrom, derive IRIs via unsafeIri
val kb   = Prefix.unsafeFrom("knora-base", "http://www.knora.org/ontology/knora-base#")
val rdfs = Prefix.unsafeFrom("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
val xsd  = Prefix.unsafeFrom("xsd", "http://www.w3.org/2001/XMLSchema#")

// IRIs derived from prefixes — no raw namespace strings repeated
val kbIsDeleted    = kb.unsafeIri("isDeleted")
val kbLastMod      = kb.unsafeIri("lastModificationDate")
val kbResource     = kb.unsafeIri("Resource")
val rdfsSubClassOf = rdfs.unsafeIri("subClassOf")
```

Two benchmarks below show the range: a trivial query, then the hardest case (conditional
fragments + iteration with indexed variables). The remaining four benchmarks are defined in
[reference-sparql.md](reference-sparql.md) and behave identically.

## Benchmark 1 — Simple SELECT with OPTIONAL

Target SPARQL:

```sparql
SELECT ?s ?p ?o
WHERE {
  ?s a <http://example.org/MyClass> .
  ?s <http://www.knora.org/ontology/knora-base#isDeleted> false .
  OPTIONAL { ?s <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModDate . }
  ?s ?p ?o .
}
ORDER BY DESC(?lastModDate)
LIMIT 25
```

Template style:

```scala
val s = Variable("s"); val p = Variable("p"); val o = Variable("o")
val lmd = Variable("lastModDate")
val resourceClass = Iri.unsafeFrom("http://example.org/MyClass")

val query = sparql"""
  SELECT $s $p $o
  WHERE {
    $s a $resourceClass .
    $s $kbIsDeleted false .
    OPTIONAL { $s $kbLastMod $lmd . }
    $s $p $o .
  }
  ORDER BY DESC($lmd)
  LIMIT 25
""".render
```

## Benchmark 4 — conditional fragments + iteration (the hard case)

A simplified sketch of the 740-line `InsertValueQueryBuilder.scala`, the most complex query
builder in the codebase. It exercises everything the legacy Twirl `@for`/`@if` machinery
does: iterate over a list, conditionally emit patterns per item, and generate **indexed
variables** (`?linkValue0`, `?linkValue1`, …) so each iteration's bindings stay distinct.

This is where the library earns its keep — the complexity lives in **shared fragment
composition**, not in the query template. Three blocks (DELETE, INSERT, WHERE) are all keyed
on the same `linkUpdates` list with different conditionals per clause:

```scala
case class LinkUpdate(
  linkPropertyIri: String, linkTargetIri: String,
  deleteDirectLink: Boolean, linkValueExists: Boolean,
  newLinkValueIri: String, newReferenceCount: Int,
)

// DELETE block: iteration + per-item conditionals
val linkDeleteFragments: List[Fragment] = linkUpdates.zipWithIndex.flatMap { case (update, index) =>
  val prop   = Iri.unsafeFrom(update.linkPropertyIri)
  val target = Iri.unsafeFrom(update.linkTargetIri)

  val directLink = Option.when(update.deleteDirectLink) {
    sparql"$resource $prop $target ."
  }
  val linkValuePatterns = Option.when(update.linkValueExists) {
    val linkValue     = Variable(s"linkValue$index")
    val linkValueUUID = Variable(s"linkValueUUID$index")
    val linkPropValue = Iri.unsafeFrom(update.linkPropertyIri + "Value")
    sparql"""$resource $linkPropValue $linkValue .
      $linkValue $kbValueHasUUID $linkValueUUID ."""
  }
  directLink.toList ++ linkValuePatterns.toList
}
val linkDeleteBlock = Fragment.join(linkDeleteFragments)
// (linkInsertBlock and linkWhereBlock are built the same way, with their own conditionals)
```

The composed blocks then drop into the template as ordinary holes:

```scala
val query = sparql"""
  PREFIX $kb
  PREFIX $rdfs
  PREFIX $xsd

  DELETE {
    GRAPH $dataNamedGraph {
      $resource $kbLastMod $resourceLastMod .
      $linkDeleteBlock
    }
  }
  INSERT {
    GRAPH $dataNamedGraph {
      $resource $kbLastMod $newLmd .
      $linkInsertBlock
    }
  }
  WHERE {
    ${Fragments.bind(sparql"IRI($dataGraphIri)", dataNamedGraph)}
    $resource a $resourceClass .
    $resource $kbIsDeleted false .
    $resourceClass ${PropertyPath.zeroOrMore(rdfsSubClassOf)} $kbResource .
    OPTIONAL { $resource $kbLastMod $resourceLastMod . }
    $linkWhereBlock
  }
""".render
```

`Fragment` composition replaces the Twirl `@for`/`@if` nesting; `Fragments.bind` and
`PropertyPath.zeroOrMore` cover the `BIND` and property-path patterns the real query uses.

## Builder style (the alternative surface)

For the same Benchmark 1, the builder makes the query structure explicit through named
slots instead of positional embedding:

```scala
val query = Sparql
  .select(s, p, o)
  .where(
    sparql"$s a $resourceClass .",
    sparql"$s $kbIsDeleted false .",
    Fragments.optional(sparql"$s $kbLastMod $lmd ."),
    sparql"$s $p $o .",
  )
  .orderBy(lmd.desc)
  .limit(25)
  .render
```

For UPDATE queries, GRAPH scope is expressed by the method name —
`deleteFrom(graph)(...)` / `insertInto(graph)(...)` — which removes any ambiguity about
which clause a `GRAPH` block applies to and supports DELETE and INSERT targeting different
named graphs.

### When to use which

| Scenario | Recommended | Why |
|----------|-------------|-----|
| Simple/medium static queries | Template | Reads like SPARQL |
| Complex multi-clause (DELETE/INSERT/WHERE) | Template | Visual structure |
| Query shape varies at runtime | Builder | Conditionally add `.orderBy()`, `.limit()`, … |
| Programmatic construction from config | Builder | No template to write |
| One-off migration of a Twirl template | Template | Closest to the original structure |

## Design notes

### Core types and combinators

- **`Fragment`** — a monoid over SPARQL text. Compose with `++`, `Fragment.join`,
  `Fragment.combineAll`. `Fragment.fromOption(opt)(f)` is the idiom for a conditional
  fragment (replaces `opt.fold(Fragment.empty)(f)`).
- **`Fragment.raw("...")`** — the only injection-risk surface; an explicit, grep-able escape
  hatch. With `PropertyPath` and `Fragments.jenaTextQuery` (below) it should rarely be needed.
- **`Fragments`** — combinators: `optional`, `union`, `filterNotExists`, `minus`, `filter`,
  `bind`, `values`, `graph`, `subquery`.
- **`PropertyPath`** — SPARQL 1.1 property paths as a `SparqlValue`:
  `zeroOrMore`, `oneOrMore`, `sequence`, `alternative`, `inverse`.
- **`Fragments.jenaTextQuery`** — typed Lucene full-text combinator that encapsulates the
  vendor-specific `<http://jena.apache.org/text#query>` syntax, isolating the Jena
  dependency to one place.

### Type construction: safe / unsafe convention

All types follow the codebase's `from` / `unsafeFrom` convention. `from` returns
`Either[String, T]` (validated); `unsafeFrom` skips validation.

| Type | Safe | Unsafe |
|------|------|--------|
| `Iri` | `Iri.from(value)` | `Iri.unsafeFrom(value)` |
| `Variable` | `Variable.from(name)` | `Variable.unsafeFrom(name)` |
| `Prefix` | `Prefix.from(name, ns)` | `Prefix.unsafeFrom(name, ns)` |
| `LanguageTag` | `LanguageTag.from(tag)` | `LanguageTag.unsafeFrom(tag)` |

`Prefix` also derives IRIs: `prefix.iri("localName")` / `prefix.unsafeIri("localName")`.

### Literal API

Two safety models:

```scala
// Type-safe — Scala types guarantee safety, no escaping needed
Literal.bool(true); Literal.int(42); Literal.double(3.14)
Literal.decimal(BigDecimal("3.14")); Literal.instant(instant)

// String-based — escaping is the safety boundary
Literal.stringEscaped("hello")                      // → "hello"
Literal.langStringEscaped("hello", lang)            // → "hello"@en
Literal.typedEscaped("2024-01-01", xsdDate)         // → "2024-01-01"^^<...>
Literal.unsafeStringUnescaped("hello")              // → "hello" — no escaping, caller owns safety
```

`LanguageTag` is the library's own BCP 47 opaque type, deliberately decoupled from
dsp-api's domain-specific `LanguageCode` enum.

### Escaping backend (the "Approach D" half)

The escaping backend is an independent choice from the API. The current prototype uses
custom escaping; **RDF4J's `Rdf.literalOf().getQueryString()` is the intended replacement**
— a drop-in with better coverage (`\f`, `\b`, single quotes). The API surface is identical
either way. Wiring this in is the first Phase 2 task (see [decision.md](decision.md#next-steps-phase-2)).

## Open questions

- **Error handling.** Safe constructors return `Either[String, T]`. Should there be a
  cohesive error type (e.g. `SparqlBuilderError`) instead of plain strings? Should errors
  compose when building a fragment from several potentially-invalid values? How does this
  interact with ZIO at the `webapi` call site?
- **PREFIX usage in template bodies.** A template may declare `PREFIX $kb` in the header and
  then use a bare `knora-base:lastModificationDate` in the body; the library can't verify at
  construction time that the prefix is declared. Options: accept bare prefixed names as raw
  text (current, reads like SPARQL), always interpolate IRIs (safer, more verbose), or
  validate at render time (complex).
- **Known rough edges.** Whitespace artifacts remain when an absent conditional leaves
  surrounding blank lines; `Fragment.empty` and `Fragment.raw("")` are semantically equal but
  structurally distinct; prefix deduplication is not yet implemented.
