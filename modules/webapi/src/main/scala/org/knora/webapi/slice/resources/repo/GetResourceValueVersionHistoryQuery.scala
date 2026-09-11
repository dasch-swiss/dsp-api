/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import java.time.Instant

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.common.ResourceIri

object GetResourceValueVersionHistoryQuery {

  def build(
    resourceIri: ResourceIri,
    withDeletedResource: Boolean = false,
    maybeStartDate: Option[Instant] = None,
    maybeEndDate: Option[Instant] = None,
  ): String = {
    val resource = Iri.unsafeFrom(resourceIri.value)

    sparql"""|PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |
              |SELECT DISTINCT ?versionDate ?author
              |WHERE {
              |  $resource ?property ?currentValue .
              |  ${sparql"$resource knora-base:isDeleted false .".unless(withDeletedResource)}
              |  ?property rdfs:subPropertyOf* knora-base:hasValue .
              |  ?currentValue knora-base:previousValue* ?valueObject .
              |  {
              |    ?valueObject knora-base:valueCreationDate ?versionDate .
              |    ?valueObject knora-base:attachedToUser ?author .
              |  } UNION {
              |    ?valueObject knora-base:deleteDate ?versionDate .
              |    ?valueObject knora-base:deletedBy ?author .
              |  }
              |  ${sparql"""|UNION {
                             |  $resource knora-base:deleteDate ?versionDate .
                             |  $resource knora-base:attachedToUser ?author .
                             |}""".when(withDeletedResource)}
              |  ${maybeStartDate.whenSome(date => sparql"FILTER(?versionDate >= ${Literal.dateTime(date)})")}
              |  ${maybeEndDate.whenSome(date => sparql"FILTER(?versionDate < ${Literal.dateTime(date)})")}
              |}
              |ORDER BY DESC(?versionDate)""".render
  }
}
