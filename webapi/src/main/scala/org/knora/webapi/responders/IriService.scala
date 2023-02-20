/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders

import com.typesafe.scalalogging.LazyLogging
import zio.ZIO
import zio._

import dsp.errors._
import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.StringFormatter.MAX_IRI_ATTEMPTS
import org.knora.webapi.store.triplestore.api.TriplestoreService

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
  triplestoreService: TriplestoreService,
  stringFormatter: StringFormatter
) extends LazyLogging {

  /**
   * Checks whether an entity with the provided custom IRI exists in the triplestore. If yes, throws an exception.
   * If no custom IRI was given, creates a random unused IRI.
   *
   * @param entityIri    the optional custom IRI of the entity.
   * @param iriFormatter the stringFormatter method that must be used to create a random IRI.
   * @return IRI of the entity.
   */
  def checkOrCreateEntityIriTask(entityIri: Option[SmartIri], iriFormatter: => IRI): Task[IRI] =
    entityIri match {
      case Some(customEntityIri: SmartIri) =>
        val entityIriAsString = customEntityIri.toString
        for {
          _ <- ZIO
                 .fail(DuplicateValueException(s"IRI: '$entityIriAsString' already exists, try another one."))
                 .whenZIO(checkIriExists(entityIriAsString))

          // Check that given entityIRI ends with a UUID
          ending: String = entityIriAsString.split('/').last
          _ <- ZIO.attempt(
                 stringFormatter.validateBase64EncodedUuid(
                   ending,
                   throw BadRequestException(s"IRI: '$entityIriAsString' must end with a valid base 64 UUID.")
                 )
               )

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
                      UpdateNotPerformedException(s"Could not make an unused new IRI after $MAX_IRI_ATTEMPTS attempts")
                    )
                  } else {
                    makeUnusedIriRec(attempts - 1)
                  }
      } yield result
    }
    makeUnusedIriRec(attempts = MAX_IRI_ATTEMPTS)
  }

  private def checkIriExists(entityIriAsString: IRI): Task[Boolean] = {
    val query = org.knora.webapi.messages.twirl.queries.sparql.admin.txt.checkIriExists(entityIriAsString).toString
    triplestoreService.sparqlHttpAsk(query).map(_.result)
  }
}

object IriService {
  val layer: ZLayer[TriplestoreService with StringFormatter, Nothing, IriService] =
    ZLayer.fromFunction(IriService.apply _)
}
