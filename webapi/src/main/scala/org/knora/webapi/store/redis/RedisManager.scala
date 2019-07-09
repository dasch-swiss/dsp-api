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

package org.knora.webapi.store.redis

import akka.actor.ActorSystem
import com.redis._
import com.redis.serialization.Parse.Implicits.parseByteArray
import com.typesafe.scalalogging.{LazyLogging, Logger}
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectIdentifierADM, ProjectIdentifierType}
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserIdentifierADM, UserIdentifierType}
import org.knora.webapi.messages.store.redismessages._
import org.knora.webapi.util.InstrumentationSupport

import scala.concurrent.{ExecutionContext, Future}

case class EmptyKey(message: String) extends RedisException(message)
case class EmptyValue(message: String) extends RedisException(message)
case class UnsupportedValueType(message: String) extends RedisException(message)

class RedisManager(system: ActorSystem) extends LazyLogging with InstrumentationSupport {

    /**
      * The Knora Akka actor system.
      */
    protected implicit val _system: ActorSystem = system

    /**
      * The Akka actor system's execution context for futures.
      */
    protected implicit val ec: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

    /**
      * The Knora settings.
      */
    protected val s: SettingsImpl = Settings(system)

    /**
      * The Redis Client Pool
      */
    val clients = new RedisClientPool(host = s.redisHost, port = s.redisPort)

    // this is needed for time measurements using 'org.knora.webapi.Timing'
    implicit val l: Logger = logger

