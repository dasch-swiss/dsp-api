# Approach C Variant: Consequent Fluent Builder

## Philosophy

Same immutable builder as Approach C, but taken further: triple patterns themselves have a fluent API for chaining. Instead of passing separate `triple()` calls into `.where()`, you start with one triple and chain additional patterns via `.and()`, `.andOptional()`, etc. The entire WHERE clause reads as a single fluent expression.

## Shared Vocabulary

```scala
val knoraBase   = "http://www.knora.org/ontology/knora-base#"
val rdfs        = "http://www.w3.org/2000/01/rdf-schema#"
val xsd         = "http://www.w3.org/2001/XMLSchema#"
val owl         = "http://www.w3.org/2002/07/owl#"
val salsahGui   = "http://www.knora.org/ontology/salsah-gui#"
val kbIsDeleted = Iri.trusted(knoraBase + "isDeleted")
val kbLastMod   = Iri.trusted(knoraBase + "lastModificationDate")
val rdfType     = Iri.trusted("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")

// Core builder functions
def triple(s: SparqlValue, p: SparqlValue, o: SparqlValue): Pattern = ...
```

### Pattern Type

```scala
// A Pattern is a composable query fragment with fluent chaining
case class Pattern(fragments: List[Fragment]) {
  def and(s: SparqlValue, p: SparqlValue, o: SparqlValue): Pattern = ...
  def andOptional(pattern: Pattern): Pattern = ...
  def andUnion(branches: Pattern*): Pattern = ...
  def andFilterNotExists(pattern: Pattern): Pattern = ...
  def andRaw(fragment: Fragment): Pattern = ...
  def render: Fragment = Fragment.join(fragments)
}
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
val s             = Variable("s")
val p             = Variable("p")
val o             = Variable("o")
val lmd           = Variable("lastModDate")
val resourceClass = Iri.trusted("http://example.org/MyClass")

val query = Select()
  .select(s, p, o)
  .where(
    triple(s, rdfType, resourceClass)
      .and(s, kbIsDeleted, Literal.bool(false))
      .andOptional(triple(s, kbLastMod, lmd))
      .and(s, p, o)
  )
  .orderBy(lmd.desc)
  .limit(25)
  .render
```

---

## Benchmark 2: ASK with UNION

### Plain SPARQL

```sparql
ASK
WHERE {
  {
    ?s <http://www.knora.org/ontology/salsah-gui#guiAttribute> "hlist=<http://rdfh.ch/lists/0001/treeList01>" .
  }
  UNION
  {
    ?s <http://www.knora.org/ontology/knora-base#valueHasListNode> <http://rdfh.ch/lists/0001/treeList01> .
  }
}
```

### Building the query

```scala
val s              = Variable("s")
val nodeIri        = Iri.trusted("http://rdfh.ch/lists/0001/treeList01")
val guiAttr        = Iri.trusted(salsahGui + "guiAttribute")
val valHasListNode = Iri.trusted(knoraBase + "valueHasListNode")

val query = Ask()
  .where(
    Pattern.union(
      triple(s, guiAttr, Literal.string(s"hlist=<${nodeIri.value}>")),
      triple(s, valHasListNode, nodeIri),
    )
  )
  .render
```

---

## Benchmark 3: DeletePropertyQuery (DELETE/INSERT WHERE)

### Plain SPARQL

```sparql
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
PREFIX owl: <http://www.w3.org/2002/07/owl#>

DELETE {
  GRAPH <http://www.knora.org/ontology/0001/anything> {
    <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2024-01-01T00:00:00Z"^^xsd:dateTime .
    <http://www.knora.org/ontology/0001/anything#hasOtherThing> ?propertyPred ?propertyObj .
    <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> ?linkValuePropertyPred ?linkValuePropertyObj .
  }
}
INSERT {
  GRAPH <http://www.knora.org/ontology/0001/anything> {
    <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2024-01-02T00:00:00Z"^^xsd:dateTime .
  }
}
WHERE {
  <http://www.knora.org/ontology/0001/anything> a owl:Ontology .
  <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2024-01-01T00:00:00Z"^^xsd:dateTime .
  <http://www.knora.org/ontology/0001/anything#hasOtherThing> a owl:ObjectProperty .
  <http://www.knora.org/ontology/0001/anything#hasOtherThing> ?propertyPred ?propertyObj .
  FILTER NOT EXISTS { ?s ?p <http://www.knora.org/ontology/0001/anything#hasOtherThing> . }
  <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> ?linkValuePropertyPred ?linkValuePropertyObj .
}
```

### Building the query

