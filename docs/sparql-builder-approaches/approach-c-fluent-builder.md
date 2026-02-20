# Approach C: Fluent Immutable Builder

## Philosophy

Method chaining on immutable case classes. No string interpolator — triple patterns are built via `triple(s, p, o)`. Each builder method returns a new copy. The builder is the API; Fragment is the internal engine.

## Shared Vocabulary

```scala
val knoraBase   = "http://www.knora.org/ontology/knora-base#"
val rdfs        = "http://www.w3.org/2000/01/rdf-schema#"
val xsd         = "http://www.w3.org/2001/XMLSchema#"
val owl         = "http://www.w3.org/2002/07/owl#"
val kbIsDeleted = Iri.trusted(knoraBase + "isDeleted")
val kbLastMod   = Iri.trusted(knoraBase + "lastModificationDate")
val rdfType     = Iri.trusted("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")

// Builder helpers (produce Fragment internally)
def triple(s: SparqlValue, p: SparqlValue, o: SparqlValue): Fragment = ...
def optional(body: Fragment): Fragment = ...
def union(branches: Fragment*): Fragment = ...
def filterNotExists(body: Fragment): Fragment = ...
```

### Builder Types

```scala
case class Select(
  variables: List[Variable] = Nil,
  patterns: List[Fragment] = Nil,
  orderByClause: List[OrderBy] = Nil,
  limitValue: Option[Int] = None,
  selectExprs: List[Fragment] = Nil,
) {
  def select(vars: Variable*): Select = copy(variables = vars.toList)
  def where(pats: Fragment*): Select  = copy(patterns = pats.toList)
  def orderBy(o: OrderBy*): Select    = copy(orderByClause = o.toList)
  def limit(n: Int): Select           = copy(limitValue = Some(n))
  def withExpr(expr: Fragment): Select = copy(selectExprs = selectExprs :+ expr)
}

case class Ask(patterns: List[Fragment] = Nil) {
  def where(pats: Fragment*): Ask = copy(patterns = pats.toList)
}

case class Update(
  prefixes: List[(String, String)] = Nil,
  deleteClause: List[Fragment] = Nil,
  insertClause: List[Fragment] = Nil,
  whereClause: List[Fragment] = Nil,
  graphIri: Option[Iri] = None,
) {
  def prefix(p: String, ns: String): Update = copy(prefixes = prefixes :+ (p, ns))
  def delete(pats: Fragment*): Update = copy(deleteClause = pats.toList)
  def insert(pats: Fragment*): Update = copy(insertClause = pats.toList)
  def where(pats: Fragment*): Update  = copy(whereClause = pats.toList)
  def graph(iri: Iri): Update         = copy(graphIri = Some(iri))
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
    triple(s, rdfType, resourceClass),
    triple(s, kbIsDeleted, Literal.bool(false)),
    optional(triple(s, kbLastMod, lmd)),
    triple(s, p, o),
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

val query = Ask()
  .where(
    union(
      triple(s, guiAttr, Literal.string(s"hlist=<${nodeIri.value}>")),
      triple(s, valHasListNode, nodeIri),
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

val linkValueDelete: List[Fragment] = linkValuePropertyIri.toList.map { lvpIri =>
  triple(lvpIri, linkValuePropertyPred, linkValuePropertyObj)
}

val linkValueWhere: List[Fragment] = linkValuePropertyIri.toList.map { lvpIri =>
  triple(lvpIri, linkValuePropertyPred, linkValuePropertyObj)
}

val deletePatterns = List(
  triple(ontologyIri, kbLastMod, lmdValue),
  triple(propertyIri, propertyPred, propertyObj),
) ++ linkValueDelete

val wherePatterns = List(
  triple(ontologyIri, rdfType, owlOntology),
  triple(ontologyIri, kbLastMod, lmdValue),
  triple(propertyIri, rdfType, owlObjectProp),
  triple(propertyIri, propertyPred, propertyObj),
  filterNotExists(triple(s, p, propertyIri)),
) ++ linkValueWhere

val query = Update()
  .prefix("knora-base", knoraBase)
  .prefix("xsd", xsd)
  .prefix("owl", owl)
  .graph(ontologyIri)
  .delete(deletePatterns*)
  .insert(triple(ontologyIri, kbLastMod, newLmd))
  .where(wherePatterns*)
  .render
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

val linkDeletePatterns: List[Fragment] = linkUpdates.zipWithIndex.flatMap { case (update, index) =>
  val directLink = Option.when(update.deleteDirectLink) {
    triple(resource, Iri.trusted(update.linkPropertyIri), Iri.trusted(update.linkTargetIri))
  }

  val linkValuePatterns = Option.when(update.linkValueExists) {
    val linkValue      = Variable(s"linkValue$index")
    val linkValueUUID  = Variable(s"linkValueUUID$index")
    val linkValuePerms = Variable(s"linkValuePermissions$index")
    val linkPropValue  = Iri.trusted(update.linkPropertyIri + "Value")
    List(
      triple(resource, linkPropValue, linkValue),
      triple(linkValue, kbValueHasUUID, linkValueUUID),
      triple(linkValue, kbHasPermissions, linkValuePerms),
    )
  }.getOrElse(Nil)

  directLink.toList ++ linkValuePatterns
}

val deletePatterns = triple(resource, kbLastMod, resourceLastMod) :: linkDeletePatterns

val query = Update()
  .delete(deletePatterns*)
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

// Lucene text#query with property list notation — no triple() equivalent
// Must use Fragment.raw or a dedicated helper
val textQuery = Fragment.raw(
  s"${resource.render} ${textQueryPred.render} (${rdfsLabel.render} ${Literal.string("test search").render}) ;\n" +
  s"    a ${resourceClass.render} ."
)

val subClassPattern = Fragment.raw(
  s"${resourceClass.render} <${rdfs}subClassOf*> <${knoraBase}Resource> ."
)

val projectFilter: List[Fragment] = limitToProject.toList.map { prj =>
  triple(resource, Iri.trusted(knoraBase + "attachedToProject"), prj)
}

val wherePatterns = List(textQuery, subClassPattern) ++
  projectFilter ++
  List(filterNotExists(triple(resource, kbIsDeleted, Literal.bool(true))))

// Note: Select needs withExpr support for computed expressions
val query = Select()
  .withExpr(Fragment.raw(s"(count(distinct ${resource.render}) as ${count.render})"))
  .where(wherePatterns*)
  .render
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

// Polymorphic value type patterns
val valueTypePatterns: List[Fragment] = {
  val base = List(
    triple(newValueIri, rdfType, kbTextValue),
    triple(newValueIri, kbValueHasString, Literal.string("Hello world")),
  )
  val comment = maybeComment.toList.map(c =>
    triple(newValueIri, kbValueHasComment, Literal.string(c))
  )
  val meta = List(
    triple(newValueIri, kbValueHasUUID, Literal.string("uuid-new-value-1")),
    triple(newValueIri, kbHasPermissions, Literal.string("CR knora-admin:ProjectAdmin")),
  )
  base ++ comment ++ meta
}

// Link value iteration
case class LinkValueUpdate(iri: String, refCount: Int, permissions: String)
val linkUpdates = List(LinkValueUpdate("http://example.org/newLinkValue1", 2, "CR knora-admin:ProjectAdmin"))

val linkValuePatterns: List[Fragment] = linkUpdates.flatMap { update =>
  val lvIri = Iri.trusted(update.iri)
  List(
    triple(lvIri, rdfType, kbLinkValue),
    triple(lvIri, kbValueHasRefCount, Literal.int(update.refCount)),
    triple(lvIri, kbHasPermissions, Literal.string(update.permissions)),
  )
}

val newLmdValue = Literal.typed("2024-01-02T00:00:00Z", Iri.trusted(xsd + "dateTime"))

val insertPatterns = valueTypePatterns ++ linkValuePatterns ++
  List(triple(resourceIri, kbLastMod, newLmdValue))

val query = Update()
  .prefix("knora-base", knoraBase)
  .prefix("xsd", xsd)
  .graph(graphIri)
  .delete(triple(resourceIri, kbLastMod, resourceLastMod))
  .insert(insertPatterns*)
  .where(triple(resourceIri, kbLastMod, resourceLastMod))
  .render
```

