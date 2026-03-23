---
name: migrate-rdf4
description: "Migrate a Twirl SPARQL template (.scala.txt) to a type-safe rdf4j query builder with test-first workflow. Use this skill whenever the user wants to migrate a SPARQL query, convert a Twirl template, or mentions migrating to rdf4j. Triggers on: migrate query, migrate twirl, convert sparql, migrate rdf4j, migrate to rdf4j."
user-invocable: true
argument-hint: <path-to-twirl-file>
---

# Migrate Twirl SPARQL Template to rdf4j Query Builder

Migrate a single Twirl SPARQL template to a type-safe rdf4j query builder using a test-first approach.
The test-first workflow catches regressions early: you write tests against the existing query output, then rewrite the query and confirm the tests still pass.

## Input

A path to a Twirl template file (`.scala.txt`), typically located at:
```
webapi/src/main/twirl/org/knora/webapi/messages/twirl/queries/sparql/{module}/{queryName}.scala.txt
```

## Workflow

### Step 1 — Understand the existing query

Read the Twirl template. Identify:
- The query type (SELECT, CONSTRUCT, ASK, INSERT/DELETE)
- The parameters (`@(param: Type, ...)`)
- All SPARQL patterns, filters, optionals, unions, named graphs
- Which prefixes are used

Also find the caller — search for the template name (without extension) in the Scala sources to understand how the query is invoked and what types the parameters have. This context is important for writing realistic test fixtures.

### Step 2 — Write unit tests for the existing query

Create a test at:
```
webapi/src/test/scala/org/knora/webapi/slice/{module}/repo/{QueryName}QuerySpec.scala
```

The test calls the Twirl template with known parameter values and asserts the full SPARQL string output.
This captures the existing behavior as a regression safety net.

**Test structure:**

```scala
package org.knora.webapi.slice.{module}.repo

import zio.test.*

object {QueryName}QuerySpec extends ZIOSpecDefault {
  override val spec: Spec[Any, Nothing] = suite("{QueryName}Query")(
    test("build should produce the expected SPARQL query") {
      val actual = // call the Twirl template with test parameters, get the rendered String
      assertTrue(actual ==
        """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |SELECT ...
          |WHERE {
          |  ...
          |}""".stripMargin)
    }
  )
}
```

Choose realistic but deterministic test parameter values (fixed IRIs, known literals).
If the template has multiple code paths (e.g. conditional blocks), write one test per path.

Assert the **entire** query string — not fragments. Use a formatted multi-line string with `.stripMargin` for readability.

### Step 3 — Run the tests

```bash
sbt "testOnly *{QueryName}QuerySpec*"
```

All tests must pass before proceeding. If a test fails, fix the expected string to match the actual Twirl output — the goal here is to capture what the template currently produces.

### Step 4 — Create the rdf4j query object

Create the new query builder at:
```
webapi/src/main/scala/org/knora/webapi/slice/{module}/repo/{QueryName}Query.scala
```

Follow this pattern:

```scala
package org.knora.webapi.slice.{module}.repo

import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

object {QueryName}Query extends QueryBuilderHelper {
  def build(/* typed parameters */): {QueryType} = {
    // 1. Define variables
    val s = variable("s")

    // 2. Build patterns
    val wherePattern = s.has(KnoraBase.property, value)

    // 3. Construct and return the query
    Queries
      .SELECT(s)
      .prefix(KnoraBase.NS)
      .where(wherePattern)
  }
}
```

**Query type mapping:**

| Twirl query type | rdf4j return type | Execution wrapper |
|------------------|-------------------|-------------------|
| SELECT           | `SelectQuery`     | `Select(query)`   |
| CONSTRUCT        | `ConstructQuery`  | `Construct(query)` |
| ASK              | `Ask` (string)    | direct            |
| INSERT/DELETE    | `ModifyQuery`     | `Update(query)`   |

**Conversion reference:**

