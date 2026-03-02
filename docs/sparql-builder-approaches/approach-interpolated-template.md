# Approach: Interpolated Template

## Philosophy

Two complementary APIs over the same core — like Rust libraries that expose both a macro and a builder.

**Template style** (primary): Write entire queries as `sparql"""..."""` (alias: `sp"""..."""`) templates with typed interpolation. Reads like raw SPARQL. Use fragment composition for dynamic parts.

**Builder style** (alternative): Construct queries programmatically via `Sparql.select(...).where(...).render`. Each slot accepts a `Fragment`. Useful when the query shape itself is dynamic or when you prefer named slots over positional embedding. Builder WHERE clauses can use either individual fragment arguments or a single multi-line `sparql"""..."""` fragment.

Both share the same foundation: `Fragment` type (monoid), `sparql"..."` / `sp"..."` interpolator, `Iri`, `Variable`, `Literal`, `Prefix`. Both render to the same output. The choice is ergonomic, not semantic.

**Industry alignment**: Doobie/Skunk write whole queries as `sql"..."` (our template style). Rust's `sqlx` offers both `query!()` macro and `QueryBuilder`. JOOQ offers both DSL and plain SQL. Exposing both is an established pattern.

## Shared Vocabulary

```scala
import org.knora.sparqlbuilder.{Fragment, Iri, Variable, Literal, Prefix, Sparql}
import org.knora.sparqlbuilder.Fragment.{sparql, sp}  // sp is a short alias for sparql
import org.knora.sparqlbuilder.Fragments

// Prefixes — constructed with unsafeFrom, derive IRIs via unsafeIri
val kb   = Prefix.unsafeFrom("knora-base", "http://www.knora.org/ontology/knora-base#")
val rdf  = Prefix.unsafeFrom("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
val rdfs = Prefix.unsafeFrom("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
val xsd  = Prefix.unsafeFrom("xsd", "http://www.w3.org/2001/XMLSchema#")
val owl  = Prefix.unsafeFrom("owl", "http://www.w3.org/2002/07/owl#")
val gui  = Prefix.unsafeFrom("salsah-gui", "http://www.knora.org/ontology/salsah-gui#")

// IRIs derived from prefixes — no raw namespace strings repeated
val kbIsDeleted    = kb.unsafeIri("isDeleted")
val kbLastMod      = kb.unsafeIri("lastModificationDate")
val kbResource     = kb.unsafeIri("Resource")
val rdfType        = rdf.unsafeIri("type")
val rdfsSubClassOf = rdfs.unsafeIri("subClassOf")
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

### Template style

```scala
val s             = Variable("s")
val p             = Variable("p")
val o             = Variable("o")
val lmd           = Variable("lastModDate")
val resourceClass = Iri.unsafeFrom("http://example.org/MyClass")

val query = sparql"""
  SELECT $s $p $o
  WHERE {
    $s a $resourceClass .
    $s $kbIsDeleted false .
    OPTIONAL { $s $kbLastMod $lmd . }
    $s $p $o .
  }
  ORDER BY DESC($lmd)
  LIMIT 25
""".render
```

### Builder style (individual fragments)

```scala
val query = Sparql
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

### Builder style (multi-line WHERE fragment)

```scala
val query = Sparql
  .select(s, p, o)
  .where(sparql"""
    $s a $resourceClass .
    $s $kbIsDeleted false .
    ${Fragments.optional(sparql"$s $kbLastMod $lmd .")}
    $s $p $o .""")
  .orderBy(lmd.desc)
  .limit(25)
  .render
```

### Comparison

All three are clean for this simple case. The template reads like SPARQL. The builder with individual fragments gives maximum structure. The builder with a multi-line WHERE fragment is a middle ground — named slots for the outer structure, readable SPARQL body. Developer choice.

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

### Template style

```scala
val s              = Variable("s")
val nodeIri        = Iri.unsafeFrom("http://rdfh.ch/lists/0001/treeList01")
val guiAttr        = gui.unsafeIri("guiAttribute")
val valHasListNode = kb.unsafeIri("valueHasListNode")

val hlistLiteral = Literal.stringEscaped(s"hlist=<${nodeIri.value}>")

val query = sparql"""
  ASK
  WHERE {
    {
      $s $guiAttr $hlistLiteral .
    }
    UNION
    {
      $s $valHasListNode $nodeIri .
    }
  }
""".render
```

### Builder style (individual fragments)

```scala
val query = Sparql.ask
  .where(
    Fragments.union(
      sparql"$s $guiAttr $hlistLiteral .",
      sparql"$s $valHasListNode $nodeIri .",
    ),
  )
  .render
```

### Builder style (multi-line WHERE fragment)

```scala
val query = Sparql.ask
  .where(sparql"""
    {
      $s $guiAttr $hlistLiteral .
    }
    UNION
    {
      $s $valHasListNode $nodeIri .
    }""")
  .render
```

### Comparison

Template is most readable — the UNION structure is visually obvious. Builder with individual fragments is compact but hides the UNION inside `Fragments.union(...)`. Builder with multi-line fragment recovers the visual structure while keeping the named `.where()` slot.

