<!---
Copyright © 2015-2018 the contributors (see Contributors.md).

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

# Gravsearch Responder Logic

@@toc

## Transformation of a Gravsearch Query

A Gravsearch query submitted by the client is parsed by `GravsearchParser` and preprocessed by `GravsearchTypeInspector` 
to get type information about the elements used in the query (resources, values, properties etc.) 
and do some basic sanity checks.

In `SearchResponderV2`, two queries are generated from a given Gravsearch query: a prequery and a main query. 

### Gravsearch Type Inspection

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

The purpose of the prequery is to get an ordered collection of results. 
Sort criteria can be submitted by the user, but the result is always deterministic also without sort criteria.
This is necessary to support paging. 
A prequery is a SPARQL SELECT query.

If the client submits a count query, the prequery returns the overall number of hits, but not the results themselves.

In a first step, the Gravsearch query's WHERE clause is transformed and the prequery (SELECT and WHERE clause) is generated from this result. 
The transformation of the Gravsearch query's WHERE clause relies on the implementation of the abstract class `AbstractSparqlTransformer`.
 
`AbstractSparqlTransformer` contains members whose state is changed during the iteration over the statements of the input query. 
They can then by used to create the converted query.

-  `mainResourceVariable: Option[QueryVariable]`: variable representing the main resource of the input query. Used in the prequery's SELECT clause.
- `dependentResourceVariables: mutable.Set[QueryVariable]`: a set of variables representing dependent resources in the input query.
- `dependentResourceVariablesGroupConcat: Set[QueryVariable]`: variables representing dependent resources' IRIs in the prequery's SELECT clause.
- `valueObjectVariables: mutable.Set[QueryVariable]`: variables representing value objects.
- `valueObjectVarsGroupConcat: Set[QueryVariable]`: variables representing value objects' IRIs in the prequery's SELECT clause.

The following Gravsearch query looks for pages with sequence number 10 that are part of a book:

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

The prequery's SELECT clause is built using the member vars defined in `AbstractSparqlTransformer`.
State of member vars after analysis:

- `mainResourceVariable`: `QueryVariable(page)`
- `dependentResourceVariables`: `Set(QueryVariable(book))`
- `dependentResourceVariablesConcat`: `Set(QueryVariable(book__Concat))`
- `valueObjectVariables`: `Set(QueryVariable(book__LinkValue), QueryVariable(seqnum))`
- `valueObjectVariablesConcat`: `Set(QueryVariable(seqnum__Concat), QueryVariable(book__LinkValue__Concat))`

The resulting SELECT clause of the prequery looks as follows:

```sparql
SELECT DISTINCT 
    ?page 
    (GROUP_CONCAT(DISTINCT(?book); SEPARATOR='') AS ?book__Concat) 
    (GROUP_CONCAT(DISTINCT(?seqnum); SEPARATOR='') AS ?seqnum__Concat)
    (GROUP_CONCAT(DISTINCT(?book__LinkValue); SEPARATOR='') AS ?book__LinkValue__Concat) 
    WHERE {...}
```

The variables `?page`, `?book__Concat`, `?seqnum__Concat`, and `?book__LinkValue__Concat` 
are accessible in the result rows and contain the IRIs of the resources and values that matched the search criteria.

### Main Query