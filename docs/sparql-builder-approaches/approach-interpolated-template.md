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
val linkValueDeleteFragment: Fragment = linkValuePropertyIri.fold(Fragment.empty) { lvpIri =>
  sparql"$lvpIri $linkValuePropertyPred $linkValuePropertyObj ."
}
val linkValueWhereFragment: Fragment = linkValuePropertyIri.fold(Fragment.empty) { lvpIri =>
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
  .delete(
    sparql"$ontologyIri $kbLastMod $lmdValue .",
    sparql"$propertyIri $propertyPred $propertyObj .",
    linkValueDeleteFragment,
  )
  .graph(ontologyIri)
  .insert(sparql"$ontologyIri $kbLastMod $newLmd .")
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
  .delete(sparql"""
    $ontologyIri $kbLastMod $lmdValue .
    $propertyIri $propertyPred $propertyObj .
    $linkValueDeleteFragment""")
  .graph(ontologyIri)
  .insert(sparql"$ontologyIri $kbLastMod $newLmd .")
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

Template clearly wins for readability — the DELETE/INSERT/WHERE structure is visually obvious. Builder with individual fragments gives maximum structure but requires understanding that `.delete()`, `.graph()`, `.insert()`, `.where()` produce the right nesting. Builder with multi-line fragments is a good middle ground. All three use the same fragment composition for the conditional link value.

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

### Shared setup

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

// Iteration + conditionals — shared by both styles
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
```

### Template style

```scala
val query = sparql"""
  DELETE {
    $resource $kbLastMod $resourceLastMod .
    $linkDeleteBlock
  }
  WHERE {
    $resource a $kbResource .
  }
""".render
```

### Builder style

```scala
val query = Sparql.update
  .delete(
    sparql"$resource $kbLastMod $resourceLastMod .",
    linkDeleteBlock,
  )
  .where(sparql"$resource a $kbResource .")
  .render
```

### Comparison

The real work is in the shared fragment composition — both styles just embed the result. Template shows the DELETE/WHERE structure; builder is marginally more compact. Either works well.

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

### Shared setup

```scala
val resource      = Variable("resource")
val resourceClass = Variable("resourceClass")
val count         = Variable("count")
val textQueryPred = Iri.trusted("http://jena.apache.org/text#query")
val rdfsLabel     = Iri.trusted(rdfs + "label")

val limitToProject: Option[Iri] = Some(Iri.trusted("http://rdfh.ch/projects/0001"))
val limitToResourceClass: Option[Iri] = None

// Conditional fragments — shared by both styles
val projectFilter: Fragment = limitToProject.fold(Fragment.empty) { prj =>
  sparql"$resource ${Iri.trusted(knoraBase + "attachedToProject")} $prj ."
}

val classFilter: Fragment = limitToResourceClass.fold(Fragment.empty) { cls =>
  Fragment.raw(s"${resourceClass.render} <${rdfs}subClassOf*> ${cls.render} .")
}

// Lucene text#query — needs Fragment.raw for property list notation
val textQueryLine = Fragment.raw(
  s"${resource.render} ${textQueryPred.render} (${rdfsLabel.render} ${Literal.string("test search").render}) ;" +
  s"\n    a ${resourceClass.render} ."
)

val subClassLine = Fragment.raw(
  s"${resourceClass.render} <${rdfs}subClassOf*> <${knoraBase}Resource> ."
)
```

### Template style

```scala
val query = sparql"""
  PREFIX $pRdfs
  PREFIX $kb

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

### Builder style

```scala
val query = Sparql
  .select()
  .prefixes("rdfs" -> rdfs, "knora-base" -> knoraBase)
  .withExpr(sparql"(count(distinct $resource) as $count)")
  .where(
    textQueryLine,
    subClassLine,
    projectFilter,
    classFilter,
    Fragments.filterNotExists(sparql"$resource $kbIsDeleted true ."),
  )
  .render
```

### Comparison

Both degrade for this benchmark due to Lucene `text#query` and property paths requiring `Fragment.raw(...)`. The template keeps the visual structure clearer. The builder's `.withExpr()` is awkward — the template's inline `SELECT (count(...) as ...)` reads more naturally.

**Known issue**: `Fragment.raw(s"...")` for Lucene and property paths is the weakest point of the entire design. A future `PropertyPath` type and Lucene combinator would address this.

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

### Shared setup

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
```

### Template style

```scala
val query = sparql"""
  PREFIX $kb
  PREFIX $pXsd

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

### Builder style

```scala
val insertClause = Fragment.join(List(
  valueTypeFragment,
  linkValueBlock,
  sparql"$resourceIri $kbLastMod $newLmdValue .",
), Fragment.raw("\n"))

val query = Sparql.update
  .prefixes("knora-base" -> knoraBase, "xsd" -> xsd)
  .delete(sparql"$resourceIri $kbLastMod $resourceLastMod .")
  .graph(graphIri)
  .insert(insertClause)
  .where(sparql"$resourceIri $kbLastMod $resourceLastMod .")
  .render
```

### Comparison

Template is clearly more readable — you see the full GRAPH/DELETE/INSERT/WHERE structure. Builder requires pre-composing the insert clause into a single `Fragment` to pass to `.insert()`. Both share the same fragment composition for the polymorphic value type and link value iteration.

---

## Notes

### Core design

- **One foundation, two surfaces**: Both styles use `Fragment`, `sparql"..."`, `Iri`, `Variable`, `Literal`, `Prefix`. The builder is a thin convenience layer over fragment composition, not a separate paradigm.
- **Builder slots accept `Fragment`**: `Sparql.select(...)`, `.where(...)`, `.delete(...)`, `.insert(...)` all take `Fragment` or `Fragment*`. No typed AST nodes needed.
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

- **`Fragment.raw(s"...")`**: Still needed for Lucene `text#query` and `subClassOf*` property paths. A `PropertyPath` type and Lucene combinator would address this.
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
