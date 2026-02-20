# Approach H: Hybrid Interpolator + Template

## Philosophy

Combine the `sparql"..."` string interpolator (from Approach A) with template-style SPARQL. Instead of binding by name after the fact, typed values are interpolated directly into what looks like a SPARQL template. The interpolator ensures type safety at compile time (raw `String` is a compile error), while the template reads like raw SPARQL.

This is essentially: "What if `sparql"..."` could be used for entire multi-line queries, not just small fragments?"

**Key question**: Can the `sparql"..."` interpolator handle the mix of typed bindings (`$variable`) and literal SPARQL syntax (`?unboundVar`, keywords, punctuation) within a large template? This needs feasibility investigation.

## Shared Vocabulary

```scala
import org.knora.sparqlbuilder.{Fragment, Iri, Variable, Literal}
import org.knora.sparqlbuilder.Fragment.sparql

val knoraBase   = "http://www.knora.org/ontology/knora-base#"
val rdfs        = "http://www.w3.org/2000/01/rdf-schema#"
val xsd         = "http://www.w3.org/2001/XMLSchema#"
val owl         = "http://www.w3.org/2002/07/owl#"
val salsahGui   = "http://www.knora.org/ontology/salsah-gui#"
val kbIsDeleted = Iri.trusted(knoraBase + "isDeleted")
val kbLastMod   = Iri.trusted(knoraBase + "lastModificationDate")
val rdfType     = Iri.trusted("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
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

val query = sparql"""
  SELECT $s $p $o
  WHERE {
    $s a $resourceClass .
    $s $kbIsDeleted ${Literal.bool(false)} .
    OPTIONAL { $s $kbLastMod $lmd . }
    $s $p $o .
  }
  ORDER BY DESC($lmd)
  LIMIT 25
""".render
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

val query = sparql"""
  ASK
  WHERE {
    {
      $s $guiAttr ${Literal.string(s"hlist=<${nodeIri.value}>")} .
    }
    UNION
    {
      $s $valHasListNode $nodeIri .
    }
  }
""".render
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

// Conditional — compose Fragment from Option
val linkValueDeleteFragment: Fragment = linkValuePropertyIri.fold(Fragment.empty) { lvpIri =>
  sparql"$lvpIri $linkValuePropertyPred $linkValuePropertyObj ."
}
val linkValueWhereFragment: Fragment = linkValuePropertyIri.fold(Fragment.empty) { lvpIri =>
  sparql"$lvpIri $linkValuePropertyPred $linkValuePropertyObj ."
}

// Note: Fragment interpolation into sparql"..." — a Fragment embedded in
// another sparql"..." expands in place. This is the key composability mechanism.
val query = sparql"""
  PREFIX knora-base: <$knoraBase>
  PREFIX xsd: <$xsd>
  PREFIX owl: <$owl>

  DELETE {
    GRAPH $ontologyIri {
      $ontologyIri knora-base:lastModificationDate $lmdValue .
      $propertyIri $propertyPred $propertyObj .
      $linkValueDeleteFragment
    }
  }
  INSERT {
    GRAPH $ontologyIri {
      $ontologyIri knora-base:lastModificationDate $newLmd .
    }
  }
  WHERE {
    $ontologyIri a $owlOntology .
    $ontologyIri knora-base:lastModificationDate $lmdValue .
    $propertyIri a $owlObjectProp .
    $propertyIri $propertyPred $propertyObj .
    FILTER NOT EXISTS { $s $p $propertyIri . }
    $linkValueWhereFragment
  }
""".render

// Note: PREFIX IRIs use raw string interpolation within the prefix
// declaration — this is a known limitation since PREFIX syntax expects
// bare URIs inside angle brackets, not escaped IRI nodes.
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

// Iteration — build list of Fragments, then combine
val linkDeleteFragments: List[Fragment] = linkUpdates.zipWithIndex.flatMap { case (update, index) =>
  val directLink = Option.when(update.deleteDirectLink) {
    val prop   = Iri.trusted(update.linkPropertyIri)
    val target = Iri.trusted(update.linkTargetIri)
    sparql"$resource $prop $target ."
  }

  val linkValuePatterns = Option.when(update.linkValueExists) {
    val linkValue      = Variable(s"linkValue$index")
    val linkValueUUID  = Variable(s"linkValueUUID$index")
    val linkValuePerms = Variable(s"linkValuePermissions$index")
    val linkPropValue  = Iri.trusted(update.linkPropertyIri + "Value")
    sparql"""$resource $linkPropValue $linkValue .
      $linkValue $kbValueHasUUID $linkValueUUID .
      $linkValue $kbHasPermissions $linkValuePerms ."""
  }

  directLink.toList ++ linkValuePatterns.toList
}

val linkDeleteBlock = Fragment.join(linkDeleteFragments)

val query = sparql"""
  DELETE {
    $resource $kbLastMod $resourceLastMod .
    $linkDeleteBlock
  }
  WHERE {
    $resource a ${Iri.trusted(knoraBase + "Resource")} .
  }
""".render
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

val projectFilter: Fragment = limitToProject.fold(Fragment.empty) { prj =>
  sparql"$resource ${Iri.trusted(knoraBase + "attachedToProject")} $prj ."
}

val classFilter: Fragment = limitToResourceClass.fold(Fragment.empty) { cls =>
  // Note: property paths like subClassOf* are a raw SPARQL syntax concern
  Fragment.raw(s"${resourceClass.render} <${rdfs}subClassOf*> ${cls.render} .")
}

// Note: Lucene text#query with property list notation doesn't fit the
// standard triple pattern. Uses Fragment.raw for that specific line.
val textQueryLine = Fragment.raw(
  s"${resource.render} ${textQueryPred.render} (${rdfsLabel.render} ${Literal.string("test search").render}) ;" +
  s"\n    a ${resourceClass.render} ."
)

val subClassLine = Fragment.raw(
  s"${resourceClass.render} <${rdfs}subClassOf*> <${knoraBase}Resource> ."
)

val query = sparql"""
  PREFIX rdfs: <$rdfs>
  PREFIX knora-base: <$knoraBase>

  SELECT (count(distinct $resource) as $count)
  WHERE {
    $textQueryLine
    $subClassLine
    $projectFilter
    $classFilter
    FILTER NOT EXISTS { $resource $kbIsDeleted ${Literal.bool(true)} . }
  }
""".render
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

// Polymorphic value type — compose Fragment from match
val commentFragment: Fragment = maybeComment.fold(Fragment.empty) { c =>
  sparql"$kbValueHasComment ${Literal.string(c)} ;"
}

val valueTypeFragment = sparql"""$newValueIri a $kbTextValue ;
      $kbValueHasString ${Literal.string("Hello world")} ;
      $commentFragment
      $kbValueHasUUID ${Literal.string("uuid-new-value-1")} ;
      $kbHasPermissions ${Literal.string("CR knora-admin:ProjectAdmin")} ."""

// Link value iteration
case class LinkValueUpdate(iri: String, refCount: Int, permissions: String)
val linkUpdates = List(LinkValueUpdate("http://example.org/newLinkValue1", 2, "CR knora-admin:ProjectAdmin"))

val linkValueFragments: List[Fragment] = linkUpdates.map { update =>
  val lvIri = Iri.trusted(update.iri)
  sparql"""$lvIri a $kbLinkValue ;
      $kbValueHasRefCount ${Literal.int(update.refCount)} ;
      $kbHasPermissions ${Literal.string(update.permissions)} ."""
}

val linkValueBlock = Fragment.join(linkValueFragments)

val newLmdValue = Literal.typed("2024-01-02T00:00:00Z", Iri.trusted(xsd + "dateTime"))

val query = sparql"""
  PREFIX knora-base: <$knoraBase>
  PREFIX xsd: <$xsd>

  DELETE {
    GRAPH $graphIri {
      $resourceIri knora-base:lastModificationDate $resourceLastMod .
    }
  }
  INSERT {
    GRAPH $graphIri {
      $valueTypeFragment
      $linkValueBlock
      $resourceIri knora-base:lastModificationDate $newLmdValue .
    }
  }
  WHERE {
    $resourceIri knora-base:lastModificationDate $resourceLastMod .
  }
""".render
```

