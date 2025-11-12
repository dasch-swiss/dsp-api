/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import zio.*

import dsp.errors.SparqlGenerationException
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.LabelOrComment
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.slice.ontology.api.LastModificationDate
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object ChangeClassLabelsOrCommentsQuery extends QueryBuilderHelper {

  def build(
    resourceClassIri: ResourceClassIri,
    labelOrComment: LabelOrComment,
    newValues: Seq[StringLiteralV2],
    lastModificationDate: LastModificationDate,
  ): UIO[Update] =
    ZIO
      .die(
        SparqlGenerationException(
          "All StringLiterals must have a language code when changing class labels or comments.",
        ),
      )
      .when(newValues.exists(_.language.isEmpty)) *> {

      val (ontologyIri, ontologyNS) = ontologyAndNamespace(resourceClassIri)
      val classIri                  = toRdfIri(resourceClassIri)
      val predicate                 = toRdfIri(labelOrComment)
      val oldValues                 = variable("oldValues")

      val deletePattern = List(
        ontologyIri.has(KB.lastModificationDate, toRdfLiteral(lastModificationDate)),
        classIri.has(predicate, oldValues),
      )

      for {
        insertPatterns <- buildInsertPatterns(ontologyIri, classIri, predicate, newValues)
        wherePatterns = List(
                          ontologyIri
                            .isA(OWL.ONTOLOGY)
                            .andHas(KB.lastModificationDate, toRdfLiteral(lastModificationDate)),
                          classIri.has(variable("p"), variable("o")),
                          classIri.has(predicate, oldValues).optional(),
                        )

        query = Queries
                  .MODIFY()
                  .prefix(KB.NS, RDFS.NS, XSD.NS, OWL.NS, ontologyNS)
                  .from(ontologyIri)
                  .delete(deletePattern: _*)
                  .into(ontologyIri)
                  .insert(insertPatterns: _*)
                  .where(wherePatterns: _*)
      } yield Update(query)
    }

  private def buildInsertPatterns(
    ontology: Iri,
    classIri: Iri,
    predicate: Iri,
    newValues: Seq[StringLiteralV2],
  ): UIO[Seq[TriplePattern]] = Clock.instant.map { now =>
    val ontologyModPattern = ontology.has(KB.lastModificationDate, toRdfLiteral(now))
    val newValuesPatterns  = newValues.map(toRdfLiteral).map(classIri.has(predicate, _))
    ontologyModPattern +: newValuesPatterns
  }
}
