# Approach F: Template + Bind via Jena ParameterizedSparqlString

## Philosophy

Write SPARQL as a template string with named placeholders, then bind typed values by name. Jena escapes values at bind time and validates the query at parse time. Closest to raw SPARQL but least composable.

**Warning**: Jena's own docs state that ParameterizedSparqlString's injection protection is "by no means foolproof" since substitution is textual.

## Shared Vocabulary

```scala
import org.apache.jena.query.ParameterizedSparqlString
import org.apache.jena.datatypes.xsd.XSDDatatype

// No typed wrappers — PSS uses setIri/setLiteral with raw strings
val knoraBase = "http://www.knora.org/ontology/knora-base#"
val rdfs      = "http://www.w3.org/2000/01/rdf-schema#"
val xsd       = "http://www.w3.org/2001/XMLSchema#"
val owl       = "http://www.w3.org/2002/07/owl#"
```

---

## Benchmark 1: Simple SELECT with OPTIONAL

### Plain SPARQL

```sparql
SELECT ?s ?p ?o
WHERE {
  ?s a ?resourceClass .
  ?s ?isDeletedPred ?isDeletedVal .
  OPTIONAL { ?s ?lastModPred ?lastModDate . }
  ?s ?p ?o .
}
ORDER BY DESC(?lastModDate)
LIMIT 25
```

### Building the query

```scala
val pss = new ParameterizedSparqlString()
pss.setCommandText("""
  SELECT ?s ?p ?o
  WHERE {
    ?s a ?resourceClass .
    ?s ?isDeletedPred ?isDeletedVal .
    OPTIONAL { ?s ?lastModPred ?lastModDate . }
    ?s ?p ?o .
  }
  ORDER BY DESC(?lastModDate)
  LIMIT 25
""")
pss.setIri("resourceClass", "http://example.org/MyClass")
pss.setIri("isDeletedPred", knoraBase + "isDeleted")
pss.setLiteral("isDeletedVal", false)
pss.setIri("lastModPred", knoraBase + "lastModificationDate")

val query = pss.toString
```

---

## Benchmark 2: ASK with UNION

### Plain SPARQL

```sparql
ASK
WHERE {
  { ?s ?guiAttr ?guiAttrValue . }
  UNION
  { ?s ?valHasListNode ?nodeIri . }
}
```

### Building the query

```scala
val pss = new ParameterizedSparqlString()
pss.setCommandText("""
  ASK
  WHERE {
    { ?s ?guiAttr ?guiAttrValue . }
    UNION
    { ?s ?valHasListNode ?nodeIri . }
  }
""")
pss.setIri("guiAttr", "http://www.knora.org/ontology/salsah-gui#guiAttribute")
pss.setLiteral("guiAttrValue", "hlist=<http://rdfh.ch/lists/0001/treeList01>")
pss.setIri("valHasListNode", knoraBase + "valueHasListNode")
pss.setIri("nodeIri", "http://rdfh.ch/lists/0001/treeList01")

val query = pss.toString
```

---

## Benchmark 3: DeletePropertyQuery (DELETE/INSERT WHERE)

### Plain SPARQL

```sparql
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
PREFIX owl: <http://www.w3.org/2002/07/owl#>

DELETE { GRAPH ?ontologyIri { ... } }
INSERT { GRAPH ?ontologyIri { ... } }
WHERE { ... FILTER NOT EXISTS { ... } ... }
```

### Building the query

```scala
val linkValuePropertyIri: Option[String] =
  Some("http://www.knora.org/ontology/0001/anything#hasOtherThingValue")

// Conditional pattern — must build template string dynamically
val linkValueDeleteClause = linkValuePropertyIri match {
  case Some(_) => "?linkValuePropertyIri ?linkValuePropertyPred ?linkValuePropertyObj ."
  case None    => ""
}

val linkValueWhereClause = linkValuePropertyIri match {
  case Some(_) => "?linkValuePropertyIri ?linkValuePropertyPred ?linkValuePropertyObj ."
  case None    => ""
}

val pss = new ParameterizedSparqlString()
pss.setCommandText(s"""
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
pss.setIri("ontologyIri", "http://www.knora.org/ontology/0001/anything")
pss.setIri("propertyIri", "http://www.knora.org/ontology/0001/anything#hasOtherThing")
pss.setLiteral("oldLmd", "2024-01-01T00:00:00Z", XSDDatatype.XSDdateTime)
pss.setLiteral("newLmd", "2024-01-02T00:00:00Z", XSDDatatype.XSDdateTime)
linkValuePropertyIri.foreach { lvpIri =>
  pss.setIri("linkValuePropertyIri", lvpIri)
}

// WARNING: The template uses s"..." string interpolation to include
// the conditional clauses. This means SPARQL structure is being built
// via string concatenation — exactly the anti-pattern we're trying to
// eliminate. PSS only protects the bound values, not the template itself.

val query = pss.toString
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

val linkUpdates = List(
  LinkUpdate("http://example.org/hasLink", "http://example.org/target1",
    deleteDirectLink = true, linkValueExists = true),
)

// Build template string dynamically — iteration requires string construction
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

val pss = new ParameterizedSparqlString()
pss.setCommandText(s"""
  DELETE {
    ?resource ?lastModPred ?resourceLastModificationDate .
    $linkDeleteClauses
  }
  WHERE {
    ?resource a ?resourceType .
  }
""")
pss.setIri("lastModPred", knoraBase + "lastModificationDate")
pss.setIri("resourceType", knoraBase + "Resource")
pss.setIri("valueHasUUID", knoraBase + "valueHasUUID")
pss.setIri("hasPermissions", knoraBase + "hasPermissions")

linkUpdates.zipWithIndex.foreach { case (update, idx) =>
  if (update.deleteDirectLink) {
    pss.setIri(s"linkProp$idx", update.linkPropertyIri)
    pss.setIri(s"linkTarget$idx", update.linkTargetIri)
  }
  if (update.linkValueExists) {
    pss.setIri(s"linkPropValue$idx", update.linkPropertyIri + "Value")
  }
}

// WARNING: Template is built via s"..." string interpolation with
// conditional and iteration logic. The PSS bindings only protect
// the values — the template structure itself is raw string concatenation.

val query = pss.toString
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

// Conditional filter clauses — again, string concatenation of template
val projectClause = limitToProject match {
  case Some(_) => "?resource knora-base:attachedToProject ?projectIri ."
  case None    => ""
}

val classClause = limitToResourceClass match {
  case Some(_) => "?resourceClass rdfs:subClassOf* ?limitClass ."
  case None    => ""
}

val pss = new ParameterizedSparqlString()
pss.setCommandText(s"""
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
pss.setLiteral("luceneQuery", "test search")
limitToProject.foreach(prj => pss.setIri("projectIri", prj))
limitToResourceClass.foreach(cls => pss.setIri("limitClass", cls))

val query = pss.toString
```

