---
name: migrate-twirl-sparql-query-to-rdf4j
description: "Migrate a SPARQL query defined using Twirl templates to use the RDF4J query builder. Triggers on: migrate query"
user-invocable: true
---

# Migrate Twirl SPARQL Query to rdf4j

This skill guides the migration of Twirl template SPARQL queries (`.scala.txt` files) to type-safe rdf4j SPARQLBuilder queries.

## Overview

DSP-API historically used Twirl templates for SPARQL queries. These are being migrated to use rdf4j's SPARQLBuilder for type safety and better maintainability.

## File Structure

### Original Twirl Template Location

```
webapi/src/main/twirl/org/knora/webapi/messages/twirl/queries/sparql/{module}/{queryName}.scala.txt
```

### New Query Object Location

```
webapi/src/main/scala/org/knora/webapi/slice/{module}/repo/{QueryName}Query.scala
```

### Test Location

```
webapi/src/test/scala/org/knora/webapi/slice/{module}/repo/{QueryName}QuerySpec.scala
```

## Migration Pattern

### Basic Structure

Every query object follows this pattern:

```scala
package org.knora.webapi.slice.{module}.repo

import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.core.query.{SelectQuery|ConstructQuery|ModifyQuery}
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

object {QueryName}Query extends QueryBuilderHelper {
  def build(/* parameters */): {QueryType} = {
    // 1. Define variables
    val s = variable("s")

    // 2. Build patterns
    val wherePattern = s.has(KnoraBase.property, value)

    // 3. Construct query
    Queries
      .SELECT(s)
      .prefix(KnoraBase.NS)
      .where(wherePattern)
  }
}
```

## QueryBuilderHelper Methods

The `QueryBuilderHelper` trait provides these helper methods:

### Variable Creation

```scala
def variable(name: String): Variable = SparqlBuilder.`var`(name)
def spo: (Variable, Variable, Variable) = (variable("s"), variable("p"), variable("o"))
```

### IRI Conversion

```scala
def toRdfIri(iri: KnoraIri): Iri      // For KnoraIri types
def toRdfIri(iri: SmartIri): Iri      // For SmartIri
def toRdfIri(iri: InternalIri): Iri   // For InternalIri
def toRdfIri(iri: StringValue): Iri   // For StringValue wrappers
```

### Literal Conversion

```scala
def toRdfLiteral(instant: Instant): RdfLiteral.StringLiteral
def toRdfLiteral(lmd: LastModificationDate): RdfLiteral.StringLiteral
def toRdfLiteral(int: Int): RdfLiteral.StringLiteral
def toRdfLiteralNonNegative(int: Int): RdfLiteral.StringLiteral
def toRdfLiteral(literalV2: StringLiteralV2): RdfLiteral.StringLiteral  // Handles language tags
def toRdfLiteral(booleanV2: BooleanLiteralV2): RdfLiteral.StringLiteral
```

### Property Paths

```scala
def zeroOrMore(pred: Iri): PropertyPath  // For path* patterns like knora-base:previousValue*
```

### Graph Management

```scala
def graphIri(knoraProject: KnoraProject): Iri  // Project data named graph
def ontologyAndNamespace(ontologyIri: OntologyIri): (Iri, SimpleNamespace)
```

### ASK Query Helper

```scala
def askWhere(triplePattern: TriplePattern): Ask
```

## Query Type Mapping

| Twirl Query Type | rdf4j Type | Wrapper |
|------------------|------------|---------|
| SELECT | `SelectQuery` | `Select(query)` |
| CONSTRUCT | `ConstructQuery` | `Construct(query)` |
| ASK | String | `Ask(sparqlString)` |
| INSERT/DELETE | `ModifyQuery` | `Update(query)` |

## Pattern Building

### Basic Triple Pattern

```scala
// Twirl: ?s knora-base:isDeleted false .
val s = variable("s")
s.has(KnoraBase.isDeleted, Rdf.literalOf(false))
```

### Type Assertion

```scala
// Twirl: ?node rdf:type knora-base:ListNode .
node.isA(KnoraBase.ListNode)
```

### Chaining Patterns

```scala
// Twirl: ?s knora-base:prop1 ?o1 ; knora-base:prop2 ?o2 .
s.has(KnoraBase.prop1, o1).andHas(KnoraBase.prop2, o2)

// Twirl: ?s knora-base:prop1 ?o1 . ?x knora-base:prop2 ?o2 .
s.has(KnoraBase.prop1, o1).and(x.has(KnoraBase.prop2, o2))
```

