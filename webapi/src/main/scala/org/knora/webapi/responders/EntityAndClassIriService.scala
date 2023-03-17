/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders
import akka.actor.ActorRef
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import dsp.errors.BadRequestException
import dsp.errors.DuplicateValueException
import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter

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
          _ = stringFormatter
                .validateBase64EncodedUuid(ending)
                .getOrElse(BadRequestException(s"IRI: '$entityIriAsString' must end with a valid base 64 UUID."))

        } yield entityIriAsString

      case None => stringFormatter.makeUnusedIri(iriFormatter, appActor, logger)
    }
}