---

## Notes

- **Readability**: For simple queries (Benchmarks 1, 2), the result reads almost exactly like SPARQL — the best readability of any approach. For complex queries, the readability depends on how well Fragment composition handles the dynamic parts.
- **Type safety**: The `sparql"..."` interpolator only accepts `SparqlValue | Fragment` — raw `String` is a compile error. This is the same safety guarantee as Approach A.
- **Composability**: Fragments can be composed and embedded within `sparql"..."` blocks. This handles conditionals (`Option.fold(Fragment.empty)(...)`) and iteration (`Fragment.join(list.map(...))`) naturally.
- **Escape hatches**: `Fragment.raw(...)` still needed for Lucene `text#query` property list notation and `subClassOf*` property paths — same limitation as A and C.
- **PREFIX handling**: The `PREFIX` declarations use bare URIs inside angle brackets. Interpolating an `Iri` would render as `<uri>`, but PREFIX syntax expects just the URI. This needs a solution — either a `Prefix` type that renders without angle brackets, or raw string interpolation for prefix lines.
- **Feasibility concern**: The `sparql"..."` interpolator currently produces `Fragment` from small snippets. Using it for entire multi-line queries may need the interpolator to handle mixed content (SPARQL keywords, punctuation, newlines) alongside typed bindings. This is the key feasibility question.
- **Comparison to Approach A**: A uses `sparql"..."` for fragments that get composed via `SparqlQuery.select().where()`. H uses `sparql"..."` for the entire query. They could be complementary — use H for simple/medium queries and A's builder for programmatic construction.
