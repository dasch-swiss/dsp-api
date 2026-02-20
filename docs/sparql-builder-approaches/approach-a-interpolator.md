# Approach A: String Interpolator + Fragment Composition

## Philosophy

Write SPARQL as `sparql"..."` with typed holes. Values are escaped at interpolation time. Fragments compose via `++` (monoid). Only `SparqlValue | Fragment` can be interpolated — raw `String` is a compile error.

**Implementation note (Approach D)**: The escaping backend is an independent choice. The current prototype uses custom escaping; Approach D demonstrated that RDF4J's `Rdf.literalOf().getQueryString()` is a drop-in replacement with better coverage (handles `\f`, `\b`, single quotes). The API surface is identical.

## Shared Vocabulary

```scala
// These would live in an adapter layer in production
val knoraBase      = "http://www.knora.org/ontology/knora-base#"
val rdf            = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
val rdfs           = "http://www.w3.org/2000/01/rdf-schema#"
val xsd            = "http://www.w3.org/2001/XMLSchema#"
val owl            = "http://www.w3.org/2002/07/owl#"
val kbIsDeleted    = Iri.trusted(knoraBase + "isDeleted")
val kbResource     = Iri.trusted(knoraBase + "Resource")
val kbLastMod      = Iri.trusted(knoraBase + "lastModificationDate")
val rdfType        = Iri.trusted(rdf + "type")
val rdfsSubClassOf = Iri.trusted(rdfs + "subClassOf")
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

val query = SparqlQuery
  .select(s, p, o)
  .where(
    sparql"$s a $resourceClass .",
    sparql"$s $kbIsDeleted false .",
    Fragments.optional(sparql"$s $kbLastMod $lmd ."),
    sparql"$s $p $o .",
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
  { ?s <http://www.knora.org/ontology/salsah-gui#guiAttribute> "hlist=<http://rdfh.ch/lists/0001/treeList01>" . }
  UNION
  { ?s <http://www.knora.org/ontology/knora-base#valueHasListNode> <http://rdfh.ch/lists/0001/treeList01> . }
}
```

### Building the query

