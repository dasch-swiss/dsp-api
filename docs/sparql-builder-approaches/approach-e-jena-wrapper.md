# Approach E: Thin Scala 3 Wrapper over Jena ARQ QueryBuilder

## Philosophy

Use Jena's `jena-querybuilder` extras module directly from Scala. Jena produces validated `Query` objects (real SPARQL AST). The trade-off: the API is mutable Java-style (methods return `this`) which clashes with immutable Scala/ZIO style.

## Shared Vocabulary

```scala
import org.apache.jena.arq.querybuilder.{AskBuilder, SelectBuilder, UpdateBuilder, WhereBuilder}
import org.apache.jena.graph.NodeFactory
import org.apache.jena.sparql.core.Var

def iri(uri: String)         = NodeFactory.createURI(uri)
def variable(name: String)   = Var.alloc(name)
def stringLit(value: String) = NodeFactory.createLiteralString(value)
def boolLit(value: Boolean)  = NodeFactory.createLiteralDT(
  value.toString, org.apache.jena.datatypes.xsd.XSDDatatype.XSDboolean)
def intLit(value: Int) = NodeFactory.createLiteralDT(
  value.toString, org.apache.jena.datatypes.xsd.XSDDatatype.XSDinteger)
def typedLit(value: String, dt: String) = NodeFactory.createLiteralDT(value, NodeFactory.getType(dt))

val knoraBase   = "http://www.knora.org/ontology/knora-base#"
val rdfs        = "http://www.w3.org/2000/01/rdf-schema#"
val xsd         = "http://www.w3.org/2001/XMLSchema#"
val owl         = "http://www.w3.org/2002/07/owl#"
val kbIsDeleted = iri(knoraBase + "isDeleted")
val kbLastMod   = iri(knoraBase + "lastModificationDate")
val rdfType     = iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
```

---

## Benchmark 1: Simple SELECT with OPTIONAL

### Plain SPARQL

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

### Building the query

```scala
val s             = variable("s")
val p             = variable("p")
val o             = variable("o")
val lmd           = variable("lastModDate")
val resourceClass = iri("http://example.org/MyClass")

val builder = new SelectBuilder()
builder.addVar(s).addVar(p).addVar(o)
builder.addWhere(s, rdfType, resourceClass)
builder.addWhere(s, kbIsDeleted, boolLit(false))
builder.addOptional(s, kbLastMod, lmd)
builder.addWhere(s, p, o)
builder.addOrderBy(lmd, Order.DESCENDING)
builder.setLimit(25)

val query = builder.buildString()
```

---

## Benchmark 2: ASK with UNION

### Plain SPARQL

```sparql
ASK
WHERE {
  { ?s <http://www.knora.org/ontology/salsah-gui#guiAttribute> "hlist=<http://rdfh.ch/lists/0001/treeList01>" . }
  UNION
  { ?s <http://www.knora.org/ontology/knora-base#valueHasListNode> <http://rdfh.ch/lists/0001/treeList01> . }
}
```

### Building the query

```scala
val s              = variable("s")
val nodeIri        = iri("http://rdfh.ch/lists/0001/treeList01")
val guiAttr        = iri("http://www.knora.org/ontology/salsah-gui#guiAttribute")
val valHasListNode = iri(knoraBase + "valueHasListNode")

val branch1 = new WhereBuilder()
  .addWhere(s, guiAttr, stringLit(s"hlist=<$nodeIri>"))
val branch2 = new WhereBuilder()
  .addWhere(s, valHasListNode, nodeIri)

val builder = new AskBuilder()
builder.addUnion(branch1)
builder.addUnion(branch2)

val query = builder.buildString()
```

---

## Benchmark 3: DeletePropertyQuery (DELETE/INSERT WHERE)

### Plain SPARQL

```sparql
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
PREFIX owl: <http://www.w3.org/2002/07/owl#>

DELETE { GRAPH <...> { ... } }
INSERT { GRAPH <...> { ... } }
WHERE { ... FILTER NOT EXISTS { ... } ... }
```

### Building the query

