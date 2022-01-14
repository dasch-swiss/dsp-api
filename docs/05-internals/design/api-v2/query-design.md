<!---
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# SPARQL Query Design

## Inference

Knora does not require the triplestore to perform inference, but may be able
to take advantage of inference if the triplestore provides it.

In particular, Knora's SPARQL queries currently need to do the following:

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
  
In some triplestores, it can be more efficient to use RDFS inference than to use property path syntax,
depending on how inference is implemented. For example, Ontotext GraphDB does inference when
data is inserted, and stores inferred triples in the repository
([forward chaining with full materialisation](http://graphdb.ontotext.com/documentation/standard/reasoning.html)).
Moreover, it provides a way of choosing whether to return explicit or inferred triples.
This allows the query above to be optimised as follows, querying inferred triples but returning
explicit triples:

```sparql
CONSTRUCT {
  ?resource a ?resourceClass .
  ?resource ?resourceValueProperty ?valueObject.
WHERE {
  ?resource a knora-base:Resource . # inferred triple

  GRAPH <http://www.ontotext.com/explicit> {
    ?resource a ?resourceClass .  # explicit triple
  }

  ?resource knora-base:hasValue ?valueObject . # inferred triple

  GRAPH <http://www.ontotext.com/explicit> {
    ?resource ?resourceValueProperty ?valueObject . # explicit triple
  }
```

By querying inferred triples that are already stored in the repository, the optimised query avoids property path
syntax and is therefore more efficient, while still only returning explicit triples in the query result.

Other triplestores use a backward-chaining inference strategy, meaning that inference is performed during
the execution of a SPARQL query, by expanding the query itself. The expanded query is likely to look like
the first example, using property path syntax, and therefore it is not likely to be more efficient. Moreover,
other triplestores may not provide a way to return explicit rather than inferred triples. To support such
a triplestore, Knora uses property path syntax rather than inference.
See [the Gravsearch design documentation](gravsearch.md#inference) for information on how this is done
for Gravsearch queries.

The support for Apache Jena Fuseki currently works in this way. However, Fuseki supports both forward-chaining
and backward-chaining rule engines, although it does not seem to have anything like
GraphDB's `<http://www.ontotext.com/explicit>`. It would be worth exploring whether Knora's query result
processing could be changed so that it could use forward-chaining inference as an optimisation, even if
nothing like `<http://www.ontotext.com/explicit>` is available. For example, the example query= could be written like
this:

```sparql
CONSTRUCT {
  ?resource a ?resourceClass .
  ?resource ?resourceValueProperty ?valueObject .
WHERE {
  ?resource a knora-base:Resource .
  ?resource a ?resourceClass .
  ?resource knora-base:hasValue ?valueObject .
  ?resource ?resourceValueProperty ?valueObject .
```

This would return inferred triples as well as explicit ones: a triple for each base class of the explicit
`?resourceClass`, and a triple for each base property of the explicit `?resourceValueProperty`. But since Knora knows
the class and property inheritance hierarchies, it could ignore the additional triples.

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