---

## Benchmark 3: DeletePropertyQuery (DELETE/INSERT WHERE)

**What this tests**: A SPARQL UPDATE query with three clauses (DELETE, INSERT, WHERE), GRAPH scoping, FILTER NOT EXISTS, and — most importantly — **conditional fragments**. The query structure changes based on runtime data: this is the Twirl `@if` equivalent.

**Domain context**: In knora-base, most value properties (e.g., `hasColor`) point to a reified value object that carries metadata (creation date, permissions, etc.). Link properties are special: a link property like `hasOtherThing` points *directly* to the target resource, while a paired property `hasOtherThingValue` (name derived by appending "Value") points to a `LinkValue` reification object. This naming convention is enforced — non-link properties must not end in "Value". When deleting a property from an ontology, if it's a link property, both `hasOtherThing` and `hasOtherThingValue` must be removed. The `linkValuePropertyIri: Option[PropertyIri]` parameter is `Some` for link properties and `None` for all others.

**Key challenges**:
- Conditional inclusion: the link value property patterns are `Option[Fragment]` — present when deleting a link property, absent for regular value properties. The same conditional appears in both the DELETE and WHERE clauses.
- FILTER NOT EXISTS: a nested pattern that guards against deleting a property still in use by any resource

### Plain SPARQL

```sparql
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
PREFIX owl: <http://www.w3.org/2002/07/owl#>

DELETE {
  GRAPH <http://www.knora.org/ontology/0001/anything> {
    <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2024-01-01T00:00:00Z"^^xsd:dateTime .
    <http://www.knora.org/ontology/0001/anything#hasOtherThing> ?propertyPred ?propertyObj .
    # conditional: only present when deleting a link property
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
  # guard: only delete if the property is not used anywhere
  FILTER NOT EXISTS { ?s ?p <http://www.knora.org/ontology/0001/anything#hasOtherThing> . }
  # conditional: only present when deleting a link property
  <http://www.knora.org/ontology/0001/anything#hasOtherThingValue> ?linkValuePropertyPred ?linkValuePropertyObj .
}
```

### Shared setup

```scala
val ontologyIri = Iri.unsafeFrom("http://www.knora.org/ontology/0001/anything")
val propertyIri = Iri.unsafeFrom("http://www.knora.org/ontology/0001/anything#hasOtherThing")
val lmdValue    = Literal.typedEscaped("2024-01-01T00:00:00Z", xsd.unsafeIri("dateTime"))
val newLmd      = Literal.typedEscaped("2024-01-02T00:00:00Z", xsd.unsafeIri("dateTime"))
val propertyPred = Variable("propertyPred")
val propertyObj  = Variable("propertyObj")
val s            = Variable("s")
val p            = Variable("p")

val owlOntology   = owl.unsafeIri("Ontology")
val owlObjectProp = owl.unsafeIri("ObjectProperty")

// Conditional link value property
val linkValuePropertyIri: Option[Iri] =
  Some(Iri.unsafeFrom("http://www.knora.org/ontology/0001/anything#hasOtherThingValue"))
val linkValuePropertyPred = Variable("linkValuePropertyPred")
val linkValuePropertyObj  = Variable("linkValuePropertyObj")

// Fragment composition — shared by all styles
val linkValueDeleteFragment: Fragment = Fragment.fromOption(linkValuePropertyIri) { lvpIri =>
  sparql"$lvpIri $linkValuePropertyPred $linkValuePropertyObj ."
}
val linkValueWhereFragment: Fragment = Fragment.fromOption(linkValuePropertyIri) { lvpIri =>
  sparql"$lvpIri $linkValuePropertyPred $linkValuePropertyObj ."
}
```

### Template style

```scala
val query = sparql"""
  PREFIX $kb
  PREFIX $xsd
  PREFIX $owl

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
```

### Builder style (individual fragments)

```scala
val query = Sparql.update
  .prefixes(kb, xsd, owl)
  .deleteFrom(ontologyIri)(
    sparql"$ontologyIri $kbLastMod $lmdValue .",
    sparql"$propertyIri $propertyPred $propertyObj .",
    linkValueDeleteFragment,
  )
  .insertInto(ontologyIri)(sparql"$ontologyIri $kbLastMod $newLmd .")
  .where(
    sparql"$ontologyIri a $owlOntology .",
    sparql"$ontologyIri $kbLastMod $lmdValue .",
    sparql"$propertyIri a $owlObjectProp .",
    sparql"$propertyIri $propertyPred $propertyObj .",
    Fragments.filterNotExists(sparql"$s $p $propertyIri ."),
    linkValueWhereFragment,
  )
  .render
```

### Builder style (multi-line fragments)

```scala
val query = Sparql.update
  .prefixes(kb, xsd, owl)
  .deleteFrom(ontologyIri)(sparql"""
    $ontologyIri $kbLastMod $lmdValue .
    $propertyIri $propertyPred $propertyObj .
    $linkValueDeleteFragment""")
  .insertInto(ontologyIri)(sparql"$ontologyIri $kbLastMod $newLmd .")
  .where(sparql"""
    $ontologyIri a $owlOntology .
    $ontologyIri $kbLastMod $lmdValue .
    $propertyIri a $owlObjectProp .
    $propertyIri $propertyPred $propertyObj .
    ${Fragments.filterNotExists(sparql"$s $p $propertyIri .")}
    $linkValueWhereFragment""")
  .render
```

