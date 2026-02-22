# Approach G: Scala Template + Bind

## Philosophy

Write SPARQL as a plain Scala multi-line string with named placeholders (`?name`), then bind typed values by name using a fluent Scala API. The template reads like raw SPARQL. Unlike Jena's ParameterizedSparqlString (Approach F), this uses an immutable, idiomatic Scala API with proper escaping.

## Shared Vocabulary

```scala
// Assumed pre-defined
val knoraBase = "http://www.knora.org/ontology/knora-base#"
val rdfs      = "http://www.w3.org/2000/01/rdf-schema#"
val xsd       = "http://www.w3.org/2001/XMLSchema#"
val owl       = "http://www.w3.org/2002/07/owl#"
val salsahGui = "http://www.knora.org/ontology/salsah-gui#"
```

---

## Benchmark 1: Simple SELECT with OPTIONAL

### Plain SPARQL

```sparql
SELECT ?s ?p ?o
WHERE {
  ?s a ?resourceClass .
  ?s <http://www.knora.org/ontology/knora-base#isDeleted> false .
  OPTIONAL { ?s <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModDate . }
  ?s ?p ?o .
}
ORDER BY DESC(?lastModDate)
LIMIT 25
```

### Building the query

```scala
val query = Sparql("""
    SELECT ?s ?p ?o
    WHERE {
      ?s a ?resourceClass .
      ?s ?isDeletedPred false .
      OPTIONAL { ?s ?lastModPred ?lastModDate . }
      ?s ?p ?o .
    }
    ORDER BY DESC(?lastModDate)
    LIMIT 25
  """)
  .withIri("resourceClass", "http://example.org/MyClass")
  .withIri("isDeletedPred", knoraBase + "isDeleted")
  .withIri("lastModPred", knoraBase + "lastModificationDate")
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
val nodeIri = "http://rdfh.ch/lists/0001/treeList01"

val query = Sparql("""
    ASK
    WHERE {
      {
        ?s ?guiAttr ?guiAttrValue .
      }
      UNION
      {
        ?s ?valHasListNode ?nodeIri .
      }
    }
  """)
  .withIri("guiAttr", salsahGui + "guiAttribute")
  .withLiteral("guiAttrValue", s"hlist=<$nodeIri>")
  .withIri("valHasListNode", knoraBase + "valueHasListNode")
  .withIri("nodeIri", nodeIri)
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
val linkValuePropertyIri: Option[String] =
  Some("http://www.knora.org/ontology/0001/anything#hasOtherThingValue")

// Conditional pattern — must build template string dynamically
val linkValueDeleteClause = linkValuePropertyIri.fold("") { _ =>
  "?linkValuePropertyIri ?linkValuePropertyPred ?linkValuePropertyObj ."
}
val linkValueWhereClause = linkValuePropertyIri.fold("") { _ =>
  "?linkValuePropertyIri ?linkValuePropertyPred ?linkValuePropertyObj ."
}

val query = Sparql(s"""
    PREFIX knora-base: <$knoraBase>
    PREFIX xsd: <$xsd>
    PREFIX owl: <$owl>

    DELETE {
      GRAPH ?ontologyIri {
        ?ontologyIri knora-base:lastModificationDate ?oldLmd .
        ?propertyIri ?propertyPred ?propertyObj .
        $linkValueDeleteClause
      }
    }
    INSERT {
      GRAPH ?ontologyIri {
        ?ontologyIri knora-base:lastModificationDate ?newLmd .
      }
    }
    WHERE {
      ?ontologyIri a owl:Ontology .
      ?ontologyIri knora-base:lastModificationDate ?oldLmd .
      ?propertyIri a owl:ObjectProperty .
      ?propertyIri ?propertyPred ?propertyObj .
      FILTER NOT EXISTS { ?s ?p ?propertyIri . }
      $linkValueWhereClause
    }
  """)
  .withIri("ontologyIri", "http://www.knora.org/ontology/0001/anything")
  .withIri("propertyIri", "http://www.knora.org/ontology/0001/anything#hasOtherThing")
  .withTypedLiteral("oldLmd", "2024-01-01T00:00:00Z", xsd + "dateTime")
  .withTypedLiteral("newLmd", "2024-01-02T00:00:00Z", xsd + "dateTime")
  .withOptionalIri("linkValuePropertyIri", linkValuePropertyIri)
  .render

// WARNING: The template uses s"..." string interpolation for conditional
// clauses. The bind API protects bound values, but the template structure
// itself is built via string concatenation — same fundamental tension as
// Approach F, just with a nicer Scala wrapper.
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

val linkUpdates = List(
  LinkUpdate("http://example.org/hasLink", "http://example.org/target1",
    deleteDirectLink = true, linkValueExists = true),
)

// Iteration requires building template string dynamically
val linkDeleteClauses = linkUpdates.zipWithIndex.map { case (update, idx) =>
  val directLink = if (update.deleteDirectLink)
    s"?resource ?linkProp$idx ?linkTarget$idx ."
  else ""

  val linkValuePatterns = if (update.linkValueExists)
    s"""?resource ?linkPropValue$idx ?linkValue$idx .
       |    ?linkValue$idx ?valueHasUUID ?linkValueUUID$idx .
       |    ?linkValue$idx ?hasPermissions ?linkValuePermissions$idx .""".stripMargin
  else ""

  s"$directLink\n    $linkValuePatterns"
}.mkString("\n    ")

val base = Sparql(s"""
    DELETE {
      ?resource ?lastModPred ?resourceLastModificationDate .
      $linkDeleteClauses
    }
    WHERE {
      ?resource a ?resourceType .
    }
  """)
  .withIri("lastModPred", knoraBase + "lastModificationDate")
  .withIri("resourceType", knoraBase + "Resource")
  .withIri("valueHasUUID", knoraBase + "valueHasUUID")
  .withIri("hasPermissions", knoraBase + "hasPermissions")

val query = linkUpdates.zipWithIndex.foldLeft(base) { case (sparql, (update, idx)) =>
  val s1 = if (update.deleteDirectLink)
    sparql
      .withIri(s"linkProp$idx", update.linkPropertyIri)
      .withIri(s"linkTarget$idx", update.linkTargetIri)
  else sparql
  if (update.linkValueExists)
    s1.withIri(s"linkPropValue$idx", update.linkPropertyIri + "Value")
  else s1
}.render

// WARNING: Same issue — template structure built via s"..." interpolation
// for iteration. Binds only protect the values, not the template.
```