```scala
val s              = Variable("s")
val nodeIri        = Iri.trusted("http://rdfh.ch/lists/0001/treeList01")
val guiAttr        = Iri.trusted("http://www.knora.org/ontology/salsah-gui#guiAttribute")
val valHasListNode = Iri.trusted(knoraBase + "valueHasListNode")

val query = SparqlQuery.ask
  .where(
    Fragments.union(
      sparql"$s $guiAttr ${Literal.string(s"hlist=<$nodeIri>")} .",
      sparql"$s $valHasListNode $nodeIri .",
    ),
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
    <...> knora-base:lastModificationDate "2024-01-01T00:00:00Z"^^xsd:dateTime .
    <...#hasOtherThing> ?propertyPred ?propertyObj .
    <...#hasOtherThingValue> ?linkValuePropertyPred ?linkValuePropertyObj .
  }
}
INSERT {
  GRAPH <http://www.knora.org/ontology/0001/anything> {
    <...> knora-base:lastModificationDate "2024-01-02T00:00:00Z"^^xsd:dateTime .
  }
}
WHERE {
  <...> a owl:Ontology .
  <...> knora-base:lastModificationDate "2024-01-01T00:00:00Z"^^xsd:dateTime .
  <...#hasOtherThing> a owl:ObjectProperty .
  <...#hasOtherThing> ?propertyPred ?propertyObj .
  FILTER NOT EXISTS { ?s ?p <...#hasOtherThing> . }
  <...#hasOtherThingValue> ?linkValuePropertyPred ?linkValuePropertyObj .
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

// Optional link value property (mirrors the Option[PropertyIri] in the real code)
val linkValuePropertyIri: Option[Iri] =
  Some(Iri.trusted("http://www.knora.org/ontology/0001/anything#hasOtherThingValue"))
val linkValuePropertyPred = Variable("linkValuePropertyPred")
val linkValuePropertyObj  = Variable("linkValuePropertyObj")

val linkValueDeletePattern: Option[Fragment] = linkValuePropertyIri.map { lvpIri =>
  sparql"$lvpIri $linkValuePropertyPred $linkValuePropertyObj ."
}

val linkValueWherePattern: Option[Fragment] = linkValuePropertyIri.map { lvpIri =>
  sparql"$lvpIri $linkValuePropertyPred $linkValuePropertyObj ."
}

val owlOntology   = Iri.trusted(owl + "Ontology")
val owlObjectProp = Iri.trusted(owl + "ObjectProperty")

val query = SparqlQuery.update
  .prefix("knora-base", knoraBase)
  .prefix("xsd", xsd)
  .prefix("owl", owl)
  .delete(
    sparql"$ontologyIri $kbLastMod $lmdValue .",
    sparql"$propertyIri $propertyPred $propertyObj .",
    Fragment.combine(linkValueDeletePattern),
  )
  .from(ontologyIri)
  .insert(sparql"$ontologyIri $kbLastMod $newLmd .")
  .into(ontologyIri)
  .where(
    sparql"$ontologyIri a $owlOntology .",
    sparql"$ontologyIri $kbLastMod $lmdValue .",
    sparql"$propertyIri a $owlObjectProp .",
    sparql"$propertyIri $propertyPred $propertyObj .",
    Fragments.filterNotExists(sparql"$s $p $propertyIri ."),
    Fragment.combine(linkValueWherePattern),
  )
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
  linkPropertyIri: String,
  linkTargetIri: String,
  deleteDirectLink: Boolean,
  linkValueExists: Boolean,
)

val resource         = Variable("resource")
val resourceLastMod  = Variable("resourceLastModificationDate")
val kbHasPermissions = Iri.trusted(knoraBase + "hasPermissions")
val kbValueHasUUID   = Iri.trusted(knoraBase + "valueHasUUID")

val linkUpdates = List(
  LinkUpdate("http://example.org/hasLink", "http://example.org/target1",
    deleteDirectLink = true, linkValueExists = true),
)

// Build delete patterns with conditional logic
val linkValueDeletePatterns: Fragment = linkUpdates.zipWithIndex.map { case (linkUpdate, index) =>
  val deleteDirectLink = Option.when(linkUpdate.deleteDirectLink) {
    val linkProp = Iri.trusted(linkUpdate.linkPropertyIri)
    val target   = Iri.trusted(linkUpdate.linkTargetIri)
    sparql"$resource $linkProp $target ."
  }

  val linkValueExistsPatterns = Option.when(linkUpdate.linkValueExists) {
    val linkValue      = Variable(s"linkValue$index")
    val linkValueUUID  = Variable(s"linkValueUUID$index")
    val linkValuePerms = Variable(s"linkValuePermissions$index")
    val linkPropValue  = Iri.trusted(linkUpdate.linkPropertyIri + "Value")
    Fragment.join(List(
      sparql"$resource $linkPropValue $linkValue .",
      sparql"$linkValue $kbValueHasUUID $linkValueUUID .",
      sparql"$linkValue $kbHasPermissions $linkValuePerms .",
    ), Fragment.raw("\n"))
  }

  Fragment.combine(deleteDirectLink, linkValueExistsPatterns)
}.combineAll

val deleteClause = Fragment.join(List(
  sparql"$resource $kbLastMod $resourceLastMod .",
  linkValueDeletePatterns,
), Fragment.raw("\n"))

val query = SparqlQuery.update
  .delete(deleteClause)
  .where(sparql"$resource a $kbResource .")
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

val luceneQuery   = "test search"
val textQueryPred = Iri.trusted("http://jena.apache.org/text#query")
val rdfsLabel     = Iri.trusted(rdfs + "label")

val limitToProject: Option[Iri]       = Some(Iri.trusted("http://rdfh.ch/projects/0001"))
val limitToResourceClass: Option[Iri] = None

// Build conditional filter fragments
val projectFilter = limitToProject.map { prj =>
  val attachedToProject = Iri.trusted(knoraBase + "attachedToProject")
  sparql"$resource $attachedToProject $prj ."
}

val classFilter = limitToResourceClass.map { cls =>
  sparql"$resourceClass ${Iri.trusted(rdfs + "subClassOf*")} $cls ."
}

val filters = Fragment.combine(projectFilter, classFilter)

val query = SparqlQuery
  .select()
  .prefix("rdfs", rdfs)
  .prefix("knora-base", knoraBase)
  .withExpr(sparql"(count(distinct $resource) as $count)")
  .where(
    sparql"""$resource $textQueryPred ($rdfsLabel ${Literal.string(luceneQuery)}) ;
    a $resourceClass .""",
    sparql"$resourceClass ${Iri.trusted(rdfs + "subClassOf*")} ${Iri.trusted(knoraBase + "Resource")} .",
    filters,
    Fragments.filterNotExists(sparql"$resource $kbIsDeleted true ."),
  )
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
val resourceIri = Iri.trusted("http://rdfh.ch/0001/resource1")
val newValueIri = Iri.trusted("http://rdfh.ch/0001/resource1/values/newValue1")
val graphIri    = Iri.trusted("http://www.knora.org/data/0001/project1")
val resourceLastMod = Variable("resourceLastModificationDate")

val kbTextValue       = Iri.trusted(knoraBase + "TextValue")
val kbLinkValue       = Iri.trusted(knoraBase + "LinkValue")
val kbValueHasString  = Iri.trusted(knoraBase + "valueHasString")
val kbValueHasComment = Iri.trusted(knoraBase + "valueHasComment")
val kbValueHasUUID    = Iri.trusted(knoraBase + "valueHasUUID")
val kbHasPermissions  = Iri.trusted(knoraBase + "hasPermissions")
val kbValueHasRefCount = Iri.trusted(knoraBase + "valueHasRefCount")

val maybeComment: Option[String] = Some("Updated value")

// Polymorphic value type — in the real template this is a @match on 13+ value types
val valueTypePatterns: Fragment = {
  // TextValue case:
  val commentPattern = maybeComment.map { c =>
    sparql"$newValueIri $kbValueHasComment ${Literal.string(c)} ;"
  }
  Fragment.combine(
    Some(sparql"""$newValueIri a $kbTextValue ;
      $kbValueHasString ${Literal.string("Hello world")} ;"""),
    commentPattern,
    Some(sparql"""$kbValueHasUUID ${Literal.string("uuid-new-value-1")} ;
      $kbHasPermissions ${Literal.string("CR knora-admin:ProjectAdmin")} ."""),
  )
}

// Link value updates (iteration)
case class LinkValueUpdate(newLinkValueIri: String, newRefCount: Int, permissions: String)
val linkUpdates = List(
  LinkValueUpdate("http://example.org/newLinkValue1", 2, "CR knora-admin:ProjectAdmin"),
)

val linkValueInsertPatterns: Fragment = linkUpdates.map { update =>
  val lvIri = Iri.trusted(update.newLinkValueIri)
  sparql"""$lvIri a $kbLinkValue ;
      $kbValueHasRefCount ${Literal.int(update.newRefCount)} ;
      $kbHasPermissions ${Literal.string(update.permissions)} ."""
}.combineAll

val newLmdValue = Literal.typed("2024-01-02T00:00:00Z", Iri.trusted(xsd + "dateTime"))

val insertClause = Fragment.join(List(
  valueTypePatterns,
  linkValueInsertPatterns,
  sparql"$resourceIri $kbLastMod $newLmdValue .",
), Fragment.raw("\n"))

val query = SparqlQuery.update
  .prefix("knora-base", knoraBase)
  .prefix("xsd", xsd)
  .delete(sparql"$resourceIri $kbLastMod $resourceLastMod .")
  .from(graphIri)
  .insert(insertClause)
  .into(graphIri)
  .where(sparql"$resourceIri $kbLastMod $resourceLastMod .")
  .render
```

