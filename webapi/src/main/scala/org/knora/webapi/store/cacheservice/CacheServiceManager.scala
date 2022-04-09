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

case class CacheServiceManager(cs: CacheService) {

  val cacheServiceWriteUserTimer = Metric
    .timer(
      name = "cache-service-write-user",
      chronoUnit = ChronoUnit.NANOS
    )

  val cacheServiceWriteProjectTimer = Metric
    .timer(
      name = "cache-service-write-project",
      chronoUnit = ChronoUnit.NANOS
    )

  val cacheServiceReadProjectTimer = Metric
    .timer(
      name = "cache-service-read-project",
      chronoUnit = ChronoUnit.NANOS
    )

  def receive(msg: CacheServiceRequest) = msg match {
    case CacheServicePutUserADM(value)         => putUserADM(value)
    case CacheServiceGetUserADM(identifier)    => getUserADM(identifier)
    case CacheServicePutProjectADM(value)      => putProjectADM(value)
    case CacheServiceGetProjectADM(identifier) => getProjectADM(identifier)
    case CacheServicePutString(key, value)     => writeStringValue(key, value)
    case CacheServiceGetString(key)            => getStringValue(key)
    case CacheServiceRemoveValues(keys)        => removeValues(keys)
    case CacheServiceFlushDB(requestingUser)   => flushDB(requestingUser)
    case CacheServiceGetStatus                 => ping()
    case other                                 => ZIO.logError(s"CacheServiceManager received an unexpected message: $other")
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
    for {
      res <- cs.putUserADM(value) @@ cacheServiceWriteUserTimer.trackDuration
      // _   <- cacheServiceWriteUserTimer.value.tap(value => ZIO.debug(value))
    } yield res

  /**
   * Retrieves the user stored under the identifier (either iri, username,
   * or email).
   *
   * @param id the project identifier.
   */
  private def getUserADM(id: UserIdentifierADM): Task[Option[UserADM]] =
    cs.getUserADM(id)

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
    for {
      res <- cs.putProjectADM(value) @@ cacheServiceWriteProjectTimer.trackDuration
      // _   <- cacheServiceWriteProjectTimer.value.tap(value => ZIO.debug(value))
    } yield res

  /**
   * Retrieves the project stored under the identifier (either iri, shortname, or shortcode).
   *
   * @param identifier the project identifier.
   */
  private def getProjectADM(id: ProjectIdentifierADM): Task[Option[ProjectADM]] =
    for {
      res <- cs.getProjectADM(id) @@ cacheServiceReadProjectTimer.trackDuration
      // _   <- cacheServiceReadProjectTimer.value.tap(value => ZIO.debug(value))
    } yield res

  /**
   * Get value stored under the key as a string.
   *
   * @param k the key.
   */
  private def getStringValue(k: String): Task[Option[String]] =
    cs.getStringValue(k)

  /**
   * Store string or byte array value under key.
   *
   * @param k the key.
   * @param v the value.
   */
  private def writeStringValue(k: String, v: String): Task[Unit] =
    cs.putStringValue(k, v)

  /**
   * Removes values for the provided keys. Any invalid keys are ignored.
   *
   * @param keys the keys.
   */
  private def removeValues(keys: Set[String]): Task[Unit] =
    cs.removeValues(keys)

  /**
   * Flushes (removes) all stored content from the store.
   */
  private def flushDB(requestingUser: UserADM): Task[Unit] =
    cs.flushDB(requestingUser)

  /**
   * Pings the cache service to see if it is available.
   */
  private def ping(): Task[CacheServiceStatusResponse] =
    cs.ping()
}

object CacheServiceManager {
  val layer: ZLayer[CacheService, Nothing, CacheServiceManager] = {
    ZLayer {
      for {
        cache <- ZIO.service[CacheService]
      } yield CacheServiceManager(cache)
    }
  }
}
