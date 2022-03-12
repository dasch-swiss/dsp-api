/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cache.impl

import akka.http.scaladsl.util.FastFuture
import com.typesafe.scalalogging.{LazyLogging, Logger}
import org.knora.webapi.exceptions.ForbiddenException
import org.knora.webapi.messages.admin.responder.projectsmessages.{
  ProjectADM,
  ProjectIdentifierADM,
  ProjectIdentifierType
}
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserIdentifierADM, UserIdentifierType}
import org.knora.webapi.messages.store.cacheservicemessages.{
  CacheServiceFlushDBACK,
  CacheServiceStatusNOK,
  CacheServiceStatusOK,
  CacheServiceStatusResponse
}
import org.knora.webapi.store.cache.serialization.CacheSerialization
import org.knora.webapi.store.cache.settings.CacheSettings
import org.knora.webapi.store.cache.api.{Cache, EmptyKey, EmptyValue}
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

import scala.concurrent.{ExecutionContext, Future}

class CacheRedisImpl(s: CacheSettings) extends Cache with LazyLogging {

  /**
   * The Redis Client Pool
   */
  val pool: JedisPool = new JedisPool(new JedisPoolConfig(), s.cacheServiceRedisHost, s.cacheServiceRedisPort, 20999)

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
   * @param value the stored value
   */
  def putUserADM(value: UserADM)(implicit ec: ExecutionContext): Future[Boolean] = {
    val resultFuture = for {
      bytes: Array[Byte] <- CacheSerialization.serialize(value)
      result: Boolean <- writeBytesValue(value.id, bytes)
      // additionally store the IRI under the username and email key
      _ = writeStringValue(value.username, value.id)
      _ = writeStringValue(value.email, value.id)
    } yield result

    val recoverableResultFuture = resultFuture.recover { case e: Exception =>
      logger.warn("Aborting writing 'UserADM' to Redis - {}", e.getMessage)
      false
    }

    recoverableResultFuture
  }

  /**
   * Retrieves the user stored under the identifier (either iri, username,
   * or email).
   *
   * @param identifier the user identifier.
   */
  def getUserADM(identifier: UserIdentifierADM)(implicit ec: ExecutionContext): Future[Option[UserADM]] = {
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
          maybeIriKey: Option[String] <- getStringValue(identifier.toUsernameOption)
          maybeBytes: Option[Array[Byte]] <- getBytesValue(maybeIriKey)
          maybeUser: Option[UserADM] <- maybeBytes match {
            case Some(bytes) => CacheSerialization.deserialize[UserADM](bytes)
            case None        => FastFuture.successful(None)
          }
        } yield maybeUser

      case UserIdentifierType.Email =>
        for {
          maybeIriKey: Option[String] <- getStringValue(identifier.toEmailOption)
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
  def putProjectADM(value: ProjectADM)(implicit ec: ExecutionContext): Future[Boolean] = {
    val resultFuture = for {
      bytes: Array[Byte] <- CacheSerialization.serialize(value)
      result: Boolean <- writeBytesValue(value.id, bytes)
      _ = writeStringValue(value.shortcode, value.id)
      _ = writeStringValue(value.shortname, value.id)
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
  def getProjectADM(identifier: ProjectIdentifierADM)(implicit ec: ExecutionContext): Future[Option[ProjectADM]] = {

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
          maybeBytes <- getBytesValue(maybeIriKey)
          maybeProject: Option[ProjectADM] <- maybeBytes match {
            case Some(bytes) => CacheSerialization.deserialize[ProjectADM](bytes)
            case None        => FastFuture.successful(None)
          }
        } yield maybeProject
      case ProjectIdentifierType.SHORTNAME =>
        for {
          maybeIriKey <- getStringValue(identifier.toShortnameOption)
          maybeBytes <- getBytesValue(maybeIriKey)
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
  def writeStringValue(key: String, value: String)(implicit ec: ExecutionContext): Future[Boolean] = {

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
  def getStringValue(maybeKey: Option[String])(implicit ec: ExecutionContext): Future[Option[String]] = {

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
  def removeValues(keys: Set[String])(implicit ec: ExecutionContext): Future[Boolean] = {

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
  def flushDB(requestingUser: UserADM)(implicit ec: ExecutionContext): Future[CacheServiceFlushDBACK] = {

    if (!requestingUser.isSystemUser) {
      throw ForbiddenException("Only the system user is allowed to perform this operation.")
    }

    val operationFuture: Future[CacheServiceFlushDBACK] = Future {

      val conn: Jedis = pool.getResource
      try {
        conn.flushDB()
        CacheServiceFlushDBACK()
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
  def ping()(implicit ec: ExecutionContext): Future[CacheServiceStatusResponse] = {
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
  private def writeBytesValue(key: String, value: Array[Byte])(implicit ec: ExecutionContext): Future[Boolean] = {

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
   * @param maybeKey the key.
   */
  private def getBytesValue(maybeKey: Option[String])(implicit ec: ExecutionContext): Future[Option[Array[Byte]]] = {

    val operationFuture: Future[Option[Array[Byte]]] = maybeKey match {
      case Some(key) =>
        Future {
          val conn = pool.getResource
          try {
            Option(conn.get(key.getBytes))
          } finally {
            conn.close()
          }
        }
      case None =>
        FastFuture.successful(None)
    }

    val recoverableOperationFuture = operationFuture.recover { case e: Exception =>
      // Log any errors.
      logger.warn("Reading byte array from Redis failed - {}", e.getMessage)
      None
    }

    recoverableOperationFuture
  }

}
