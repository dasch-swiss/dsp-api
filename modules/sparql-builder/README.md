# sparql-builder

Safe, composable SPARQL query building: whole queries as `sparql"""..."""` interpolated
templates with typed holes, dynamic structure composed as `Fragment` values. This is the
**only** SPARQL generation style for new dsp-api code — see
[the decision record](../../docs/development/dsp-api-sparql-builder.md) for why, and for
the RDF4J SparqlBuilder migration ratchet.

## Usage

```scala
import org.knora.sparqlbuilder.*

// Typed values — all constructors validate; there is no unvalidated path.
val project  = Iri.unsafeFrom("http://rdfh.ch/projects/0001") // throws on invalid input
val userIri  = Iri.from(untrustedString)                      // Either[String, Iri]
val resource = Variable("resource")                           // VARNAME-restricted names
val label    = Literal.string(userInput)                      // escaped at construction
val labelDe  = Literal.langString("Haus", "de")               // lang tag validated

// A whole query is a template; only SparqlValue (Iri, Variable, Literal) and
// Fragment can be interpolated — a raw String is a compile error.
val query: String = sparql"""
  SELECT $resource
  WHERE {
    $resource <http://www.knora.org/ontology/knora-base#attachedToProject> $project .
    ${Fragments.filterNotExists(sparql"$resource <http://www.knora.org/ontology/knora-base#isDeleted> true .")}
  }
""".render
```

### Dynamic structure

Conditionals are `Option[Fragment]`, iteration is `.map(...).combineAll`; composed
fragments drop into the template as ordinary holes:

```scala
val commentPattern: Option[Fragment] =
  maybeComment.map(c => sparql"$value <…#valueHasComment> ${Literal.string(c)} .")

val linkPatterns: Fragment = linkUpdates.zipWithIndex.map { case (u, i) =>
  val linkValue = Variable(s"linkValue$i")               // indexed variables per iteration
  sparql"$resource ${Iri.unsafeFrom(u.propertyIri)} $linkValue ."
}.combineAll

val where = Fragment.combine(Some(basePattern), commentPattern) ++ linkPatterns
```

Helpers: `Fragment.combine` (options), `Fragment.join` (separator), `combineAll`
(iterables), and in `Fragments`: `optional`, `union`, `graph`, `filter`,
`filterNotExists`, `minus`, `bind`, `values`, `subquery`.

### Safety model

- **Compile time:** the interpolator accepts only `SparqlValue | Fragment`.
- **Construction time:** `Iri` rejects every character that could terminate the `<...>`
  wrapper (SPARQL `IRIREF`), `Variable` names are `VARNAME`-restricted, language tags must
  match `LANGTAG`, and `Literal` holds only its final escaped rendering.
- **Escaping:** byte-for-byte identical to RDF4J's, covering the full `ECHAR` set
  (`\ " ' \t \b \n \r \f`) — pinned by `Rdf4jEscapingSpec` (RDF4J is a test-only
  dependency). Migrations are verified by diffing rendered SPARQL against the old
  builder's `getQueryString` output.
- **Escape hatch:** `Fragment.raw(...)` is the single way to inject raw text, and it is
  auditable: `grep -rn "Fragment.raw" modules/`.

### Notes

- Property paths: the operator sits *outside* the hole — `sparql"$cls $subClassOf* $t"`
  renders `?cls <…#subClassOf>* ?t`. Never bake `*`/`+` into an IRI string.
- Non-finite doubles render as `"NaN"^^xsd:double` / `"INF"` / `"-INF"` typed literals
  (bare tokens would be invalid SPARQL).
- The library renders strings; it deliberately does not model whole-query structure.
