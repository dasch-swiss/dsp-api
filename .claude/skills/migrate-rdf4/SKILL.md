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

#### Prefer typed IRI parameters over raw `IRI` (String)

When translating Twirl template parameters to the `build` method signature, replace raw `IRI` (`String`) parameters with their typed equivalents wherever possible:

| Twirl parameter type | Preferred `build` parameter type | Conversion in body |
|----------------------|----------------------------------|--------------------|
| `dataNamedGraph: IRI` | `project: Project` (or `KnoraProject`) | `graphIri(project)` |
| link/resource source IRI | `ResourceIri` | `toRdfIri(resourceIri)` |
| user IRI | `UserIri` | `toRdfIri(userIri)` |
| project IRI | `ProjectIri` | `toRdfIri(projectIri)` |
| property IRI        | `PropertyIri` | `toRdfIri(propertyIri)` |
| value IRI | `ValueIri` | `toRdfIri(valueIri)` |
| list IRI | `ListIri` | `toRdfIri(listIri)` |
| resource IRI | `ResourceIri` | `toRdfIri(resourceIri)` |

The caller may need minor adjustments (e.g. `resourceInfo.projectADM` instead of `dataNamedGraph`, `requestingUser.userIri` instead of `requestingUser.id`). Check the caller context to determine the right typed wrapper and the conversion needed at the call site.

#### Handling `Instant` / `currentTime` parameters

Check whether the `Instant` value is **only** used inside the query, or also returned/reused by the caller after the query is built, and whether it is always generated or sometimes caller-supplied.

- **Used only inside the query**: move the timestamp into `build` using `zio.Clock.instant`, removing the parameter.
- **Passed in unconditionally (always `Instant.now`) and reused after building**: move the timestamp into `build` using `zio.Clock.instant` and return it alongside the query as a tuple `(Instant, QueryType)`. This keeps timestamp creation inside the builder while giving the caller access.
- **Conditionally provided by the caller** (e.g. a custom creation date via `Option[Instant]`): keep it as a parameter. The caller owns the decision of which instant to use.

To decide, **always read the actual call site** — do not guess from the template signature alone. Check:
1. How is the instant created? Look for `Instant.now`, `Clock.instant`, or a conditional like `customDate.getOrElse(Instant.now)`. If it is conditional or derived from a caller-supplied `Option[Instant]`, it must stay as a parameter.
2. Does the instant appear after the query-building line (e.g. in a return value, a result object, or another query)?

If (1) is always unconditionally generated and (2) is yes → return a tuple.
If (1) is always unconditionally generated and (2) is no → generate inside `build`, no need to return it.
If (1) is conditionally provided → keep as a parameter.

```scala
// Example: Instant generated inside build and returned as a tuple
def build(...): IO[SparqlGenerationException, (Instant, ModifyQuery)] =
  for {
    ...
    now <- Clock.instant
  } yield {
    val currentTimeLiteral = Rdf.literalOfType(now.toString, XSD.DATETIME)
    // ... build query using currentTimeLiteral ...
    (now, query)
  }

// Caller destructures the tuple
(createdAt, sparqlUpdate) <- MyQuery.build(...)
_ <- triplestoreService.query(Update(sparqlUpdate))
// ... use createdAt in the return value ...
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

#### Precondition validation with `failIf`

Many Twirl templates contain `@if` / `@else` blocks that throw `SparqlGenerationException` when a boolean flag has an unexpected value. Translate these to `failIf` calls from `QueryBuilderHelper`:

```scala
// failIf is defined in QueryBuilderHelper — no need for a local definition
for {
  _ <- failIf(!linkUpdate.deleteDirectLink, "linkUpdate.deleteDirectLink must be true in this SPARQL template")
  _ <- failIf(linkUpdate.newReferenceCount != 0, "linkUpdate.newReferenceCount must be 0 in this SPARQL template")
} yield {
  // build the query ...
}
```

When the `build` method uses `failIf`, its return type must be `IO[SparqlGenerationException, QueryType]` (e.g. `IO[SparqlGenerationException, ModifyQuery]`). Structure the method as a `for / yield` comprehension: validations in the `for` block, query construction in the `yield` block.

Also write validation tests that assert each precondition failure:

```scala
test("should fail when deleteDirectLink is false") {
  val effect = MyQuery.build(invalidParams)
  assertZIO(effect.exit)(
    failsWithA[SparqlGenerationException] &&
      fails(hasMessage(containsString("deleteDirectLink must be true"))),
  )
}
```

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
sparqlUpdate = sparql.v2.txt.queryName(dataNamedGraph, linkSourceIri, ..., currentTime, requestingUser.id)
_ <- triplestoreService.query(Update(sparqlUpdate))

// After (rdf4j) — note typed parameters and effectful build
sparqlUpdate <- QueryNameQuery.build(resourceInfo.projectADM, ResourceIri.unsafeFrom(iri.toSmartIri), ..., currentTime, requestingUser.userIri)
_ <- triplestoreService.query(Update(sparqlUpdate))
```

Wrap the query in the appropriate execution wrapper: `Select()`, `Construct()`, `Update()`, or pass `Ask` directly.

When the `build` method returns an effect (`IO[SparqlGenerationException, QueryType]`), use `<-` instead of `=` in the caller's for-comprehension.

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
