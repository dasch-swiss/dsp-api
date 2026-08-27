# Reference SPARQL for Benchmark Queries

This document defines the **exact parameter values** and **target SPARQL** for each of the 6 benchmark queries. All approach showcase documents reference these as the "plain SPARQL for comparison."

## Shared Vocabulary

All approaches use the following IRIs and variables. In the approach documents, these are assumed to be pre-defined.

```
knoraBase      = "http://www.knora.org/ontology/knora-base#"
rdf            = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
rdfs           = "http://www.w3.org/2000/01/rdf-schema#"
xsd            = "http://www.w3.org/2001/XMLSchema#"
owl            = "http://www.w3.org/2002/07/owl#"
salsahGui      = "http://www.knora.org/ontology/salsah-gui#"
```

---

## Benchmark 1: Simple SELECT with OPTIONAL

**Scenario**: Find all triples about non-deleted resources of a given class, with optional last modification date.

**Parameters**:
- `resourceClass` = `http://example.org/MyClass`
- `ORDER BY DESC` on last modification date
- `LIMIT 25`

### Target SPARQL

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

---

## Benchmark 2: IsNodeUsedQuery (ASK with UNION)

**Scenario**: Check if a list node is referenced anywhere — either as a GUI attribute value or as a list node value.

**Parameters**:
- `nodeIri` = `http://rdfh.ch/lists/0001/treeList01`

### Target SPARQL

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

---

## Benchmark 3: DeletePropertyQuery (DELETE/INSERT WHERE with conditional patterns)

**Scenario**: Delete a property from an ontology, including its optional link value property. Update the ontology's last modification date. Guard with FILTER NOT EXISTS to ensure the property is not used.

**Parameters**:
- `ontologyIri` = `http://www.knora.org/ontology/0001/anything`
- `propertyIri` = `http://www.knora.org/ontology/0001/anything#hasOtherThing`
- `linkValuePropertyIri` = `Some(http://www.knora.org/ontology/0001/anything#hasOtherThingValue)` (present in this scenario)
- `oldLastModDate` = `2024-01-01T00:00:00Z`
- `newLastModDate` = `2024-01-02T00:00:00Z`

### Target SPARQL

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

---

## Benchmark 4: InsertValueQueryBuilder (conditional + iteration with indexed variables)

**Scenario**: Insert a new value for a resource, handling link updates. One link update has both `deleteDirectLink = true` and `linkValueExists = true`.

**Parameters**:
- `resource` = `?resource` (variable)
- One link update:
  - `linkPropertyIri` = `http://example.org/hasLink`
  - `linkTargetIri` = `http://example.org/target1`
  - `deleteDirectLink` = `true`
  - `linkValueExists` = `true`
  - `newLinkValueIri` = `http://example.org/newLinkValue1`
  - `newReferenceCount` = `1`

### Target SPARQL

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

---

## Benchmark 5: SearchQueries.selectCountByLabel (Lucene + conditional filters)

**Scenario**: Count distinct resources matching a full-text search, filtered by project. No resource class filter in this scenario.

**Parameters**:
- `luceneQuery` = `test search`
- `limitToProject` = `Some(http://rdfh.ch/projects/0001)` (present)
- `limitToResourceClass` = `None` (absent)

### Target SPARQL

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

---

## Benchmark 6: addValueVersion (polymorphic match + iteration with indexed variables)

**Scenario**: A simplified sketch of the 518-line Twirl template. Creates a new version of a TextValue with an optional comment, handling one link update with indexed variables.

**Parameters**:
- `resource` = `http://rdfh.ch/0001/resource1`
- `newValueIri` = `http://rdfh.ch/0001/resource1/values/newValue1`
- `valueType` = TextValue (one case of the polymorphic match)
- `textValue` = `Hello world`
- `maybeComment` = `Some("Updated value")`
- One link update:
  - `linkPropertyIri` = `http://example.org/hasLink`
  - `linkTargetIri` = `http://example.org/target1`
  - `newLinkValueIri` = `http://example.org/newLinkValue1`
  - `newReferenceCount` = `2`
  - `newPermissions` = `CR knora-admin:ProjectAdmin`

### Target SPARQL

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

Note: This is a **simplified sketch** of the real `addValueVersion.scala.txt` template (which is 518 lines with 13+ value type cases). It demonstrates the key challenges: polymorphic value type handling, optional comment, link value iteration with indexed variables, and GRAPH-scoped DELETE/INSERT.
