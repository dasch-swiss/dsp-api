/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.eclipse.rdf4j.model.vocabulary.OWL

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.common.QueryBuilderHelper

object IsOntologyUsedQuery extends QueryBuilderHelper {

  def build(
    ontologyNamedGraphIri: SmartIri,
    classIris: Set[SmartIri],
    propertyIris: Set[SmartIri],
  ): String = {
    val s        = variable("s")
    val o        = variable("o")
    val g        = variable("g")
    val pred     = variable("pred")
    val ontology = variable("ontology")
    val graphIri = toRdfIri(ontologyNamedGraphIri)

    def dataUsageOfProperty(propIri: SmartIri): String =
      s"{ ${s.has(toRdfIri(propIri), o).getQueryString} }"

    def dataUsageOfClass(classIri: SmartIri): String =
      s"{ ${s.isA(toRdfIri(classIri)).getQueryString} }"

    def otherOntologyRef(iri: SmartIri): String = {
      val graphContent = ontology.isA(OWL.ONTOLOGY).and(s.has(pred, toRdfIri(iri)))
      s"""|{ GRAPH ${g.getQueryString} {
          |    ${graphContent.getQueryString}
          |  }
          |  FILTER ( ${g.getQueryString} != ${graphIri.getQueryString} )
          |}""".stripMargin
    }

    val propertyUnions = propertyIris.toVector.flatMap { propIri =>
      Seq(dataUsageOfProperty(propIri), otherOntologyRef(propIri))
    }

    val classUnions = classIris.toVector.flatMap { classIri =>
      Seq(dataUsageOfClass(classIri), otherOntologyRef(classIri))
    }

    val allUnions    = propertyUnions ++ classUnions
    val whereContent = if (allUnions.nonEmpty) allUnions.mkString(" UNION\n") else ""

    s"""SELECT DISTINCT ${s.getQueryString}
       |WHERE {
       |$whereContent
       |}
       |LIMIT 100""".stripMargin
  }
}