### Comparison

Template clearly wins for readability — the DELETE/INSERT/WHERE structure is visually obvious. The builder variants are also readable: `deleteFrom(ontologyIri)(...)` and `insertInto(ontologyIri)(...)` make the GRAPH scope explicit in the method name, avoiding any ambiguity. All three styles share the same `Fragment.fromOption` composition for the conditional link value property.

---

## Benchmark 4: InsertValueQueryBuilder (conditional + iteration)

**What this tests**: A simplified sketch of the 740-line `InsertValueQueryBuilder.scala` — the most complex RDF4J query builder in the codebase. It inserts a new value for a resource, handling link updates with conditional DELETE/INSERT patterns, indexed variables, BIND, and property path validation. This is the Twirl `@for` + `@if` equivalent: iterate over a list, conditionally emit patterns per item, and generate **indexed variables** (`?linkValue0`, `?linkValue1`, ...) to keep each iteration's bindings distinct.

**Key challenges**:
- Iteration with indexed variables: a `List[LinkUpdate]` produces a variable number of patterns in both DELETE and INSERT, each with unique variable names (`?linkValue0`, `?linkValue1`, ...)
- Conditionals per item: each update has `deleteDirectLink` and `linkValueExists` flags controlling which patterns appear
- BIND: the real query uses `BIND(IRI("...") AS ?resource)` to bind string IRIs to variables — the library needs a `Fragments.bind` combinator
- Property path in WHERE: `rdfs:subClassOf*` validates the resource class hierarchy
- GRAPH scoping: DELETE and INSERT target a named graph

### Plain SPARQL

```sparql
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

DELETE {
  GRAPH ?dataNamedGraph {
    ?resource knora-base:lastModificationDate ?resourceLastModificationDate .
    # conditional: only when deleteDirectLink = true (link update 0)
    ?resource <http://example.org/hasLink> <http://example.org/target1> .
    # conditional: only when linkValueExists = true (link update 0)
    ?resource <http://example.org/hasLinkValue> ?linkValue0 .
    ?linkValue0 knora-base:valueHasUUID ?linkValueUUID0 .
    ?linkValue0 knora-base:hasPermissions ?linkValuePermissions0 .
    # link update 1: deleteDirectLink = false, linkValueExists = false → no patterns
  }
}
INSERT {
  GRAPH ?dataNamedGraph {
    ?resource knora-base:lastModificationDate "2024-01-02T00:00:00Z"^^xsd:dateTime .
    <http://example.org/newLinkValue0> a knora-base:LinkValue ;
      knora-base:valueHasRefCount 1 ;
      knora-base:valueHasUUID ?linkValueUUID0 ;
      knora-base:previousValue ?linkValue0 ;
      knora-base:hasPermissions ?linkValuePermissions0 .
    ?resource <http://example.org/hasLinkValue> <http://example.org/newLinkValue0> .
    <http://example.org/newLinkValue1> a knora-base:LinkValue ;
      knora-base:valueHasRefCount 1 .
    ?resource <http://example.org/hasOtherLinkValue> <http://example.org/newLinkValue1> .
    ?resource <http://example.org/hasOtherLink> <http://example.org/target2> .
  }
}
WHERE {
  # BIND: real query binds string IRIs to variables
  BIND(IRI("http://www.knora.org/data/0001/project1") AS ?dataNamedGraph)
  BIND(IRI("http://rdfh.ch/0001/resource1") AS ?resource)
  ?resource a ?resourceClass .
  ?resource knora-base:isDeleted false .
  # property path: validate resource class hierarchy
  ?resourceClass rdfs:subClassOf* knora-base:Resource .
  OPTIONAL { ?resource knora-base:lastModificationDate ?resourceLastModificationDate . }
  # conditional: link update 0 has linkValueExists = true
  ?resource <http://example.org/hasLinkValue> ?linkValue0 .
  ?linkValue0 knora-base:valueHasUUID ?linkValueUUID0 .
  ?linkValue0 knora-base:hasPermissions ?linkValuePermissions0 .
}
```

### Shared setup