### Optional Patterns

```scala
// Twirl: OPTIONAL { ?s rdfs:label ?label }
s.has(RDFS.LABEL, label).optional()
```

### Property Paths

```scala
// Twirl: ?current knora-base:previousValue* ?old
val previousValuePath = zeroOrMore(KnoraBase.previousValue)
current.has(previousValuePath, old)
```

### FILTER NOT EXISTS

```scala
// Twirl: FILTER NOT EXISTS { ?s knora-base:lastModificationDate ?date }
GraphPatterns.filterNotExists(s.has(KnoraBase.lastModificationDate, date))
```

## Query Examples

### SELECT Query

**Twirl Template:**

```
@(filename: String)
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>

SELECT ?creator ?project ?permissions
WHERE {
    ?fileValue knora-base:internalFilename "@filename" .
    ?fileValue knora-base:attachedToUser ?creator .
    ?currentFileValue knora-base:previousValue* ?fileValue ;
        knora-base:hasPermissions ?permissions ;
        knora-base:isDeleted false .
    ?resource ?prop ?currentFileValue ;
        knora-base:attachedToProject ?project ;
        knora-base:isDeleted false .
}
```

**rdf4j Migration:**

```scala
object FileValuePermissionsQuery extends QueryBuilderHelper {
  def build(filename: InternalFilename): SelectQuery = {
    val fileValue        = variable("fileValue")
    val currentFileValue = variable("currentFileValue")
    val resource         = variable("resource")
    val prop             = variable("prop")
    val creator          = variable("creator")
    val project          = variable("project")
    val permissions      = variable("permissions")

    val previousValuePath = zeroOrMore(KnoraBase.previousValue)

    val wherePattern = fileValue
      .has(KnoraBase.internalFilename, Rdf.literalOf(filename.value))
      .andHas(KnoraBase.attachedToUser, creator)
      .and(
        currentFileValue
          .has(previousValuePath, fileValue)
          .andHas(KnoraBase.hasPermissions, permissions)
          .andHas(KnoraBase.isDeleted, Rdf.literalOf(false)),
      )
      .and(
        resource
          .has(prop, currentFileValue)
          .andHas(KnoraBase.attachedToProject, project)
          .andHas(KnoraBase.isDeleted, Rdf.literalOf(false)),
      )

    Queries
      .SELECT(creator, project, permissions)
      .prefix(KnoraBase.NS)
      .where(wherePattern)
  }
}
```

### ASK Query with UNION

**Twirl Template:**

```
@(nodeIri: IRI)
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX salsah-gui: <http://www.knora.org/ontology/salsah-gui#>

SELECT DISTINCT ?isUsed
WHERE {
    BIND(IRI("@nodeIri") AS ?node)
    BIND(true AS ?isUsed)
    {
        ?s salsah-gui:guiAttribute "hlist=<@nodeIri>" .
    } UNION {
        ?s knora-base:valueHasListNode ?node .
    }
}
```

**rdf4j Migration:**
The ASK query migration is an exception to other query types like `CONSTRUCT` and `SELECT` because rdf4j does not have a dedicated `AskQuery` class. 
Instead, we build the ASK query as a string using string interpolation for the UNION patterns.
Do not use the string interpolation for `CONSTRUCT` or `SELECT` queries; use the query builder methods instead.

```scala
object IsNodeUsedQuery extends QueryBuilderHelper {
  def build(nodeIri: ListIri): Ask = {
    val s = variable("s")

    // Build individual patterns, then use string interpolation for UNION
    val guiAttributePattern = s.has(SalsahGui.guiAttribute, Rdf.literalOf(s"hlist=<$nodeIri>")).getQueryString
    val valueHasListNodePattern = s.has(KnoraBase.valueHasListNode, toRdfIri(nodeIri)).getQueryString

    Ask(s"""
           |ASK
           |WHERE {
           |  {
           |    $guiAttributePattern
           |  } UNION {
           |    $valueHasListNodePattern
           |  }
           |}
           |""".stripMargin)
  }
}
```

### DELETE Query (ModifyQuery)

**rdf4j Implementation:**

