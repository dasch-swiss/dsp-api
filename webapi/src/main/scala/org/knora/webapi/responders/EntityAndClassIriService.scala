/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import zio.ZLayer

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import dsp.errors.BadRequestException
import dsp.errors.DuplicateValueException
import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.SparqlSelectRequest
import org.knora.webapi.messages.util.rdf.SparqlSelectResult

/**
 * This service somewhat handles checking of ontology entities and some creation of entity IRIs.
 *
 * It was extracted from the base class of all responders in order to be able to break up the inheritance hierarchy
 * in the future - once we are porting more responders to the zio world.
 *
 * It is by no means complete, has already too many responsibilities and
 * will be subject to further refactoring once we extract more services.
 */
final case class EntityAndClassIriService(
  actorDeps: ActorDeps,
  stringFormatter: StringFormatter
) extends LazyLogging {
  private implicit val ec: ExecutionContext = actorDeps.executionContext
  private implicit val timeout: Timeout     = actorDeps.timeout

  private val appActor: ActorRef = actorDeps.appActor

  /**
   * Checks whether an entity is used in the triplestore.
   *
   * @param entityIri                 the IRI of the entity.
   * @param ignoreKnoraConstraints    if `true`, ignores the use of the entity in Knora subject or object constraints.
   * @param ignoreRdfSubjectAndObject if `true`, ignores the use of the entity in `rdf:subject` and `rdf:object`.
   *
   * @return `true` if the entity is used.
   */
  def isEntityUsed(
    entityIri: SmartIri,
    ignoreKnoraConstraints: Boolean = false,
    ignoreRdfSubjectAndObject: Boolean = false
  ): Future[Boolean] = {
    val query = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
      .isEntityUsed(entityIri.toInternalIri, ignoreKnoraConstraints, ignoreRdfSubjectAndObject)
      .toString()
    appActor
      .ask(SparqlSelectRequest(query))
      .mapTo[SparqlSelectResult]
      .map(_.results.bindings.nonEmpty)
  }

  /**
   * Throws an exception if an entity is used in the triplestore.
   *
   * @param entityIri the IRI of the entity.
   * @param errorFun                  a function that throws an exception. It will be called if the entity is used.
   * @param ignoreKnoraConstraints    if `true`, ignores the use of the entity in Knora subject or object constraints.
   * @param ignoreRdfSubjectAndObject if `true`, ignores the use of the entity in `rdf:subject` and `rdf:object`.
   */
  def throwIfEntityIsUsed(
    entityIri: SmartIri,
    ignoreKnoraConstraints: Boolean = false,
    ignoreRdfSubjectAndObject: Boolean = false,
    errorFun: => Nothing
  ): Future[Unit] =
    for {
      entityIsUsed: Boolean <- isEntityUsed(entityIri, ignoreKnoraConstraints, ignoreRdfSubjectAndObject)

      _ = if (entityIsUsed) {
            errorFun
          }
    } yield ()

  /**
   * Throws an exception if a class is used in data.
   *
   * @param classIri  the IRI of the class.
   * @param errorFun  a function that throws an exception. It will be called if the class is used.
   */
  def throwIfClassIsUsedInData(classIri: SmartIri, errorFun: => Nothing): Future[Unit] =
    for {
      classIsUsed: Boolean <- isClassUsedInData(classIri)
      _                     = if (classIsUsed) { errorFun }
    } yield ()

  /**
   * Checks whether an instance of a class (or any of its sub-classes) exists.
   *
   * @param classIri  the IRI of the class.
   * @return `true` if the class is used.
   */
  def isClassUsedInData(classIri: SmartIri): Future[Boolean] = {
    val query = org.knora.webapi.messages.twirl.queries.sparql.v2.txt.isClassUsedInData(classIri = classIri).toString()
    appActor.ask(SparqlSelectRequest(query)).mapTo[SparqlSelectResult].map(_.results.bindings.nonEmpty)
  }

  /**
   * Checks whether an entity with the provided custom IRI exists in the triplestore. If yes, throws an exception.
   * If no custom IRI was given, creates a random unused IRI.
   *
   * @param entityIri    the optional custom IRI of the entity.
   * @param iriFormatter the stringFormatter method that must be used to create a random IRI.
   * @return IRI of the entity.
   */
  def checkOrCreateEntityIri(entityIri: Option[SmartIri], iriFormatter: => IRI): Future[IRI] =
    entityIri match {
      case Some(customEntityIri: SmartIri) =>
        val entityIriAsString = customEntityIri.toString
        for {

          result <- stringFormatter.checkIriExists(entityIriAsString, appActor)
          _ = if (result) {
                throw DuplicateValueException(s"IRI: '$entityIriAsString' already exists, try another one.")
              }
          // Check that given entityIRI ends with a UUID
          ending: String = entityIriAsString.split('/').last
          _ = stringFormatter.validateBase64EncodedUuid(
                ending,
                throw BadRequestException(s"IRI: '$entityIriAsString' must end with a valid base 64 UUID.")
              )

        } yield entityIriAsString

      case None => stringFormatter.makeUnusedIri(iriFormatter, appActor, logger)
    }
}

object EntityAndClassIriService {
  val layer: ZLayer[ActorDeps with StringFormatter, Nothing, EntityAndClassIriService] =
    ZLayer.fromFunction(EntityAndClassIriService.apply _)
}