```scala
case class LinkUpdate(
  linkPropertyIri: String, linkTargetIri: String,
  deleteDirectLink: Boolean, linkValueExists: Boolean,
  newLinkValueIri: String, newReferenceCount: Int,
)

val dataNamedGraph  = Variable("dataNamedGraph")
val resource        = Variable("resource")
val resourceClass   = Variable("resourceClass")
val resourceLastMod = Variable("resourceLastModificationDate")
val kbHasPermissions  = kb.unsafeIri("hasPermissions")
val kbValueHasUUID    = kb.unsafeIri("valueHasUUID")
val kbValueHasRefCount = kb.unsafeIri("valueHasRefCount")
val kbPreviousValue   = kb.unsafeIri("previousValue")
val kbLinkValue       = kb.unsafeIri("LinkValue")

// Two link updates — different flags produce different patterns
val linkUpdates = List(
  LinkUpdate("http://example.org/hasLink", "http://example.org/target1",
    deleteDirectLink = true, linkValueExists = true,
    newLinkValueIri = "http://example.org/newLinkValue0", newReferenceCount = 1),
  LinkUpdate("http://example.org/hasOtherLink", "http://example.org/target2",
    deleteDirectLink = false, linkValueExists = false,
    newLinkValueIri = "http://example.org/newLinkValue1", newReferenceCount = 1),
)

// --- DELETE fragment: iteration + conditionals ---
val linkDeleteFragments: List[Fragment] = linkUpdates.zipWithIndex.flatMap { case (update, index) =>
  val prop   = Iri.unsafeFrom(update.linkPropertyIri)
  val target = Iri.unsafeFrom(update.linkTargetIri)

  val directLink = Option.when(update.deleteDirectLink) {
    sparql"$resource $prop $target ."
  }

  val linkValuePatterns = Option.when(update.linkValueExists) {
    val linkValue      = Variable(s"linkValue$index")
    val linkValueUUID  = Variable(s"linkValueUUID$index")
    val linkValuePerms = Variable(s"linkValuePermissions$index")
    val linkPropValue  = Iri.unsafeFrom(update.linkPropertyIri + "Value")
    sparql"""$resource $linkPropValue $linkValue .
      $linkValue $kbValueHasUUID $linkValueUUID .
      $linkValue $kbHasPermissions $linkValuePerms ."""
  }

  directLink.toList ++ linkValuePatterns.toList
}
val linkDeleteBlock = Fragment.join(linkDeleteFragments)

// --- INSERT fragment: new link values ---
val linkInsertFragments: List[Fragment] = linkUpdates.zipWithIndex.map { case (update, index) =>
  val newLvIri     = Iri.unsafeFrom(update.newLinkValueIri)
  val linkPropValue = Iri.unsafeFrom(update.linkPropertyIri + "Value")

  val baseTriples = sparql"""$newLvIri a $kbLinkValue ;
      $kbValueHasRefCount ${Literal.int(update.newReferenceCount)} ."""

  // Conditional: reuse UUID and link to previous value when updating existing
  val previousValueTriples = Option.when(update.linkValueExists) {
    val linkValue      = Variable(s"linkValue$index")
    val linkValueUUID  = Variable(s"linkValueUUID$index")
    val linkValuePerms = Variable(s"linkValuePermissions$index")
    sparql"""$newLvIri $kbValueHasUUID $linkValueUUID .
      $newLvIri $kbPreviousValue $linkValue .
      $newLvIri $kbHasPermissions $linkValuePerms ."""
  }

  val resourceToLinkValue = sparql"$resource $linkPropValue $newLvIri ."

  // Conditional: insert direct link when needed
  val directLink = Option.when(update.deleteDirectLink || !update.linkValueExists) {
    val prop   = Iri.unsafeFrom(update.linkPropertyIri)
    val target = Iri.unsafeFrom(update.linkTargetIri)
    sparql"$resource $prop $target ."
  }

  Fragment.join(List(Some(baseTriples), previousValueTriples, Some(resourceToLinkValue), directLink).flatten)
}
val linkInsertBlock = Fragment.join(linkInsertFragments)

// --- WHERE fragment: conditional link value bindings ---
val linkWhereFragments: List[Fragment] = linkUpdates.zipWithIndex.flatMap { case (update, index) =>
  Option.when(update.linkValueExists) {
    val linkValue      = Variable(s"linkValue$index")
    val linkValueUUID  = Variable(s"linkValueUUID$index")
    val linkValuePerms = Variable(s"linkValuePermissions$index")
    val linkPropValue  = Iri.unsafeFrom(update.linkPropertyIri + "Value")
    sparql"""$resource $linkPropValue $linkValue .
      $linkValue $kbValueHasUUID $linkValueUUID .
      $linkValue $kbHasPermissions $linkValuePerms ."""
  }
}
val linkWhereBlock = Fragment.join(linkWhereFragments)

val newLmd = Literal.typedEscaped("2024-01-02T00:00:00Z", xsd.unsafeIri("dateTime"))
val dataGraphIri = Literal.stringEscaped("http://www.knora.org/data/0001/project1")
val resourceIriLit = Literal.stringEscaped("http://rdfh.ch/0001/resource1")
```

### Template style

```scala
val query = sparql"""
  PREFIX $kb
  PREFIX $rdfs
  PREFIX $xsd

  DELETE {
    GRAPH $dataNamedGraph {
      $resource $kbLastMod $resourceLastMod .
      $linkDeleteBlock
    }
  }
  INSERT {
    GRAPH $dataNamedGraph {
      $resource $kbLastMod $newLmd .
      $linkInsertBlock
    }
  }
  WHERE {
    ${Fragments.bind(sparql"IRI($dataGraphIri)", dataNamedGraph)}
    ${Fragments.bind(sparql"IRI($resourceIriLit)", resource)}
    $resource a $resourceClass .
    $resource $kbIsDeleted false .
    $resourceClass ${PropertyPath.zeroOrMore(rdfsSubClassOf)} $kbResource .
    OPTIONAL { $resource $kbLastMod $resourceLastMod . }
    $linkWhereBlock
  }
""".render
```

