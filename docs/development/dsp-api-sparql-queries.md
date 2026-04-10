---
name: sparqlbuilder
description: "Write SPARQL and Gravsearch queries in Scala using RDF4J SparqlBuilder. Use for building type-safe SPARQL SELECT, CONSTRUCT, MODIFY, and Gravsearch queries programmatically. Triggers on: sparqlbuilder, rdf4j query, scala sparql, build sparql query, gravsearch."
---

# SPARQL and Gravsearch Queries in Scala

Build type-safe SPARQL queries in Scala using the
[RDF4J SparqlBuilder](https://rdf4j.org/documentation/tutorials/sparqlbuilder/)
fluent API, and Gravsearch queries via string interpolation.

## Reference Implementation

The dps-api project contains a helper trait and many real-world examples.
Read `webapi/src/main/scala/org/knora/webapi/slice/common/QueryBuilderHelper.scala`
for the base trait — your query builders should extend or mix in `QueryBuilderHelper`.

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

## Vocabularies

Standard RDF vocabularies are available from `org.eclipse.rdf4j.model.vocabulary`:

| Import   | Prefix        | Namespace                                        |
|----------|---------------|--------------------------------------------------|
| `RDF`    | `rdf:`        | `http://www.w3.org/1999/02/22-rdf-syntax-ns#`    |
| `RDFS`   | `rdfs:`       | `http://www.w3.org/2000/01/rdf-schema#`          |
| `XSD`    | `xsd:`        | `http://www.w3.org/2001/XMLSchema#`              |
| `OWL`    | `owl:`        | `http://www.w3.org/2002/07/owl#`                 |

The dsp-api project vocabularies are defined in
`webapi/src/main/scala/org/knora/webapi/slice/common/repo/rdf/Vocabulary.scala`:

```scala
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraAdmin as KA
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.NamedGraphs
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.SalsahGui
```

| Object        | Prefix         | Example terms                                          |
|---------------|----------------|--------------------------------------------------------|
| `KnoraBase`   | `knora-base:`  | `KB.Resource`, `KB.isDeleted`, `KB.linkValue`          |
| `KnoraAdmin`  | `knora-admin:` | `KA.User`, `KA.KnoraProject`, `KA.forProject`          |
| `NamedGraphs` | (none)         | `NamedGraphs.dataAdmin`, `NamedGraphs.dataPermissions` |
| `SalsahGui`   | `salsah-gui:`  | `SalsahGui.guiOrder`, `SalsahGui.guiElement`           |

Each vocabulary object provides a `.NS` namespace for use with `.prefix()`.
There is no `knora-api` vocabulary object — the `knora-api:` prefix is only used in
Gravsearch queries via string interpolation (see [Gravsearch Queries](#gravsearch-queries)).

## Factory Classes

| Class            | Purpose                                                                                             |
| ---------------- | --------------------------------------------------------------------------------------------------- |
| `Queries`        | Create query objects: `SELECT()`, `CONSTRUCT()`, `MODIFY()`                                         |
| `SparqlBuilder`  | Create variables and prefixes: `` `var`(name) ``, `prefix(ns)`                                      |
| `Rdf`            | Create RDF values: `iri()`, `literalOf()`, `literalOfType()`, `literalOfLanguage()`, `bNode()`      |
| `GraphPatterns`  | Create patterns: `tp()`, `and()`, `union()`, `optional()`, `select()` (subquery)                    |
| `Expressions`    | Create constraints: `regex()`, `lt()`, `gt()`, `equals()`, `notEquals()`, `not()`, `and()`, `or()`  |

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

### DELETE

For deleting triples without inserting replacements, use `Queries.DELETE()`:

```scala
val graph   = graphIri(project)
val pattern = toRdfIri(nodeIri).has(RDFS.COMMENT, variable("comments"))
Queries.DELETE(pattern).prefix(RDFS.NS).from(graph).where(pattern.from(graph))
```

This is distinct from `Queries.MODIFY()` which supports combined DELETE/INSERT.

Note the named graph methods on `MODIFY`:

- `` .`with`(graphIri) `` — produces `WITH <graph>`, setting the default graph
  for both DELETE and INSERT clauses. Triples appear without a GRAPH wrapper.
- `.from(graphIri)` — wraps the DELETE clause in `DELETE { GRAPH <graph> { ... } }`
- `.into(graphIri)` — wraps the INSERT clause in `INSERT { GRAPH <graph> { ... } }`

```scala
// WITH — single default graph for both DELETE and INSERT (note backticks — `with` is a Scala keyword)
// Produces: WITH <graph> DELETE { ... } INSERT { ... } WHERE { ... }
Queries.MODIFY().`with`(graphIri(project)).delete(...).insert(...).where(...)

// FROM/INTO — explicit GRAPH blocks, can target different graphs
// Produces: DELETE { GRAPH <graph> { ... } } INSERT { GRAPH <graph> { ... } } WHERE { ... }
Queries.MODIFY().from(dataGraph).delete(...).into(dataGraph).insert(...).where(...)
```

These are **not** semantically equivalent. `WITH` sets the default graph for DELETE, INSERT,
**and** WHERE — patterns in WHERE implicitly match against that graph. With `.from()`/`.into()`,
only DELETE and INSERT get GRAPH wrappers — the WHERE clause has no default graph, so you
must use `pattern.from(graph)` explicitly to match triples in a named graph.

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

### FILTER NOT EXISTS

```scala
val existingType = variable("existingType")
val filterNotExists = GraphPatterns.filterNotExists(ontology.isA(existingType))
```

**Workaround**: rdf4j drops `FILTER NOT EXISTS` when it is the only pattern in a WHERE
clause ([rdf4j#5561](https://github.com/eclipse-rdf4j/rdf4j/issues/5561), fixed in 5.3.0).
We are on 5.2.2 — remove this workaround when upgrading. Build the query without WHERE,
then append it manually:

```scala
val insertQuery = Queries.MODIFY()
  .prefix(KB.NS, RDF.NS)
  .insert(insertPattern)
  .into(ontology)
  .getQueryString
  .replaceFirst("WHERE \\{\\s*}", "")
  .strip()

val sparql = s"$insertQuery\nWHERE { ${filterNotExists.getQueryString} }"
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

### Conditional and Aggregate Expressions

```scala
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions

// IF/THEN/ELSE — produces IF(condition, thenExpr, elseExpr)
Expressions.iff(
  Expressions.bound(maxOrder),
  Expressions.add(maxOrder, literalOf(1)),
  literalOf(0),
).as(nextOrder)

// Aggregate functions
Expressions.max(order).as(maxOrder)

// Arithmetic
Expressions.add(x, literalOf(1))

// BOUND check — useful in OPTIONAL patterns
Expressions.bound(optionalVar)
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

| Method                      | Purpose                                                                         |
| --------------------------- | ------------------------------------------------------------------------------- |
| `variable(name)`            | Create a `Variable` (wraps `` SparqlBuilder.`var`(name) ``)                     |
| `spo`                       | Returns `(variable("s"), variable("p"), variable("o"))`                         |
| `toRdfIri(iri)`             | Convert `SmartIri`, `InternalIri`, `KnoraIri`, or `StringValue` to `Iri`        |
| `toRdfLiteral(v)`           | Convert `StringLiteralV2`, `BooleanLiteralV2`, `Instant`, `Int` to `RdfLiteral` |
| `toRdfValue(v)`             | Convert `OntologyLiteralV2` to `RdfValue`                                       |
| `zeroOrMore(pred)`          | Create a `PropertyPath` with `*` modifier                                       |
| `graphIri(project)`         | Get the named graph IRI for a `KnoraProject`                                    |
| `askWhere(triple)`          | Wrap a triple pattern in an `ASK WHERE { ... }` query                           |
| `ontologyAndNamespace(iri)` | Get `(Iri, SimpleNamespace)` tuple for prefix declarations                      |

## Getting the Query String

Every query element implements `QueryElement` with `.getQueryString`:

```scala
val queryString: String = query.getQueryString
```

## Integrating with TriplestoreService

Query builders produce rdf4j query objects. To execute them, wrap in the appropriate
`TriplestoreService.Queries` type:

```scala
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.*

// From rdf4j query objects — preferred
val select: Select       = Select(selectQuery)           // SelectQuery → Select
val construct: Construct = Construct(constructQuery)      // ConstructQuery → Construct
val update: Update       = Update(modifyQuery)            // ModifyQuery → Update
val update2: Update      = Update(insertDataQuery)        // InsertDataQuery → Update

// From raw SPARQL strings — when using string interpolation fallbacks
val ask: Ask         = Ask(sparqlString)
val select: Select   = Select(sparqlString)
val update: Update   = Update(sparqlString)

// Gravsearch variants with extended timeout
val gs: Select     = Select.gravsearch(sparqlString)
val gc: Construct  = Construct.gravsearch(sparqlString)
val gc2: Construct = Construct.gravsearch(constructQuery)

// Execute via TriplestoreService
triplestore.query(select)    // Task[SparqlSelectResult]
triplestore.query(construct) // Task[SparqlConstructResponse]
triplestore.query(update)    // Task[Unit]
triplestore.query(ask)       // Task[Boolean]
```

## ZIO Effect Wrapping and Validation

Query builders return ZIO effects to handle validation and side effects like timestamps or UUIDs:

```scala
object CreateLinkQuery extends QueryBuilderHelper {

  def build(
    project: Project,
    resourceIri: ResourceIri,
    linkUpdate: SparqlTemplateLinkUpdate,
  ): IO[SparqlGenerationException, ModifyQuery] =
    for {
      // Validate preconditions — fails with SparqlGenerationException
      _ <- failIf(!linkUpdate.insertDirectLink, "insertDirectLink must be true")
      _ <- failIf(linkUpdate.directLinkExists, "directLinkExists must be false")
    } yield {
      Queries.MODIFY()...
    }
}
```

The `failIf` helper from `QueryBuilderHelper` is a concise way to validate preconditions:

```scala
// Definition in QueryBuilderHelper:
inline def failIf(condition: Boolean, message: String): IO[SparqlGenerationException, Unit] =
  ZIO.fail(SparqlGenerationException(message)).when(condition).unit
```

For queries that generate timestamps or UUIDs, use ZIO's `Clock` and `Random`:

```scala
def build(...): IO[SparqlGenerationException, (UUID, Update)] =
  for {
    newValueUUID <- Random.nextUUID
    currentTime  <- Clock.instant
    _            <- failIf(...)
  } yield (newValueUUID, Update(query))
```

## Known Limitations

The SparqlBuilder does **not** support:

- VALUES blocks
- RDF collection syntax `( ... )`
- DESCRIBE queries
- ASK queries (use `QueryBuilderHelper.askWhere()` which builds the string manually)

For these, fall back to string interpolation with `.getQueryString` for the parts that _are_ supported.

## String Interpolation Fallbacks

When SparqlBuilder lacks support, fall back to string interpolation. Common cases:

### BIND

Not needed for standard SPARQL queries — use the actual IRI directly in triple patterns
via `Rdf.iri()` or `toRdfIri()` rather than binding to a variable.

### VALUES

```scala
val valuesClause = iris.map(iri => s"<$iri>").mkString(" ")
s"VALUES ?resource { $valuesClause }"
```

### GROUP_CONCAT and complex aggregates

```scala
s"""(GROUP_CONCAT(IF(BOUND(?valueObject), STR(?valueObject), ""); SEPARATOR="$separator") AS ?valueObjectConcat)"""
```

When mixing string interpolation with builder output, use `.getQueryString` to extract
the SPARQL string from builder elements:

```scala
val filterClause = filterNotExists.getQueryString
val builderPart = Queries.MODIFY().prefix(...).insert(...).into(...).getQueryString
val sparql = s"$builderPart\nWHERE { $filterClause }"
```

## Patterns to Follow

1. **One query builder object per file** — keep query construction in dedicated `*Query.scala` objects
2. **Extend QueryBuilderHelper** — use the trait for consistent IRI/literal conversions
3. **Separate concerns** — split complex MODIFY queries into `buildDeletePatterns()`,
   `buildInsertPatterns()`, `buildWhereClause()` methods
4. **Use `.andHas()` for readability** — chain multiple predicates on the same subject
5. **Use `.and()` for combining** — join independent graph patterns
6. **Use `.optional()` on patterns** that may not match
7. **Use `zeroOrMore()` / property paths** for transitive relations like `rdfs:subClassOf*`

## Gravsearch Queries

Gravsearch queries are CONSTRUCT queries parsed by the Gravsearch engine, not sent directly
to the triplestore. They differ from standard SPARQL in three ways:

1. **BIND for the main resource** — the main resource must be a `?variable` assigned with
   `BIND(<iri> AS ?resource)`, not an IRI used directly in triple patterns.
2. **`knora-api:isMainResource true`** — the CONSTRUCT clause must mark the main resource
   with this triple.
3. **API v2 complex schema IRIs** — use `knora-api:` namespace and complex-schema property
   IRIs (via `.toComplexSchema`), not internal-schema IRIs.

IRIs like `ResourceIri`, `ProjectIri`, `UserIri`, and `ValueIri` have no schema
representation — for these it is safe to use `toRdfIri()`. Ontology, class, and property
IRIs **must** be converted to complex schema with `.toComplexSchema`.

Because of the BIND requirement, Gravsearch queries use string interpolation rather than
SparqlBuilder (which does not support BIND):

```scala
object MyGravsearchQuery extends QueryBuilderHelper {

  def build(resourceIri: ResourceIri, propertyIri: PropertyIri): String = {
    val resourceIriStr = toRdfIri(resourceIri).getQueryString  // no schema — safe
    val apiV2Property  = propertyIri.toComplexSchema            // must use complex schema

    s"""|PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
        |
        |CONSTRUCT {
        |  ?resource knora-api:isMainResource true .
        |  ?resource <$apiV2Property> ?value .
        |} WHERE {
        |  BIND($resourceIriStr AS ?resource)
        |  ?resource a knora-api:Resource .
        |  OPTIONAL {
        |    ?resource <$apiV2Property> ?value .
        |  }
        |}
        |""".stripMargin
  }
}
```

See the [Gravsearch API documentation](../03-endpoints/api-v2/query-language.md) and the
[Gravsearch design documentation](../05-internals/design/api-v2/gravsearch.md) for full
details on the query language and its internals.

## Testing Query Builders

### Direct string comparison

For simple queries, compare the generated SPARQL string directly:

```scala
test("should produce correct DELETE query") {
  val actual = DeleteListNodeCommentsQuery.build(nodeIri, project).getQueryString
  assertTrue(actual == """PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                         |DELETE { ... }
                         |WHERE { ... }""".stripMargin)
}
```

### Golden tests

For complex queries, use the `GoldenTest` utility to compare against expected files:

```scala
test("should produce correct INSERT query") {
  for {
    query <- CreateLinkQuery.build(project, resourceIri, linkUpdate, uuid, instant, None)
    result = replaceUuidPatterns(query.getQueryString)
  } yield assertGolden(result, "createLink__basic")
}
```

### Apache Jena parsing for structural validation

Parse the generated SPARQL with Apache Jena to verify it is syntactically valid,
then inspect the parsed structure:

```scala
import org.apache.jena.update.UpdateFactory

test("should produce a valid SPARQL update") {
  for {
    query <- CreateLinkQuery.build(...)
  } yield {
    val parsed = UpdateFactory.create(query.getQueryString)
    assertTrue(parsed.getOperations.size() == 1)
  }
}
```