    def receive(msg: RedisRequest) = msg match {
        case RedisPutUserADM(value) => redisPutUserADM(value)
        case RedisGetUserADM(identifier) => redisGetUserADM(identifier)
        case RedisPutProjectADM(value) => redisPutProjectADM(value)
        case RedisGetProjectADM(identifier) => redisGetProjectADM(identifier)
        case RedisPutString(key, value) => writeValue(key, value)
        case RedisGetString(key) => getStringValue(key)
        case RedisRemoveValues(keys) => removeValues(keys)
        case other => throw UnexpectedMessageException(s"RedisManager received an unexpected message: $other")
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
    private def redisPutUserADM(value: UserADM): Future[Boolean] = {

        val resultFuture = for {
            bytes: Array[Byte] <- RedisSerialization.serialize(value)
            result: Boolean <- writeValue(value.id, bytes)
            // additionally store the IRI under the username and email key
            _ = writeValue(value.username, value.id)
            _ = writeValue(value.email, value.id)
        } yield result


        val recoverableResultFuture = resultFuture.recover{
            case exception: Exception =>
                logger.error("Aborting writing 'UserADM' to Redis - {}", exception.getMessage)
                false
        }

        timed("Redis write user:")(recoverableResultFuture)
    }

    /**
      * Retrieves the user stored under the identifier (either iri, username,
      * or email).
      *
      * @param identifier the project identifier.
      */
    private def redisGetUserADM(identifier: UserIdentifierADM): Future[Option[UserADM]] = {

        // The data is stored under the IRI key.
        // Additionally, the SHORTNAME and SHORTCODE keys point to the IRI key
        val resultFuture: Future[Option[UserADM]] = identifier.hasType match {
            case UserIdentifierType.IRI =>
                for {
                    bytes <- getBytesValue(identifier.toIri)
                    user: UserADM <- RedisSerialization.deserialize[UserADM](bytes)
                } yield Some(user)

            case UserIdentifierType.USERNAME =>
                for {
                    iriKey <- getStringValue(identifier.toUsername)
                    bytes <- getBytesValue(iriKey)
                    user: UserADM <- RedisSerialization.deserialize[UserADM](bytes)
                } yield Some(user)

            case UserIdentifierType.EMAIL =>
                for {
                    iriKey <- getStringValue(identifier.toEmail)
                    bytes <- getBytesValue(iriKey)
                    user: UserADM <- RedisSerialization.deserialize[UserADM](bytes)
                } yield Some(user)
        }

        val recoverableResultFuture = resultFuture.recover {
            case e: Exception =>
                logger.error(s"Aborting reading 'UserADM' from Redis - ${e.getMessage}")
                None
        }

        timed("Redis read user:")(recoverableResultFuture)
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
    private def redisPutProjectADM(value: ProjectADM): Future[Boolean] = timed("Redis write project.") {

        val resultFuture = for {
            bytes: Array[Byte] <- RedisSerialization.serialize(value)
            result: Boolean <- writeValue(value.id, bytes)
            _ = writeValue(value.shortcode, value.id)
            _ = writeValue(value.shortname, value.id)
        } yield result

        val bytesF: Future[Array[Byte]] = RedisSerialization.serialize(value)

        val recoverableResultFuture = resultFuture.recover {

            case e: Exception =>
                logger.error("Aborting writing 'ProjectADM' to Redis.", e)
                false

        }

        recoverableResultFuture
    }

    /**
      * Retrieves the project stored under the identifier (either iri, shortname, or shortcode).
      *
      * @param identifier the project identifier.
      */
    private def redisGetProjectADM(identifier: ProjectIdentifierADM): Future[Option[ProjectADM]] = timed("Redis read project.") {

        // The data is stored under the IRI key.
        // Additionally, the SHORTNAME and SHORTCODE keys point to the IRI key
        val resultFuture: Future[Option[ProjectADM]] = identifier.hasType match {
            case ProjectIdentifierType.IRI =>
                for {
                    bytes <- getBytesValue(identifier.toIri)
                    project: ProjectADM <- RedisSerialization.deserialize[ProjectADM](bytes)
                } yield Some(project)
            case ProjectIdentifierType.SHORTCODE =>
                for {
                    iriKey <- getStringValue(identifier.toShortcode)
                    bytes <- getBytesValue(iriKey)
                    project: ProjectADM <- RedisSerialization.deserialize[ProjectADM](bytes)
                } yield Some(project)
            case ProjectIdentifierType.SHORTNAME =>
                for {
                    iriKey <- getStringValue(identifier.toShortname)
                    bytes <- getBytesValue(iriKey)
                    project: ProjectADM <- RedisSerialization.deserialize[ProjectADM](bytes)
                } yield Some(project)
        }

        val recoverableResultFuture = resultFuture.recover {
            case e: Exception =>
                logger.error("Aborting reading 'ProjectADM' from Redis.", e)
                None
        }

        recoverableResultFuture
    }

    /**
      * Get value stored under the key as a byte array. If no value is found
      * under the key, then [[Array.emptyByteArray]] is returned.
      * @param key the key.
      */
    private def getBytesValue(key: String): Future[Array[Byte]] = {

        if (key.isEmpty)
            throw EmptyKey("Empty key. Aborting getting bytes value.")

        val operationFuture: Future[Array[Byte]] = clients.withClient {
            client =>
                Future {
                    /** Note to future self: This call to redis returns an
                      * optional value and thus would result in Future[Option].
                      * In later processing, this option needs to be combined
                      * with other futures. To make it possible to do this in
                      * the same for comprehension, there are two solutions:
                      * 1. Monad transformers (have fun reading and understanding):
                      *     - https://www.47deg.com/blog/fp-for-the-average-joe-part-2-scalaz-monad-transformers/
                      *     - http://debasishg.blogspot.com/2011/07/monad-transformers-in-scala.html
                      * 2. Get rid of the option
                      *
                      * Guess what I implemented.
                      */
                    val maybeByteAray = client.get[Array[Byte]](key)
                    maybeByteAray.getOrElse(Array.emptyByteArray)
                }
        }

        val recoverableOperationFuture = operationFuture.recover {
            case e: Exception =>
                // Log any errors.
                logger.error("Reading byte array from Redis failed - {}", e.getMessage)
                Array.emptyByteArray
        }

        recoverableOperationFuture
    }

    /**
      * Get value stored under the key as a string.
      * @param key the key.
      */
    private def getStringValue(key: String): Future[String] = {

        if (key.isEmpty)
            throw EmptyKey("Empty key. Aborting getting string value.")

        val operationFuture: Future[String] = clients.withClient {
            client =>
                Future {
                    // See note to myself in getBytesValue!
                    val maybeString = client.get[String](key)
                    maybeString.getOrElse("")
                }
        }

        val recoverableOperationFuture = operationFuture.recover {
            case exception: Exception =>
                // Log any errors.
                logger.error("Reading string from Redis failed.", exception)
                ""
        }

        recoverableOperationFuture
    }

    /**
      * Store string or byte array value under key.
      *
      * @param key the key.
      * @param value the value.
      */
    private def writeValue(key: String, value: Any): Future[Boolean] = {

        if (key.isEmpty)
            throw EmptyKey("The key under which the value should be written is empty. Aborting writing to redis.")

        value match {
            case s: String => if (s.isEmpty) throw EmptyValue("The string value is empty. Aborting writing to redis.")
            case ba: Array[Byte] => if (ba.isEmpty) throw EmptyValue("The byte array value is empty. Aborting writing to redis.")
            case other => throw UnsupportedValueType(s"Writing '${other.getClass}' natively to Redis is not supported. Aborting writing to redis.")
        }

        val operationFuture: Future[Boolean] = clients.withClient {
            client =>
                Future {
                    client.set(key, value)
                }
        }

        val recoverableOperationFuture = operationFuture.recover {
            case exception: Exception =>
                // Log any errors.
                logger.error("Writing to Redis failed.", exception)
                false
        }

        recoverableOperationFuture
    }

    /**
      * Removes values for the provided keys. Any invalid keys are ignored.
      *
      * @param keys the keys.
      */
    private def removeValues(keys: Seq[String]): Future[Boolean] = {

        logger.debug("removeValues - {}", keys)

        val operationFuture: Future[Boolean] = clients.withClient {
            client =>
                Future {
                    client.del(keys)
                } map (_.isDefined)
        }

        val recoverableOperationFuture = operationFuture.recover {
            case exception: Exception =>
                // Log any errors.
                logger.error("Removing keys from Redis failed.", exception)
                false
        }

        timed("Remove values from Redis")(recoverableOperationFuture)
    }
}