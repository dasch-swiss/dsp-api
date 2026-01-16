/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.constraint.propertypath.builder.PropertyPathBuilder

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.util.FusekiLucenceQuery

object SearchQueries extends QueryBuilderHelper {

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
          |    ?resource <http://jena.apache.org/text#query> (rdfs:label "${luceneQuery.getQueryString}") ;
          |        a ?resourceClass .
          |    ?resourceClass rdfs:subClassOf* knora-base:Resource .
          |    ${filterByProjectAndResourceClass(limitToProject, limitToResourceClass)}
          |    FILTER NOT EXISTS { ?resource knora-base:isDeleted true . }
          |}
          |""".stripMargin,
    )

  private def filterByProjectAndResourceClass(
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[ResourceClassIri],
  ): String = List(
    limitToProject
      .map(toRdfIri)
      .map(prj => variable("resource").has(KnoraBase.attachedToProject, prj)),
    limitToResourceClass
      .map(toRdfIri)
      .map(cls => variable("resourceClass").has(PropertyPathBuilder.of(RDFS.SUBCLASSOF).zeroOrOne().build(), cls)),
  ).flatten.map(_.getQueryString).mkString("\n")

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
          |            ?resource <http://jena.apache.org/text#query> (rdfs:label "${luceneQuery.getQueryString}") ;
          |                a ?resourceClass ;
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
          |        FILTER NOT EXISTS { ?valueObjectValue a knora-base:StandoffTag . }
          |    }
          |}
          |""".stripMargin,
    )
}
