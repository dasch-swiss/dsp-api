/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import zio.*

import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

object DeleteClassQuery extends QueryBuilderHelper {

  def build(
    classIri: ResourceClassIri,
    lmd: LastModificationDate,
  ): UIO[(LastModificationDate, ModifyQuery)] =
    Clock.instant.map { now =>
      val (ontology, ontologyNS) = ontologyAndNamespace(classIri)
      val clazz                  = toRdfIri(classIri)

      val classPred       = variable("classPred")
      val classObj        = variable("classObj")
      val restriction     = variable("restriction")
      val restrictionPred = variable("restrictionPred")
      val restrictionObj  = variable("restrictionObj")
      val (s, p, _)       = spo

      val deletePatterns = List(
        ontology.has(KnoraBase.lastModificationDate, toRdfLiteral(lmd)),
        clazz.has(classPred, classObj),
        restriction.has(restrictionPred, restrictionObj),
      )

      val wherePatterns = List(
        ontology.isA(OWL.ONTOLOGY).andHas(KnoraBase.lastModificationDate, toRdfLiteral(lmd)),
        clazz.isA(OWL.CLASS),
        GraphPatterns.union(
          clazz.has(classPred, classObj),
          clazz
            .has(RDFS.SUBCLASSOF, restriction)
            .filter(Expressions.isBlank(restriction))
            .and(
              restriction
                .isA(OWL.RESTRICTION)
                .andHas(restrictionPred, restrictionObj),
            ),
        ),
        GraphPatterns.filterNotExists(s.has(p, clazz)),
      )

      (
        LastModificationDate.from(now),
        Queries
          .MODIFY()
          .prefix(KnoraBase.NS, XSD.NS, RDF.NS, RDFS.NS, OWL.NS, ontologyNS)
          .delete(deletePatterns*)
          .from(ontology)
          .insert(ontology.has(KnoraBase.lastModificationDate, toRdfLiteral(now)))
          .into(ontology)
          .where(wherePatterns*),
      )
    }
}