---

## Notes

- **Readability**: `triple(s, p, o)` is slightly less direct than `sparql"$s $p $o ."` but still readable. The builder chain (`.select().where().orderBy().limit()`) reads well.
- **No interpolator needed**: The entire API works through method calls. No macro or string interpolator magic.
- **Escape hatches**: `Fragment.raw(...)` is still needed for Lucene queries and property paths — same limitation as Approach A.
- **Conditional logic**: `Option.toList` + list concatenation works but is slightly more boilerplate than `Fragment.combine(Option[Fragment]*)`.
- **Iteration**: `flatMap` on lists is natural Scala. Patterns accumulate as `List[Fragment]`, then splat into `.where(patterns*)`.
- **Similarity to A**: The builder structure (Select, Update) is almost identical to SparqlQuery. The main difference is `triple(s, p, o)` vs `sparql"$s $p $o ."` for atomic patterns.

---

## Design Review Feedback

**Likes:**
- Overall readability comparably good to Approach A
- `triple()` is more expressive than `tp()`, though `tp()` is more compact — tradeoff noted

**Suggested improvements:**
- **Drop "Fluent" prefix**: `Select()`, `Ask()`, `Update()` — not `FluentSelect()` etc. *(applied above)*
- **`triple().optional()` over `optional(triple())`**: Wrapping should be a method on the pattern, not a standalone function.
- **Bulk prefixes**: `.prefixes("knora-base" -> knoraBase, "xsd" -> xsd)` instead of chaining `.prefix()`.
- **Composability**: Looks powerful but readability could be better; conditionality/iteration still undecided.
- **See also**: Approach C Variant ("Consequent Fluent") explores taking the fluent chaining further with `.and()`, `.andOptional()`, `.andAll()` on triple patterns themselves.
