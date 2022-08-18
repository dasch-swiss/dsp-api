/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cache

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.messages.store.cacheservicemessages._
import org.knora.webapi.store.cache.api.CacheService
import zio._
import zio.metrics.Metric

import java.time.temporal.ChronoUnit
import zio.macros.accessible

@accessible
trait CacheServiceManager {
  def receive(msg: CacheServiceRequest): ZIO[Any, Throwable, Any]
}

object CacheServiceManager {
  val layer: ZLayer[CacheService, Nothing, CacheServiceManager] =
    ZLayer {
      for {
        cs <- ZIO.service[CacheService]
      } yield new CacheServiceManager {

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

        override def receive(msg: CacheServiceRequest) = msg match {
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
        def putUserADM(value: UserADM): Task[Unit] =
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
        def getUserADM(id: UserIdentifierADM): Task[Option[UserADM]] =
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
        def putProjectADM(value: ProjectADM): Task[Unit] =
          for {
            res <- cs.putProjectADM(value) @@ cacheServiceWriteProjectTimer.trackDuration
            // _   <- cacheServiceWriteProjectTimer.value.tap(value => ZIO.debug(value))
          } yield res

        /**
         * Retrieves the project stored under the identifier (either iri, shortname, or shortcode).
         *
         * @param identifier the project identifier.
         */
        def getProjectADM(id: ProjectIdentifierADM): Task[Option[ProjectADM]] =
          for {
            res <- cs.getProjectADM(id) @@ cacheServiceReadProjectTimer.trackDuration
            // _   <- cacheServiceReadProjectTimer.value.tap(value => ZIO.debug(value))
          } yield res

        /**
         * Get value stored under the key as a string.
         *
         * @param k the key.
         */
        def getStringValue(k: String): Task[Option[String]] =
          cs.getStringValue(k)

        /**
         * Store string or byte array value under key.
         *
         * @param k the key.
         * @param v the value.
         */
        def writeStringValue(k: String, v: String): Task[Unit] =
          cs.putStringValue(k, v)

        /**
         * Removes values for the provided keys. Any invalid keys are ignored.
         *
         * @param keys the keys.
         */
        def removeValues(keys: Set[String]): Task[Unit] =
          cs.removeValues(keys)

        /**
         * Flushes (removes) all stored content from the store.
         */
        def flushDB(requestingUser: UserADM): Task[Unit] =
          cs.flushDB(requestingUser)

        /**
         * Pings the cache service to see if it is available.
         */
        def ping(): UIO[CacheServiceStatusResponse] =
          cs.getStatus
      }
    }
}