### Builder style (individual fragments)

```scala
val query = Sparql.update
  .prefixes(kb, rdfs, xsd)
  .deleteFrom(dataNamedGraph)(
    sparql"$resource $kbLastMod $resourceLastMod .",
    linkDeleteBlock,
  )
  .insertInto(dataNamedGraph)(
    sparql"$resource $kbLastMod $newLmd .",
    linkInsertBlock,
  )
  .where(
    Fragments.bind(sparql"IRI($dataGraphIri)", dataNamedGraph),
    Fragments.bind(sparql"IRI($resourceIriLit)", resource),
    sparql"$resource a $resourceClass .",
    sparql"$resource $kbIsDeleted false .",
    sparql"$resourceClass ${PropertyPath.zeroOrMore(rdfsSubClassOf)} $kbResource .",
    Fragments.optional(sparql"$resource $kbLastMod $resourceLastMod ."),
    linkWhereBlock,
  )
  .render
```

### Builder style (multi-line fragment)

```scala
val query = Sparql.update
  .prefixes(kb, rdfs, xsd)
  .deleteFrom(dataNamedGraph)(sparql"""
    $resource $kbLastMod $resourceLastMod .
    $linkDeleteBlock""")
  .insertInto(dataNamedGraph)(sparql"""
    $resource $kbLastMod $newLmd .
    $linkInsertBlock""")
  .where(sparql"""
    ${Fragments.bind(sparql"IRI($dataGraphIri)", dataNamedGraph)}
    ${Fragments.bind(sparql"IRI($resourceIriLit)", resource)}
    $resource a $resourceClass .
    $resource $kbIsDeleted false .
    $resourceClass ${PropertyPath.zeroOrMore(rdfsSubClassOf)} $kbResource .
    ${Fragments.optional(sparql"$resource $kbLastMod $resourceLastMod .")}
    $linkWhereBlock""")
  .render
```

### Comparison

The real complexity lives in the shared fragment composition — three separate iteration blocks (DELETE, INSERT, WHERE) all keyed on the same `linkUpdates` list with different conditionals per clause. The two link updates produce visibly different patterns: update 0 has both `deleteDirectLink` and `linkValueExists`, so it appears in all three clauses; update 1 has neither, so it only appears in INSERT. This is where the library's `Fragment` composition replaces the Twirl `@for`/`@if` nesting. The `Fragments.bind` combinator and `PropertyPath.zeroOrMore` handle the WHERE clause validation patterns that the real query uses extensively.

---

## Benchmark 5: SearchQueries.selectCountByLabel (Lucene)

**What this tests**: A full-text search query using Jena's **vendor-specific `text#query` predicate** with Lucene, combined with **property paths** (`rdfs:subClassOf*`), aggregate expressions (`COUNT(DISTINCT ...)`), conditional filters, and FILTER NOT EXISTS. This is the hardest benchmark for the type system because both `text#query` and property paths fall outside standard SPARQL triple patterns.

**Key challenges**:
- Vendor extension: `?resource <text#query> (rdfs:label "search term")` — Jena-specific predicate with property list notation
- Property paths: `rdfs:subClassOf*` — the `*` (zero-or-more) operator on a predicate
- Aggregate expression in SELECT: `(COUNT(DISTINCT ?resource) AS ?count)`
- Conditional fragments: project filter and resource class filter are both optional
- PREFIX usage with prefixed names in the body

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

### Shared setup

```scala
val resource      = Variable("resource")
val resourceClass = Variable("resourceClass")
val count         = Variable("count")
val rdfsLabel     = rdfs.unsafeIri("label")
val kbAttachedToProject = kb.unsafeIri("attachedToProject")

val limitToProject: Option[Iri] = Some(Iri.unsafeFrom("http://rdfh.ch/projects/0001"))
val limitToResourceClass: Option[Iri] = None

// Conditional fragments — shared by all styles
val projectFilter: Fragment = Fragment.fromOption(limitToProject) { prj =>
  sparql"$resource $kbAttachedToProject $prj ."
}

val classFilter: Fragment = Fragment.fromOption(limitToResourceClass) { cls =>
  sparql"$resourceClass ${PropertyPath.zeroOrMore(rdfsSubClassOf)} $cls ."
}

// Lucene text#query — typed combinator instead of Fragment.raw
val textQueryLine: Fragment = Fragments.jenaTextQuery(
  subject = resource,
  predicate = rdfsLabel,
  query = Literal.stringEscaped("test search"),
)

// Property path for subclass traversal
val subClassLine: Fragment =
  sparql"$resourceClass ${PropertyPath.zeroOrMore(rdfsSubClassOf)} $kbResource ."
```

### Template style