---

## Benchmark 5: SearchQueries.selectCountByLabel (Lucene)

### Plain SPARQL

```sparql
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>

SELECT (count(distinct ?resource) as ?count)
WHERE {
  ?resource <http://jena.apache.org/text#query> (rdfs:label ?luceneQuery) ;
    a ?resourceClass .
  ?resourceClass rdfs:subClassOf* knora-base:Resource .
  ?resource knora-base:attachedToProject ?projectIri .
  FILTER NOT EXISTS { ?resource knora-base:isDeleted true . }
}
```

### Building the query

```scala
val limitToProject: Option[String] = Some("http://rdfh.ch/projects/0001")
val limitToResourceClass: Option[String] = None

val projectClause = limitToProject.fold("") { _ =>
  "?resource knora-base:attachedToProject ?projectIri ."
}
val classClause = limitToResourceClass.fold("") { _ =>
  "?resourceClass rdfs:subClassOf* ?limitClass ."
}

val query = Sparql(s"""
    PREFIX rdfs: <$rdfs>
    PREFIX knora-base: <$knoraBase>

    SELECT (count(distinct ?resource) as ?count)
    WHERE {
      ?resource <http://jena.apache.org/text#query> (rdfs:label ?luceneQuery) ;
        a ?resourceClass .
      ?resourceClass rdfs:subClassOf* knora-base:Resource .
      $projectClause
      $classClause
      FILTER NOT EXISTS { ?resource knora-base:isDeleted true . }
    }
  """)
  .withLiteral("luceneQuery", "test search")
  .withOptionalIri("projectIri", limitToProject)
  .withOptionalIri("limitClass", limitToResourceClass)
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
val maybeComment: Option[String] = Some("Updated value")

// Polymorphic value type — build template clause via match
val commentLine = maybeComment.fold("") { _ =>
  "?newValueIri knora-base:valueHasComment ?commentValue ;"
}

val valueTypeClause =
  s"""?newValueIri a knora-base:TextValue ;
     |      knora-base:valueHasString ?textValue ;
     |      $commentLine
     |      knora-base:valueHasUUID ?valueUUID ;
     |      knora-base:hasPermissions ?valuePermissions .""".stripMargin

// Link value iteration
case class LinkValueUpdate(iri: String, refCount: Int, permissions: String)
val linkUpdates = List(LinkValueUpdate("http://example.org/newLinkValue1", 2, "CR knora-admin:ProjectAdmin"))

val linkValueClauses = linkUpdates.zipWithIndex.map { case (_, idx) =>
  s"""?linkValue$idx a knora-base:LinkValue ;
     |      knora-base:valueHasRefCount ?refCount$idx ;
     |      knora-base:hasPermissions ?lvPermissions$idx .""".stripMargin
}.mkString("\n    ")

val base = Sparql(s"""
    PREFIX knora-base: <$knoraBase>
    PREFIX xsd: <$xsd>

    DELETE {
      GRAPH ?graphIri {
        ?resourceIri knora-base:lastModificationDate ?resourceLastModificationDate .
      }
    }
    INSERT {
      GRAPH ?graphIri {
        $valueTypeClause
        $linkValueClauses
        ?resourceIri knora-base:lastModificationDate ?newLmd .
      }
    }
    WHERE {
      ?resourceIri knora-base:lastModificationDate ?resourceLastModificationDate .
    }
  """)
  .withIri("graphIri", "http://www.knora.org/data/0001/project1")
  .withIri("resourceIri", "http://rdfh.ch/0001/resource1")
  .withIri("newValueIri", "http://rdfh.ch/0001/resource1/values/newValue1")
  .withLiteral("textValue", "Hello world")
  .withOptionalLiteral("commentValue", maybeComment)
  .withLiteral("valueUUID", "uuid-new-value-1")
  .withLiteral("valuePermissions", "CR knora-admin:ProjectAdmin")
  .withTypedLiteral("newLmd", "2024-01-02T00:00:00Z", xsd + "dateTime")

val query = linkUpdates.zipWithIndex.foldLeft(base) { case (sparql, (update, idx)) =>
  sparql
    .withIri(s"linkValue$idx", update.iri)
    .withIntLiteral(s"refCount$idx", update.refCount)
    .withLiteral(s"lvPermissions$idx", update.permissions)
}.render

// WARNING: Template structure built via s"..." for polymorphic clauses,
// conditional comment line, and link value iteration. Same fundamental
// tension as all template-based approaches.
```

---

## Notes

- **Readability**: The template reads like raw SPARQL for simple queries (Benchmarks 1, 2). For complex queries with conditionals/iteration, the template becomes fragmented by `s"..."` interpolation — same problem as Approach F but with a nicer binding API.
- **Immutable API**: `.withIri()`, `.withLiteral()` return new instances — idiomatic Scala, unlike Jena's mutable PSS.
- **Convenience helpers**: `.withOptionalIri()`, `.withOptionalLiteral()` handle `Option` values cleanly.
- **Fundamental tension**: Template-and-bind works beautifully for **static** queries with fixed structure and variable values. dsp-api's queries have **dynamic structure** (conditional patterns, iteration) — the template must be built via `s"..."` string interpolation, which is unprotected. The bind API only protects the values, not the template structure.
- **Comparison to Approach F**: Same concept, much nicer Scala API. But the core limitation is identical — conditional/iteration patterns require building the template dynamically.
- **Composability**: None. Each template is a monolithic string. No way to extract reusable fragments like "permission check" or "isDeleted filter."