---

## Notes

- **Readability**: The `sparql"..."` interpolator reads closest to actual SPARQL. Developers can visually verify the query structure.
- **Composability**: `Fragment.combine`, `combineAll`, `++` provide flexible composition. Optional fragments work naturally with `Option[Fragment]`.
- **Type safety**: Only `SparqlValue | Fragment` can be interpolated. Raw `String` is a compile error. `Fragment.raw()` is the only escape hatch.
- **Conditional logic**: `Option.when(condition)(sparql"...")` maps 1:1 to Twirl's `@if`.
- **Iteration**: `.map { ... sparql"..." }.combineAll` maps 1:1 to Twirl's `@for`.
- **Concerns**: `Literal.string(s"hlist=<$nodeIri>")` in benchmark 2 feels like a workaround — the `s"..."` inside bypasses the type system's protection. The `subClassOf*` property path in benchmark 5 is interpolated as a raw IRI string, which is not ideal.

---

## Design Review Feedback

**Likes:**
- The `SparqlQuery.select(s, p, o).where(...).orderBy(...).limit(25).render` fluent API reads beautifully

**Suggested improvements:**
- **Prefer `tp()` over `sparql"..."`**: The `sparql"..."` interpolator feels like a lot of repetition for little gain when constructing triple patterns. `tp(s, rdfType, resourceClass)` would be more compact.
- **`triple().optional()` over `Fragments.optional(sparql"...")`**: Wrapping patterns should be a method on the pattern itself, not a standalone function. `tp(s, kbLastMod, lmd).optional` reads better.
- **Bulk prefixes**: Instead of chaining `.prefix("a", a).prefix("b", b)`, support `.prefixes("knora-base" -> knoraBase, "xsd" -> xsd, "owl" -> owl)`.
- **Safety**: The `sparql"..."` interpolator only accepts `SparqlValue | Fragment` — raw `String` is a compile error. This is a strong safety guarantee worth documenting prominently.
- **Composability/conditionality**: Undecided — needs more thought on how `Fragment.combine`, `Option[Fragment]`, etc. feel in practice.