```scala
val query = sparql"""
  PREFIX $rdfs
  PREFIX $kb

  SELECT (count(distinct $resource) as $count)
  WHERE {
    $textQueryLine ;
      a $resourceClass .
    $subClassLine
    $projectFilter
    $classFilter
    FILTER NOT EXISTS { $resource $kbIsDeleted true . }
  }
""".render
```

### Builder style (individual fragments)

```scala
val query = Sparql
  .select(sparql"(count(distinct $resource) as $count)")
  .prefixes(rdfs, kb)
  .where(
    sparql"$textQueryLine ; a $resourceClass .",
    subClassLine,
    projectFilter,
    classFilter,
    Fragments.filterNotExists(sparql"$resource $kbIsDeleted true ."),
  )
  .render
```

### Builder style (multi-line fragment)

```scala
val query = Sparql
  .select(sparql"(count(distinct $resource) as $count)")
  .prefixes(rdfs, kb)
  .where(sparql"""
    $textQueryLine ;
      a $resourceClass .
    $subClassLine
    $projectFilter
    $classFilter
    ${Fragments.filterNotExists(sparql"$resource $kbIsDeleted true .")}""")
  .render
```

### Comparison

With `PropertyPath` and `Fragments.jenaTextQuery`, this benchmark no longer requires `Fragment.raw(s"...")`. The template style reads most naturally — the SELECT expression, PREFIX declarations, and WHERE body all appear in their natural SPARQL positions. The builder's `.select(sparql"(count(...))")` is slightly awkward but workable.

### New types introduced

**`PropertyPath`** — represents SPARQL 1.1 property paths:
```scala
PropertyPath.zeroOrMore(iri)   // iri*   — zero or more
PropertyPath.oneOrMore(iri)    // iri+   — one or more
PropertyPath.sequence(a, b)    // a / b  — sequence
PropertyPath.alternative(a, b) // a | b  — alternative
PropertyPath.inverse(iri)      // ^iri   — inverse
```

`PropertyPath` implements `SparqlValue` so it can be interpolated in `sparql"..."`.

**`Fragments.jenaTextQuery`** — typed Lucene full-text search combinator:
```scala
Fragments.jenaTextQuery(
  subject: Variable,
  predicate: Iri,       // the RDF predicate to search (e.g., rdfs:label)
  query: Literal,       // the Lucene search string (escaped)
): Fragment
// Renders: ?subject <http://jena.apache.org/text#query> (predicate "query")
```

This encapsulates the vendor-specific `text#query` syntax, keeping the Jena dependency isolated to a single combinator.

---

## Benchmark 6: addValueVersion (polymorphic match + iteration)

**What this tests**: A simplified sketch of the 518-line Twirl template `addValueVersion.scala.txt` — the most complex SPARQL generation site in the codebase. It creates a new version of a value for a resource, handling **polymorphic value types** (TextValue shown here, but the real template has 13+ cases), **optional properties** (comment), **link value iteration with indexed variables**, and **GRAPH-scoped DELETE/INSERT**.

**Key challenges**:
- Polymorphic dispatch: `match` on value type determines which triple patterns to emit (TextValue, IntValue, LinkValue, etc.)
- Optional properties: `maybeComment` is `Option[String]` — when absent, the `valueHasComment` triple must be absent entirely
- Link value iteration: a `List[LinkValueUpdate]` produces variable-length INSERT patterns, each with its own IRI
- GRAPH scoping: both DELETE and INSERT are wrapped in `GRAPH <namedGraph> { ... }`
- PREFIX declarations with prefixed names in the body
- This is the Twirl template that most strongly motivates replacing the template engine — 518 lines of `@if`/`@for`/`@match` interleaved with SPARQL

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

### Shared setup

```scala
val resourceIri  = Iri.unsafeFrom("http://rdfh.ch/0001/resource1")
val newValueIri  = Iri.unsafeFrom("http://rdfh.ch/0001/resource1/values/newValue1")
val graphIri     = Iri.unsafeFrom("http://www.knora.org/data/0001/project1")
val resourceLastMod = Variable("resourceLastModificationDate")

val kbTextValue       = kb.unsafeIri("TextValue")
val kbLinkValue       = kb.unsafeIri("LinkValue")
val kbValueHasString  = kb.unsafeIri("valueHasString")
val kbValueHasComment = kb.unsafeIri("valueHasComment")
val kbValueHasUUID    = kb.unsafeIri("valueHasUUID")
val kbHasPermissions  = kb.unsafeIri("hasPermissions")
val kbValueHasRefCount = kb.unsafeIri("valueHasRefCount")

val maybeComment: Option[String] = Some("Updated value")

// Polymorphic value type — compose Fragment from match
val commentFragment: Fragment = Fragment.fromOption(maybeComment) { c =>
  sparql"$kbValueHasComment ${Literal.stringEscaped(c)} ;"
}

val valueTypeFragment = sparql"""$newValueIri a $kbTextValue ;
      $kbValueHasString ${Literal.stringEscaped("Hello world")} ;
      $commentFragment
      $kbValueHasUUID ${Literal.stringEscaped("uuid-new-value-1")} ;
      $kbHasPermissions ${Literal.stringEscaped("CR knora-admin:ProjectAdmin")} ."""

// Link value iteration
case class LinkValueUpdate(iri: String, refCount: Int, permissions: String)
val linkUpdates = List(LinkValueUpdate("http://example.org/newLinkValue1", 2, "CR knora-admin:ProjectAdmin"))

val linkValueFragments: List[Fragment] = linkUpdates.map { update =>
  val lvIri = Iri.unsafeFrom(update.iri)
  sparql"""$lvIri a $kbLinkValue ;
      $kbValueHasRefCount ${Literal.int(update.refCount)} ;
      $kbHasPermissions ${Literal.stringEscaped(update.permissions)} ."""
}

val linkValueBlock = Fragment.join(linkValueFragments)

val newLmdValue = Literal.typedEscaped("2024-01-02T00:00:00Z", xsd.unsafeIri("dateTime"))
```

