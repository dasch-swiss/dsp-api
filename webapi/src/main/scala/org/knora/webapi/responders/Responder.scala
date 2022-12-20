/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi
package responders

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.http.scaladsl.util.FastFuture
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.scalalogging.Logger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import dsp.errors._
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.store.cache.settings.CacheServiceSettings

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
      s"$who received an unexpected message $message of type ${message.getClass.getCanonicalName}"
    )
    FastFuture.failed(unexpectedMessageException)
  }
}

/**
 * An abstract class providing values that are commonly used in Knora responders.
 */
abstract class Responder(responderData: ResponderData) extends LazyLogging {

  protected implicit val system: ActorSystem              = responderData.system
  protected implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  protected implicit val timeout: Timeout                 = responderData.appConfig.defaultTimeoutAsDuration
  protected implicit val executionContext: ExecutionContext =
    system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  protected val cacheServiceSettings: CacheServiceSettings = responderData.cacheServiceSettings
  protected val appActor: ActorRef                         = responderData.appActor
  protected val log: Logger                                = logger

  private val iriService = EntityAndClassIriService(system, appActor, responderData.appConfig, stringFormatter)

  def isEntityUsed(
    entityIri: SmartIri,
    ignoreKnoraConstraints: Boolean = false,
    ignoreRdfSubjectAndObject: Boolean = false
  ): Future[Boolean] = iriService.isEntityUsed(entityIri, ignoreKnoraConstraints, ignoreRdfSubjectAndObject)

  def throwIfEntityIsUsed(
    entityIri: SmartIri,
    errorFun: => Nothing,
    ignoreKnoraConstraints: Boolean = false,
    ignoreRdfSubjectAndObject: Boolean = false
  ): Future[Unit] =
    iriService.throwIfEntityIsUsed(entityIri, errorFun, ignoreKnoraConstraints, ignoreRdfSubjectAndObject)

  def throwIfClassIsUsedInData(classIri: SmartIri, errorFun: => Nothing): Future[Unit] =
    iriService.throwIfClassIsUsedInData(classIri, errorFun)

  def checkOrCreateEntityIri(entityIri: Option[SmartIri], iriFormatter: => IRI): Future[IRI] =
    iriService.checkOrCreateEntityIri(entityIri, iriFormatter)
}
