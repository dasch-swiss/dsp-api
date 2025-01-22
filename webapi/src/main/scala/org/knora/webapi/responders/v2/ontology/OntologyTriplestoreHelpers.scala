/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.ontology

import zio.*

import java.time.Instant
import scala.collection.immutable

import dsp.errors.*
import org.knora.webapi.*
import org.knora.webapi.config.Features
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.v2.responder.ontologymessages.*
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

final case class OntologyTriplestoreHelpers(
  features: Features,
  ontologyRepo: OntologyRepo,
  triplestore: TriplestoreService,
  stringFormatter: StringFormatter,
) {

  /**
   * Checks that the last modification date of an ontology is the same as the one we expect it to be. If not, return
   * an error message fitting for the "before update" case.
   *
   * @param ontologyIri          the IRI of the ontology.
   * @param expectedLastModificationDate the last modification date that should now be attached to the ontology.
   * @return a failed Future if the expected last modification date is not found.
   */
  def checkOntologyLastModificationDate(
    ontologyIri: SmartIri,
    expectedLastModificationDate: Instant,
  ): Task[Unit] =
    val ontologyIriExternal = ontologyIri.toComplexSchema.toString
    for {
      lmd <- ontologyRepo
               .findById(ontologyIri.toInternalIri)
               .map(_.flatMap(_.ontologyMetadata.lastModificationDate))
               .someOrFail(NotFoundException(s"Ontology $ontologyIriExternal not found."))
      _ <- ZIO.fail {
             val msg =
               s"Ontology $ontologyIriExternal has been modified by another user, please reload it and try again."
             EditConflictException(msg)
           }.when(!features.disableLastModificationDateCheck && lmd != expectedLastModificationDate)
    } yield ()

  /**
   * Gets the set of subjects that refer to an ontology or its entities.
   *
   * @param ontology the ontology.
   * @return the set of subjects that refer to the ontology or its entities.
   */
  def getSubjectsUsingOntology(ontology: ReadOntologyV2): Task[Set[IRI]] = {
    val query = sparql.v2.txt
      .isOntologyUsed(
        ontologyNamedGraphIri = ontology.ontologyMetadata.ontologyIri,
        classIris = ontology.classes.keySet,
        propertyIris = ontology.properties.keySet,
      )
    triplestore.query(Select(query)).map(_.getColOrThrow("s").toSet)
  }
}

object OntologyTriplestoreHelpers { val layer = ZLayer.derive[OntologyTriplestoreHelpers] }
