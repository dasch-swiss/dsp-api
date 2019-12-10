/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.store.cacheservice

import akka.actor.{Actor, ActorLogging, ActorSystem, Status}
import akka.http.scaladsl.util.FastFuture
import com.typesafe.scalalogging.{LazyLogging, Logger}
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectIdentifierADM, ProjectIdentifierType}
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserIdentifierADM, UserIdentifierType}
import org.knora.webapi.messages.store.cacheservicemessages._
import org.knora.webapi.util.ActorUtil.future2Message
import org.knora.webapi.util.InstrumentationSupport
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

import scala.concurrent.{ExecutionContext, Future}

case class EmptyKey(message: String) extends RedisException(message)
case class EmptyValue(message: String) extends RedisException(message)
case class UnsupportedValueType(message: String) extends RedisException(message)

class CacheServiceManager extends Actor with ActorLogging with LazyLogging with InstrumentationSupport {

    /**
      * The Knora Akka actor system.
      */
    protected implicit val _system: ActorSystem = context.system

    /**
      * The Akka actor system's execution context for futures.
      */
    protected implicit val ec: ExecutionContext = context.system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

    /**
      * The Knora settings.
      */
    protected val s: SettingsImpl = Settings(context.system)

    /**
      * The Redis Client Pool
      */
    val pool: JedisPool = new JedisPool(new JedisPoolConfig(), s.redisHost, s.redisPort, 20999)

    // this is needed for time measurements using 'org.knora.webapi.Timing'

    implicit val l: Logger = logger

    // close the redis client pool
    override def postStop(): Unit = {
        logger.info("CacheServiceManager - shutdown in progress, initiating post stop cleanup.")
        pool.close()
    }