| Twirl pattern | rdf4j equivalent |
|---------------|------------------|
| `@param` variable interpolation | Typed method parameter |
| `?var` | `variable("var")` |
| `IRI("@iri")` | `toRdfIri(iri)` |
| `"@value"` | `Rdf.literalOf(value)` or `toRdfLiteral(value)` |
| `prop*` (property path) | `zeroOrMore(prop)` |
| `OPTIONAL { ... }` | `pattern.optional()` |
| `FILTER NOT EXISTS { ... }` | `GraphPatterns.filterNotExists(pattern)` |
| `GRAPH <@graph>` | `.from(graphIri(project))` |
| `?s rdf:type X` | `s.isA(X)` |
| `?s prop1 ?o1 ; prop2 ?o2` | `s.has(prop1, o1).andHas(prop2, o2)` |
| `?s prop1 ?o1 . ?x prop2 ?o2` | `s.has(prop1, o1).and(x.has(prop2, o2))` |

Use IRIs from `Vocabulary.scala` (`KnoraBase`, `KnoraAdmin`, `SalsahGui`) and rdf4j built-ins (`RDF`, `RDFS`, `OWL`, `XSD`).

Read `webapi/src/main/scala/org/knora/webapi/slice/common/QueryBuilderHelper.scala` for available helper methods.
Read `docs/development/dsp-api-sparql-builder.md` for the full SparqlBuilder API reference.

### Step 5 — Update tests and verify

Repoint the tests to call the new `{QueryName}Query.build(...)` method instead of the Twirl template.
Get the query string via `.getQueryString` (for SELECT/CONSTRUCT/MODIFY) or `.value` (for ASK).

Run the tests again:

```bash
sbt "testOnly *{QueryName}QuerySpec*"
```

The rdf4j builder may produce slightly different whitespace or prefix ordering compared to the Twirl template. If a test fails only due to insignificant formatting differences, update the expected string to match the rdf4j output — as long as the SPARQL is semantically equivalent.

If a test fails due to a real semantic difference (missing triple pattern, wrong variable, etc.), fix the query builder until the tests pass.

### Step 6 — Update the caller

Find the code that calls the Twirl template (identified in Step 1) and replace it with the new query object:

```scala
// Before (Twirl)
triplestoreService.query(Select(queries.sparql.module.queryName(param1, param2).toString()))

// After (rdf4j)
triplestoreService.query(Select({QueryName}Query.build(param1, param2)))
```

Wrap the query in the appropriate execution wrapper: `Select()`, `Construct()`, `Update()`, or pass `Ask` directly.

### Step 7 — Delete the Twirl template

Remove the old `.scala.txt` file. Verify the project still compiles:

```bash
sbt compile
```

If compilation fails, there are other callers still referencing the template — update them too.

### Step 8 — Final verification

Run the full test suite for the affected module to make sure nothing else broke:

```bash
sbt "testOnly *{QueryName}*"
```

Run `sbt fmt` to format the new files.

## ASK Query Exception

rdf4j does not have a dedicated `AskQuery` class. For ASK queries, build pattern fragments with the query builder, then assemble the final query via string interpolation:

```scala
object IsNodeUsedQuery extends QueryBuilderHelper {
  def build(nodeIri: ListIri): Ask = {
    val s = variable("s")
    val guiAttr = s.has(SalsahGui.guiAttribute, Rdf.literalOf(s"hlist=<$nodeIri>")).getQueryString
    val valueNode = s.has(KnoraBase.valueHasListNode, toRdfIri(nodeIri)).getQueryString

    Ask(s"""ASK
           |WHERE {
           |  { $guiAttr } UNION { $valueNode }
           |}""".stripMargin)
  }
}
```

## Common Imports

```scala
// rdf4j core
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.core.query.{SelectQuery, ConstructQuery, ModifyQuery}
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns

// Standard RDF vocabularies
import org.eclipse.rdf4j.model.vocabulary.{RDF, RDFS, OWL, XSD}

// dsp-api helpers
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.{KnoraBase, KnoraAdmin, SalsahGui}
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.{Ask, Select, Construct, Update}
```