```scala
val ontologyIri = Iri.trusted("http://www.knora.org/ontology/0001/anything")
val propertyIri = Iri.trusted("http://www.knora.org/ontology/0001/anything#hasOtherThing")
val lmdValue    = Literal.typed("2024-01-01T00:00:00Z", Iri.trusted(xsd + "dateTime"))
val newLmd      = Literal.typed("2024-01-02T00:00:00Z", Iri.trusted(xsd + "dateTime"))
val propertyPred = Variable("propertyPred")
val propertyObj  = Variable("propertyObj")
val s            = Variable("s")
val p            = Variable("p")

val owlOntology   = Iri.trusted(owl + "Ontology")
val owlObjectProp = Iri.trusted(owl + "ObjectProperty")

val linkValuePropertyIri: Option[Iri] =
  Some(Iri.trusted("http://www.knora.org/ontology/0001/anything#hasOtherThingValue"))
val linkValuePropertyPred = Variable("linkValuePropertyPred")
val linkValuePropertyObj  = Variable("linkValuePropertyObj")

// Conditional patterns — compose via Option
val linkValueDelete: Option[Pattern] = linkValuePropertyIri.map { lvpIri =>
  triple(lvpIri, linkValuePropertyPred, linkValuePropertyObj)
}

val linkValueWhere: Option[Pattern] = linkValuePropertyIri.map { lvpIri =>
  triple(lvpIri, linkValuePropertyPred, linkValuePropertyObj)
}

val deletePattern =
  triple(ontologyIri, kbLastMod, lmdValue)
    .and(propertyIri, propertyPred, propertyObj)
    .andAll(linkValueDelete)  // appends the optional pattern if present

val wherePattern =
  triple(ontologyIri, rdfType, owlOntology)
    .and(ontologyIri, kbLastMod, lmdValue)
    .and(propertyIri, rdfType, owlObjectProp)
    .and(propertyIri, propertyPred, propertyObj)
    .andFilterNotExists(triple(s, p, propertyIri))
    .andAll(linkValueWhere)

val query = Update()
  .prefixes("knora-base" -> knoraBase, "xsd" -> xsd, "owl" -> owl)
  .graph(ontologyIri)
  .delete(deletePattern)
  .insert(triple(ontologyIri, kbLastMod, newLmd))
  .where(wherePattern)
  .render
```

---

## Benchmark 4: InsertValueQueryBuilder (conditional + iteration)

### Plain SPARQL

```sparql
DELETE {
  ?resource <http://www.knora.org/ontology/knora-base#lastModificationDate> ?resourceLastModificationDate .
  ?resource <http://example.org/hasLink> <http://example.org/target1> .
  ?resource <http://example.org/hasLinkValue> ?linkValue0 .
  ?linkValue0 <http://www.knora.org/ontology/knora-base#valueHasUUID> ?linkValueUUID0 .
  ?linkValue0 <http://www.knora.org/ontology/knora-base#hasPermissions> ?linkValuePermissions0 .
}
WHERE {
  ?resource a <http://www.knora.org/ontology/knora-base#Resource> .
}
```

### Building the query

