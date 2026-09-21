# Gravsearch Design

For a detailed overview of Gravsearch, see
[Gravsearch: Transforming SPARQL to query humanities data](https://journals.sagepub.com/doi/full/10.3233/SW-200386).

## Type Inspection

The code that converts Gravsearch queries into SPARQL queries, and processes the query results, needs to know the
types of the entities that are used in the input query. As explained in
[Type Inference](../../../03-endpoints/api-v2/query-language.md#type-inference), these types can be inferred,
or they can be specified in the query using type annotations.

Type inspection is implemented in the package `org.knora.webapi.messages.util.search.gravsearch.types`.
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
   [type annotations](../../../03-endpoints/api-v2/query-language.md#type-annotations) included in a Gravsearch query.
- `InferringGravsearchTypeInspector`: infers the types of entities from the context in which they are used, as well
  as from ontology information that it requests from `OntologyResponderV2`.

Each type inspector takes as input, and returns as output, an `IntermediateTypeInspectionResult`, which
associates each `TypeableEntity` with zero or more types. Initially, each `TypeableEntity` has no types. 
Each type inspector adds whatever types it finds for each entity. 

At the end of the pipeline, each entity should
have exactly one type. Therefore, to only keep the most specific type for an entity, 
the method `refineDeterminedTypes` refines the determined types by removing those that are base classes of others. However,
it can be that inconsistent types are determined for entities. For example, in cases where multiple resource class types 
are determined, but one is not a base class of the others. From the following statement 

```
{ ?document a beol:manuscript . } UNION { ?document a beol:letter .}
```

two inconsistent types can be inferred for `?document`: `beol:letter` and `beol:manuscript`.
In these cases, a sanitizer `sanitizeInconsistentResourceTypes` replaces the inconsistent resource types by 
their common base resource class (in the above example, it would be `beol:writtenSource`). 

Lastly, an error is returned if

- An entity's type could not be determined. The client must add a type annotation to make the query work.
- Inconsistent types could not be sanitized (an entity appears to have more than one type). The client must correct the query.

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
if the entity is used as the object of a statement and the predicate's `knora-api:objectType` is known. For each `TypeableEntity`, 
if a type is inferred from a property, the entity and the inferred type are added to 
`IntermediateTypeInspectionResult.entitiesInferredFromProperty`.

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

- `WhereTransformer`: instructions how to convert statements in the WHERE clause of a SPARQL query 
  (to generate the prequery's Where clause).

To improve query performance, this trait defines the method `optimiseQueryPatterns` whose implementation can call 
private methods to optimise the generated SPARQL. For example, before transformation of statements in WHERE clause, query 
pattern orders must be optimised by moving `LuceneQueryPatterns` to the beginning and `isDeleted` statement patterns to the end of the WHERE clause. 

- `AbstractPrequeryGenerator` (extends `WhereTransformer`): converts a Gravsearch query into a prequery; 
  this one has two implementations for regular search queries and for count queries.
- `SelectTransformer` (extends `WhereTransformer`): transforms a Select query into a Select query with simulated RDF inference.
- `ConstructTransformer`: transforms a Construct query into a Construct query with simulated RDF inference.

### Prequery

The purpose of the prequery is to get an ordered collection of results representing only the IRIs of one page of matching resources and values.
Sort criteria can be submitted by the user, but the result is always deterministic also without sort criteria.
This is necessary to support paging.
A prequery is a SPARQL SELECT query.

The classes involved in generating prequeries can be found in `org.knora.webapi.messages.util.search.gravsearch.prequery`.

If the client submits a count query, the prequery returns the overall number of hits, but not the results themselves.

In a first step, before transforming the WHERE clause, query patterns must be further optimised by removing
the `rdfs:type` statement for entities whose type could be inferred from their use with a property IRI, since there would be no need 
for explicit `rdfs:type` statements for them (unless the property IRI from which the type of an entity must be inferred from 
is wrapped in an `OPTIONAL` block). This optimisation takes the Gravsearch query as input (rather than the generated SPARQL),
because it uses type information that refers to entities in the Gravsearch query, and the generated SPARQL might
have different entities.

Next, the Gravsearch query's WHERE clause is transformed and the prequery (SELECT and WHERE clause) is generated from this result.
The transformation of the Gravsearch query's WHERE clause relies on the implementation of the abstract class `AbstractPrequeryGenerator`.

`AbstractPrequeryGenerator` contains members whose state is changed during the iteration over the statements of the input query.
They can then be used to create the converted query.

- `mainResourceVariable: Option[QueryVariable]`: 
  SPARQL variable representing the main resource of the input query. 
  Present in the prequery's SELECT clause.
- `dependentResourceVariables: mutable.Set[QueryVariable]`: 
  a set of SPARQL variables representing dependent resources in the input query. 
  Used in an aggregation function in the prequery's SELECT clause (see below).
- `dependentResourceVariablesGroupConcat: Set[QueryVariable]`: 
  a set of SPARQL variables representing an aggregation of dependent resources. 
  Present in the prequery's SELECT clause.
- `valueObjectVariables: mutable.Set[QueryVariable]`: 
  a set of SPARQL variables representing value objects. 
  Used in an aggregation function in the prequery's SELECT clause (see below).
- `valueObjectVarsGroupConcat: Set[QueryVariable]`: 
  a set of SPARQL variables representing an aggregation of value objects. 
  Present in the prequery's SELECT clause.

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

The prequery's SELECT clause is built by
`NonTriplestoreSpecificGravsearchToPrequeryTransformer.getSelectColumns`,
based on the variables used in the input query's `CONSTRUCT` clause.
The resulting SELECT clause looks as follows:

```sparql
SELECT DISTINCT
    ?page
    (GROUP_CONCAT(DISTINCT(IF(BOUND(?book), STR(?book), "")); SEPARATOR='') AS ?book__Concat)
    (GROUP_CONCAT(DISTINCT(IF(BOUND(?seqnum), STR(?seqnum), "")); SEPARATOR='') AS ?seqnum__Concat)
    (GROUP_CONCAT(DISTINCT(IF(BOUND(?book__LinkValue), STR(?book__LinkValue), "")); SEPARATOR='') AS ?book__LinkValue__Concat)
    WHERE {...}
    GROUP BY ?page
    ORDER BY ASC(?page)
    LIMIT 25
```

`?page` represents the main resource. When accessing the prequery's result rows, `?page` contains the IRI of the main resource.
The prequery's results are grouped by the main resource so that there is exactly one result row per matching main resource.
`?page` is also used as a sort criterion although none has been defined in the input query.
This is necessary to make paging work: results always have to be returned in the same order (the prequery is always deterministic).
Like this, results can be fetched page by page using LIMIT and OFFSET.

Grouping by main resource requires other results to be aggregated using the function `GROUP_CONCAT`.
`?book` is used as an argument of the aggregation function.
The aggregation's result is accessible in the prequery's result rows as `?book__Concat`.
The variable `?book` is bound to an IRI.
Since more than one IRI could be bound to a variable representing a dependent resource, the results have to be aggregated.
`GROUP_CONCAT` takes two arguments: a collection of strings (IRIs in our use case) and a separator
(we use the non-printing Unicode character `INFORMATION SEPARATOR ONE`).
When accessing `?book__Concat` in the prequery's results containing the IRIs of dependent resources, 
the string has to be split with the separator used in the aggregation function.
The result is a collection of IRIs representing dependent resources.
The same logic applies to value objects.

Each `GROUP_CONCAT` checks whether the concatenated variable is bound in each result in the group; if a variable
is unbound, we concatenate an empty string. This is necessary because, in Apache Jena (and perhaps other
triplestores), "If `GROUP_CONCAT` has an unbound value in the list of values to concat, the overall result is 'error'"
(see [this Jena issue](https://issues.apache.org/jira/browse/JENA-1856)).

If the input query contains a `UNION`, and a variable is bound in one branch
of the `UNION` and not in another branch, it is possible that the prequery
will return more than one row per main resource. To deal with this situation,
`SearchResponderV2` merges rows that contain the same main resource IRI.

### Main Query

The purpose of the main query is to get all requested information 
about the main resource, dependent resources, and value objects.
The IRIs of those resources and value objects were returned by the prequery.
Since the prequery only returns resources and value objects matching the input query's criteria,
the main query can specifically ask for more detailed information on these resources and values 
without having to reconsider these criteria.

#### Generating the Main Query

The main query is a SPARQL CONSTRUCT query. Its generation is handled by the
method `GravsearchMainQueryGenerator.createMainQuery`.
It takes three arguments:
`mainResourceIris: Set[IriRef], dependentResourceIris: Set[IriRef], valueObjectIris: Set[IRI]`.

These sets are constructed based on information about variables representing
dependent resources and value objects in the prequery, which is provided by
`NonTriplestoreSpecificGravsearchToPrequeryTransformer`:

- `dependentResourceVariablesGroupConcat`: `Set(QueryVariable(book__Concat))`
- `valueObjectVariablesGroupConcat`: `Set(QueryVariable(seqnum__Concat), QueryVariable(book__LinkValue__Concat))`

From the given Iris, statements are
generated that ask for complete information on *exactly* these resources and
values. For any given resource Iri, only the values present in
`valueObjectIris` are to be queried. This is achieved by using SPARQL's
`VALUES` expression for the main resource and dependent resources as well as
for values.

#### Processing the Main Query's results

To do the permission checking, the results of the main query are passed to
`ConstructResponseUtilV2.splitMainResourcesAndValueRdfData`,
which transforms a `SparqlConstructResponse` (a set of RDF triples)
into a structure organized by main resource Iris. In this structure, dependent
resources and values are nested and can be accessed via their main resource,
and resources and values that the user does not have permission to see are
filtered out. As a result, a page of results may contain fewer than the maximum
allowed number of results per page, even if more pages of results are available.

`MainQueryResultProcessor.getRequestedValuesFromResultsWithFullGraphPattern`
then filters out values that the user did not explicitly ask for in the input
query.

Finally, `ConstructResponseUtilV2.createApiResponse` transforms the query
results into an API response (a `ReadResourcesSequenceV2`). If the number
of main resources found (even if filtered out because of permissions) is equal
to the maximum allowed page size, the predicate
`knora-api:mayHaveMoreResults: true` is included in the response.

## Inference

Gravsearch queries support a subset of RDFS reasoning 
(see [Inference](../../../03-endpoints/api-v2/query-language.md#inference) in the API documentation on Gravsearch). 
This is implemented as follows:

To simulate RDF inference, the API expands all `rdfs:subClassOf` and `rdfs:subPropertyOf` statements 
using `UNION` statements for all subclasses and subproperties from the ontologies 
(equivalent to `rdfs:subClassOf*` and `rdfs:subPropertyOf*`). 
Similarly, the API replaces `knora-api:standoffTagHasStartAncestor` with `knora-base:standoffTagHasStartParent*`.


## Optimisation of generated SPARQL

The triplestore-specific transformers in `SparqlTransformer.scala` can run optimisations on the generated SPARQL, in
the method `optimiseQueryPatterns` inherited from `WhereTransformer`. `optimiseIsDeletedWithFilter` is now the
only pass left in `optimiseQueryPatterns`; pattern ordering (formerly done here) has moved to the single seam
described below.

### Query Optimisation by Connectivity-Aware Pattern Ordering

In Jena Fuseki, the performance of a query highly depends on the order of the query statements. Without a
`stats.opt` statistics file, TDB2 reorders triple patterns for selectivity only within one Basic Graph
Pattern, and only by counting bound terms (Fact 1 of `docs/development/dsp-api-fuseki-query-execution.md`);
it never reorders across an `OPTIONAL`, `UNION`, `MINUS`, property-path, or subquery boundary. So the written
order of a prequery's patterns is, in large part, the execution plan Fuseki runs.

`PrequeryPatternOrdering.order` (in the `transformers` package) reorders each WHERE block's patterns for this,
and is applied at exactly one seam: `QueryTraverser.transformSelectToSelect` calls it once, after the
per-pattern transform loop has produced the prequery's WHERE patterns. It replaces three legacy passes that
each hoisted or reordered patterns independently — a graph-library-based topological sort and two separate
statement-hoisting passes for `BIND` and Lucene statements — with a single greedy pass over the whole block.

#### The tier table

Every unit (a `StatementPattern` or an opaque `GroupPattern`) is assigned a tier; lower tiers lead:

| Tier | Unit |
| --- | --- |
| T1 | Lucene: a `text:query` statement, or a `GroupPattern` containing one directly or in a nested `GroupPattern`; a `text:query` inside an `OPTIONAL`, `UNION`, or `MINUS` within the group is not found |
| T2 | Bound IRI: a non-type statement (property paths included) with an `IriRef` subject or object, whose predicate is a bound IRI other than `knora-base:attachedToProject`; also an `rdf:type` statement with an `IriRef` subject |
| T3 | Bound literal: a non-type statement with an `XsdLiteral` object |
| T4 | Project-class type unit: `rdf:type` naming a class in a project data ontology (an internal ontology IRI carrying a project shortcode), either directly or as the sole content of an enumerating `VALUES` |
| T5 | Enumerating technical type unit: `rdf:type` on a variable bound by a non-empty `VALUES` enumeration where not every entry is a class in a project data ontology - for example an enumeration mixing project and built-in classes, or naming an external IRI, or naming only classes from a built-in ontology (`knora-base`, `standoff`, `salsah-gui`, `knora-admin`, or `shared`) |
| T6 | `?x knora-base:attachedToProject <iri>` |
| T7 | Plain: everything else; within T7, non-path statements before property-path statements |

A non-type statement with a variable predicate, or with predicate `rdfs:subClassOf`/`rdfs:subPropertyOf`, is
excluded from T2 and ranks T7 regardless of a bound object — promoting a wildcard-predicate scan would
institutionalise the pathology Fact 1's bound-object corollary warns about. A type unit whose object is a
single `IriRef` naming `knora-base:LinkValue` or `knora-base:Resource` is *unselective-technical*: it ranks T7
and may never lead a component, because it restricts nothing (it matches essentially the whole store). This
ban is deliberately by name, not by namespace: several other `knora-base` classes (`Region`, `Annotation`,
`StillImageRepresentation`, `ListNode`, `DeletedResource`) are selective and must remain able to lead. Together
with T1 pre-emption, the `rdf:object` deferral, and the type-before-path rule (both below), this is one of the
pass's four eligibility rules - find all four by searching for "eligibility rule".

Ties within a tier are broken, in order, by: more bound terms first (IRIs, literals, and variables already in
the bound set); then a statement whose predicate is in a project data ontology, ahead of one whose predicate
is built-in; then the lexical order of the pattern's rendered SPARQL text (see "Determinism", below).

#### The greedy connectivity rule

Within one block, the pass emits units one at a time. At each step:

0. First, an eligibility rule narrows the candidate pool: a statement whose predicate is the bound IRI
   `rdf:object` and whose subject is a variable not yet bound is removed from candidacy - unless that would
   empty the pool, in which case it is put back. This defers the generated link-value statement
   (`?lv rdf:object ?o`) until its link value `?lv` is bound, instead of starting the join from every link
   value pointing at the object.
1. If any remaining candidate is T1 (Lucene), it leads, regardless of connectivity to what is already bound.
   This pre-empts every other rule.
2. Otherwise, prefer a non-type candidate connected to a variable already bound by an emitted unit in this
   block - except that a further eligibility rule excludes a property-path statement from this step while a
   candidate `rdf:type` unit whose subject is already bound also exists; that type unit is emitted first (see
   the type-before-path rule, below).
3. Otherwise, prefer a type candidate connected to what is already bound — so a type unit is emitted after the
   connected non-type statements of the same component, not before them.
4. Otherwise (nothing remaining is connected to what is bound), a new component is started: pick the best
   remaining candidate by (tier, tie-break) among all non-type units and all type units *except*
   unselective-technical ones.
5. If nothing else qualifies, fall back to the best remaining candidate overall.

Each step picks exactly one unit and adds its variables to the bound set, so the pass consumes one unit per
step and terminates on any input — cycles included — without needing a DAG.

`BindPattern`s are hoisted to the front of the block unconditionally (safe only because a Gravsearch bind
expression is always a constant, never a variable read); `ValuesPattern`s are re-attached immediately before
the first unit or block that references their variable; `FilterPattern`s and `FilterNotExistsPattern`s follow
the ordered units. A `GroupPattern` (used by the `matchFulltext` expansion, below) is an opaque leaf: its
contents are never inspected beyond the T1 Lucene check, and it is never recursed into.

#### Recursion into nested blocks

`OPTIONAL`, `UNION` (each branch separately), and `FILTER NOT EXISTS` are recursed into with the *outer*
block's bound-variable set as the seed, because Fuseki evaluates them with the outer bindings visible.
`MINUS` is recursed into with an *empty* seed, because Fact 4 of
`docs/development/dsp-api-fuseki-query-execution.md` establishes that Fuseki evaluates a `MINUS`'s right side
without the outer bindings. These seeds are an execution-plan heuristic mirroring Fuseki's evaluation, not a
SPARQL-semantics claim: placing every statement before every block (the `StatementsFirst` partition,
described below) can change results when a block binds a variable a later statement also uses, and is kept
consistent with that partition rather than derived from SPARQL identity.

A `MINUS` (or `OPTIONAL`) written after the class statement in the Gravsearch text was previously emitted
*before* it in the prequery whenever the class statement was the only statement binding the variable the
block shared with the rest of the query, which made the `MINUS` a no-op (it ran against an empty solution).
Because this pass emits all top-level statements before blocks, such a `MINUS` now takes effect.

#### Determinism

The pass's tie-break chain always ends in the unit's rendered SPARQL text (`pattern.toSparql`), never a
positional index into the input. This makes the emitted order a function of the pattern *set*, independent of
the order patterns arrived in and of `Set`/`Map` iteration order — which is what keeps the golden corpus in
`GravsearchToPrequeryTransformerE2ESpec` and `GravsearchToCountPrequeryTransformerE2ESpec` byte-stable across
runs. The pass consumes exactly one unit per step regardless of what remains, so it terminates on any input,
cycles included.

See DEV-7287 for the ticket this pass was built under, and the object Scaladoc of
`PrequeryPatternOrdering` for the authoritative tier and tie-break rules.

#### Measured basis

The tier table above, and the connectivity rule's treatment of type units and unselective-technical classes,
were derived from a spike that measured wall-clock time on the **stage** triplestore (a copy of prod) through
the dsp-cli SPARQL passthrough (`dsp vre sparql query -s stage`) — never against a local Fuseki or a dump.
Measurements were taken 2026-09-17 (a mini follow-up round on 2026-09-18). The exact Fuseki server version
running on stage that day was not captured by the spike; the only in-repo Fuseki version pin is the Jena dist
version used to build the image (`FUSEKI_DIST_VERSION` in `MODULE.bazel`, `6.2.0` at time of writing), which
is not confirmed to be what stage ran on the measurement date.

Per-case results (decision rule: a layout wins when its median is at least 20% faster than the runner-up and
its minimum is not slower than the runner-up's median; otherwise the case ties):

| Case | Question | Winner | Effect |
| --- | --- | --- | --- |
| S1 | Project-class type vs. `attachedToProject` leading | type-first (A/D tie) | 12-20x net over project-first |
| S2 / S2big | Technical type `VALUES` vs. `attachedToProject` | type-`VALUES`-first | 16x (0102), >=17x (0812, censored competitors) |
| S3 | Which bound-IRI statement leads | tie (below harness resolution) | both ~30x over today's order |
| S4 | Property-path anchor vs. tier-only sort | connectivity-aware order | 11x over today; tier-only order regresses 2.7x-worse |
| S5 | Bound literal as anchor | literal-first | 40 ms, weakest result in the spike |
| S6 | Bound IRI vs. `attachedToProject` | bound-IRI-first | outright win, net cost 0 vs. 1.06 s |
| S7 | "Leads to a FILTER" preference | tie | no preference added |
| S8 / S8w | Anchorless link query; may a technical type lead | project-class type leads; `LinkValue` type must never lead | leading with `LinkValue` type is >=25x worse |
| S9 | Built-in `standoff` class as component leader | anchor (`VALUES`) before the built-in type statement | 166x |
| S10 | Built-in predicate driving a sort-by-date join | project predicate before the built-in predicate | 6.3x-9.8x |

Measured tier table, with the provenance distinction the spike draws between rows it measured directly and
rows it carries forward on a working hypothesis (do not read the table as wholly empirical):

| Tier | Unit | Provenance |
| --- | --- | --- |
| T1 | Lucene | hypothesis - no case measures it |
| T2 | Bound IRI | measured above types and project (S4, S6); the choice within T2 is below resolution (S3) |
| T3 | Bound literal | existence measured (S5, 40 ms, one shape); rank relative to T2 and T6 is hypothesis |
| T4 | Project-class type | measured above plain statements and above project (S1, S8); exclusion of built-in vocabularies other than `knora-base` is measured by S9 (166x) |
| T5 | Enumerating technical type | measured above project (S2, S2big); T4-versus-T5 is hypothesis (no case contains both) |
| T6 | `attachedToProject` | measured below T2, T4, T5 (S1, S2, S2big, S6); T6-above-T7 is hypothesis |
| T7 | Plain | the tier is the residue; the type-before-path eligibility rule that reorders property-path statements against a connected type unit is measured (see below), the rest of the path sub-ordering is not |

The one shape the stage replay found ordered worse than the previous topological sort - two link hops whose
predicates are variables restricted only by a `FILTER` (`?linkingProp1 = beol:hasAuthor || beol:hasRecipient`),
anchored by a literal (the `reorder` golden) - is fixed by the `rdf:object` deferral rule (above). `rdf:object`
appears in a prequery only on the generated link-value node (`?s <linkValueProp> ?lv . ?lv rdf:object ?o`), and
emitting it while `?lv` is unbound starts the join from every link value pointing at the object. Deferring it
until its subject is bound takes this shape from 6.98 s to 0.66 s on stage (identical rows, 5 interleaved runs
each), about 8x faster than the pre-DEV-7287 order as well. A FILTER-to-`VALUES` rewrite of the same query was
measured too and is harmful (53 s), so it was not adopted.

A later stage replay found the reverse problem inside `ruleA`: a standoff query
(`?letter a beol:letter . ?letter beol:hasText ?text . ?text knora-api:textValueHasStandoff ?tag . ?tag a
standoff:StandoffItalicTag . ?tag knora-api:standoffTagHasStartAncestor ?para . ?para a
standoff:StandoffParagraphTag`) regressed from 1.5 s to 4.0 s after the DEV-7287 stack merged, because the
emitted prequery put the `standoffTagHasStartAncestor` path ahead of the type check on its own subject
(`?tag a standoff:StandoffItalicTag`): Fuseki walked the ancestors of every standoff tag in every letter before
narrowing to italic tags. The fix is the type-before-path eligibility rule: within `ruleA`, a property-path
statement is not a candidate while a candidate *selective* `rdf:type` unit whose subject is already bound
exists, so that type unit is emitted first; a bound-subject type unit that is unselective-technical (its
object is a bare `IriRef` naming `knora-base:LinkValue` or `knora-base:Resource`) does not defer a path,
since it restricts essentially nothing and deferring the path for it would buy none of the rule's benefit.
Per Fact 3 of `docs/development/dsp-api-fuseki-query-execution.md`, a `*`/`+`
property path fans out from every binding of its anchored end, whereas a type check on an already-bound
subject costs one index lookup per binding and shrinks the binding set before the path runs - so the cheap,
selective check belongs first. Measured on stage (dsp-cli, 5 interleaved runs, 1344 rows in both layouts): 3.71
s with the ancestor path emitted ahead of the type check, 1.20 s with the type check moved directly before the
path, about 3x. A confirmation run of 3 interleaved pairs on 2026-09-18 gave 3.45-3.50 s versus 1.01-1.03 s,
same row count. This is deliberately not generalised to "type units before all connected non-type units" -
that broader rule is unmeasured and would reorder many other golden files; it fires only against property-path
statements, and only inside `ruleA`. A property-path statement that leads its component (its variable end
not yet bound, so a bound `IriRef` on the other end anchors it - the list-node anchor shape) is unaffected:
T2 already ranks it ahead of the type unit as a component anchor, before `ruleA` is ever reached; the same
path statement can still reach `ruleA` later in the same run, once its `IriRef` end is no longer what starts
the component. Regenerating both golden corpora after this change produced no golden diff at all, so the
rule is pinned only by `PrequeryPatternOrderingSpec`, not by any golden file.

## The `matchFulltext` Function Expansion

`knora-api:matchFulltext` (see
[Filtering on Any Indexed Text of a Resource](../../../03-endpoints/api-v2/query-language.md#filtering-on-any-indexed-text-of-a-resource))
gives the same result set as the `GET /v2/search/{term}` fulltext endpoint, expressed as a Gravsearch function.
Its handler, `AbstractPrequeryGenerator.handleMatchFulltextFunction`, replaces the `FILTER` with a hand-proven
SPARQL shape that mirrors the WHERE core of `SearchFulltextQuery` (the fulltext endpoint's own query builder):
a Lucene hit anchors the match, two `OPTIONAL` blocks resolve it to a containing resource — either the resource
that owns the matched value (a text value or a value comment), or the resource that references a matched list
node (including sub-nodes) via a list value — and a `BIND(COALESCE(...))` picks whichever resolved, falling back
to the match itself for a direct label hit.

### Why the Expansion Needs an Opaque Group

A naive expansion using ordinary `OptionalPattern`/`BindPattern`/`StatementPattern` nodes breaks in two ways once
it goes through the passes described above:

1. **The optimizer passes hoist or reorder statements independently of scoping.** `PrequeryPatternOrdering`
   hoists `BindPattern`s to the front of a block unconditionally, which would place `BIND(COALESCE(...))`
   above the `OPTIONAL` blocks it depends on, leaving it referencing unbound variables. Its greedy
   connectivity rule (above) would also place the trailing `?resourceVar a ?resClass` check before the `BIND`
   that introduces `?resourceVar`, which is illegal SPARQL scoping.
2. **The inference pass rejects `rdf:type` statements with a variable object.** `OntologyInferencer.transformStatementInWhere`
   throws `GravsearchException` when the object of `rdf:type` is a variable rather than an IRI (see
   [Inference](#inference), above) — but the expansion's value-type check (`?match a ?valType`) and its final
   resource-class check (`?resourceVar a ?resClass`) both need exactly that shape, because the type isn't known
   in advance.

Both problems disappear if nothing after the handler ever looks inside the expansion. `GroupPattern` (in
`SparqlQuery.scala`) is a `QueryPattern` that exists for exactly this: it renders its contents verbatim inside
`{ ... }`, and every pass that matches on `QueryPattern` either has a wildcard fallback that returns it
unchanged, or (for the two passes that match exhaustively — `QueryTraverser.transformWherePatterns` and
`GravsearchTypeInspectionUtil.transformPattern`) has an explicit case added that does the same. The
`OntologyInferencer`, `GravsearchQueryOptimisation`, and `InferenceOptimizationService` passes never see a
`GroupPattern`'s interior at all, so the handler can emit the same statement shapes `SearchFulltextQuery` already
proves correct, unmodified.

### Why the Expansion Must Be Hoisted

Even with an opaque group, *where* it sits in the WHERE clause matters. A classless `matchFulltext` query (no
resource class selected) still carries the user's `?mainRes a knora-api:Resource`, and because that gives the
relevant-ontologies inference nothing to narrow on, `OntologyInferencer` expands it into a `VALUES` block
enumerating every resource class in the repository. Evaluating that block before the (cheap, index-anchored)
Lucene lookup measured roughly 300× slower in the performance spike behind this feature — the classic
join-order pessimization `PrequeryPatternOrdering`'s T1 Lucene tier (above) already exists to prevent for a
bare Lucene `StatementPattern`. T1 also recognizes a `GroupPattern` whose contents include a Lucene statement
at any depth, and T1 pre-empts the connectivity rule unconditionally, so the whole expansion — not just a
bare statement — leads its block ahead of patterns like the class-enumeration `VALUES` block.

### Determinism for Snapshot Testing

`OntologyInferencer` names the `VALUES` variables it introduces (e.g. `?resTypes`, `?subProp`) with
`SparqlTransformer.createInferenceVariable(statement, kind)`, a content-derived name of the form
`<base>__<kind>__<hash>` — for example `?thing__resTypes__3fa2b91c`. `base` comes from the statement's subject
(the variable name if the subject is a variable, or an IRI's local name if it is an IRI), sanitised down to
`[A-Za-z0-9_]`; `kind` is a short discriminator such as `resTypes` or `subProp`; and `hash` is an 8-hex-digit
`String.hashCode` of the statement's rendered SPARQL. The same query always renders the same SPARQL, which is
what `GravsearchToPrequeryTransformerE2ESpec`'s golden-snapshot tests on the matchFulltext expansion depend on;
it also means a later pass that reorders WHERE patterns cannot renumber these variables out from under the
snapshots.

Identical statements deliberately produce the same variable name. This is harmless: the constraint the
statement contributes to the `VALUES` block is identical either way, so sharing the variable loses nothing.

The existing lossy `escapeEntityForVariable` helper is not reused here. It collides two different IRIs onto the
same escaped string (`.../ab#cd` and `.../abc#d` escape alike), and it passes an `XsdLiteral`'s raw text (e.g.
`"(DE-588)118531379"`) through unchanged, offering no `VARNAME` guarantee for any entity position.
`createInferenceVariable` instead sanitises its `base` unconditionally on every branch, including the fallback
branch. Either failure mode in `escapeEntityForVariable` would merge two unrelated `VALUES` blocks under one
name.

The suffix must stay unique per statement, not a shared constant: a constant would collapse unrelated `VALUES`
variables (e.g. one from `?mainRes a Resource`, another from `?val a Value`) into the same variable,
intersecting their blocks into silently empty results.

The other source of pattern-order nondeterminism is handled by `PrequeryPatternOrdering` itself: its final
tie-break is always the unit's rendered SPARQL text, never a positional index, so the emitted order is a
function of the pattern set and is independent of the order patterns arrived in or of `Set`/`Map` iteration
order (see "Determinism", above).