### Template style

```scala
val query = sparql"""
  PREFIX $kb
  PREFIX $xsd

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

### Builder style (individual fragments)

```scala
val query = Sparql.update
  .prefixes(kb, xsd)
  .deleteFrom(graphIri)(sparql"$resourceIri $kbLastMod $resourceLastMod .")
  .insertInto(graphIri)(
    valueTypeFragment,
    linkValueBlock,
    sparql"$resourceIri $kbLastMod $newLmdValue .",
  )
  .where(sparql"$resourceIri $kbLastMod $resourceLastMod .")
  .render
```

### Builder style (multi-line fragments)

```scala
val query = Sparql.update
  .prefixes(kb, xsd)
  .deleteFrom(graphIri)(sparql"$resourceIri $kbLastMod $resourceLastMod .")
  .insertInto(graphIri)(sparql"""
    $valueTypeFragment
    $linkValueBlock
    $resourceIri $kbLastMod $newLmdValue .""")
  .where(sparql"$resourceIri $kbLastMod $resourceLastMod .")
  .render
```

### Comparison

Template is clearly the most readable — the full GRAPH/DELETE/INSERT/WHERE structure is visually obvious. The builder variants are nearly identical here because the real complexity lives in the shared fragment composition (polymorphic value type dispatch, optional comment, link value iteration). The builder with individual fragments explicitly separates the three insert components; the multi-line variant keeps them as one visual block. All three styles benefit equally from the `Fragment` composition model — this is where the library earns its keep, replacing 518 lines of Twirl `@if`/`@for`/`@match` with typed Scala.

---

## Notes

### Core design

- **One foundation, two surfaces**: Both styles use `Fragment`, `sparql"..."`, `Iri`, `Variable`, `Literal`, `Prefix`. The builder is a thin convenience layer over fragment composition, not a separate paradigm.
- **Builder slots accept `Fragment`**: `Sparql.select(...)`, `.where(...)`, `.delete(...)`, `.insert(...)` all take `Fragment` or `Fragment*`. No typed AST nodes needed.
- **GRAPH scope via method name**: `deleteFrom(graph)(...)` / `insertInto(graph)(...)` instead of a separate `.graph()` call. This avoids ambiguity about which clause the GRAPH applies to, and supports DELETE and INSERT targeting different named graphs. Plain `.delete(...)` / `.insert(...)` are used when no GRAPH wrapper is needed.
- **`Fragment.fromOption`**: `Fragment.fromOption(opt)(f)` replaces the common `opt.fold(Fragment.empty)(f)` pattern for conditional fragments.
- **No forced choice**: Developers pick per-query. Simple queries → template. Programmatic construction → builder. Both can coexist in the same file.

### Template style strengths

- **Best readability**: Reads like raw SPARQL with typed holes. Developers can visually verify structure.
- **Doobie/Skunk alignment**: This IS the Doobie pattern — whole queries in a single interpolation.
- **PREFIX handling**: `sparql"PREFIX $kb"` reads naturally in the template.
- **Complex structure**: DELETE/INSERT/WHERE, GRAPH blocks, UNION — all visually obvious.

### Builder style strengths

- **Named slots**: `.select()`, `.where()`, `.orderBy()`, `.limit()` make structure explicit for readers unfamiliar with SPARQL.
- **Programmatic construction**: When the query shape varies (e.g., conditional SELECT vs COUNT, optional ORDER BY), the builder avoids awkward template-level conditionals.
- **Combinators**: `Fragments.optional(...)`, `Fragments.union(...)`, `Fragments.filterNotExists(...)` read well inside `.where()`.
- **Familiar pattern**: Similar to JOOQ, Slick, Quill — developers who know SQL builders will recognize this.

### When to use which

| Scenario | Recommended | Why |
|----------|------------|-----|
| Simple/medium static queries | Template | Reads like SPARQL |
| Complex multi-clause queries (DELETE/INSERT/WHERE) | Template | Visual structure |
| Dynamic WHERE with static SELECT | Either | Builder's `.where(dynamicFragment)` or template with `$dynamicFragment` |
| Query shape varies at runtime | Builder | Conditionally add `.orderBy()`, `.limit()`, etc. |
| Programmatic construction from config | Builder | No template to write |
| One-off migration of Twirl template | Template | Closest to the original Twirl structure |

### Possible extension: Fragment chaining

Inspired by C-variant's `.and()` idea, `Fragment` could optionally support chaining via extension methods:

```scala
sparql"$s a $resourceClass ."
  .and(sparql"$s $kbIsDeleted false .")
  .andOptional(sparql"$s $kbLastMod $lmd .")
  .andAll(conditionalFragment)