    def receive = {
        case CacheServicePutUserADM(value) => future2Message(sender(), redisPutUserADM(value), log)
        case CacheServiceGetUserADM(identifier) => future2Message(sender(), redisGetUserADM(identifier), log)
        case CacheServicePutProjectADM(value) => future2Message(sender(), redisPutProjectADM(value), log)
        case CacheServiceGetProjectADM(identifier) => future2Message(sender(), redisGetProjectADM(identifier), log)
        case CacheServicePutString(key, value) => future2Message(sender(), writeStringValue(key, value), log)
        case CacheServiceGetString(key) => future2Message(sender(), getStringValue(key), log)
        case CacheServiceRemoveValues(keys) => future2Message(sender(), removeValues(keys), log)
        case CacheServiceFlushDB(requestingUser) => future2Message(sender(), flushDB(requestingUser), log)
        case CacheServiceGetStatus => future2Message(sender(), ping(), log)
        case other =>  sender ! Status.Failure(UnexpectedMessageException(s"RedisManager received an unexpected message: $other"))
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
    private def redisPutUserADM(value: UserADM): Future[Boolean] = tracedFuture("redis-write-user") {

        val resultFuture = for {
            bytes: Array[Byte] <- CacheSerialization.serialize(value)
            result: Boolean <- writeBytesValue(value.id, bytes)
            // additionally store the IRI under the username and email key
            _ = writeStringValue(value.username, value.id)
            _ = writeStringValue(value.email, value.id)
        } yield result

        val recoverableResultFuture = resultFuture.recover{
            case e: Exception =>
                logger.warn("Aborting writing 'UserADM' to Redis - {}", e.getMessage)
                false
        }

        recoverableResultFuture
    }

    /**
      * Retrieves the user stored under the identifier (either iri, username,
      * or email).
      *
      * @param identifier the project identifier.
      */
    private def redisGetUserADM(identifier: UserIdentifierADM): Future[Option[UserADM]] = tracedFuture("redis-read-user") {

        // The data is stored under the IRI key.
        // Additionally, the SHORTNAME and SHORTCODE keys point to the IRI key
        val resultFuture: Future[Option[UserADM]] = identifier.hasType match {
            case UserIdentifierType.IRI =>
                for {
                    maybeBytes: Option[Array[Byte]] <- getBytesValue(identifier.toIriOption)
                    maybeUser: Option[UserADM] <- maybeBytes match {
                        case Some(bytes) => CacheSerialization.deserialize[UserADM](bytes)
                        case None => FastFuture.successful(None)
                    }
                } yield maybeUser

            case UserIdentifierType.USERNAME =>
                for {
                    maybeIriKey: Option[String] <- getStringValue(identifier.toUsernameOption)
                    maybeBytes: Option[Array[Byte]] <- getBytesValue(maybeIriKey)
                    maybeUser: Option[UserADM] <- maybeBytes match {
                        case Some(bytes) => CacheSerialization.deserialize[UserADM](bytes)
                        case None => FastFuture.successful(None)
                    }
                } yield maybeUser

            case UserIdentifierType.EMAIL =>
                for {
                    maybeIriKey: Option[String] <- getStringValue(identifier.toEmailOption)
                    maybeBytes: Option[Array[Byte]] <- getBytesValue(maybeIriKey)
                    maybeUser: Option[UserADM] <- maybeBytes match {
                        case Some(bytes) => CacheSerialization.deserialize[UserADM](bytes)
                        case None => FastFuture.successful(None)
                    }
                } yield maybeUser
        }

        val recoverableResultFuture = resultFuture.recover {
            case e: Exception =>
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
    private def redisPutProjectADM(value: ProjectADM): Future[Boolean] = tracedFuture("redis-write-project") {

        val resultFuture = for {
            bytes: Array[Byte] <- CacheSerialization.serialize(value)
            result: Boolean <- writeBytesValue(value.id, bytes)
            _ = writeStringValue(value.shortcode, value.id)
            _ = writeStringValue(value.shortname, value.id)
        } yield result

        val recoverableResultFuture = resultFuture.recover {
            case e: Exception =>
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
    private def redisGetProjectADM(identifier: ProjectIdentifierADM): Future[Option[ProjectADM]] = tracedFuture("redis-read-project") {

        // The data is stored under the IRI key.
        // Additionally, the SHORTNAME and SHORTCODE keys point to the IRI key
        val resultFuture: Future[Option[ProjectADM]] = identifier.hasType match {
            case ProjectIdentifierType.IRI =>
                for {
                    maybeBytes <- getBytesValue(identifier.toIriOption)
                    maybeProject <- maybeBytes match {
                        case Some(bytes) => CacheSerialization.deserialize[ProjectADM](bytes)
                        case None => FastFuture.successful(None)
                    }
                } yield maybeProject
            case ProjectIdentifierType.SHORTCODE =>
                for {
                    maybeIriKey <- getStringValue(identifier.toShortcodeOption)
                    maybeBytes <- getBytesValue(maybeIriKey)
                    maybeProject: Option[ProjectADM] <- maybeBytes match {
                        case Some(bytes) => CacheSerialization.deserialize[ProjectADM](bytes)
                        case None => FastFuture.successful(None)
                    }
                } yield maybeProject
            case ProjectIdentifierType.SHORTNAME =>
                for {
                    maybeIriKey <- getStringValue(identifier.toShortnameOption)
                    maybeBytes <- getBytesValue(maybeIriKey)
                    maybeProject: Option[ProjectADM] <- maybeBytes match {
                        case Some(bytes) => CacheSerialization.deserialize[ProjectADM](bytes)
                        case None => FastFuture.successful(None)
                    }
                } yield maybeProject
        }

        val recoverableResultFuture = resultFuture.recover {
            case e: Exception =>
                logger.warn("Aborting reading 'ProjectADM' from Redis - {}", e.getMessage)
                None
        }

        recoverableResultFuture
    }

    /**
      * Get value stored under the key as a byte array. If no value is found
      * under the key, then a [[None]] is returned..
      * @param maybeKey the key.
      */
    private def getBytesValue(maybeKey: Option[String]): Future[Option[Array[Byte]]] = {

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

        val recoverableOperationFuture = operationFuture.recover {
            case e: Exception =>
                // Log any errors.
                logger.warn("Reading byte array from Redis failed - {}", e.getMessage)
                None
        }

        recoverableOperationFuture
    }

    /**
      * Store string or byte array value under key.
      *
      * @param key the key.
      * @param value the value.
      */
    private def writeBytesValue(key: String, value: Array[Byte]): Future[Boolean] = {

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

        val recoverableOperationFuture = operationFuture.recover {
            case e: Exception =>
                // Log any errors.
                logger.warn("Writing to Redis failed - {}", e.getMessage)
                false
        }

        recoverableOperationFuture
    }

    /**
      * Get value stored under the key as a string.
      * @param maybeKey the key.
      */
    private def getStringValue(maybeKey: Option[String]): Future[Option[String]] = {

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

        val recoverableOperationFuture = operationFuture.recover {
            case e: Exception =>
                // Log any errors.
                logger.warn("Reading string from Redis failed, {}", e)
                None
        }

        recoverableOperationFuture
    }

    /**
      * Store string or byte array value under key.
      *
      * @param key the key.
      * @param value the value.
      */
    private def writeStringValue(key: String, value: String): Future[Boolean] = {

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

        val recoverableOperationFuture = operationFuture.recover {
            case e: Exception =>
                // Log any errors.
                logger.warn("Writing to Redis failed - {}", e.getMessage)
                false
        }

        recoverableOperationFuture
    }

    /**
      * Removes values for the provided keys. Any invalid keys are ignored.
      *
      * @param keys the keys.
      */
    private def removeValues(keys: Set[String]): Future[Boolean] = tracedFuture("redis-remove-values") {

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

        val recoverableOperationFuture = operationFuture.recover {
            case e: Exception =>
                // Log any errors.
                logger.warn("Removing keys from Redis failed.", e.getMessage)
                false
        }

        recoverableOperationFuture
    }

    /**
      * Flushes (removes) all stored content from the Redis store.
      */
    private def flushDB(requestingUser: UserADM): Future[CacheServiceFlushDBACK] = tracedFuture("redis-flush-db") {

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

        val recoverableOperationFuture = operationFuture.recover {
            case e: Exception =>
                // Log any errors.
                logger.warn("Flushing DB failed", e.getMessage)
                throw e
        }

        recoverableOperationFuture
    }

    /**
      * Pings the Redis store to see if it is available.
      */
    private def ping(): Future[CacheServiceStatusResponse] = {
        val operationFuture: Future[CacheServiceStatusResponse] = Future {

            val conn: Jedis = pool.getResource
            try {
                conn.ping("test")
                CacheServiceStatusOK
            } finally {
                conn.close()
            }
        }

        val recoverableOperationFuture = operationFuture.recover {
            case e: Exception =>
                CacheServiceStatusNOK
        }

        recoverableOperationFuture
    }
}