```scala
val ontologyIri = iri("http://www.knora.org/ontology/0001/anything")
val propertyIri = iri("http://www.knora.org/ontology/0001/anything#hasOtherThing")
val lmdValue    = typedLit("2024-01-01T00:00:00Z", xsd + "dateTime")
val newLmd      = typedLit("2024-01-02T00:00:00Z", xsd + "dateTime")
val propertyPred = variable("propertyPred")
val propertyObj  = variable("propertyObj")
val s            = variable("s")
val p            = variable("p")

val owlOntology   = iri(owl + "Ontology")
val owlObjectProp = iri(owl + "ObjectProperty")

val linkValuePropertyIri: Option[String] =
  Some("http://www.knora.org/ontology/0001/anything#hasOtherThingValue")
val linkValuePropertyPred = variable("linkValuePropertyPred")
val linkValuePropertyObj  = variable("linkValuePropertyObj")

// Jena's UpdateBuilder for DELETE/INSERT/WHERE
val builder = new UpdateBuilder()

// DELETE patterns
builder.addDelete(ontologyIri, kbLastMod, lmdValue)
builder.addDelete(propertyIri, propertyPred, propertyObj)
linkValuePropertyIri.foreach { lvpIri =>
  builder.addDelete(iri(lvpIri), linkValuePropertyPred, linkValuePropertyObj)
}

// INSERT patterns
builder.addInsert(ontologyIri, kbLastMod, newLmd)

// WHERE patterns
builder.addWhere(ontologyIri, rdfType, owlOntology)
builder.addWhere(ontologyIri, kbLastMod, lmdValue)
builder.addWhere(propertyIri, rdfType, owlObjectProp)
builder.addWhere(propertyIri, propertyPred, propertyObj)

// FILTER NOT EXISTS — Jena's builder requires constructing a subquery
val filterBuilder = new WhereBuilder()
filterBuilder.addWhere(s, p, propertyIri)
builder.addMinus(filterBuilder)  // Note: addFilter for NOT EXISTS is more complex

linkValuePropertyIri.foreach { lvpIri =>
  builder.addWhere(iri(lvpIri), linkValuePropertyPred, linkValuePropertyObj)
}

// Note: Jena's UpdateBuilder doesn't directly support GRAPH-scoped
// DELETE/INSERT. Would need to use the lower-level UpdateModify API
// or build the query string manually for the GRAPH wrapping.

val query = builder.buildRequest().toString
```

---

## Benchmark 4: InsertValueQueryBuilder (conditional + iteration)

### Plain SPARQL

```sparql
DELETE {
  ?resource knora-base:lastModificationDate ?resourceLastModificationDate .
  ?resource <http://example.org/hasLink> <http://example.org/target1> .
  ?resource <http://example.org/hasLinkValue> ?linkValue0 .
  ?linkValue0 knora-base:valueHasUUID ?linkValueUUID0 .
  ?linkValue0 knora-base:hasPermissions ?linkValuePermissions0 .
}
WHERE { ?resource a knora-base:Resource . }
```

### Building the query

```scala
case class LinkUpdate(
  linkPropertyIri: String, linkTargetIri: String,
  deleteDirectLink: Boolean, linkValueExists: Boolean,
)

val resource        = variable("resource")
val resourceLastMod = variable("resourceLastModificationDate")
val kbHasPermissions = iri(knoraBase + "hasPermissions")
val kbValueHasUUID   = iri(knoraBase + "valueHasUUID")

val linkUpdates = List(
  LinkUpdate("http://example.org/hasLink", "http://example.org/target1",
    deleteDirectLink = true, linkValueExists = true),
)

val builder = new UpdateBuilder()

// Base delete pattern
builder.addDelete(resource, kbLastMod, resourceLastMod)

// Conditional + iteration — imperative mutation
linkUpdates.zipWithIndex.foreach { case (update, index) =>
  if (update.deleteDirectLink) {
    builder.addDelete(resource, iri(update.linkPropertyIri), iri(update.linkTargetIri))
  }
  if (update.linkValueExists) {
    val linkValue      = variable(s"linkValue$index")
    val linkValueUUID  = variable(s"linkValueUUID$index")
    val linkValuePerms = variable(s"linkValuePermissions$index")
    val linkPropValue  = iri(update.linkPropertyIri + "Value")
    builder.addDelete(resource, linkPropValue, linkValue)
    builder.addDelete(linkValue, kbValueHasUUID, linkValueUUID)
    builder.addDelete(linkValue, kbHasPermissions, linkValuePerms)
  }
}

// WHERE clause
builder.addWhere(resource, rdfType, iri(knoraBase + "Resource"))

val query = builder.buildRequest().toString
```

---

## Benchmark 5: SearchQueries.selectCountByLabel (Lucene)

### Plain SPARQL

```sparql
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>

SELECT (count(distinct ?resource) as ?count)
WHERE {
  ?resource <http://jena.apache.org/text#query> (rdfs:label "test search") ;
    a ?resourceClass .
  ?resourceClass rdfs:subClassOf* knora-base:Resource .
  ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> .
  FILTER NOT EXISTS { ?resource knora-base:isDeleted true . }
}
```

### Building the query

