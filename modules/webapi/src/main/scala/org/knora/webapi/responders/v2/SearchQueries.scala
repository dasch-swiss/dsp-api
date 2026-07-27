/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.constraint.propertypath.builder.PropertyPathBuilder

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.util.FusekiLucenceQuery

object SearchQueries extends QueryBuilderHelper {

  private val luceneHitLimit = OntologyConstants.Fuseki.luceneHitLimit

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
   * this question. Replace the pattern here with that guard when it lands.
   */
  def selectCountByLabel(
    luceneQuery: FusekiLucenceQuery,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[ResourceClassIri],
  ): Select =
    Select(
      s"""|PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |SELECT (count(distinct ?resource) as ?count)
          |WHERE {
          |    ?resource <http://jena.apache.org/text#query> (rdfs:label "${luceneQuery.getQueryString}" $luceneHitLimit) ;
          |        knora-base:creationDate ?resourceCreationDate .
          |    ${filterByProjectAndResourceClass(limitToProject, limitToResourceClass)}
          |    FILTER NOT EXISTS { ?resource knora-base:isDeleted true . }
          |}
          |""".stripMargin,
    )

  /**
   * Emits the optional project and resource-class restrictions shared by both label queries.
   *
   * The class restriction walks `rdfs:subClassOf*`. It must not be narrowed to `subClassOf?`: the subclass closure is not
   * materialised in the triplestore and there is no query-time inference, so zero-or-one matched only the target class and
   * its direct subclasses. Any class two or more hops below the target was invisible, which made a class-restricted label
   * search return nothing at all whenever the instantiated classes sat deeper than one level (DEV-6833).
   *
   * `?resource a ?resourceClass` is emitted here rather than in the query bodies so the type join is only paid when a
   * class restriction is actually requested.
   */
  private def filterByProjectAndResourceClass(
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[ResourceClassIri],
  ): String = {
    val projectPattern = limitToProject
      .map(toRdfIri)
      .map(prj => variable("resource").has(KnoraBase.attachedToProject, prj).getQueryString)

    val resourceClassPatterns = limitToResourceClass.map(toRdfIri).map { cls =>
      val subClassOfStar = PropertyPathBuilder.of(RDFS.SUBCLASSOF).zeroOrMore().build()
      List(
        variable("resource").isA(variable("resourceClass")).getQueryString,
        variable("resourceClass").has(subClassOfStar, cls).getQueryString,
      ).mkString("\n")
    }

    List(projectPattern, resourceClassPatterns).flatten.mkString("\n")
  }

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
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[ResourceClassIri],
    limit: Int,
    offset: Int,
  ): Construct =
    Construct(
      s"""|PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
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
          |            ?resource <http://jena.apache.org/text#query> (rdfs:label "${luceneQuery.getQueryString}" $luceneHitLimit) ;
          |                rdfs:label ?label .
          |            ${filterByProjectAndResourceClass(limitToProject, limitToResourceClass)}
          |            FILTER NOT EXISTS { ?resource knora-base:isDeleted true . }
          |        }
          |        ORDER BY ?resource
          |        LIMIT $limit
          |        OFFSET $offset
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
          |}
          |""".stripMargin,
    )
}
