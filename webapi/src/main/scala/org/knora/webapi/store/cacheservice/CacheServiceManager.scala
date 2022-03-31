/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cacheservice

import akka.actor.{Actor, ActorLogging, ActorSystem, Status}
import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.exceptions.UnexpectedMessageException
import org.knora.webapi.instrumentation.InstrumentationSupport
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectIdentifierADM}
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserIdentifierADM}
import org.knora.webapi.messages.store.cacheservicemessages._
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.util.ActorUtil.zio2Message
import org.knora.webapi.store.cacheservice.api.CacheService

import scala.concurrent.{ExecutionContext, Future}
import zio._
import zio.metrics._
import zio.metrics.Metric
import zio.metrics.MetricLabel
import java.time.temporal.ChronoUnit
import zio.metrics.MetricClient

class CacheServiceManager(cs: CacheService)
    extends Actor
    with ActorLogging
    with LazyLogging
    with InstrumentationSupport {

  /**
   * The Knora Akka actor system.
   */
  protected implicit val _system: ActorSystem = context.system

  /**
   * The Akka actor system's execution context for futures.
   */
  // protected implicit val ec: ExecutionContext = context.system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  val cacheServiceWriteUserTimer = Metric
    .timer(
      name = "cache-service-write-user",
      chronoUnit = ChronoUnit.MILLIS
    )
    .trackDuration

  def receive: Receive = {
    case CacheServicePutUserADM(value)         => zio2Message(sender(), putUserADM(value), log)
    case CacheServiceGetUserADM(identifier)    => zio2Message(sender(), getUserADM(identifier), log)
    case CacheServicePutProjectADM(value)      => zio2Message(sender(), putProjectADM(value), log)
    case CacheServiceGetProjectADM(identifier) => zio2Message(sender(), getProjectADM(identifier), log)
    case CacheServicePutString(key, value)     => zio2Message(sender(), writeStringValue(key, value), log)
    case CacheServiceGetString(key)            => zio2Message(sender(), getStringValue(key), log)
    case CacheServiceRemoveValues(keys)        => zio2Message(sender(), removeValues(keys), log)
    case CacheServiceFlushDB(requestingUser)   => zio2Message(sender(), flushDB(requestingUser), log)
    case CacheServiceGetStatus                 => zio2Message(sender(), ping(), log)
    case other =>
      sender() ! Status.Failure(UnexpectedMessageException(s"RedisManager received an unexpected message: $other"))
  }

  /**
   * Stores the user under the IRI and additionally the IRI under the keys of
   * USERNAME and EMAIL:
   *
   * IRI -> byte array
   * username -> IRI
   * email -> IRI
   *
   * @param value the stored value
   */
  private def putUserADM(value: UserADM): Task[Unit] =
    cs.putUserADM(value) // cleanup

  /**
   * Retrieves the user stored under the identifier (either iri, username,
   * or email).
   *
   * @param id the project identifier.
   */
  private def getUserADM(id: UserIdentifierADM): Task[Option[UserADM]] =
    CacheService(_.getUserADM(id)).provide(cs)

  /**
   * Stores the project under the IRI and additionally the IRI under the keys
   * of SHORTCODE and SHORTNAME:
   *
   * IRI -> byte array
   * shortname -> IRI
   * shortcode -> IRI
   *
   * @param value the stored value
   */
  private def putProjectADM(value: ProjectADM): Task[Unit] =
    CacheService(_.putProjectADM(value)).provide(cs)

  /**
   * Retrieves the project stored under the identifier (either iri, shortname, or shortcode).
   *
   * @param identifier the project identifier.
   */
  private def getProjectADM(id: ProjectIdentifierADM): Task[Option[ProjectADM]] =
    CacheService(_.getProjectADM(id)).provide(cs)

  /**
   * Get value stored under the key as a string.
   *
   * @param k the key.
   */
  private def getStringValue(k: String): Task[Option[String]] =
    CacheService(_.getStringValue(k)).provide(cs)

  /**
   * Store string or byte array value under key.
   *
   * @param k the key.
   * @param k the value.
   */
  private def writeStringValue(k: String, v: String): Task[Unit] =
    CacheService(_.writeStringValue(k, v)).provide(cs)

  /**
   * Removes values for the provided keys. Any invalid keys are ignored.
   *
   * @param keys the keys.
   */
  private def removeValues(keys: Set[String]): Task[Unit] =
    CacheService(_.removeValues(keys)).provide(cs)

  /**
   * Flushes (removes) all stored content from the Redis store.
   */
  private def flushDB(requestingUser: UserADM): Task[Unit] =
    CacheService(_.flushDB(requestingUser)).provide(cs)

  /**
   * Pings the cache service to see if it is available.
   */
  private def ping(): Task[CacheServiceStatusResponse] =
    CacheService(_.ping()).provide(cs)
}
