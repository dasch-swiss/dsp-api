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
import javassist.bytecode.ByteArray

class CacheServiceRedisImpl(pool: Task[JedisPool]) extends CacheService with LazyLogging {

  // this is needed for time measurements using 'org.knora.webapi.Timing'

  implicit val l: Logger = logger

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
      _ <- writeBytesValue(user.id, bytes).catchAll(ex =>
             ZIO.logWarning(s"Aborting writing 'UserADM' to Redis - ${ex.getMessage()}")
           )
      _ <- writeStringValue(user.username, user.id)
      _ <- writeStringValue(user.email, user.id)
    } yield ()

  /**
   * Retrieves the user stored under the identifier (either iri, username,
   * or email).
   *
   * @param identifier the user identifier.
   */
  def getUserADM(identifier: UserIdentifierADM): Task[Option[UserADM]] = {
    // The data is stored under the IRI key.
    // Additionally, the USERNAME and EMAIL keys point to the IRI key
    val resultFuture: Future[Option[UserADM]] = identifier.hasType match {
      case UserIdentifierType.Iri =>
        for {
          maybeBytes: Option[Array[Byte]] <- getBytesValue(identifier.toIriOption)
          maybeUser: Option[UserADM] <- maybeBytes match {
                                          case Some(bytes) => CacheSerialization.deserialize[UserADM](bytes)
                                          case None        => FastFuture.successful(None)
                                        }
        } yield maybeUser

      case UserIdentifierType.Username =>
        for {
          maybeIriKey: Option[String]     <- getStringValue(identifier.toUsernameOption)
          maybeBytes: Option[Array[Byte]] <- getBytesValue(maybeIriKey)
          maybeUser: Option[UserADM] <- maybeBytes match {
                                          case Some(bytes) => CacheSerialization.deserialize[UserADM](bytes)
                                          case None        => FastFuture.successful(None)
                                        }
        } yield maybeUser

      case UserIdentifierType.Email =>
        for {
          maybeIriKey: Option[String]     <- getStringValue(identifier.toEmailOption)
          maybeBytes: Option[Array[Byte]] <- getBytesValue(maybeIriKey)
          maybeUser: Option[UserADM] <- maybeBytes match {
                                          case Some(bytes) => CacheSerialization.deserialize[UserADM](bytes)
                                          case None        => FastFuture.successful(None)
                                        }
        } yield maybeUser
    }

    val recoverableResultFuture = resultFuture.recover { case e: Exception =>
      logger.warn("Aborting reading 'UserADM' from Redis - {}", e.getMessage)
      None
    }

    recoverableResultFuture
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
  def putProjectADM(value: ProjectADM): Task[Unit] = {
    val resultFuture = for {
      bytes: Array[Byte] <- CacheSerialization.serialize(value)
      result: Boolean    <- writeBytesValue(value.id, bytes)
      _                   = writeStringValue(value.shortcode, value.id)
      _                   = writeStringValue(value.shortname, value.id)
    } yield result

    val recoverableResultFuture = resultFuture.recover { case e: Exception =>
      logger.warn("Aborting writing 'ProjectADM' to Redis - {}", e.getMessage)
      false
    }

    recoverableResultFuture
  }

  /**
   * Retrieves the project stored under the identifier (either iri, shortname, or shortcode).
   *
   * @param identifier the project identifier.
   */
  def getProjectADM(identifier: ProjectIdentifierADM): Task[Option[ProjectADM]] = {

    // The data is stored under the IRI key.
    // Additionally, the SHORTNAME and SHORTCODE keys point to the IRI key
    val resultFuture: Future[Option[ProjectADM]] = identifier.hasType match {
      case ProjectIdentifierType.IRI =>
        for {
          maybeBytes <- getBytesValue(identifier.toIriOption)
          maybeProject <- maybeBytes match {
                            case Some(bytes) => CacheSerialization.deserialize[ProjectADM](bytes)
                            case None        => FastFuture.successful(None)
                          }
        } yield maybeProject
      case ProjectIdentifierType.SHORTCODE =>
        for {
          maybeIriKey <- getStringValue(identifier.toShortcodeOption)
          maybeBytes  <- getBytesValue(maybeIriKey)
          maybeProject: Option[ProjectADM] <- maybeBytes match {
                                                case Some(bytes) => CacheSerialization.deserialize[ProjectADM](bytes)
                                                case None        => FastFuture.successful(None)
                                              }
        } yield maybeProject
      case ProjectIdentifierType.SHORTNAME =>
        for {
          maybeIriKey <- getStringValue(identifier.toShortnameOption)
          maybeBytes  <- getBytesValue(maybeIriKey)
          maybeProject: Option[ProjectADM] <- maybeBytes match {
                                                case Some(bytes) => CacheSerialization.deserialize[ProjectADM](bytes)
                                                case None        => FastFuture.successful(None)
                                              }
        } yield maybeProject
    }

    val recoverableResultFuture = resultFuture.recover { case e: Exception =>
      logger.warn("Aborting reading 'ProjectADM' from Redis - {}", e.getMessage)
      None
    }

    recoverableResultFuture
  }

  /**
   * Store string or byte array value under key.
   *
   * @param key   the key.
   * @param value the value.
   */
  def writeStringValue(key: String, value: String): Task[Boolean] = {

    if (key.isEmpty)
      throw EmptyKey("The key under which the value should be written is empty. Aborting writing to redis.")

    if (value.isEmpty)
      throw EmptyValue("The string value is empty. Aborting writing to redis.")

    val operationFuture: Future[Boolean] = Future {

      val conn: Jedis = pool.getResource
      try {
        conn.set(key, value)
        true
      } finally {
        conn.close()
      }

    }

    val recoverableOperationFuture = operationFuture.recover { case e: Exception =>
      // Log any errors.
      logger.warn("Writing to Redis failed - {}", e.getMessage)
      false
    }

    recoverableOperationFuture
  }

  /**
   * Get value stored under the key as a string.
   *
   * @param maybeKey the key.
   */
  def getStringValue(maybeKey: Option[String]): Task[Option[String]] = {

    val operationFuture: Future[Option[String]] = maybeKey match {
      case Some(key) =>
        Future {
          val conn: Jedis = pool.getResource
          try {
            Option(conn.get(key))
          } finally {
            conn.close()
          }
        }
      case None =>
        FastFuture.successful(None)
    }

    val recoverableOperationFuture = operationFuture.recover { case e: Exception =>
      // Log any errors.
      logger.warn("Reading string from Redis failed, {}", e)
      None
    }

    recoverableOperationFuture
  }

  /**
   * Removes values for the provided keys. Any invalid keys are ignored.
   *
   * @param keys the keys.
   */
  def removeValues(keys: Set[String]): Task[Boolean] = {

    logger.debug("removeValues - {}", keys)

    val operationFuture: Future[Boolean] = Future {
      // del takes a vararg so I nee to convert the set to a swq and then to vararg
      val conn: Jedis = pool.getResource
      try {
        conn.del(keys.toSeq: _*)
        true
      } finally {
        conn.close()
      }
    }

    val recoverableOperationFuture = operationFuture.recover { case e: Exception =>
      // Log any errors.
      logger.warn("Removing keys from Redis failed.", e.getMessage)
      false
    }

    recoverableOperationFuture
  }

  /**
   * Flushes (removes) all stored content from the Redis store.
   */
  def flushDB(requestingUser: UserADM): Task[Unit] = {

    if (!requestingUser.isSystemUser) {
      throw ForbiddenException("Only the system user is allowed to perform this operation.")
    }

    val operationFuture: Future[Unit] = Future {

      val conn: Jedis = pool.getResource
      try {
        conn.flushDB()
        ()
      } finally {
        conn.close()
      }
    }

    val recoverableOperationFuture = operationFuture.recover { case e: Exception =>
      // Log any errors.
      logger.warn("Flushing DB failed", e.getMessage)
      throw e
    }

    recoverableOperationFuture
  }

  /**
   * Pings the Redis store to see if it is available.
   */
  def ping(): Task[CacheServiceStatusResponse] = {
    val operationFuture: Future[CacheServiceStatusResponse] = Future {

      val conn: Jedis = pool.getResource
      try {
        conn.ping("test")
        CacheServiceStatusOK
      } finally {
        conn.close()
      }
    }

    val recoverableOperationFuture = operationFuture.recover { case e: Exception =>
      CacheServiceStatusNOK
    }

    recoverableOperationFuture
  }

  /**
   * Store string or byte array value under key.
   *
   * @param key   the key.
   * @param value the value.
   */
  private def writeBytesValue(key: String, value: Array[Byte]): Task[Unit] = {

    if (key.isEmpty)
      throw EmptyKey("The key under which the value should be written is empty. Aborting writing to redis.")

    if (value.isEmpty)
      throw EmptyValue("The byte array value is empty. Aborting writing to redis.")

    val operationFuture: Future[Boolean] = Future {
      val conn: Jedis = pool.getResource
      try {
        conn.set(key.getBytes, value)
        true
      } finally {
        conn.close()
      }
    }

    val recoverableOperationFuture = operationFuture.recover { case e: Exception =>
      // Log any errors.
      logger.warn("Writing to Redis failed - {}", e.getMessage)
      false
    }

    recoverableOperationFuture
  }

  /**
   * Get value stored under the key as a byte array. If no value is found
   * under the key, then a [[None]] is returned..
   *
   * @param key the key.
   */
  private def getBytesValue(key: String): Task[Option[Array[Byte]]] =
    for {
      conn  <- pool.map(_.getResource)
      value: Array[Byte] = conn.get(key.getBytes)
      res <- if (value == "nil".getBytes) Task.succeed(None)
             else Task.succeed(Some(value))
      // try {
      //   Option(conn.get(key.getBytes))
      // } finally {
      //   conn.close()
      // }
    } yield res

  // val recoverableOperationFuture = operationFuture.recover { case e: Exception =>
  //   // Log any errors.
  //   logger.warn("Reading byte array from Redis failed - {}", e.getMessage)
  //   None
  // }

  // recoverableOperationFuture

}

object CacheServiceRedisImpl {
  val layer: ZLayer[Any, Nothing, CacheService] = {
    ZLayer {
      for {
        pool <- ZIO.attempt(new JedisPool(new JedisPoolConfig(), "localhost", 6379, 20999)) // the Redis Client Pool
      } yield CacheServiceRedisImpl(pool)
    }.tap(_ => ZIO.debug("Initializing Redis Cache Service"))
  }
}
