<!---
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# SPARQL Query Design

## Inference

DSP-API does not require the triplestore to perform inference, as different triplestores implement inference quite differently, so that taking advantage of inference would require triplestore specific code, which is not well maintainable. Instead, the API simulates inference for each Gravsearch query, so that the expected results are returned.

Gravsearch queries currently need to do the following:

- Given a base property, find triples using a subproperty as predicate, and
  return the subproperty used in each case.
- Given a base class, find triples using an instance of subclass as subject or
  object, and return the subclass used in each case.

Without inference, this can be done using property path syntax.

```sparql
CONSTRUCT {
  ?resource a ?resourceClass .
  ?resource ?resourceValueProperty ?valueObject.
WHERE {
  ?resource a ?resourceClass .
  ?resourceType rdfs:subClassOf* knora-base:Resource .
  ?resource ?resourceValueProperty ?valueObject .
  ?resourceValueProperty rdfs:subPropertyOf* knora-base:hasValue .
```

This query:

- Checks that the queried resource belongs to a subclass of `knora-base:Resource`.

- Returns the class that the resource explicitly belongs to.

- Finds the Knora values attached to the resource, and returns each value along with
  the property that explicitly attaches it to the resource.
  
However, such a query is very inefficient. Instead, the API does inference on the query, so that the relevant information can be found in a timely manner.

For this, the query is analyzed to check which project ontologies are relevant to the query. If an ontology is not relevant to a query, then all class and property definitions of this ontology are disregarded for inference.

Then, each statement that requires inference (i.e. that could be phrased with property path syntax, as described above) is cross-referenced with the relevant ontologies, to see which property/class definitions would fit the statement according to the rules of RDF inference. And each of those definitions is added to the query as a separate `UNION` statement.

E.g.: Given the resource class `B` is a subclass of `A` and the property `hasY` is a subproperty of `hasX`, then the following query

```sparql
SELECT {
  ?res ?prop .
} WHERE {
  ?res a <A> .
  ?res <hasX> ?prop .
}
```

can be rewritten as

```sparql
SELECT {
  ?res ?prop .
} WHERE {
  {?res a <A>} UNION {?res a <B>} .
  {?res <hasX> ?prop} UNION {?res <hasY> ?prop} .
}

```


## Querying Past Value Versions

Value versions are a linked list, starting with the current version. Each value points to
the previous version via `knora-base:previousValue`. The resource points only to the current
version.

Past value versions are queried in `getResourcePropertiesAndValues.scala.txt`, which can
take a timestamp argument. Given the current value version, we must find the most recent
past version that existed at the target date.

First, we get the set of previous values that were created on or before the target
date:

```
?currentValue knora-base:previousValue* ?valueObject .
?valueObject knora-base:valueCreationDate ?valueObjectCreationDate .
FILTER(?valueObjectCreationDate <= "@versionDate"^^xsd:dateTime)
```

The resulting versions are now possible values of `?valueObject`. Next, out of this set
of versions, we exclude all versions except for the most recent one. We do this by checking,
for each `?valueObject`, whether there is another version, `?otherValueObject`, that is more
recent and was also created before the target date. If such a version exists, we exclude
the one we are looking at.

```
FILTER NOT EXISTS {
    ?currentValue knora-base:previousValue* ?otherValueObject .
    ?otherValueObject knora-base:valueCreationDate ?otherValueObjectCreationDate .

    FILTER(
        (?otherValueObjectCreationDate <= "@versionDate"^^xsd:dateTime) &&
        (?otherValueObjectCreationDate > ?valueObjectCreationDate)
    )
}
```

This excludes all past versions except the one we are interested in.
