/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.eclipse.rdf4j.model.vocabulary.RDFS

import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask

object IsClassUsedInDataQuery extends QueryBuilderHelper {

  def build(classIri: ResourceClassIri): Ask = {
    val resourceInstance = variable("resourceInstance")
    val subClass         = variable("subClass")
    val classIriRdf      = toRdfIri(classIri)

    // Pattern 1: Direct instances of the class
    val directInstancePattern = resourceInstance.isA(classIriRdf).getQueryString

    // Pattern 2: Instances of subclasses
    val subClassPattern = subClass
      .has(zeroOrMore(RDFS.SUBCLASSOF), classIriRdf)
      .and(resourceInstance.isA(subClass))
      .getQueryString

    Ask(s"""
           |ASK
           |WHERE {
           |  {
           |    $directInstancePattern
           |  }
           |  UNION {
           |    $subClassPattern
           |  }
           |}
           |""".stripMargin)
  }
}