---

## Benchmark 6: addValueVersion (polymorphic match + iteration)

### Plain SPARQL

```sparql
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

DELETE { GRAPH ?graphIri { ... } }
INSERT { GRAPH ?graphIri { ... TextValue ... LinkValue ... } }
WHERE { ... }
```

### Building the query

```scala
val maybeComment: Option[String] = Some("Updated value")

// Polymorphic value type — build template clauses via match
val valueTypeClause = {
  // TextValue case:
  val commentLine = maybeComment match {
    case Some(_) => "?newValueIri knora-base:valueHasComment ?commentValue ;"
    case None    => ""
  }
  s"""?newValueIri a knora-base:TextValue ;
     |      knora-base:valueHasString ?textValue ;
     |      $commentLine
     |      knora-base:valueHasUUID ?valueUUID ;
     |      knora-base:hasPermissions ?valuePermissions .""".stripMargin
}

// Link value iteration
case class LinkValueUpdate(iri: String, refCount: Int, permissions: String)
val linkUpdates = List(LinkValueUpdate("http://example.org/newLinkValue1", 2, "CR knora-admin:ProjectAdmin"))

val linkValueClauses = linkUpdates.zipWithIndex.map { case (_, idx) =>
  s"""?linkValue$idx a knora-base:LinkValue ;
     |      knora-base:valueHasRefCount ?refCount$idx ;
     |      knora-base:hasPermissions ?lvPermissions$idx .""".stripMargin
}.mkString("\n    ")

val pss = new ParameterizedSparqlString()
pss.setCommandText(s"""
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
pss.setIri("graphIri", "http://www.knora.org/data/0001/project1")
pss.setIri("resourceIri", "http://rdfh.ch/0001/resource1")
pss.setIri("newValueIri", "http://rdfh.ch/0001/resource1/values/newValue1")
pss.setLiteral("textValue", "Hello world")
maybeComment.foreach(c => pss.setLiteral("commentValue", c))
pss.setLiteral("valueUUID", "uuid-new-value-1")
pss.setLiteral("valuePermissions", "CR knora-admin:ProjectAdmin")
pss.setLiteral("newLmd", "2024-01-02T00:00:00Z", XSDDatatype.XSDdateTime)

linkUpdates.zipWithIndex.foreach { case (update, idx) =>
  pss.setIri(s"linkValue$idx", update.iri)
  pss.setLiteral(s"refCount$idx", update.refCount.toString, XSDDatatype.XSDinteger)
  pss.setLiteral(s"lvPermissions$idx", update.permissions)
}

// WARNING: Template is heavily built via s"..." string interpolation.
// The polymorphic value type clause, the conditional comment line,
// and the link value iteration all use string concatenation.
// PSS only protects the bound values, not the template structure.

val query = pss.toString
```

---

## Notes

- **Readability**: The template itself reads like raw SPARQL, which is a strength for simple queries. For complex queries, the template becomes fragmented by string interpolation.
- **Composability**: None. Templates are monolithic strings. There is no way to extract a reusable "permission check" or "isDeleted filter" fragment. Every template is standalone.
- **Conditional logic**: Requires building the template string dynamically with `s"..."` — this is exactly the string interpolation anti-pattern the library initiative aims to eliminate.
- **Iteration**: Requires dynamically building placeholder names in the template via `.map { ... s"?linkValue$idx" }.mkString` — raw string concatenation of SPARQL structure.
- **Injection safety**: PSS protects `setIri`/`setLiteral` bindings, but the template itself is unprotected. If a developer uses `s"...$userInput..."` in the template (as all conditional/iteration patterns require), PSS provides zero protection. Jena docs explicitly warn this is "by no means foolproof."
- **Validation**: PSS does validate the final query at parse time (via `asQuery()`/`asUpdate()`), catching structural errors. But injection payloads that produce valid SPARQL would pass.
- **Fundamental tension**: PSS works well for static queries with fixed structure and variable values. dsp-api's queries have dynamic structure (conditional patterns, iteration) — the exact use case PSS handles poorly.
