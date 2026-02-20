# Approach B: AST Case Classes + Typed Rendering

## Philosophy

Model SPARQL constructs as explicit case classes forming an AST (Abstract Syntax Tree). Each node renders to SPARQL. More structured than string interpolation, but more verbose. The AST can be analyzed, optimized, or serialized.

## Shared Vocabulary

```scala
val knoraBase   = "http://www.knora.org/ontology/knora-base#"
val rdfs        = "http://www.w3.org/2000/01/rdf-schema#"
val xsd         = "http://www.w3.org/2001/XMLSchema#"
val owl         = "http://www.w3.org/2002/07/owl#"
val kbIsDeleted = Iri.trusted(knoraBase + "isDeleted")
val kbLastMod   = Iri.trusted(knoraBase + "lastModificationDate")
val rdfType     = Iri.trusted("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")

// AST helper
def tp(s: SparqlValue, p: SparqlValue, o: SparqlValue): GraphPattern =
  GraphPattern.Triple(TriplePattern(s, p, o))
```

### AST Types

```scala
case class TriplePattern(subject: SparqlValue, predicate: SparqlValue, obj: SparqlValue) {
  def render: String = s"${subject.render} ${predicate.render} ${obj.render} ."
}

enum GraphPattern {
  case Triple(pattern: TriplePattern)
  case Optional(patterns: List[GraphPattern])
  case Union(branches: List[List[GraphPattern]])
  case FilterNotExists(patterns: List[GraphPattern])
  case Minus(patterns: List[GraphPattern])
  case Filter(expr: String)
  case Bind(expr: String, variable: Variable)
  case Raw(fragment: Fragment)
}

case class AstSelect(
  variables: List[Variable],
  patterns: List[GraphPattern],
  orderBy: List[OrderBy] = Nil,
  limit: Option[Int] = None,
  distinct: Boolean = false,
)

case class AstAsk(patterns: List[GraphPattern])

case class AstUpdate(
  prefixes: List[(String, String)] = Nil,
  deletePatterns: List[GraphPattern] = Nil,
  insertPatterns: List[GraphPattern] = Nil,
  wherePatterns: List[GraphPattern] = Nil,
  graphIri: Option[Iri] = None,
)
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

val query = AstSelect(
  variables = List(s, p, o),
  patterns = List(
    tp(s, rdfType, resourceClass),
    tp(s, kbIsDeleted, Literal.bool(false)),
    GraphPattern.Optional(List(tp(s, kbLastMod, lmd))),
    tp(s, p, o),
  ),
  orderBy = List(lmd.desc),
  limit = Some(25),
).render
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

val query = AstAsk(
  patterns = List(
    GraphPattern.Union(List(
      List(tp(s, guiAttr, Literal.string(s"hlist=<${nodeIri.value}>"))),
      List(tp(s, valHasListNode, nodeIri)),
    )),
  ),
).render
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

val linkValueDeletePatterns: List[GraphPattern] = linkValuePropertyIri.toList.map { lvpIri =>
  tp(lvpIri, linkValuePropertyPred, linkValuePropertyObj)
}

val linkValueWherePatterns: List[GraphPattern] = linkValuePropertyIri.toList.map { lvpIri =>
  tp(lvpIri, linkValuePropertyPred, linkValuePropertyObj)
}

val query = AstUpdate(
  prefixes = List(("knora-base", knoraBase), ("xsd", xsd), ("owl", owl)),
  deletePatterns = List(
    tp(ontologyIri, kbLastMod, lmdValue),
    tp(propertyIri, propertyPred, propertyObj),
  ) ++ linkValueDeletePatterns,
  insertPatterns = List(
    tp(ontologyIri, kbLastMod, newLmd),
  ),
  wherePatterns = List(
    tp(ontologyIri, rdfType, owlOntology),
    tp(ontologyIri, kbLastMod, lmdValue),
    tp(propertyIri, rdfType, owlObjectProp),
    tp(propertyIri, propertyPred, propertyObj),
    GraphPattern.FilterNotExists(List(tp(s, p, propertyIri))),
  ) ++ linkValueWherePatterns,
  graphIri = Some(ontologyIri),
).render
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

val resource        = Variable("resource")
val resourceLastMod = Variable("resourceLastModificationDate")
val kbHasPermissions = Iri.trusted(knoraBase + "hasPermissions")
val kbValueHasUUID   = Iri.trusted(knoraBase + "valueHasUUID")

val linkUpdates = List(
  LinkUpdate("http://example.org/hasLink", "http://example.org/target1",
    deleteDirectLink = true, linkValueExists = true),
)

val linkDeletePatterns: List[GraphPattern] = linkUpdates.zipWithIndex.flatMap { case (update, index) =>
  val directLink = Option.when(update.deleteDirectLink) {
    tp(resource, Iri.trusted(update.linkPropertyIri), Iri.trusted(update.linkTargetIri))
  }

  val linkValuePatterns = Option.when(update.linkValueExists) {
    val linkValue      = Variable(s"linkValue$index")
    val linkValueUUID  = Variable(s"linkValueUUID$index")
    val linkValuePerms = Variable(s"linkValuePermissions$index")
    val linkPropValue  = Iri.trusted(update.linkPropertyIri + "Value")
    List(
      tp(resource, linkPropValue, linkValue),
      tp(linkValue, kbValueHasUUID, linkValueUUID),
      tp(linkValue, kbHasPermissions, linkValuePerms),
    )
  }.getOrElse(Nil)

  directLink.toList ++ linkValuePatterns
}

val query = AstUpdate(
  deletePatterns = tp(resource, kbLastMod, resourceLastMod) :: linkDeletePatterns,
  wherePatterns = List(tp(resource, rdfType, Iri.trusted(knoraBase + "Resource"))),
).render
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

// The Lucene text#query with property list notation doesn't map to a triple pattern.
// The AST needs a Raw escape hatch for this.
val textQueryPattern = GraphPattern.Raw(
  sparql"""$resource $textQueryPred ($rdfsLabel ${Literal.string("test search")}) ;
    a $resourceClass ."""
)

val subClassPattern = GraphPattern.Raw(
  sparql"$resourceClass ${Iri.trusted(rdfs + "subClassOf*")} ${Iri.trusted(knoraBase + "Resource")} ."
)

val projectFilter: List[GraphPattern] = limitToProject.toList.map { prj =>
  tp(resource, Iri.trusted(knoraBase + "attachedToProject"), prj)
}

// Note: AstSelect would need to support computed expressions like
// (count(distinct ?resource) as ?count) — not currently in the AST.
// This would require extending the AST with a SelectExpr node.

val query = AstSelect(
  variables = Nil, // Would need selectExprs support
  patterns = List(textQueryPattern, subClassPattern) ++
    projectFilter ++
    List(GraphPattern.FilterNotExists(List(
      tp(resource, kbIsDeleted, Literal.bool(true)),
    ))),
).render
```