```scala
case class LinkUpdate(
  linkPropertyIri: String, linkTargetIri: String,
  deleteDirectLink: Boolean, linkValueExists: Boolean,
)

val resource        = Variable("resource")
val resourceLastMod = Variable("resourceLastModificationDate")
val kbHasPermissions = Iri.trusted(knoraBase + "hasPermissions")
val kbValueHasUUID   = Iri.trusted(knoraBase + "valueHasUUID")

val linkUpdates = List(
  LinkUpdate("http://example.org/hasLink", "http://example.org/target1",
    deleteDirectLink = true, linkValueExists = true),
)

// Iteration — build list of Patterns, then fold into the chain
val linkDeletePatterns: List[Pattern] = linkUpdates.zipWithIndex.flatMap { case (update, index) =>
  val directLink = Option.when(update.deleteDirectLink) {
    triple(resource, Iri.trusted(update.linkPropertyIri), Iri.trusted(update.linkTargetIri))
  }

  val linkValuePatterns = Option.when(update.linkValueExists) {
    val linkValue      = Variable(s"linkValue$index")
    val linkValueUUID  = Variable(s"linkValueUUID$index")
    val linkValuePerms = Variable(s"linkValuePermissions$index")
    val linkPropValue  = Iri.trusted(update.linkPropertyIri + "Value")
    triple(resource, linkPropValue, linkValue)
      .and(linkValue, kbValueHasUUID, linkValueUUID)
      .and(linkValue, kbHasPermissions, linkValuePerms)
  }

  directLink.toList ++ linkValuePatterns.toList
}

val deletePattern =
  triple(resource, kbLastMod, resourceLastMod)
    .andAll(linkDeletePatterns)

val query = Update()
  .delete(deletePattern)
  .where(triple(resource, rdfType, Iri.trusted(knoraBase + "Resource")))
  .render
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
val resource      = Variable("resource")
val resourceClass = Variable("resourceClass")
val count         = Variable("count")
val textQueryPred = Iri.trusted("http://jena.apache.org/text#query")
val rdfsLabel     = Iri.trusted(rdfs + "label")

val limitToProject: Option[Iri] = Some(Iri.trusted("http://rdfh.ch/projects/0001"))
val limitToResourceClass: Option[Iri] = None

// Lucene text#query — still needs raw escape hatch
val textQueryPattern = Pattern.raw(
  sparql"""$resource $textQueryPred ($rdfsLabel ${Literal.string("test search")}) ;
    a $resourceClass ."""
)

val subClassPattern = Pattern.raw(
  Fragment.raw(s"${resourceClass.render} <${rdfs}subClassOf*> <${knoraBase}Resource> .")
)

val projectFilter: Option[Pattern] = limitToProject.map { prj =>
  triple(resource, Iri.trusted(knoraBase + "attachedToProject"), prj)
}

val classFilter: Option[Pattern] = limitToResourceClass.map { cls =>
  Pattern.raw(Fragment.raw(s"${resourceClass.render} <${rdfs}subClassOf*> ${cls.render} ."))
}

val wherePattern = textQueryPattern
  .andRaw(subClassPattern)
  .andAll(projectFilter)
  .andAll(classFilter)
  .andFilterNotExists(triple(resource, kbIsDeleted, Literal.bool(true)))

val query = Select()
  .selectExpr(s"(count(distinct ${resource.render}) as ${count.render})")
  .where(wherePattern)
  .render
```

---

## Benchmark 6: addValueVersion (polymorphic match + iteration)

### Plain SPARQL

```sparql
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

DELETE {
  GRAPH <http://www.knora.org/data/0001/project1> {
    <http://rdfh.ch/0001/resource1> knora-base:lastModificationDate ?resourceLastModificationDate .
  }
}
INSERT {
  GRAPH <http://www.knora.org/data/0001/project1> {
    <http://rdfh.ch/0001/resource1/values/newValue1> a knora-base:TextValue ;
      knora-base:valueHasString "Hello world" ;
      knora-base:valueHasComment "Updated value" ;
      knora-base:valueHasUUID "uuid-new-value-1" ;
      knora-base:hasPermissions "CR knora-admin:ProjectAdmin" .
    <http://example.org/newLinkValue1> a knora-base:LinkValue ;
      knora-base:valueHasRefCount 2 ;
      knora-base:hasPermissions "CR knora-admin:ProjectAdmin" .
    <http://rdfh.ch/0001/resource1> knora-base:lastModificationDate "2024-01-02T00:00:00Z"^^xsd:dateTime .
  }
}
WHERE {
  <http://rdfh.ch/0001/resource1> knora-base:lastModificationDate ?resourceLastModificationDate .
}
```

### Building the query

```scala
val resourceIri  = Iri.trusted("http://rdfh.ch/0001/resource1")
val newValueIri  = Iri.trusted("http://rdfh.ch/0001/resource1/values/newValue1")
val graphIri     = Iri.trusted("http://www.knora.org/data/0001/project1")
val resourceLastMod = Variable("resourceLastModificationDate")

val kbTextValue       = Iri.trusted(knoraBase + "TextValue")
val kbLinkValue       = Iri.trusted(knoraBase + "LinkValue")
val kbValueHasString  = Iri.trusted(knoraBase + "valueHasString")
val kbValueHasComment = Iri.trusted(knoraBase + "valueHasComment")
val kbValueHasUUID    = Iri.trusted(knoraBase + "valueHasUUID")
val kbHasPermissions  = Iri.trusted(knoraBase + "hasPermissions")
val kbValueHasRefCount = Iri.trusted(knoraBase + "valueHasRefCount")

val maybeComment: Option[String] = Some("Updated value")

// Polymorphic value type — start with base, conditionally add comment
val commentPattern: Option[Pattern] = maybeComment.map { c =>
  triple(newValueIri, kbValueHasComment, Literal.string(c))
}

val valueTypePattern =
  triple(newValueIri, rdfType, kbTextValue)
    .and(newValueIri, kbValueHasString, Literal.string("Hello world"))
    .andAll(commentPattern)
    .and(newValueIri, kbValueHasUUID, Literal.string("uuid-new-value-1"))
    .and(newValueIri, kbHasPermissions, Literal.string("CR knora-admin:ProjectAdmin"))

// Link value iteration
case class LinkValueUpdate(iri: String, refCount: Int, permissions: String)
val linkUpdates = List(LinkValueUpdate("http://example.org/newLinkValue1", 2, "CR knora-admin:ProjectAdmin"))

val linkValuePatterns: List[Pattern] = linkUpdates.map { update =>
  val lvIri = Iri.trusted(update.iri)
  triple(lvIri, rdfType, kbLinkValue)
    .and(lvIri, kbValueHasRefCount, Literal.int(update.refCount))
    .and(lvIri, kbHasPermissions, Literal.string(update.permissions))
}

val newLmdValue = Literal.typed("2024-01-02T00:00:00Z", Iri.trusted(xsd + "dateTime"))

val insertPattern = valueTypePattern
  .andAll(linkValuePatterns)
  .and(resourceIri, kbLastMod, newLmdValue)

val query = Update()
  .prefixes("knora-base" -> knoraBase, "xsd" -> xsd)
  .graph(graphIri)
  .delete(triple(resourceIri, kbLastMod, resourceLastMod))
  .insert(insertPattern)
  .where(triple(resourceIri, kbLastMod, resourceLastMod))
  .render
```