```scala
object DeleteNodeQuery extends QueryBuilderHelper {
  def buildForChildNode(nodeIri: ListIri, project: KnoraProject): ModifyQuery =
    val node               = toRdfIri(nodeIri)
    val (parentNode, p, o) = (variable("parentNode"), variable("p"), variable("o"))
    Queries
      .MODIFY()
      .prefix(KnoraBase.NS, RDF.NS)
      .delete(node.has(p, o), parentNode.has(KnoraBase.hasSubListNode, node))
      .from(graphIri(project))
      .where(
        node
          .isA(KnoraBase.ListNode)
          .andHas(p, o)
          .and(parentNode.isA(KnoraBase.ListNode).andHas(KnoraBase.hasSubListNode, node)),
      )
}
```

### CONSTRUCT Query

**rdf4j Implementation:**

```scala
object GetListNodeQuery extends QueryBuilderHelper {
  def build(nodeIri: ListIri): ConstructQuery =
    val (node, p, o) = (toRdfIri(nodeIri), variable("p"), variable("o"))
    Queries
      .CONSTRUCT(node.has(p, o))
      .prefix(KnoraBase.NS)
      .where(node.isA(KnoraBase.ListNode).andHas(p, o))
}
```

## Vocabulary Usage

Use predefined IRIs from `Vocabulary.scala`:

```scala
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraAdmin
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.SalsahGui

// Examples
KnoraBase.ListNode           // knora-base:ListNode
KnoraBase.isDeleted          // knora-base:isDeleted
KnoraBase.attachedToProject  // knora-base:attachedToProject
KnoraAdmin.User              // knora-admin:User
SalsahGui.guiAttribute       // salsah-gui:guiAttribute
```

For standard RDF vocabularies, use rdf4j's built-in:

```scala
import org.eclipse.rdf4j.model.vocabulary.{RDF, RDFS, OWL, XSD}

RDF.TYPE      // rdf:type
RDFS.LABEL    // rdfs:label
RDFS.COMMENT  // rdfs:comment
OWL.ONTOLOGY  // owl:Ontology
```

## Query Execution

Wrap queries for execution via `TriplestoreService`:

```scala
// SELECT
triplestoreService.query(Select(FileValuePermissionsQuery.build(filename)))

// CONSTRUCT
triplestoreService.query(Construct(GetListNodeQuery.build(nodeIri)))

// ASK
triplestoreService.query(IsNodeUsedQuery.build(nodeIri))

// UPDATE (INSERT/DELETE)
triplestoreService.query(Update(DeleteNodeQuery.buildForChildNode(nodeIri, project)))
```

## Migration Checklist

1. [ ] Create new query object in appropriate `slice/{module}/repo/` directory
2. [ ] Extend `QueryBuilderHelper`
3. [ ] Define `build` method with typed parameters
4. [ ] Convert variables: `@param` -> `variable("param")`
5. [ ] Convert IRIs: `"@iri"` -> `toRdfIri(iri)`
6. [ ] Convert literals: `"@value"` -> `Rdf.literalOf(value)` or typed literal
7. [ ] Convert property paths: `prop*` -> `zeroOrMore(prop)`
8. [ ] Add prefixes via `.prefix(NS1, NS2, ...)`
9. [ ] Update caller to use new query object
10. [ ] Delete old Twirl template
11. [ ] Add unit test for query generation (optional but recommended)

## Common Imports

```scala
// Core rdf4j
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.core.query.{SelectQuery, ConstructQuery, ModifyQuery}
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns

// Standard vocabularies
import org.eclipse.rdf4j.model.vocabulary.{RDF, RDFS, OWL, XSD}

// DSP-API helpers
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.{KnoraBase, KnoraAdmin, SalsahGui}
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.{Ask, Select, Construct, Update}
```

## Key Differences from Twirl

| Aspect | Twirl | rdf4j SPARQLBuilder |
|--------|-------|---------------------|
| String interpolation | `"@variable"` | Not needed - type safe |
| SPARQL injection risk | Possible | Eliminated |
| Prefixes | Manual in template | `.prefix(NS)` |
| Named graphs | `GRAPH <@graph>` | `.from(graphIri)` |
| UNION patterns | Direct SPARQL | Use `.getQueryString` + interpolation |
| Type safety | None | Full compile-time checking |

## Testing Pattern

```scala
class {QueryName}QuerySpec extends ZIOSpecDefault {
  def spec = suite("{QueryName}Query")(
    test("build should generate correct SPARQL") {
      val query = {QueryName}Query.build(testParam)
      val sparql = query.getQueryString
      assertTrue(
        sparql.contains("SELECT"),
        sparql.contains("knora-base:property"),
      )
    }
  )
}
```

Check the write-unit-test-for-query-builder skill for more details on testing query builders.
