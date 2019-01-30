<!---
Copyright © 2015-2019 the contributors (see Contributors.md).

This file is part of Knora.

Knora is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Knora is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public
License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
-->

# Gravsearch Design

@@toc

## Gravsearch Package

The classes that process Gravsearch queries and results can be found in `org.knora.webapi.responders.v2.search.gravsearch`.

## Type Inspection

The code that converts Gravserch queries into SPARQL queries, and processes the query results, needs to know the
types of the entities that are used in the input query. As explained in
@ref:[Type Inference](../../../03-apis/api-v2/query-language.md#type-inference), these types can be inferred,
or they can be specified in the query using type annotations.

Type inspection is implemented in the package `org.knora.webapi.responders.v2.search.gravsearch.types`.
The entry point to this package is `GravsearchTypeInspectionRunner`, which is instantiated by `SearchResponderV2`.
The result of type inspection is a `GravsearchTypeInspectionResult`, in which each typeable entity in the input query is
associated with a `GravsearchEntityTypeInfo`, which can be either:

- A `PropertyTypeInfo`, which specifies the type of object that a property is expected to have.
- A `NonPropertyTypeInfo`, which specifies the type of a variable, or the type of an IRI representing a resource or value.

### Identifying Typeable Entities

After parsing a Gravsearch query, `SearchResponderV2` calls `GravsearchTypeInspectionRunner.inspectTypes`, passing
the WHERE clause of the input query. This method first identifies the entities whose types need to be determined. Each
of these entities is represented as a `TypeableEntity`. To do this, `GravsearchTypeInspectionRunner` uses `QueryTraverser`
to traverse the WHERE clause, collecting typeable entities in a visitor called `TypeableEntityCollectingWhereVisitor`.
The entities that are considered to need type information are:

- All variables.
- All IRIs except for those that represent type annotations or types.

### The Type Inspection Pipeline

`GravsearchTypeInspectionRunner` contains a pipeline of type inspectors, each of which extends `GravsearchTypeInspector`.
There are two type inspectors in the pipeline:

- `AnnotationReadingGravsearchTypeInspector`: reads
   @ref:[type annotations](../../../03-apis/api-v2/query-language.md#type-annotations) included in a Gravsearch query.
- `InferringGravsearchTypeInspector`: infers the types of entities from the context in which they are used, as well
  as from ontology information that it requests from `OntologyResponderV2`.

Each type inspector takes as input, and returns as output, an `IntermediateTypeInspectionResult`, which
associates each `TypeableEntity` with zero or more types. Initially, each `TypeableEntity` has no types. Each type inspector
adds whatever types it finds for each entity. At the end of the pipeline, each entity should
have exactly one type. If not, that's an error, with two possible causes:

- An entity's type could not be determined. The client must add a type annotation to make the query work.
- An entity appears to have more than one type, because the query used the entity inconsistently.

If there are no errors, `GravsearchTypeInspectionRunner` converts the pipeline's output to a
`GravsearchTypeInspectionResult`, in which each entity is associated with exactly one type.

#### AnnotationReadingGravsearchTypeInspector

This inspector uses `QueryTraverser` to traverse the WHERE clause, collecting type annotations in a visitor called
`AnnotationCollectingWhereVisitor`. It then converts each annotation to a `GravsearchEntityTypeInfo`.

#### InferringGravsearchTypeInspector

This inspector first uses `QueryTraverser` to traverse the WHERE clause, assembling an index of
usage information about typeable entities in a visitor called `UsageIndexCollectingWhereVisitor`. The `UsageIndex` contains,
for example, an index of all the entities that are used as subjects, predicates, or objects, along with the
statements in which they are used. It also contains sets of all the Knora class and property IRIs
that are used in the WHERE clause. `InferringGravsearchTypeInspector` then asks `OntologyResponderV2` for information
about those classes and properties, as well as about the classes that are subject types or object types of those properties.

Next, the inspector runs inference rules (which extend `InferenceRule`) on each `TypeableEntity`. Each rule
takes as input a `TypeableEntity`, the usage index, the ontology information, and the `IntermediateTypeInspectionResult`,
and returns a new `IntermediateTypeInspectionResult`. For example, `TypeOfObjectFromPropertyRule` infers an entity's type
if the entity is used as the object of a statement and the predicate's `knora-api:objectType` is known.

The inference rules are run repeatedly, because the output of one rule may allow another rule to infer additional
information. There are two pipelines of rules: a pipeline for the first iteration of type inference, and a
pipeline for subsequent iterations. This is because some rules can return additional information if they are run
more than once on the same entity, while others cannot.

The number of iterations is limited to `InferringGravsearchTypeInspector.MAX_ITERATIONS`, but in practice
two iterations are sufficient for most realistic queries, and it is difficult to design a query that requires more than
six iterations.

## Transformation of a Gravsearch Query

A Gravsearch query submitted by the client is parsed by `GravsearchParser` and preprocessed by `GravsearchTypeInspector`
to get type information about the elements used in the query (resources, values, properties etc.)
and do some basic sanity checks.

In `SearchResponderV2`, two queries are generated from a given Gravsearch query: a prequery and a main query.

### Query Transformers

The Gravsearch query is passed to `QueryTraverser` along with a query transformer. Query transformers are classes
that implement traits supported by `QueryTraverser`:

- `WhereTransformer`: instructions how to convert statements in the Where clause of a SPARQL query (to generate the prequery's Where clause).
- `ConstructToSelectTransformer` (extends `WhereTransformer`): instructions how to turn a Construct query into a Select query (converts a Gravsearch query into a prequery)
- `SelectToSelectTransformer` (extends `WhereTransformer`): instructions how to turn a triplestore independent Select query into a triplestore dependent Select query (implementation of inference).    
- `ConstructToConstructTransformer` (extends `WhereTransformer`): instructions how to turn a triplestore independent Construct query into a triplestore dependent Construct query (implementation of inference).

The traits listed above define methods that are implemented in the transformer classes and called by `QueryTraverser` to perform SPARQL to SPARQL conversions.
When iterating over the statements of the input query, the transformer class's transformation methods are called to perform the conversion.

### Prequery

The purpose of the prequery is to get an ordered collection of results representing only the IRIs of one page of matching resources and values.
Sort criteria can be submitted by the user, but the result is always deterministic also without sort criteria.
This is necessary to support paging.
A prequery is a SPARQL SELECT query.

The classes involved in generating prequeries can be found in `org.knora.webapi.responders.v2.search.gravsearch.prequery`.

If the client submits a count query, the prequery returns the overall number of hits, but not the results themselves.

In a first step, the Gravsearch query's WHERE clause is transformed and the prequery (SELECT and WHERE clause) is generated from this result.
The transformation of the Gravsearch query's WHERE clause relies on the implementation of the abstract class `AbstractPrequeryGenerator`.

`AbstractPrequeryGenerator` contains members whose state is changed during the iteration over the statements of the input query.
They can then by used to create the converted query.

-  `mainResourceVariable: Option[QueryVariable]`: SPARQL variable representing the main resource of the input query. Present in the prequery's SELECT clause.
- `dependentResourceVariables: mutable.Set[QueryVariable]`: a set of SPARQL variables representing dependent resources in the input query. Used in an aggregation function in the prequery's SELECT clause (see below).
- `dependentResourceVariablesGroupConcat: Set[QueryVariable]`: a set of SPARQL variables representing an aggregation of dependent resources. Present in the prequery's SELECT clause.
- `valueObjectVariables: mutable.Set[QueryVariable]`: a set of SPARQL variables representing value objects. Used in an aggregation function in the prequery's SELECT clause (see below).
- `valueObjectVarsGroupConcat: Set[QueryVariable]`: a set of SPARQL variables representing an aggregation of value objects. Present in the prequery's SELECT clause.

The variables mentioned above are present in the prequery's result rows because they are part of the prequery's SELECT clause.

The following example illustrates the handling of variables.
The following Gravsearch query looks for pages with a sequence number of 10 that are part of a book:

```sparql
PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>

    CONSTRUCT {
        ?page knora-api:isMainResource true .

        ?page knora-api:isPartOf ?book .

        ?page incunabula:seqnum ?seqnum .
    } WHERE {

        ?page a incunabula:page .

        ?page knora-api:isPartOf ?book .

        ?book a incunabula:book .

        ?page incunabula:seqnum ?seqnum .

        FILTER(?seqnum = 10)

    }
```

The prequery's SELECT clause is built using the member variables defined in `AbstractPrequeryGenerator`.
State of member variables after transformation of the input query into the prequery:

- `mainResourceVariable`: `QueryVariable(page)`
- `dependentResourceVariables`: `Set(QueryVariable(book))`
- `dependentResourceVariablesGroupConcat`: `Set(QueryVariable(book__Concat))`
- `valueObjectVariables`: `Set(QueryVariable(book__LinkValue), QueryVariable(seqnum))`: `?book` represents the dependent resource and `?book__LinkValue` the link value connecting `?page` and `?book`.
- `valueObjectVariablesGroupConcat`: `Set(QueryVariable(seqnum__Concat), QueryVariable(book__LinkValue__Concat))`

The resulting SELECT clause of the prequery looks as follows:

```sparql
SELECT DISTINCT
    ?page
    (GROUP_CONCAT(DISTINCT(?book); SEPARATOR='') AS ?book__Concat)
    (GROUP_CONCAT(DISTINCT(?seqnum); SEPARATOR='') AS ?seqnum__Concat)
    (GROUP_CONCAT(DISTINCT(?book__LinkValue); SEPARATOR='') AS ?book__LinkValue__Concat)
    WHERE {...}
    GROUP BY ?page
    ORDER BY ASC(?page)
    LIMIT 25
```
`?page` represents the main resource. When accessing the prequery's result rows, `?page` contains the Iri of the main resource.
The prequery's results are grouped by the main resource so that there is exactly one result row per matching main resource.
`?page` is also used as a sort criterion although none has been defined in the input query.
This is necessary to make paging work: results always have to be returned in the same order (the prequery is always deterministic).
Like this, results can be fetched page by page using LIMIT and OFFSET.

Grouping by main resource requires other results to be aggregated using the function `GROUP_CONCAT`.
`?book` is used as an argument of the aggregation function.
The aggregation's result is accessible in the prequery's result rows as `?book__Concat`.
The variable `?book` is bound to an Iri.
Since more than one Iri could be bound to a variable representing a dependent resource, the results have to be aggregated.
`GROUP_CONCAT` takes two arguments: a collection of strings (Iris in our use case) and a separator.
When accessing `?book__Concat` in the prequery's results containing the Iris of dependent resources, the string has to be split with the separator used in the aggregation function.
The result is a collection of Iris representing dependent resources.
The same logic applies to value objects.

### Main Query

The purpose of the main query is to get all requested information about the main resource, dependent resources, and value objects.
The Iris of those resources and value objects were returned by the prequery.
Since the prequery only returns resources and value objects matching the input query's criteria,
the main query can specifically ask for more detailed information on these resources and values without having to reconsider these criteria.

#### Generating the Main Query

The classes involved in generating prequeries can be found in `org.knora.webapi.responders.v2.search.gravsearch.mainquery`.

The main query is a SPARQL CONSTRUCT query. Its generation is handled by the method `GravsearchMainQueryGenerator.createMainQuery`.
It takes three arguments: `mainResourceIris: Set[IriRef], dependentResourceIris: Set[IriRef], valueObjectIris: Set[IRI]`.
From the given Iris, statements are generated that ask for complete information on *exactly* these resources and values.
For any given resource Iri, only the values present in `valueObjectIris` are to be queried.
This is achieved by using SPARQL's `VALUES` expression for the main resource and dependent resources as well as for values.

#### Processing the Main Query's results

When processing the main query's results, permissions are checked and resources and values that the user did not explicitly ask for in the input query are filtered out. This is implemented in `MainQueryResultProcessor`.

The method `getMainQueryResultsWithFullGraphPattern` takes the main query's results as an input and makes sure that the client has sufficient permissions on the results.
A main resource and its dependent resources and values are only returned if the user has view permissions on all the resources and value objects present in the main query.
Otherwise the method suppresses the main resource.
To do the permission checking, the results of the main query are passed to `ConstructResponseUtilV2` which transforms a `SparqlConstructResponse` (a set of RDF triples)
into a structure organized by main resource Iris. In this structure, dependent resources and values are nested can be accessed via their main resource.
`SparqlConstructResponse` suppresses all resources and values the user has insufficient permissions on.
For each main resource, a check is performed for the presence of all resources and values after permission checking.

The method `getRequestedValuesFromResultsWithFullGraphPattern` filters out those resources and values that the user does not want to be returned by the query.
All the resources and values not present in the input query's CONSTRUCT clause are filtered out. This only happens after permission checking.

The main resources that have been filtered out due to insufficient permissions are represented by the placeholder `ForbiddenResource`.
This placeholder stands for a main resource that cannot be returned, nevertheless it informs the client that such a resource exists.
This is necessary for a consistent behaviour when doing paging.
