/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns

import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

object EraseResourceQuery extends QueryBuilderHelper {
  def build(project: Project, resourceIri: ResourceIri): ModifyQuery = {
    val resourcePred      = variable("resourcePred")
    val resourceObj       = variable("resourceObj")
    val value             = variable("value")
    val valuePred         = variable("valuePred")
    val valueObj          = variable("valueObj")
    val standoff          = variable("standoff")
    val standoffPred      = variable("standoffPred")
    val standoffObj       = variable("standoffObj")
    val resourceClass     = variable("resourceClass")
    val valueProp         = variable("valueProp")
    val currentValue      = variable("currentValue")
    val currentValueClass = variable("currentValueClass")
    val currentTextValue  = variable("currentTextValue")
    val textValue         = variable("textValue")

    val resIri       = toRdfIri(resourceIri)
    val dataGraphIri = graphIri(project)

    // Resource type check
    val typeCheck = resIri
      .isA(resourceClass)
      .and(resourceClass.has(zeroOrMore(RDFS.SUBCLASSOF), KnoraBase.Resource))

    // Union pattern 1: all statements whose subject is the resource
    val pattern1 = resIri.has(resourcePred, resourceObj)

    // Union pattern 2: all statements whose subject is a value of the resource
    val pattern2 = resIri
      .has(valueProp, currentValue)
      .and(currentValue.isA(currentValueClass))
      .and(currentValueClass.has(zeroOrMore(RDFS.SUBCLASSOF), KnoraBase.Value))
      .and(currentValue.has(zeroOrMore(KnoraBase.previousValue), value))
      .and(value.has(valuePred, valueObj))

    // Union pattern 3: all statements whose subject is a standoff tag attached to a value
    val pattern3 = resIri
      .has(valueProp, currentTextValue)
      .and(
        currentTextValue
          .isA(KnoraBase.TextValue)
          .andHas(zeroOrMore(KnoraBase.previousValue), textValue),
      )
      .and(textValue.has(KnoraBase.valueHasStandoff, standoff))
      .and(standoff.has(standoffPred, standoffObj))

    val union = GraphPatterns.union(pattern1, pattern2, pattern3)

    Queries
      .MODIFY()
      .prefix(KnoraBase.NS, RDF.NS, RDFS.NS)
      .delete(
        resIri.has(resourcePred, resourceObj),
        value.has(valuePred, valueObj),
        standoff.has(standoffPred, standoffObj),
      )
      .from(dataGraphIri)
      .where(typeCheck.and(union))
  }
}