```

This is just sugar over `++` / `combineAll` and can be added later if the chaining style proves ergonomic. Not required for the core design.

### Type construction: safe/unsafe convention

All types follow the codebase's `from`/`unsafeFrom` convention:

| Type | Safe | Unsafe |
|------|------|--------|
| `Iri` | `Iri.from(value): Either[String, Iri]` | `Iri.unsafeFrom(value): Iri` |
| `Variable` | `Variable.from(name): Either[String, Variable]` | `Variable.unsafeFrom(name): Variable` |
| `Prefix` | `Prefix.from(name, namespace): Either[String, Prefix]` | `Prefix.unsafeFrom(name, namespace): Prefix` |
| `LanguageTag` | `LanguageTag.from(tag): Either[String, LanguageTag]` | `LanguageTag.unsafeFrom(tag): LanguageTag` |

`Prefix` also derives IRIs: `prefix.iri("localName"): Either[String, Iri]` / `prefix.unsafeIri("localName"): Iri`.

### Literal API

Literals split into two safety models:

**Type-safe** — Scala types guarantee safety, no escaping needed:
```scala
Literal.bool(true)                  // → true
Literal.int(42)                     // → 42
Literal.long(42L)                   // → 42
Literal.double(3.14)                // → 3.14e0
Literal.decimal(BigDecimal("3.14")) // → 3.14
Literal.instant(instant)            // → "2024-01-01T00:00:00Z"^^xsd:dateTime
```

**String-based** — escaping is the safety boundary:
```scala
Literal.stringEscaped("hello")                        // → "hello"
Literal.unsafeStringUnescaped("hello")                // → "hello" — no escaping!
Literal.langStringEscaped("hello", lang)              // → "hello"@en
Literal.unsafeLangStringUnescaped("hello", lang)      // → "hello"@en — no escaping!
Literal.typedEscaped("2024-01-01", xsdDate)           // → "2024-01-01"^^<...>
Literal.unsafeTypedUnescaped("2024-01-01", xsdDate)   // → "2024-01-01"^^<...> — no escaping!
```

The `escaped` variants always succeed (any string can be escaped). The `unsafeUnescaped` variants pass content through raw — the caller is responsible for safety.

### LanguageTag type

RDF language tags follow BCP 47 (`en`, `de-CH`, `pt-BR`, etc.). The library defines its own `LanguageTag` opaque type (zero coupling to dsp-api's domain-specific `LanguageCode` enum which is restricted to `de`, `en`, `fr`, `it`, `rm` for ontology metadata).

```scala
opaque type LanguageTag = String
object LanguageTag:
  def from(tag: String): Either[String, LanguageTag]  // validates BCP 47 format
  def unsafeFrom(tag: String): LanguageTag             // no validation
```

The adapter layer converts: `extension (lc: LanguageCode) def toLanguageTag: LanguageTag = LanguageTag.unsafeFrom(lc.value)`.

### Interpolator alias

`sp"..."` is a short alias for `sparql"..."`. Both return `Fragment`. Use whichever reads better in context — `sparql"""..."""` for multi-line templates, `sp"..."` for inline fragments.

### Known issues (shared by both styles)

- **`Fragment.raw(s"...")`**: ~~Still needed for Lucene `text#query` and `subClassOf*` property paths.~~ Resolved: `PropertyPath` type (Benchmark 5) and `Fragments.jenaTextQuery` combinator replace the two main `Fragment.raw` use cases. `Fragment.raw` remains available as an escape hatch but should be rare.
- **Whitespace with `Fragment.empty`**: When a conditional is absent, surrounding whitespace remains. Needs normalization.
- **`Fragment.empty` vs `Fragment.raw("")`**: Structurally different but semantically identical. Needs equality fix.
- **Prefix deduplication** missing.

### Implementation note (Approach D)

The escaping backend is an independent choice. The current prototype uses custom escaping; RDF4J's `Rdf.literalOf().getQueryString()` is a drop-in replacement with better coverage (handles `\f`, `\b`, single quotes). The API surface is identical either way.

### Open design questions

- **Error handling**: The safe constructors (`Iri.from`, `Variable.from`, etc.) return `Either[String, T]`. Should there be a cohesive error type (e.g., `SparqlBuilderError`) instead of plain strings? Should errors compose (e.g., building a fragment from multiple potentially-invalid values)? This needs a dedicated design discussion.

---

## Design Review Feedback

This is a unified approach combining the strengths of former Approaches A and H. See the individual approach documents (now in `eliminated/`) for their separate review histories.

### Status: Primary contender

The Interpolated Template approach is the primary candidate for Step 4 validation. The template style aligns with Doobie/Skunk industry patterns. The builder provides a familiar alternative for programmatic construction. Both share the same `Fragment` foundation.
