# The Chosen API: Interpolated Templates

Write whole SPARQL queries as `sparql"""..."""` templates with typed holes; compose dynamic
parts as `Fragment` values. Reads like raw SPARQL, safe by construction. The implementation
lives in `modules/sparql-builder/` (package `org.knora.sparqlbuilder`).

## Philosophy

One style, one foundation:

- **Whole-query templates** — every query is a `sparql"""..."""` template. Static text is
  written as SPARQL; anything dynamic is an interpolated hole.
- **`Fragment` composition for dynamic structure** — conditionals are `Option[Fragment]`
  (combined with `Fragment.combine`), iteration is `.map(...).combineAll`, and the composed
  fragment drops into the template as an ordinary hole.

There is deliberately **no** programmatic query-builder surface (no
`select(...).where(...)` API). An earlier revision of this spike exposed one as a secondary
style; it was dropped — two ways to write the same query means every author decides and
every reader learns both, and the builder re-implemented clause assembly that a template
already expresses. See [alternatives-considered.md](alternatives-considered.md).

## The API surface

```scala
import org.knora.sparqlbuilder.*

// Typed values — all constructors validate; there is no unvalidated path.
val projectIri = Iri.unsafeFrom("http://rdfh.ch/projects/0001") // throws on invalid input
val userIri    = Iri.from(untrustedString)                      // Either[String, Iri]
val resource   = Variable("resource")                           // throws on non-VARNAME names
val label      = Literal.string(userInput)                      // escaped at construction
val labelDe    = Literal.langString("Haus", "de")               // lang tag validated
val count      = Literal.int(42)
val stamp      = Literal.dateTime(java.time.Instant.now())

// Fragments — the composable unit. Only SparqlValue (Iri, Variable, Literal) and
// Fragment can be interpolated; a raw String is a compile error.
val pattern: Fragment = sparql"$resource a $projectIri ."

// Composition
val f1 = pattern ++ sparql" $resource ?p ?o ."          // monoid append
val f2 = Fragment.combine(Some(pattern), None)          // Option[Fragment]* — conditionals
val f3 = List(pattern, pattern).combineAll              // Iterable[Fragment] — iteration
val f4 = Fragment.join(List(pattern), Fragment.raw("\n")) // join with separator

// Common SPARQL constructs (Fragments object)
Fragments.optional(pattern)             // OPTIONAL { ... }
Fragments.union(f1, f2)                 // { ... } UNION { ... }
Fragments.graph(sparql"$projectIri")(pattern) // GRAPH <...> { ... }
Fragments.filterNotExists(pattern)      // FILTER NOT EXISTS { ... }
Fragments.filter(sparql"?n > $count")   // FILTER(...)
Fragments.bind(sparql"NOW()", resource) // BIND(... AS ?resource)
Fragments.values(resource, iris)        // VALUES ?resource { <...> <...> }

// The audited escape hatch — the ONLY way to inject raw text:
Fragment.raw("FILTER(REGEX(?label, 'x', 'i'))")
```

### Safety model

- **Compile time:** the `sparql"..."` interpolator accepts only `SparqlValue | Fragment`.
  Interpolating a `String` (or anything else) does not compile.
- **Construction time:** `Iri` rejects every character that could terminate the `<...>`
  wrapper (the SPARQL `IRIREF` production), `Variable` names are restricted to `VARNAME`
  characters, language tags must match `LANGTAG`, and `Literal` holds only its final
  escaped rendering — there is no constructor that stores unescaped content.
- **Escaping:** byte-for-byte identical to RDF4J's (`Rdf.literalOf(v).getQueryString`),
  covering the full `ECHAR` set (`\ " ' \t \b \n \r \f`). Parity is pinned by
  `Rdf4jEscapingSpec`, with RDF4J as a test-only oracle. Identical escaping means a query
  migrated from RDF4J SparqlBuilder renders identical literals — migrations are verifiable
  by diffing rendered SPARQL.
- **Escape hatch:** `Fragment.raw(...)` is the single injection-risk surface, and it is
  grep-able: `grep -rn "Fragment.raw" modules/`.

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

Template:

```scala
val s   = Variable("s"); val p = Variable("p"); val o = Variable("o")
val lmd = Variable("lastModDate")
val resourceClass = Iri.unsafeFrom("http://example.org/MyClass")

val query = sparql"""
  SELECT $s $p $o
  WHERE {
    $s a $resourceClass .
    $s $kbIsDeleted false .
    ${Fragments.optional(sparql"$s $kbLastMod $lmd .")}
    $s $p $o .
  }
  ORDER BY DESC($lmd)
  LIMIT 25
""".render
```

## Benchmark 4 — conditional fragments + iteration (the hard case)

A simplified sketch of the 750-line `InsertValueQueryBuilder.scala`, the most complex query
builder in the codebase. It exercises everything dynamic query generation needs: iterate
over a list, conditionally emit patterns per item, and generate **indexed variables**
(`?linkValue0`, `?linkValue1`, …) so each iteration's bindings stay distinct.

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
val linkDeleteBlock: Fragment = linkUpdates.zipWithIndex.map { case (update, index) =>
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
  Fragment.combine(directLink, linkValuePatterns)
}.combineAll
// (linkInsertBlock and linkWhereBlock are built the same way, with their own conditionals)

// The composed blocks drop into the template as ordinary holes:
val query = sparql"""
  DELETE {
    $resource $kbLastMod $resourceLastMod .
    $linkDeleteBlock
  }
  INSERT { ... }
  WHERE { ... }
""".render
```

Compare with the current RDF4J-builder rendition of the same logic
(`buildFileValuePatterns` in `InsertValueQueryBuilder.scala`): five chained
`match`/`foldLeft` steps to express five optional patterns. Here each optional pattern is
one `Option.when`, and the query shape stays visible in the template.

## Design notes

- **Property paths:** the star/plus operator sits *outside* the interpolated IRI —
  `sparql"$cls $rdfsSubClassOf* $target"` renders `?cls <...#subClassOf>* ?target`, which
  is valid SPARQL. Never bake path operators into the IRI string (an `Iri` would reject
  most of them anyway).
- **Vendor extensions** (Jena `text:query`, etc.) interpolate like anything else; the
  Lucene query string goes through `Literal.string` and is escaped. A dedicated
  `LuceneQuery` type can be layered on later.
- **PREFIX declarations** are plain template text. In practice most dsp-api queries
  interpolate full IRIs and need no prefixes.
- **Non-finite doubles** (`NaN`, `±Infinity`) are not valid SPARQL numeric tokens and
  render as `"NaN"^^xsd:double` / `"INF"^^xsd:double` / `"-INF"^^xsd:double`.
- **Rendering is `Fragment.render`** — the library produces strings, deliberately. It does
  not model or validate whole-query structure; optional parse-validation through Jena can
  be added at the `TriplestoreService` boundary later if wanted.
