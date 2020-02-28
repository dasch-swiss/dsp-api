/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.responders

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import akka.util.Timeout
import com.typesafe.scalalogging.{LazyLogging, Logger}
import org.knora.webapi.messages.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse}
import org.knora.webapi.messages.v2.responder.resourcemessages.{ReadResourceV2, ReadResourcesSequenceV2, ResourcesGetRequestV2}
import org.knora.webapi.util.{SmartIri, StringFormatter}
import org.knora.webapi.{ApiV2Complex, InconsistentTriplestoreDataException, KnoraDispatchers, KnoraSystemInstances, Settings, SettingsImpl, UnexpectedMessageException}

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
        val unexpectedMessageException = UnexpectedMessageException(s"$who received an unexpected message $message of type ${message.getClass.getCanonicalName}")
        FastFuture.failed(unexpectedMessageException)
    }
}

/**
 * Data needed to be passed to each responder.
 *
 * @param system   the actor system.
 * @param appActor the main application actor ActorRef.
 */
case class ResponderData(system: ActorSystem, appActor: ActorRef)

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
    protected implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

    /**
     * The application settings.
     */
    protected val settings: SettingsImpl = Settings(system)

    /**
     * The reference to the main application actor which will forward messages
     * for the responder manager to the responder manager.
     */
    protected val responderManager: ActorRef = responderData.appActor

    /**
     * The reference to the main application actor which will forward messages
     * for the store manager to the store manager.
     */
    protected val storeManager: ActorRef = responderData.appActor

    /**
     * The reference to the main application actor
     */
    protected val appActor: ActorRef = responderData.appActor

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
    val log: Logger = logger

    protected lazy val forbiddenResourceFuture: Future[ReadResourceV2] = {
        for {
            forbiddenResourceSeq: ReadResourcesSequenceV2 <- (responderManager ? ResourcesGetRequestV2(
                resourceIris = Seq(StringFormatter.ForbiddenResourceIri),
                targetSchema = ApiV2Complex, // This has no effect, because ForbiddenResource has no values.
                requestingUser = KnoraSystemInstances.Users.SystemUser)).mapTo[ReadResourcesSequenceV2]
        } yield forbiddenResourceSeq.toResource(StringFormatter.ForbiddenResourceIri)
    }

    /**
     * Checks whether an entity is used in the triplestore.
     *
     * @param entityIri                 the IRI of the entity.
     * @param errorFun                  a function that throws an exception. It will be called if the entity is used.
     * @param ignoreKnoraConstraints    if `true`, ignores the use of the entity in Knora subject or object constraints.
     * @param ignoreRdfSubjectAndObject if `true`, ignores the use of the entity in `rdf:subject` and `rdf:object`.
     */
    protected def isEntityUsed(entityIri: SmartIri,
                               errorFun: => Nothing,
                               ignoreKnoraConstraints: Boolean = false,
                               ignoreRdfSubjectAndObject: Boolean = false): Future[Unit] = {
        // #sparql-select
        for {
            isEntityUsedSparql <- Future(queries.sparql.v2.txt.isEntityUsed(
                triplestore = settings.triplestoreType,
                entityIri = entityIri,
                ignoreKnoraConstraints = ignoreKnoraConstraints,
                ignoreRdfSubjectAndObject = ignoreRdfSubjectAndObject
            ).toString())

            isEntityUsedResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(isEntityUsedSparql)).mapTo[SparqlSelectResponse]
            // #sparql-select

            _ = if (isEntityUsedResponse.results.bindings.nonEmpty) {
                errorFun
            }
        } yield ()
    }

    /**
     * Checks whether an entity exists in the triplestore.
     *
     * @param entityIri the IRI of the entity.
     * @return `true` if the entity exists.
     */
    protected def checkEntityExists(entityIri: SmartIri): Future[Boolean] = {
        for {
            checkEntityExistsSparql <- Future(queries.sparql.v2.txt.checkEntityExists(
                triplestore = settings.triplestoreType,
                entityIri = entityIri
            ).toString())

            entityExistsResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(checkEntityExistsSparql)).mapTo[SparqlSelectResponse]
            result: Boolean = entityExistsResponse.results.bindings.nonEmpty
        } yield result
    }
}
