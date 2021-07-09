/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi
package responders

import exceptions.{BadRequestException, DuplicateValueException, UnexpectedMessageException}
import messages.store.triplestoremessages.SparqlSelectRequest
import messages.util.ResponderData
import messages.util.rdf.SparqlSelectResult
import messages.{SmartIri, StringFormatter}
import settings.{KnoraDispatchers, KnoraSettings, KnoraSettingsImpl}

import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import akka.util.Timeout
import com.typesafe.scalalogging.{LazyLogging, Logger}
import org.knora.webapi.store.cacheservice.settings.CacheServiceSettings

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * Responder helper methods.
  */
object Responder {

  /**
    * An responder use this method to handle unexpected request messages in a consistent way.
    *
    * @param message the message that was received.
    * @param log     a [[Logger]].
    * @param who     the responder receiving the message.
    */
  def handleUnexpectedMessage(message: Any, log: Logger, who: String): Future[Nothing] = {
    val unexpectedMessageException = UnexpectedMessageException(
      s"$who received an unexpected message $message of type ${message.getClass.getCanonicalName}")
    FastFuture.failed(unexpectedMessageException)
  }
}

/**
  * An abstract class providing values that are commonly used in Knora responders.
  */
abstract class Responder(responderData: ResponderData) extends LazyLogging {

  /**
    * The actor system.
    */
  protected implicit val system: ActorSystem = responderData.system

  /**
    * The execution context for futures created in Knora actors.
    */
  protected implicit val executionContext: ExecutionContext =
    system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  /**
    * The application settings.
    */
  protected val settings: KnoraSettingsImpl = responderData.knoraSettings

  /**
    * The Cache Service settings.
    */
  protected val cacheServiceSettings: CacheServiceSettings = responderData.cacheServiceSettings

  /**
    * The main application actor.
    */
  protected val appActor: ActorRef = responderData.appActor

  /**
    * The main application actor forwards messages to the responder manager.
    */
  protected val responderManager: ActorRef = responderData.appActor

  /**
    * The main application actor forwards messages to the store manager.
    */
  protected val storeManager: ActorRef = responderData.appActor

  /**
    * A string formatter.
    */
  protected implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  /**
    * The application's default timeout for `ask` messages.
    */
  protected implicit val timeout: Timeout = settings.defaultTimeout

  /**
    * Provides logging
    */
  protected val log: Logger = logger
  protected val loggingAdapter: LoggingAdapter = akka.event.Logging(system, this.getClass)

  /**
    * Checks whether an entity is used in the triplestore.
    *
    * @param entityIri                 the IRI of the entity.
    * @param ignoreKnoraConstraints    if `true`, ignores the use of the entity in Knora subject or object constraints.
    * @param ignoreRdfSubjectAndObject if `true`, ignores the use of the entity in `rdf:subject` and `rdf:object`.
    *
    * @return `true` if the entity is used.
    */
  protected def isEntityUsed(entityIri: SmartIri,
                             ignoreKnoraConstraints: Boolean = false,
                             ignoreRdfSubjectAndObject: Boolean = false): Future[Boolean] = {
    for {
      isEntityUsedSparql <- Future(
        org.knora.webapi.messages.twirl.queries.sparql.v2.txt
          .isEntityUsed(
            triplestore = settings.triplestoreType,
            entityIri = entityIri,
            ignoreKnoraConstraints = ignoreKnoraConstraints,
            ignoreRdfSubjectAndObject = ignoreRdfSubjectAndObject
          )
          .toString())

      isEntityUsedResponse: SparqlSelectResult <- (storeManager ? SparqlSelectRequest(isEntityUsedSparql))
        .mapTo[SparqlSelectResult]

    } yield isEntityUsedResponse.results.bindings.nonEmpty
  }

  /**
    * Throws an exception if an entity is used in the triplestore.
    *
    * @param entityIri                 the IRI of the entity.
    * @param errorFun                  a function that throws an exception. It will be called if the entity is used.
    * @param ignoreKnoraConstraints    if `true`, ignores the use of the entity in Knora subject or object constraints.
    * @param ignoreRdfSubjectAndObject if `true`, ignores the use of the entity in `rdf:subject` and `rdf:object`.
    */
  protected def throwIfEntityIsUsed(entityIri: SmartIri,
                                    errorFun: => Nothing,
                                    ignoreKnoraConstraints: Boolean = false,
                                    ignoreRdfSubjectAndObject: Boolean = false): Future[Unit] = {

    for {
      entityIsUsed: Boolean <- isEntityUsed(entityIri, ignoreKnoraConstraints, ignoreRdfSubjectAndObject)

      _ = if (entityIsUsed) {
        errorFun
      }
    } yield ()
  }

  /**
    * Checks whether an entity with the provided custom IRI exists in the triplestore, if yes, throws an exception.
    * If no custom IRI was given, creates a random unused IRI.
    *
    * @param entityIri    the optional custom IRI of the entity.
    * @param iriFormatter the stringFormatter method that must be used to create a random Iri.
    * @return IRI of the entity.
    */
  protected def checkOrCreateEntityIri(entityIri: Option[SmartIri], iriFormatter: => IRI): Future[IRI] = {

    entityIri match {
      case Some(customEntityIri: SmartIri) =>
        val entityIriAsString = customEntityIri.toString
        for {

          result <- stringFormatter.checkIriExists(entityIriAsString, storeManager)
          _ = if (result) {
            throw DuplicateValueException(s"IRI: '$entityIriAsString' already exists, try another one.")
          }
          // Check that given entityIRI ends with a UUID
          ending: String = entityIriAsString.split('/').last
          _ = stringFormatter.validateBase64EncodedUuid(
            ending,
            throw BadRequestException(s"IRI: '$entityIriAsString' must end with a valid base 64 UUID."))

        } yield entityIriAsString

      case None => stringFormatter.makeUnusedIri(iriFormatter, storeManager, loggingAdapter)
    }
  }

  /**
    * Make a new UUID considering optional custom UUID and custom IRI.
    * If a custom UUID is given, this method checks that it matches the ending of a given IRI, if there was any.
    * If no custom UUID is given for the entity, it checks if a custom IRI is given or not. If yes, it extracts the
    * UUID from the given IRI. If no custom IRI was given, it generates a random UUID.
    *
    * @param maybeCustomIri  the optional IRI.
    * @param maybeCustomUUID the optional UUID.
    * @return the new UUID.
    */
  protected def makeNewUUID(maybeCustomIri: Option[SmartIri], maybeCustomUUID: Option[UUID]): UUID = {
    // Is there any custom UUID given?
    maybeCustomUUID match {
      case Some(customUUID) =>
        // Yes. Check that if a custom IRI is given, it ends with the same UUID
        if (maybeCustomIri.nonEmpty && stringFormatter.base64DecodeUuid(maybeCustomIri.get.toString.split("/").last) != customUUID) {
          throw BadRequestException(
            s" Given custom IRI ${maybeCustomIri.get} should contain the given custom UUID ${stringFormatter
              .base64EncodeUuid(customUUID)}.")
        }
        customUUID
      case None =>
        // No. Is there a custom IRI given?
        maybeCustomIri match {
          case Some(customIri: SmartIri) =>
            // Yes. Get the UUID from the given IRI
            val uuidInIRI = customIri.toString.split("/").last
            stringFormatter.validateBase64EncodedUuid(
              uuidInIRI,
              throw BadRequestException(s"Given IRI $customIri does not contain a valid base64 UUID"))

          case None => UUID.randomUUID
        }
    }
  }
}
