/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.util.FusekiLucenceQuery

object SearchQueries {

  private val luceneHitLimit = Literal.int(OntologyConstants.Fuseki.luceneHitLimit)

  // The Lucene lookup both label queries start from, shared so the count and the page scope the same candidate set.
  private def labelLookupClause(luceneQuery: FusekiLucenceQuery, dataGraph: Option[InternalIri]): Fragment = {
    // `Literal.string` escapes for SPARQL itself, so the raw Lucene query goes in — not `getQueryString`,
    // which is already SPARQL-escaped and would be escaped twice.
    val query  = Literal.string(luceneQuery.value)
    val lookup = sparql"?resource <http://jena.apache.org/text#query> (rdfs:label $query $luceneHitLimit) ."
    dataGraph match {
      case Some(graph) =>
        sparql"""|GRAPH ${Iri.unsafeFrom(graph.value)} {
                 |    $lookup
                 |}"""
      case None => lookup
    }
  }

  /*
   * Both label queries below emit the same optional project scope and resource-class restriction.
   *
   * `dataGraph` is the data graph of the project the search is limited to. It wraps only the `text:query` statement in
   * `GRAPH <dataGraph> { }` (labelLookupClause); every other pattern stays outside, since the class walk reads ontology
   * graphs and a wider GRAPH returns no rows. No `attachedToProject` is emitted: a label hit is the resource itself, and
   * a resource lives in its project's data graph (the one-graph rule), so the join would only repeat the scope. An
   * unknown project never reaches these builders: the responder answers it with no results
   * (docs/adr/0012-graph-scoped-lucene-text-lookups.md).
   *
   * The class restriction walks `rdfs:subClassOf*`. It must not be narrowed to `subClassOf?`: the subclass closure is not
   * materialised in the triplestore and there is no query-time inference, so zero-or-one matched only the target class and
   * its direct subclasses. Any class two or more hops below the target was invisible, which made a class-restricted label
   * search return nothing at all whenever the instantiated classes sat deeper than one level (DEV-6833).
   *
   * `?resource a ?resourceClass` is emitted inside the class-restriction hole rather than in the query bodies so the type
   * join is only paid when a class restriction is actually requested.
   */

  /**
   * Counts the resources whose `rdfs:label` matches the Lucene query.
   *
   * Resource-ness is asserted via the presence of `knora-base:creationDate`, which `knora-base` declares with
   * `subjectClassConstraint :Resource` and a cardinality of exactly 1 on `knora-base:Resource` — so it is present on every
   * resource and on nothing else. This replaces a per-hit `?resourceClass rdfs:subClassOf* knora-base:Resource` walk that
   * cost 3-4x as much for identical counts (measured on stage: 12.55s -> 2.92s for a common term). It is the same
   * discriminator `constructSearchByLabel` already relies on below.
   *
   * TODO(DEV-6850): this is a stopgap. `creationDate` means "when was this made", not "this is a resource"; DEV-6850
   * materialises the entailed `?resource a knora-base:Resource` and introduces one shared guard for all ~21 sites that ask
   * this question. Replace the pattern here with that guard when it lands. See also SearchFulltextQuery and
   * AbstractPrequeryGenerator.matchFulltextExpansion, which carry the same stopgap.
   */
  def selectCountByLabel(
    luceneQuery: FusekiLucenceQuery,
    dataGraph: Option[InternalIri],
    limitToResourceClass: Option[ResourceClassIri],
  ): Select =
    Select(
      sparql"""|PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |SELECT (count(distinct ?resource) as ?count)
               |WHERE {
               |    ${labelLookupClause(luceneQuery, dataGraph)}
               |    ?resource knora-base:creationDate ?resourceCreationDate .
               |    ${limitToResourceClass.whenSome(cls => sparql"""|?resource a ?resourceClass .
                     |?resourceClass rdfs:subClassOf* ${Iri.unsafeFrom(cls.smartIri.toInternalSchema.toIri)} .""")}
               |    FILTER NOT EXISTS { ?resource knora-base:isDeleted true . }
               |}""".render,
    )