```scala
val resource      = variable("resource")
val resourceClass = variable("resourceClass")
val count         = variable("count")
val textQueryPred = iri("http://jena.apache.org/text#query")
val rdfsLabel     = iri(rdfs + "label")

val limitToProject: Option[String] = Some("http://rdfh.ch/projects/0001")

val builder = new SelectBuilder()

// Computed expression: count(distinct ?resource) as ?count
// Jena's SelectBuilder supports this via addVar with expression
builder.addVar("count(distinct ?resource)", count)

// Lucene text#query — Jena's builder doesn't directly support property list notation
// with text search predicates. Would need to build this part manually or use
// a custom handler.
//
// Workaround: use addWhere with a pre-built triple, or fall back to string construction
// for the text#query pattern.
//
// This is a significant limitation of the Jena builder approach for this use case.

// Simulating what would work:
builder.addWhere(resourceClass, iri(rdfs + "subClassOf*"), iri(knoraBase + "Resource"))

limitToProject.foreach { prj =>
  builder.addWhere(resource, iri(knoraBase + "attachedToProject"), iri(prj))
}

// FILTER NOT EXISTS
val filterBuilder = new WhereBuilder()
filterBuilder.addWhere(resource, kbIsDeleted, boolLit(true))
builder.addFilter("NOT EXISTS { " + filterBuilder.buildString() + " }")

// Note: The Lucene text#query pattern and property path (subClassOf*)
// push against the edges of Jena's builder API. In practice, this query
// would likely need manual string construction for those parts.

val query = builder.buildString()
```

---

## Benchmark 6: addValueVersion (polymorphic match + iteration)

### Plain SPARQL

```sparql
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

DELETE { GRAPH <...> { ... } }
INSERT { GRAPH <...> { ... TextValue ... LinkValue ... } }
WHERE { ... }
```

### Building the query

```scala
val resourceIri  = iri("http://rdfh.ch/0001/resource1")
val newValueIri  = iri("http://rdfh.ch/0001/resource1/values/newValue1")
val graphIri     = iri("http://www.knora.org/data/0001/project1")
val resourceLastMod = variable("resourceLastModificationDate")

val kbTextValue       = iri(knoraBase + "TextValue")
val kbLinkValue       = iri(knoraBase + "LinkValue")
val kbValueHasString  = iri(knoraBase + "valueHasString")
val kbValueHasComment = iri(knoraBase + "valueHasComment")
val kbValueHasUUID    = iri(knoraBase + "valueHasUUID")
val kbHasPermissions  = iri(knoraBase + "hasPermissions")
val kbValueHasRefCount = iri(knoraBase + "valueHasRefCount")

val maybeComment: Option[String] = Some("Updated value")

val builder = new UpdateBuilder()

// DELETE
builder.addDelete(resourceIri, kbLastMod, resourceLastMod)

// INSERT — polymorphic value type (imperative)
builder.addInsert(newValueIri, rdfType, kbTextValue)
builder.addInsert(newValueIri, kbValueHasString, stringLit("Hello world"))
maybeComment.foreach { c =>
  builder.addInsert(newValueIri, kbValueHasComment, stringLit(c))
}
builder.addInsert(newValueIri, kbValueHasUUID, stringLit("uuid-new-value-1"))
builder.addInsert(newValueIri, kbHasPermissions, stringLit("CR knora-admin:ProjectAdmin"))

// Link value iteration
case class LinkValueUpdate(iri: String, refCount: Int, permissions: String)
val linkUpdates = List(LinkValueUpdate("http://example.org/newLinkValue1", 2, "CR knora-admin:ProjectAdmin"))

linkUpdates.foreach { update =>
  val lvIri = iri(update.iri)
  builder.addInsert(lvIri, rdfType, kbLinkValue)
  builder.addInsert(lvIri, kbValueHasRefCount, intLit(update.refCount))
  builder.addInsert(lvIri, kbHasPermissions, stringLit(update.permissions))
}

val newLmdValue = typedLit("2024-01-02T00:00:00Z", xsd + "dateTime")
builder.addInsert(resourceIri, kbLastMod, newLmdValue)

// WHERE
builder.addWhere(resourceIri, kbLastMod, resourceLastMod)

// Note: GRAPH scoping for DELETE/INSERT is not directly supported
// by Jena's UpdateBuilder — same limitation as Benchmark 3.

val query = builder.buildRequest().toString
```

---

## Notes

- **Validation**: Jena produces validated `Query` objects — invalid SPARQL fails at build time. This is the only approach with real query validation.
- **Mutable API**: Every method mutates the builder and returns `this`. Cannot safely compose, fork, or reuse builders. This clashes fundamentally with immutable Scala/ZIO style.
- **Imperative conditionals**: `Option.foreach(c => builder.addWhere(...))` works but feels like Java, not idiomatic Scala.
- **Imperative iteration**: `list.foreach { ... builder.addInsert(...) }` — mutation in a loop.
- **GRAPH scoping**: Jena's `UpdateBuilder` doesn't directly support `GRAPH <uri> { ... }` in DELETE/INSERT. This is a significant limitation for dsp-api's use case.
- **Lucene/property paths**: Jena's builder struggles with `text#query` property list notation and `subClassOf*` property paths. These require manual string construction or workarounds.
- **External dependency**: Adds `jena-querybuilder` as a new dependency (currently not in dsp-api).
- **Type safety**: Methods accept `Object` loosely — no compile-time type safety beyond what Java provides.
