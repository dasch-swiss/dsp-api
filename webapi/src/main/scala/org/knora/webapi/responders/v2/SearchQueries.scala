/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.knora.webapi.IRI
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

object SearchQueries {

  def selectCountByLabel(
    searchTerm: String,
    limitToProject: Option[IRI],
    limitToResourceClass: Option[IRI],
  ): Select =
    Select(
      s"""|PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |SELECT (count(distinct ?resource) as ?count)
          |WHERE {
          |    ?resource <http://jena.apache.org/text#query> (rdfs:label "$searchTerm") ;
          |        a ?resourceClass .
          |    ?resourceClass rdfs:subClassOf* knora-base:Resource .
          |    ${limitToResourceClass.fold("")(resourceClass => s"?resourceClass rdfs:subClassOf* <$resourceClass> .")}
          |    ${limitToProject.fold("")(project => s"?resource knora-base:attachedToProject <$project> .")}
          |    FILTER NOT EXISTS { ?resource knora-base:isDeleted true . }
          |}
          |""".stripMargin,
    )

  def constructSearchByLabel(
    searchTerm: String,
    limitToResourceClass: Option[IRI] = None,
    limitToProject: Option[IRI] = None,
    limit: Int,
    offset: Int = 0,
  ): Construct = {
    val limitToClassOrProject =
      (limitToResourceClass, limitToProject) match {
        case (Some(cls), _)     => s"?resourceClass rdfs:subClassOf* <$cls> ."
        case (_, Some(project)) => s"?resource knora-base:attachedToProject <$project> ."
        case _                  => ""
      }
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
          |            ?resource <http://jena.apache.org/text#query> (rdfs:label "$searchTerm") ;
          |                a ?resourceClass ;
          |                rdfs:label ?label .
          |            $limitToClassOrProject
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

}