---

## Notes

- **Readability**: The fluent chain reads very naturally: `triple(s, rdfType, class).and(s, isDeleted, false).andOptional(triple(s, lastMod, lmd)).and(s, p, o)`. Each `.and()` adds a triple pattern, `.andOptional()` wraps in OPTIONAL, etc.
- **Naming**: Uses `Select()`, `Ask()`, `Update()` (no "Fluent" prefix). Uses `triple()` (more expressive than `tp()`).
- **`.andAll(Option[Pattern])`**: Cleanly handles optional patterns — appends if `Some`, no-op if `None`.
- **`.andAll(List[Pattern])`**: Handles iteration results — appends all patterns from a list.
- **`.prefixes("a" -> a, "b" -> b)`**: Bulk prefix declaration using varargs of tuples.
- **Conditional logic**: `Option.map` to create `Option[Pattern]`, then `.andAll()` to fold it in. Clean and readable.
- **Iteration**: `list.map { ... triple().and().and() }` creates `List[Pattern]`, then `.andAll()` appends them. Natural Scala.
- **Escape hatches**: `Pattern.raw(fragment)` still needed for Lucene and property paths.
- **vs Approach C**: The key difference is `.and()` chaining vs passing a flat list to `.where()`. Chaining reads more fluidly, especially for simple queries. For complex queries with conditionals, `.andAll(Option)` is cleaner than list concatenation.
- **Potential concern**: Very long chains might become hard to read. For Benchmark 6, the insert pattern has ~8 chained calls. Whether this is "too much" is a matter of taste.
- **Design question**: Should `.and()` take `(s, p, o)` directly, or should it take a `Pattern`? The examples above use both — `.and(s, p, o)` for simple triples, `.andAll(pattern)` for composed patterns. This dual interface needs careful design.

---

## Design Review Feedback

### Deep Review Findings

**Weaknesses:**
- **Doubles API surface without expressiveness gain**: Every operation that C expresses via list manipulation, C-variant expresses via `.and()` chaining — but both handle the same cases. The extra API surface adds learning cost without enabling new patterns.
- **Dual interface problem**: `.and(s, p, o)` for simple triples and `.andAll(pattern)` for composed patterns create two overlapping ways to add patterns. Developers must choose between them, and mixing them in the same chain is confusing.
- **Reverts to list-building for complex queries**: For Benchmark 4 (InsertValueQueryBuilder), the `.andAll(linkValuePatterns)` call still requires building a `List[Pattern]` via `.map { ... }` first — the fluent chaining provides no advantage over C's approach for the hard cases.
- **Very long chains become hard to read**: Benchmark 4's insert pattern has ~8 chained calls. At that length, the fluent chain loses its readability advantage over explicit list construction.
- **Same escape hatch limitations as C**: `Pattern.raw(fragment)` still needed for Lucene and property paths — the fluent API doesn't improve safety coverage.

**Industry comparison:**
- The `.and()` chaining pattern resembles JOOQ's style, but JOOQ operates in a SQL ecosystem with much more structured queries. For SPARQL's graph patterns, the added ceremony of `.and()` over direct fragment composition provides little benefit.
- Further from the Doobie/Skunk model than either A or H — no industry equivalent in the FP query builder space.

### Status: Eliminated (subsumed)

C itself is subsumed by the Interpolated Template approach's builder style. C-variant's `.and()` / `.andAll()` chaining idea can be trivially added as extension methods on `Fragment` within the Interpolated Template approach (e.g., `fragment.and(sparql"...")`, `fragment.andAll(optionalFragment)`) if the chaining style proves useful. No separate approach needed.