---

## Benchmark 6: addValueVersion (polymorphic match + iteration)

### Plain SPARQL

```sparql
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

DELETE { GRAPH <...> { ... } }
INSERT { GRAPH <...> { ... TextValue patterns ... LinkValue patterns ... } }
WHERE { ... }
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

// Polymorphic value type — each case produces a list of triple patterns
val valueTypePatterns: List[GraphPattern] = {
  // TextValue case:
  val base = List(
    tp(newValueIri, rdfType, kbTextValue),
    tp(newValueIri, kbValueHasString, Literal.string("Hello world")),
  )
  val comment = maybeComment.toList.map(c =>
    tp(newValueIri, kbValueHasComment, Literal.string(c))
  )
  val meta = List(
    tp(newValueIri, kbValueHasUUID, Literal.string("uuid-new-value-1")),
    tp(newValueIri, kbHasPermissions, Literal.string("CR knora-admin:ProjectAdmin")),
  )
  base ++ comment ++ meta
}

// Link value iteration
case class LinkValueUpdate(iri: String, refCount: Int, permissions: String)
val linkUpdates = List(LinkValueUpdate("http://example.org/newLinkValue1", 2, "CR knora-admin:ProjectAdmin"))

val linkValuePatterns: List[GraphPattern] = linkUpdates.flatMap { update =>
  val lvIri = Iri.trusted(update.iri)
  List(
    tp(lvIri, rdfType, kbLinkValue),
    tp(lvIri, kbValueHasRefCount, Literal.int(update.refCount)),
    tp(lvIri, kbHasPermissions, Literal.string(update.permissions)),
  )
}

val newLmdValue = Literal.typed("2024-01-02T00:00:00Z", Iri.trusted(xsd + "dateTime"))

val query = AstUpdate(
  prefixes = List(("knora-base", knoraBase), ("xsd", xsd)),
  deletePatterns = List(tp(resourceIri, kbLastMod, resourceLastMod)),
  insertPatterns = valueTypePatterns ++ linkValuePatterns ++
    List(tp(resourceIri, kbLastMod, newLmdValue)),
  wherePatterns = List(tp(resourceIri, kbLastMod, resourceLastMod)),
  graphIri = Some(graphIri),
).render
```

---

## Notes

- **Structure**: The AST makes query structure explicit and inspectable. You can traverse, analyze, or transform the tree before rendering.
- **Verbosity**: `tp(s, p, o)` is more verbose than `sparql"$s $p $o ."` — every triple becomes a function call.
- **Escape hatches**: `GraphPattern.Raw(fragment)` is needed for anything outside the AST's coverage (Lucene `text#query`, property paths like `subClassOf*`, computed SELECT expressions).
- **Conditional logic**: Works via `Option.toList` and list concatenation — functional but less ergonomic than `Option[Fragment]` with `Fragment.combine`.
- **Iteration**: Works via `flatMap` on lists — natural Scala but produces `List[GraphPattern]` that needs flattening.
- **Gaps**: The current AST doesn't model GRAPH scoping, PREFIX declarations, or computed SELECT expressions. These would need to be added or handled via `Raw`.
