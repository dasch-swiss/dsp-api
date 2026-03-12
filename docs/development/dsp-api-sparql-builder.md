---
name: sparqlbuilder
description: "Write SPARQL queries in Scala using RDF4J SparqlBuilder. Use for building type-safe SPARQL SELECT, CONSTRUCT, MODIFY queries programmatically. Triggers on: sparqlbuilder, rdf4j query, scala sparql, build sparql query."
---

# RDF4J SparqlBuilder — Scala SPARQL Query Skill

Build type-safe SPARQL queries in Scala using the [RDF4J SparqlBuilder](https://rdf4j.org/documentation/tutorials/sparqlbuilder/) fluent API.

## Reference Implementation

The dps-api project contains a helper trait and many real-world examples.
Read `webapi/src/main/scala/org/knora/webapi/slice/common/QueryBuilderHelper.scala` for the base trait — your query builders should extend or mix in `QueryBuilderHelper`.

## Dependency

```scala
// sbt
"org.eclipse.rdf4j" % "rdf4j-sparqlbuilder" % rdf4jVersion
```

## Core Imports

```scala
import org.eclipse.rdf4j.sparqlbuilder.core.{Queries, SparqlBuilder, Variable}
import org.eclipse.rdf4j.sparqlbuilder.core.query.{SelectQuery, ConstructQuery, ModifyQuery}
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.{GraphPattern, GraphPatterns, TriplePattern}
import org.eclipse.rdf4j.sparqlbuilder.rdf.{Iri, Rdf, RdfLiteral, RdfValue}
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions
import org.eclipse.rdf4j.sparqlbuilder.constraint.propertypath.builder.PropertyPathBuilder
import org.eclipse.rdf4j.model.vocabulary.{RDF, RDFS, XSD, OWL}
```

## Factory Classes

| Class              | Purpose                                           |
|--------------------|---------------------------------------------------|
| `Queries`          | Create query objects: `SELECT()`, `CONSTRUCT()`, `MODIFY()` |
| `SparqlBuilder`    | Create variables and prefixes: `` `var`(name) ``, `prefix(ns)` |
| `Rdf`              | Create RDF values: `iri()`, `literalOf()`, `literalOfType()`, `literalOfLanguage()`, `bNode()` |
| `GraphPatterns`    | Create patterns: `tp()`, `and()`, `union()`, `optional()`, `select()` (subquery) |
| `Expressions`      | Create constraints: `regex()`, `lt()`, `gt()`, `equals()`, `notEquals()`, `not()`, `and()`, `or()` |

## Building Queries

### Variables and IRIs

```scala
// Variables — note backticks because `var` is a Scala keyword
val x    = SparqlBuilder.`var`("x")
val name = SparqlBuilder.`var`("name")

// Or using the QueryBuilderHelper convenience method
val x    = variable("x")
val name = variable("name")

// IRIs
val bookIri = Rdf.iri("http://example.org/book/1")

// From a namespace prefix
import org.eclipse.rdf4j.model.vocabulary.FOAF
val foafName: org.eclipse.rdf4j.model.IRI = FOAF.NAME  // model IRI, not builder Iri
```

### SELECT

```scala
val (name, mbox) = (variable("name"), variable("mbox"))
val query: SelectQuery =
  Queries.SELECT(name, mbox)
    .prefix(FOAF.NS)
    .where(
      x.has(FOAF.NAME, name)
       .andHas(FOAF.MBOX, mbox)
    )
    .orderBy(name)
    .limit(10)
    .offset(0)

query.getQueryString  // produces the SPARQL string
```

### CONSTRUCT

```scala
val (s, p, o) = spo  // from QueryBuilderHelper — shortcut for (variable("s"), variable("p"), variable("o"))
val graphPattern = s.has(p, o)
val query: ConstructQuery =
  Queries.CONSTRUCT(graphPattern)
    .prefix(KB.NS)
    .where(graphPattern.from(toRdfIri(ontologyIri)))
```

### MODIFY (INSERT/DELETE)

```scala
val query: ModifyQuery =
  Queries.MODIFY()
    .prefix(RDF.NS, RDFS.NS, XSD.NS, KB.NS)
    .from(dataGraphIri)
    .delete(oldTriple1, oldTriple2)
    .into(dataGraphIri)
    .insert(newTriple1, newTriple2, newTriple3)
    .where(wherePattern1, wherePattern2)
```

## Graph Patterns

### Triple Patterns

```scala
// subject.has(predicate, object)
val triple: TriplePattern = book.has(DC.AUTHOR, Rdf.literalOf("Tolkien"))

// Chaining multiple predicates on the same subject
val triple = resource
  .isA(KB.Resource)                              // rdf:type shortcut
  .andHas(KB.hasPermissions, permissions)
  .andHas(KB.attachedToUser, creator)
  .andHas(KB.attachedToProject, project)
```

### Combining Patterns

```scala
// AND — group graph patterns (joined with .)
val combined = pattern1.and(pattern2).and(pattern3)

// Or pass varargs to .where()
Queries.SELECT(vars).where(pattern1, pattern2, pattern3)

// OPTIONAL
val opt = pattern.optional()
// or:  GraphPatterns.optional(pattern)

// UNION
val union = GraphPatterns.union(pattern1, pattern2)

// NAMED GRAPH (FROM)
val fromGraph = pattern.from(graphIri)
```

### Property Paths

```scala
// Transitive closure: zeroOrMore
val subClassPath = PropertyPathBuilder.of(RDFS.SUBCLASSOF).zeroOrMore().build()
// Usage: subject.has(subClassPath, object)

// Sequence path: pred1 / pred2
x.has(p => p.pred(FOAF.ACCOUNT).then(FOAF.MBOX), name)
// => foaf:account / foaf:mbox

// Alternative path: pred1 | pred2
x.has(p => p.pred(EX.MOTHER_OF).or(EX.FATHER_OF).oneOrMore(), ancestor)
// => ( ex:motherOf | ex:fatherOf )+

// QueryBuilderHelper provides shortcut:
val path = zeroOrMore(KB.previousValue)
```

### Filters

```scala
// Inline filter on a triple pattern
fileValue.has(objPred, objObj)
  .filter(Expressions.notEquals(objPred, KB.previousValue))

// Common expressions
Expressions.regex(name, "Smith")
Expressions.lt(price, Rdf.literalOf(100))
Expressions.equals(x, y)
Expressions.not(Expressions.bound(optionalVar))
Expressions.and(expr1, expr2)
Expressions.or(expr1, expr2)
```

### Subqueries

```scala
val subSelect = GraphPatterns.select()
// configure the subselect, then use it as a graph pattern in the outer query
```

## Literals

```scala
Rdf.literalOf("plain string")
Rdf.literalOf(42)                           // xsd:integer
Rdf.literalOf(3.14)                         // xsd:double
Rdf.literalOf(true)                         // xsd:boolean
Rdf.literalOfLanguage("Hallo", "de")        // "Hallo"@de
Rdf.literalOfType("2024-01-01", XSD.DATE)   // "2024-01-01"^^xsd:date
Rdf.literalOfType(instant.toString, XSD.DATETIME)
```

## QueryBuilderHelper Trait

When working in the dsp-api codebase, extend `QueryBuilderHelper` for these conveniences:

| Method | Purpose |
|--------|---------|
| `variable(name)` | Create a `Variable` (wraps `` SparqlBuilder.`var`(name) ``) |
| `spo` | Returns `(variable("s"), variable("p"), variable("o"))` |
| `toRdfIri(iri)` | Convert `SmartIri`, `InternalIri`, `KnoraIri`, or `StringValue` to `Iri` |
| `toRdfLiteral(v)` | Convert `StringLiteralV2`, `BooleanLiteralV2`, `Instant`, `Int` to `RdfLiteral` |
| `toRdfValue(v)` | Convert `OntologyLiteralV2` to `RdfValue` |
| `zeroOrMore(pred)` | Create a `PropertyPath` with `*` modifier |
| `graphIri(project)` | Get the named graph IRI for a `KnoraProject` |
| `askWhere(triple)` | Wrap a triple pattern in an `ASK WHERE { ... }` query |
| `ontologyAndNamespace(iri)` | Get `(Iri, SimpleNamespace)` tuple for prefix declarations |

## Getting the Query String

Every query element implements `QueryElement` with `.getQueryString`:

```scala
val queryString: String = query.getQueryString
```

## Known Limitations

The SparqlBuilder does **not** support:
- VALUES blocks
- RDF collection syntax `( ... )`
- DESCRIBE queries
- ASK queries (use `QueryBuilderHelper.askWhere()` which builds the string manually)

For these, fall back to string interpolation with `.getQueryString` for the parts that _are_ supported.

## Patterns to Follow

1. **One query builder object per file** — keep query construction in dedicated `*Query.scala` objects
2. **Extend QueryBuilderHelper** — use the trait for consistent IRI/literal conversions
3. **Separate concerns** — split complex MODIFY queries into `buildDeletePatterns()`, `buildInsertPatterns()`, `buildWhereClause()` methods
4. **Use `.andHas()` for readability** — chain multiple predicates on the same subject
5. **Use `.and()` for combining** — join independent graph patterns
6. **Use `.optional()` on patterns** that may not match
7. **Use `zeroOrMore()` / property paths** for transitive relations like `rdfs:subClassOf*`