  /**
   * Retrieves a page of resources whose `rdfs:label` matches the Lucene query, with their values.
   *
   * Standoff is excluded by filtering the *predicate* (`?valueObjectProperty != knora-base:valueHasStandoff`) rather than
   * the object's type. The previous `FILTER NOT EXISTS { ?valueObjectValue a knora-base:StandoffTag }` matched nothing at
   * all: standoff nodes carry a concrete subclass type from their mapping, never the abstract base class, and there is no
   * query-time inference. The predicate filter is equivalent for well-formed data because `valueHasStandoff` is the only
   * property in `knora-base` with a Value-family subject and a `StandoffTag` object — every other StandoffTag-valued
   * property has a StandoffTag subject, already excluded by the `?valueObjectType rdfs:subClassOf* knora-base:Value` guard.
   * Verified on stage: both formulations return byte-identical results, and the dead filter was leaking up to 6232 junk
   * triples (1.47 MB) on a single standoff-heavy page.
   */
  def constructSearchByLabel(
    luceneQuery: FusekiLucenceQuery,
    dataGraph: Option[InternalIri],
    limitToResourceClass: Option[ResourceClassIri],
    limit: Int,
    offset: Int,
  ): Construct =
    Construct(
      sparql"""|PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |CONSTRUCT {
               |    ?resource rdfs:label ?label ;
               |        a knora-base:Resource ;
               |        knora-base:isMainResource true ;
               |        knora-base:isDeleted false ;
               |        a ?resourceType ;
               |        knora-base:attachedToUser ?resourceCreator ;
               |        knora-base:hasPermissions ?resourcePermissions ;
               |        knora-base:attachedToProject ?resourceProject  ;
               |        knora-base:creationDate ?creationDate ;
               |        knora-base:lastModificationDate ?lastModificationDate ;
               |        knora-base:hasValue ?valueObject ;
               |        ?resourceValueProperty ?valueObject .
               |    ?valueObject ?valueObjectProperty ?valueObjectValue .
               |} WHERE {
               |    {
               |        SELECT DISTINCT ?resource ?label
               |        WHERE {
               |            ${labelLookupClause(luceneQuery, dataGraph)}
               |            ?resource rdfs:label ?label .
               |            ${limitToResourceClass.whenSome(cls => sparql"""|?resource a ?resourceClass .
                     |?resourceClass rdfs:subClassOf* ${Iri.unsafeFrom(cls.smartIri.toInternalSchema.toIri)} .""")}
               |            FILTER NOT EXISTS { ?resource knora-base:isDeleted true . }
               |        }
               |        ORDER BY ?resource
               |        LIMIT ${Literal.int(limit)}
               |        OFFSET ${Literal.int(offset)}
               |    }
               |
               |    ?resource a ?resourceType ;
               |        knora-base:attachedToUser ?resourceCreator ;
               |        knora-base:hasPermissions ?resourcePermissions ;
               |        knora-base:attachedToProject ?resourceProject ;
               |        knora-base:creationDate ?creationDate ;
               |        rdfs:label ?label .
               |    OPTIONAL { ?resource knora-base:lastModificationDate ?lastModificationDate . }
               |    OPTIONAL {
               |        ?resource ?resourceValueProperty ?valueObject .
               |        ?resourceValueProperty rdfs:subPropertyOf* knora-base:hasValue .
               |        ?valueObject a ?valueObjectType ;
               |            ?valueObjectProperty ?valueObjectValue .
               |        ?valueObjectType rdfs:subClassOf* knora-base:Value .
               |        FILTER(?valueObjectType != knora-base:LinkValue)
               |        FILTER NOT EXISTS { ?valueObject knora-base:isDeleted true . }
               |        FILTER(?valueObjectProperty != knora-base:valueHasStandoff)
               |    }
               |}""".render,
    )
}
