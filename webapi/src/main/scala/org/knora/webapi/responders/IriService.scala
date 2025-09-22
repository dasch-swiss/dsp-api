/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders

import zio.*

import dsp.errors.*
import dsp.valueobjects.UuidUtil
import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.StringFormatter.MAX_IRI_ATTEMPTS
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

/**
 * This service somewhat handles checking of ontology entities and some creation of entity IRIs.
 *
 * It was extracted from the base class of all responders in order to be able to break up the inheritance hierarchy
 * in the future - once we are porting more responders to the zio world.
 *
 * It is by no means complete, has already too many responsibilities and
 * will be subject to further refactoring once we extract more services.
 */
final case class IriService(
  private val iriConverter: IriConverter,
  private val triplestore: TriplestoreService,
  private val stringFormatter: StringFormatter,
) {

  /**
   * Checks whether an entity is used in the triplestore (in data or ontologies).
   *
   * @param iri                 the IRI of the entity.
   * @param ignoreKnoraConstraints    if `true`, ignores the use of the entity in Knora subject or object constraints.
   * @param ignoreRdfSubjectAndObject if `true`, ignores the use of the entity in `rdf:subject` and `rdf:object`.
   *
   * @return `true` if the entity is used.
   */
  def isEntityUsed(
    iri: SmartIri,
    ignoreKnoraConstraints: Boolean = false,
    ignoreRdfSubjectAndObject: Boolean = false,
  ): Task[Boolean] = triplestore
    .query(Ask(sparql.v2.txt.isEntityUsed(iri.toInternalIri, ignoreKnoraConstraints, ignoreRdfSubjectAndObject)))

  /**
   * Checks whether an instance of a class (or any of its sub-classes) exists.
   *
   * @param classIri  the IRI of the class.
   * @return `true` if the class is used.
   */
  def isClassUsedInData(classIri: SmartIri): Task[Boolean] =
    triplestore.query(Select(sparql.v2.txt.isClassUsedInData(classIri))).map(_.nonEmpty)

  def checkOrCreateNewUserIri(entityIri: Option[UserIri]): Task[UserIri] =
    for {
      // check the custom IRI; if not given, create an unused IRI
      customUserIri <- ZIO.foreach(entityIri.map(_.value))(iriConverter.asSmartIri)
      userIriStr    <- checkOrCreateEntityIri(customUserIri, UserIri.makeNew.value)
      userIri <- ZIO
                   .fromEither(UserIri.from(userIriStr))
                   .orElseFail(BadRequestException(s"Invalid User IRI: $userIriStr"))
    } yield userIri

  def checkOrCreateNewGroupIri(entityIri: Option[GroupIri], shortcode: Shortcode): Task[GroupIri] =
    for {
      iriToSmartIri            <- ZIO.foreach(entityIri.map(_.value))(iriConverter.asSmartIri)
      checkedCustomIriOrNewIri <- checkOrCreateEntityIri(iriToSmartIri, GroupIri.makeNew(shortcode).value)
      iri <- ZIO
               .fromEither(GroupIri.from(checkedCustomIriOrNewIri))
               .orElseFail(BadRequestException(s"Invalid Group IRI: $checkedCustomIriOrNewIri"))
    } yield iri

  /**
   * Checks whether an entity with the provided custom IRI exists in the triplestore. If yes, throws an exception.
   * If no custom IRI was given, creates a random unused IRI.
   *
   * @param entityIri    the optional custom IRI of the entity.
   * @param iriFormatter the stringFormatter method that must be used to create a random IRI.
   * @return IRI of the entity.
   */
  def checkOrCreateEntityIri(entityIri: Option[SmartIri], iriFormatter: => IRI): Task[IRI] =
    entityIri match {
      case Some(customEntityIri: SmartIri) =>
        val entityIriAsString = customEntityIri.toString
        for {
          _ <- ZIO
                 .fail(DuplicateValueException(s"IRI: '$entityIriAsString' already exists, try another one."))
                 .whenZIO(checkIriExists(entityIriAsString))

          // Check that given entityIRI ends with a UUID
          ending: String = UuidUtil.fromIri(entityIriAsString)
          _ <- ZIO
                 .fromTry(UuidUtil.base64Decode(ending))
                 .orElseFail(BadRequestException(s"IRI: '$entityIriAsString' must end with a valid base 64 UUID."))
        } yield entityIriAsString

      case None => makeUnusedIri(iriFormatter)
    }

  def makeUnusedIri(iriFun: => IRI): Task[IRI] = {
    def makeUnusedIriRec(attempts: Int): Task[IRI] = {
      val newIri = iriFun
      for {
        iriExists <- checkIriExists(newIri)
        result <- if (!iriExists) {
                    ZIO.succeed(newIri)
                  } else if (attempts == 0) {
                    ZIO.fail(
                      UpdateNotPerformedException(s"Could not make an unused new IRI after $MAX_IRI_ATTEMPTS attempts"),
                    )
                  } else {
                    makeUnusedIriRec(attempts - 1)
                  }
      } yield result
    }
    makeUnusedIriRec(attempts = MAX_IRI_ATTEMPTS)
  }

  def checkIriExists(iri: IRI): Task[Boolean] = triplestore.query(Ask(sparql.admin.txt.checkIriExists(iri)))
}

object IriService {
  val layer = ZLayer.derive[IriService]
}
