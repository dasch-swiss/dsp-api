/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object EraseResourceQuery {

  def build(project: Project, resourceIri: ResourceIri): Update = {
    val graph = Iri.unsafeFrom(ProjectService.projectDataNamedGraphV2(project).value)
    val res   = Iri.unsafeFrom(resourceIri.value)
    Update(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |
               |DELETE {
               |  GRAPH $graph {
               |    $res ?resourcePred ?resourceObj .
               |    ?value ?valuePred ?valueObj .
               |    ?standoff ?standoffPred ?standoffObj .
               |  }
               |}
               |WHERE {
               |  $res a ?resourceClass .
               |  ?resourceClass rdfs:subClassOf* knora-base:Resource .
               |  {
               |    $res ?resourcePred ?resourceObj .
               |  } UNION {
               |    $res ?valueProp ?currentValue .
               |    ?currentValue a ?currentValueClass .
               |    ?currentValueClass rdfs:subClassOf* knora-base:Value .
               |    ?currentValue knora-base:previousValue* ?value .
               |    ?value ?valuePred ?valueObj .
               |  } UNION {
               |    $res ?valueProp ?currentTextValue .
               |    ?currentTextValue a knora-base:TextValue ;
               |      knora-base:previousValue* ?textValue .
               |    ?textValue knora-base:valueHasStandoff ?standoff .
               |    ?standoff ?standoffPred ?standoffObj .
               |  }
               |}""".render,
    )
  }
}
