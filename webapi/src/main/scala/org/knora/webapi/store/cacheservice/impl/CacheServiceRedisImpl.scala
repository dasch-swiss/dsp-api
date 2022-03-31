/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cacheservice.impl

import com.typesafe.scalalogging.{LazyLogging, Logger}
import org.knora.webapi.exceptions.ForbiddenException
import org.knora.webapi.messages.admin.responder.projectsmessages.{
  ProjectADM,
  ProjectIdentifierADM,
  ProjectIdentifierType
}
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserIdentifierADM, UserIdentifierType}
import org.knora.webapi.messages.store.cacheservicemessages.{
  CacheServiceStatusNOK,
  CacheServiceStatusOK,
  CacheServiceStatusResponse
}
import org.knora.webapi.store.cacheservice.serialization.CacheSerialization
import org.knora.webapi.store.cacheservice.settings.CacheServiceSettings
import org.knora.webapi.store.cacheservice.api.{CacheService, EmptyKey, EmptyValue}
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

import zio._
import org.knora.webapi.store.cacheservice.config.RedisConfig

case class CacheServiceRedisImpl(pool: JedisPool) extends CacheService {

  /**
   * Stores the user under the IRI and additionally the IRI under the keys of
   * USERNAME and EMAIL:
   *
   * IRI -> byte array
   * username -> IRI
   * email -> IRI
   *
   * @param user the user to store.
   */
  def putUserADM(user: UserADM): Task[Unit] =
    for {
      bytes <- CacheSerialization.serialize(user)
      _     <- writeBytesValue(user.id, bytes)
      _     <- writeStringValue(user.username, user.id)
      _     <- writeStringValue(user.email, user.id)
    } yield ()

  /**
   * Retrieves the user stored under the identifier (either iri, username,
   * or email).
   *
   * The data is stored under the IRI key.
   * Additionally, the USERNAME and EMAIL keys point to the IRI key
   *
   * @param identifier the user identifier.
   */
  def getUserADM(identifier: UserIdentifierADM): Task[Option[UserADM]] =
    identifier.hasType match {
      case UserIdentifierType.Iri      => getUserByIri(identifier.toIri)
      case UserIdentifierType.Username => getUserByUsernameOrEmail(identifier.toUsername)
      case UserIdentifierType.Email    => getUserByUsernameOrEmail(identifier.toEmail)
    }

  /**
   * Retrieves the user stored under the IRI.
   *
   * @param id the user's IRI.
   * @return an optional [[UserADM]].
   */
  def getUserByIri(id: String): Task[Option[UserADM]] =
    for {
      maybeBytes <- getBytesValue(id)
      maybeUser <- maybeBytes match {
                     case Some(bytes) => CacheSerialization.deserialize[UserADM](bytes)
                     case None        => ZIO.succeed(None)
                   }
    } yield maybeUser

  /**
   * Retrieves the user stored under the username or email.
   *
   * @param usernameOrEmail of the user.
   * @return an optional [[UserADM]].
   */
  def getUserByUsernameOrEmail(usernameOrEmail: String): Task[Option[UserADM]] =
    for {
      maybeIriKey <- getStringValue(usernameOrEmail)
      maybeUser   <- getUserByIri(maybeIriKey.getOrElse("-")) //FIXME: not cool
    } yield maybeUser

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
  def putProjectADM(project: ProjectADM): Task[Unit] =
    for {
      bytes <- CacheSerialization.serialize(project)
      _     <- writeBytesValue(project.id, bytes)
      _     <- writeStringValue(project.shortcode, project.id)
      _     <- writeStringValue(project.shortname, project.id)
    } yield ()

  /**
   * Retrieves the project stored under the identifier (either iri, shortname, or shortcode).
   *
   * @param identifier the project identifier.
   */
  def getProjectADM(identifier: ProjectIdentifierADM): Task[Option[ProjectADM]] =
    // The data is stored under the IRI key.
    // Additionally, the SHORTNAME and SHORTCODE keys point to the IRI key
    identifier.hasType match {
      case ProjectIdentifierType.IRI       => getProjectByIri(identifier.toIri)
      case ProjectIdentifierType.SHORTCODE => getProjectByShortcodeOrShortname(identifier.toShortcode)
      case ProjectIdentifierType.SHORTNAME => getProjectByShortcodeOrShortname(identifier.toShortname)
    }

  /**
   * Retrieves the project stored under the IRI.
   *
   * @param id the project's IRI
   * @return an optional [[ProjectADM]].
   */
  def getProjectByIri(id: String): Task[Option[ProjectADM]] =
    for {
      maybeBytes <- getBytesValue(id)
      maybeProject <- maybeBytes match {
                        case Some(bytes) => CacheSerialization.deserialize[ProjectADM](bytes)
                        case None        => ZIO.succeed(None)
                      }
    } yield maybeProject

  /**
   * Retrieves the project stored under a SHORTCODE or SHORTNAME.
   *
   * @param shortcodeOrShortname of the project.
   * @return an optional [[ProjectADM]]
   */
  def getProjectByShortcodeOrShortname(shortcodeOrShortname: String): Task[Option[ProjectADM]] =
    for {
      maybeIriKey  <- getStringValue(shortcodeOrShortname)
      maybeProject <- getProjectByIri(maybeIriKey.getOrElse("-"))
    } yield maybeProject

  /**
   * Store string or byte array value under key.
   *
   * @param key   the key.
   * @param value the value.
   */
  def writeStringValue(key: String, value: String): Task[Unit] = Task.attempt {

    if (key.isEmpty)
      throw EmptyKey("The key under which the value should be written is empty. Aborting writing to redis.")

    if (value.isEmpty)
      throw EmptyValue("The string value is empty. Aborting writing to redis.")

    val conn: Jedis = pool.getResource
    try {
      conn.set(key, value)
      ()
    } finally {
      conn.close()
    }

  }.catchAll(ex => ZIO.logError(s"Writing to Redis failed: ${ex.getMessage}"))

  /**
   * Get value stored under the key as a string.
   *
   * @param maybeKey the key.
   */
  def getStringValue(key: String): Task[Option[String]] = {
    // FIXME: make it resource safe, i.e., use Scope and add finalizers for the connection
    for {
      conn  <- ZIO.attempt(pool.getResource)
      value <- ZIO.attemptBlocking(conn.get(key))
      res <- if (value == "nil".getBytes) Task.succeed(None)
             else Task.succeed(Some(value))
      _ = conn.close()
    } yield res
  }.catchAll(ex => ZIO.logError(s"Reading string from Redis failed: ${ex.getMessage}") *> Task.succeed(None))

  /**
   * Removes values for the provided keys. Any invalid keys are ignored.
   *
   * @param keys the keys.
   */
  def removeValues(keys: Set[String]): Task[Unit] = Task.attemptBlocking {

    // del takes a vararg so I nee to convert the set to a swq and then to vararg
    val conn: Jedis = pool.getResource
    try {
      conn.del(keys.toSeq: _*)
      ()
    } finally {
      conn.close()
    }

  }.catchAll(ex => ZIO.logError(s"Removing keys from Redis failed: ${ex.getMessage}"))

  /**
   * Store string or byte array value under key.
   *
   * @param key   the key.
   * @param value the value.
   */
  private def writeBytesValue(key: String, value: Array[Byte]): Task[Unit] = Task.attemptBlocking {

    if (key.isEmpty)
      throw EmptyKey("The key under which the value should be written is empty. Aborting writing to redis.")

    if (value.isEmpty)
      throw EmptyValue("The byte array value is empty. Aborting writing to redis.")

    val conn: Jedis = pool.getResource
    try {
      conn.set(key.getBytes, value)
      ()
    } finally {
      conn.close()
    }

  }.catchAll(ex => ZIO.logError(s"Writing to Redis failed: ${ex.getMessage}"))

  /**
   * Get value stored under the key as a byte array. If no value is found
   * under the key, then a [[None]] is returned..
   *
   * @param key the key.
   */
  private def getBytesValue(key: String): Task[Option[Array[Byte]]] =
    // FIXME: make it resource safe, i.e., use Scope and add finalizers for the connection
    for {
      conn  <- ZIO.attempt(pool.getResource).onError(ZIO.logErrorCause(_)).orDie
      value <- ZIO.attemptBlocking(conn.get(key.getBytes))
      res <- if (value == "nil".getBytes) Task.succeed(None)
             else Task.succeed(Some(value))
      _ = conn.close()
    } yield res

  /**
   * Flushes (removes) all stored content from the Redis store.
   */
  def flushDB(requestingUser: UserADM): Task[Unit] = Task.attemptBlocking {

    if (!requestingUser.isSystemUser) {
      throw ForbiddenException("Only the system user is allowed to perform this operation.")
    }

    val conn: Jedis = pool.getResource
    try {
      conn.flushDB()
      ()
    } finally {
      conn.close()
    }

  }.catchAll(ex => ZIO.logError(s"Flushing DB failed: ${ex.getMessage}"))

  /**
   * Pings the Redis store to see if it is available.
   */
  def ping(): Task[CacheServiceStatusResponse] = Task.attemptBlocking {

    val conn: Jedis = pool.getResource
    try {
      conn.ping("test")
      CacheServiceStatusOK
    } finally {
      conn.close()
    }
  }.catchAll(ex => ZIO.logError(s"Ping failed: ${ex.getMessage}") *> Task.succeed(CacheServiceStatusNOK))
}

object CacheServiceRedisImpl {
  val layer: ZLayer[RedisConfig, Nothing, CacheService] = {
    ZLayer {
      for {
        config <- ZIO.service[RedisConfig]
        pool <- ZIO
                  .attempt(new JedisPool(new JedisPoolConfig(), config.server, config.port1, config.port2))
                  .onError(ZIO.logErrorCause(_))
                  .orDie // the Redis Client Pool
      } yield CacheServiceRedisImpl(pool)
    }.tap(_ => ZIO.debug("Initializing Redis Cache Service"))
  }
}
