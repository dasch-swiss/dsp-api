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
import org.knora.webapi.util.ActorUtil.future2Message

import scala.concurrent.{ExecutionContext, Future}

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
  protected implicit val ec: ExecutionContext = context.system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  def receive: Receive = {
    case CacheServicePutUserADM(value)         => future2Message(sender(), putUserADM(value), log)
    case CacheServiceGetUserADM(identifier)    => future2Message(sender(), getUserADM(identifier), log)
    case CacheServicePutProjectADM(value)      => future2Message(sender(), putProjectADM(value), log)
    case CacheServiceGetProjectADM(identifier) => future2Message(sender(), getProjectADM(identifier), log)
    case CacheServicePutString(key, value)     => future2Message(sender(), writeStringValue(key, value), log)
    case CacheServiceGetString(key)            => future2Message(sender(), getStringValue(key), log)
    case CacheServiceRemoveValues(keys)        => future2Message(sender(), removeValues(keys), log)
    case CacheServiceFlushDB(requestingUser)   => future2Message(sender(), flushDB(requestingUser), log)
    case CacheServiceGetStatus                 => future2Message(sender(), ping(), log)
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
  private def putUserADM(value: UserADM): Future[Boolean] = tracedFuture("caches-service-write-user") {
    cs.putUserADM(value)
  }

  /**
   * Retrieves the user stored under the identifier (either iri, username,
   * or email).
   *
   * @param identifier the project identifier.
   */
  private def getUserADM(identifier: UserIdentifierADM): Future[Option[UserADM]] =
    tracedFuture("cache-service-get-user") {
      cs.getUserADM(identifier)
    }

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
  private def putProjectADM(value: ProjectADM)(implicit ec: ExecutionContext): Future[Boolean] =
    tracedFuture("cache-service-write-project") {
      cs.putProjectADM(value)
    }

  /**
   * Retrieves the project stored under the identifier (either iri, shortname, or shortcode).
   *
   * @param identifier the project identifier.
   */
  private def getProjectADM(
    identifier: ProjectIdentifierADM
  )(implicit ec: ExecutionContext): Future[Option[ProjectADM]] =
    tracedFuture("cache-read-project") {
      cs.getProjectADM(identifier)
    }

  /**
   * Get value stored under the key as a string.
   *
   * @param maybeKey the key.
   */
  private def getStringValue(maybeKey: Option[String]): Future[Option[String]] =
    tracedFuture("cache-service-get-string") {
      cs.getStringValue(maybeKey)
    }

  /**
   * Store string or byte array value under key.
   *
   * @param key   the key.
   * @param value the value.
   */
  private def writeStringValue(key: String, value: String): Future[Boolean] =
    tracedFuture("cache-service-write-string") {
      cs.writeStringValue(key, value)
    }

  /**
   * Removes values for the provided keys. Any invalid keys are ignored.
   *
   * @param keys the keys.
   */
  private def removeValues(keys: Set[String]): Future[Boolean] =
    tracedFuture("cache-remove-values") {
      cs.removeValues(keys)
    }

  /**
   * Flushes (removes) all stored content from the Redis store.
   */
  private def flushDB(requestingUser: UserADM): Future[CacheServiceFlushDBACK] =
    tracedFuture("cache-flush") {
      cs.flushDB(requestingUser)
    }

  /**
   * Pings the cache service to see if it is available.
   */
  private def ping(): Future[CacheServiceStatusResponse] =
    cs.ping()
}
