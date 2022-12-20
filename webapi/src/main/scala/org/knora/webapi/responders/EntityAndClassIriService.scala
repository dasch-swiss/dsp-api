/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import zio.ZLayer

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import dsp.errors.BadRequestException
import dsp.errors.DuplicateValueException
import org.knora.webapi.IRI
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.SparqlSelectRequest
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.settings.KnoraDispatchers

final case class EntityAndClassIriService(
  actorSystem: ActorSystem,
  appActor: ActorRef,
  appConfig: AppConfig,
  stringFormatter: StringFormatter
) extends LazyLogging {

  private implicit val system: ActorSystem = actorSystem
  private implicit val executionContext: ExecutionContext =
    system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)
  private implicit val timeout: Timeout = appConfig.defaultTimeoutAsDuration

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
      .isEntityUsed(entityIri, ignoreKnoraConstraints, ignoreRdfSubjectAndObject)
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
    errorFun: => Nothing,
    ignoreKnoraConstraints: Boolean = false,
    ignoreRdfSubjectAndObject: Boolean = false
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
   * Checks whether an instance of a class (or any ob its sub-classes) exists
   *
   * @param classIri  the IRI of the class.
   * @return `true` if the class is used.
   */
  def isClassUsedInData(classIri: SmartIri): Future[Boolean] = {
    val query = org.knora.webapi.messages.twirl.queries.sparql.v2.txt.isClassUsedInData(classIri = classIri).toString()
    appActor.ask(SparqlSelectRequest(query)).mapTo[SparqlSelectResult].map(_.results.bindings.nonEmpty)
  }

  /**
   * Checks whether an entity with the provided custom IRI exists in the triplestore, if yes, throws an exception.
   * If no custom IRI was given, creates a random unused IRI.
   *
   * @param entityIri    the optional custom IRI of the entity.
   * @param iriFormatter the stringFormatter method that must be used to create a random Iri.
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
  val layer = ZLayer.fromFunction(EntityAndClassIriService.apply _)
}
