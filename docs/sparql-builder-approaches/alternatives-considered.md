# Alternatives Considered

The spike prototyped a broad design space before settling on the interpolated template
(see [recommended-approach.md](recommended-approach.md)). This document records the
alternatives and why each lost, so the decision doesn't have to be re-litigated later.

Each approach was tested against the same six benchmark queries in
[reference-sparql.md](reference-sparql.md).

## Folded into the recommendation

Two early prototypes were **not** rejected — the recommended approach is their synthesis:

- **Interpolator + Fragment composition** — the `sparql"..."` interpolator and the
  `Fragment` monoid. Retained as the foundation; matches Doobie/Skunk. Its bolt-on fluent
  builder survives as the secondary "builder style."
- **Whole-query interpolation (hybrid)** — using the interpolator for entire multi-line
  query templates rather than small fragments. This became the primary template style.

The genuinely rejected approaches follow.

## AST case classes + typed rendering

Model SPARQL constructs as explicit case classes forming an inspectable AST; each node
renders to a string.

```scala
enum GraphPattern {
  case Triple(pattern: TriplePattern)
  case Optional(patterns: List[GraphPattern])
  case Union(branches: List[List[GraphPattern]])
  case FilterNotExists(patterns: List[GraphPattern])
  case Filter(expr: String)
  case Bind(expr: String, variable: Variable)
  case Raw(fragment: Fragment)
}
```

**Rejected:** more verbose than interpolation (every triple is a constructor call), and the
AST has gaps — it models neither GRAPH scoping, PREFIX declarations, nor computed SELECT
expressions, all of which fall back to a `Raw` escape hatch. The structure it buys isn't
worth the verbosity unless query introspection becomes a requirement (it isn't one).

## Fluent immutable builder

Method chaining on immutable case classes with no interpolator; triple patterns via
`triple(s, p, o)`.

```scala
val query = Select()
  .select(s, p, o)
  .where(
    triple(s, rdfType, resourceClass),
    triple(s, kbIsDeleted, Literal.bool(false)),
    optional(triple(s, kbLastMod, lmd)),
    triple(s, p, o),
  )
  .orderBy(lmd.desc)
  .limit(25)
  .render
```

**Rejected (subsumed):** `triple(s, p, o)` only covers the simplest patterns — OPTIONAL,
UNION, FILTER, and GRAPH all need `Fragment.raw` escapes, a worse safety ratio than the
interpolator. It is otherwise virtually identical to the recommended approach's builder
style, with `triple(...)` in place of `sparql"..."`. Nothing is gained.

### Variant: consequent fluent builder

The same idea pushed further — triple patterns themselves chain via `.and()`,
`.andOptional()`, `.andUnion()`.

```scala
.where(
  triple(s, rdfType, resourceClass)
    .and(s, kbIsDeleted, Literal.bool(false))
    .andOptional(triple(s, kbLastMod, lmd))
    .and(s, p, o)
)
```

**Rejected (subsumed):** doubles the API surface with no expressiveness gain, has a
confusing dual interface (`.and(s,p,o)` vs `.andAll(pattern)`), and reverts to list-building
for complex queries anyway. The chaining sugar can be added later as extension methods on
`Fragment` if it ever proves ergonomic — no separate approach needed.

## Thin Scala wrapper over Jena ARQ QueryBuilder

Use Jena's `jena-querybuilder` directly to produce validated `Query` objects (a real SPARQL
AST). The trade-off is a mutable, Java-style API.

```scala
val builder = new SelectBuilder()
builder.addVar(s).addVar(p).addVar(o)
builder.addWhere(s, rdfType, resourceClass)
builder.addOptional(s, kbLastMod, lmd)
builder.addOrderBy(lmd, Order.DESCENDING)
builder.setLimit(25)
val query = builder.buildString()
```

**Rejected:** the mutable builder clashes fundamentally with immutable Scala/ZIO style —
it can't be safely composed, forked, or reused, and conditionals/iteration become imperative
mutation. It doesn't support GRAPH-scoped DELETE/INSERT (a blocker for dsp-api), needs manual
string construction for Lucene `text#query` and `subClassOf*`, adds a new dependency, and is
only loosely typed (`Object`). The one upside — a validated AST — doesn't outweigh this.

## Template + bind (Jena ParameterizedSparqlString)

Write SPARQL as a template string with named placeholders, then bind typed values by name;
Jena escapes at bind time.

```scala
val pss = new ParameterizedSparqlString()
pss.setCommandText("""
  SELECT ?s ?p ?o
  WHERE { ?s a ?resourceClass . OPTIONAL { ?s ?lastModPred ?lastModDate . } ?s ?p ?o . }
""")
pss.setIri("resourceClass", "http://example.org/MyClass")
val query = pss.toString
```

**Rejected:** zero composability — templates are monolithic strings with no reusable
fragments. Conditional and iterated patterns force building the template itself via `s"..."`
concatenation, which is exactly the anti-pattern this initiative exists to remove. Jena's own
docs warn that its injection protection is "by no means foolproof," and it protects only
bound values, not the template text.

### Variant: Scala template + bind

The same concept with an immutable, idiomatic Scala binding API instead of Jena's mutable PSS.

```scala
val query = Sparql("""
    SELECT ?s ?p ?o
    WHERE { ?s a ?resourceClass . OPTIONAL { ?s ?lastModPred ?lastModDate . } ?s ?p ?o . }
  """)
  .withIri("resourceClass", "http://example.org/MyClass")
  .withIri("lastModPred", knoraBase + "lastModificationDate")
  .render
```

**Rejected:** the binding API is nicer than Jena's, but the core limitation is identical —
dynamic structure (conditionals, iteration, polymorphic clauses) still requires building the
template via unprotected `s"..."` interpolation, and there is no fragment composability.
