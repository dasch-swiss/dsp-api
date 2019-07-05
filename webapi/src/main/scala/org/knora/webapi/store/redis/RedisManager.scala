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

import akka.http.scaladsl.util.FastFuture
import com.redis._
import com.redis.serialization.Parse.Implicits.parseByteArray
import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.UnexpectedMessageException
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectIdentifierADM, ProjectIdentifierType}
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserIdentifierADM, UserIdentifierType}
import org.knora.webapi.messages.store.redismessages._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class RedisManager(host: String, port: Int) extends LazyLogging {

    val r = new RedisClient(host, port)

    def receive(msg: RedisRequest) = msg match {
        case RedisPutUserADM(value) => redisPutUserADM(value)
        case RedisGetUserADM(identifier) => redisGetUserADM(identifier)
        case RedisPutProjectADM(value) => redisPutProjectADM(value)
        case RedisGetProjectADM(identifier) => redisGetProjectADM(identifier)
        case RedisPutString(key, value) => redisPutString(key, value)
        case RedisGetString(key) => redisGetString(key)
        case RedisRemoveValues(keys) => redisRemoveValues(keys)
        case other => throw UnexpectedMessageException(s"RedisManager received an unexpected message: $other")
    }

    /**
      * Stores the user under the IRI and additionally the IRI under the keys of USERNAME and EMAIL:
      * IRI -> byte array
      * username -> IRI
      * email -> IRI
      *
      * @param value the stored value
      */
    private def redisPutUserADM(value: UserADM): Future[Boolean] = {

        val start = System.currentTimeMillis()

        val maybeBytes: Try[Array[Byte]] = RedisSerialization.serialize(value)

        val result = maybeBytes match {
            case Success(bytes) =>
                val res: Boolean = setBytesValue(value.id, bytes)
                //FIXME: failure of these is not checked
                setStringValue(value.username, value.id)
                setStringValue(value.email, value.id)
                res
            case Failure(exception) =>
                logger.error("Serialization failed. Aborting writing 'UserADM' to Redis.", exception)
                false
        }

        val took = System.currentTimeMillis() - start
        logger.info(s"Redis write user: ${took}ms")

        FastFuture.successful(result)
    }

    /**
      * Retrieves the user stored under the identifier (either iri, username, or email).
      *
      * @param identifier the project identifier.
      */
    private def redisGetUserADM(identifier: UserIdentifierADM): Future[Option[UserADM]] = {

        val start = System.currentTimeMillis()

        // The data is stored under the IRI key.
        // Additionally, the SHORTNAME and SHORTCODE keys point to the IRI key
        val result: Option[Array[Byte]] = identifier.hasType match {
            case UserIdentifierType.IRI =>
                getBytesValue(identifier.toIri)
            case UserIdentifierType.USERNAME =>
                getStringValue(identifier.toUsername).flatMap(iriKey => getBytesValue(iriKey))
            case UserIdentifierType.EMAIL =>
                getStringValue(identifier.toEmail).flatMap(iriKey => getBytesValue(iriKey))
        }

        val res = if (result.isDefined) {
            val bytes = result.get
            val maybeUser = RedisSerialization.deserialize[UserADM](bytes)
            maybeUser match {
                case Success(user) =>
                    Some(user)
                case Failure(exception) =>
                    logger.error("Deserialization failed. Aborting reading 'UserADM' from Redis.", exception)
                    None
            }
        } else {
            None
        }

        val took = System.currentTimeMillis() - start
        logger.info(s"Redis read user: ${took}ms")

        FastFuture.successful(res)
    }


    /**
      * Stores the project under the IRI and additionally the IRI under the keys of SHORTCODE and SHORTNAME:
      * IRI -> byte array
      * shortname -> IRI
      * shortcode -> IRI
      *
      * @param value the stored value
      */
    private def redisPutProjectADM(value: ProjectADM): Future[Boolean] = {

        val start = System.currentTimeMillis()

        val maybeBytes: Try[Array[Byte]] = RedisSerialization.serialize(value)

        val result: Boolean = maybeBytes match {
            case Success(bytes) =>
                val res: Boolean = setBytesValue(value.id, bytes)
                //FIXME: failure of these is not checked
                setStringValue(value.shortcode, value.id)
                setStringValue(value.shortname, value.id)
                res
            case Failure(exception) =>
                logger.error("Serialization failed. Aborting writing 'ProjectADM' to Redis.", exception)
                false

        }

        val took = System.currentTimeMillis() - start
        logger.info(s"Redis write project: ${took}ms")

        FastFuture.successful(result)
    }

    /**
      * Retrieves the project stored under the identifier (either iri, shortname, or shortcode).
      *
      * @param identifier the project identifier.
      */
    private def redisGetProjectADM(identifier: ProjectIdentifierADM): Future[Option[ProjectADM]] = {

        val start = System.currentTimeMillis()

        // The data is stored under the IRI key.
        // Additionally, the SHORTNAME and SHORTCODE keys point to the IRI key
        val result: Option[Array[Byte]] = identifier.hasType match {
            case ProjectIdentifierType.IRI =>
                getBytesValue(identifier.toIri)
            case ProjectIdentifierType.SHORTCODE =>
                getStringValue(identifier.toShortcode).flatMap(iriKey => getBytesValue(iriKey))
            case ProjectIdentifierType.SHORTNAME =>
                getStringValue(identifier.toShortname).flatMap(iriKey => getBytesValue(iriKey))
        }

        val res: Option[ProjectADM] = if (result.isDefined) {
            val bytes = result.get
            val maybeProject = RedisSerialization.deserialize[ProjectADM](bytes)
            maybeProject match {
                case Success(project) =>
                    Some(project)
                case Failure(exception) =>
                    logger.error("Deserialization failed. Aborting reading 'ProjectADM' from Redis.", exception)
                    None
            }
        } else {
            None
        }

        val took = System.currentTimeMillis() - start
        logger.info(s"Redis read project: ${took}ms")

        FastFuture.successful(res)
    }

    /**
      * Store a string value under the key.
      *
      * @param key the key.
      * @param value the string value.
      */
    private def redisPutString(key: String, value: String): Future[Boolean] = {
        FastFuture.successful(setStringValue(key, value))
    }

    /**
      * Get the string value stored under the key.
      *
      * @param key the key.
      */
    private def redisGetString(key: String): Future[Option[String]] = {
        FastFuture.successful(getStringValue(key))
    }

    /**
      * Removes the values stored under the keys. Any invalid keys are ignored.
      *
      * @param keys the keys.
      */
    private def redisRemoveValues(keys: Seq[String]): Future[Boolean] = {
        FastFuture.successful(removeValues(keys))
    }

    /**
      * Get value stored under the key as a byte array.
      * @param key the key.
      */
    private def getBytesValue(key: String): Option[Array[Byte]] = {

        // try to get the byte array stored under the key
        val redisResponseTry = Try {
            val response: Option[Array[Byte]] = r.get[Array[Byte]](key)
            response
        }

        redisResponseTry match {
            case Success(value) => value
            case Failure(exception) =>
                // Log any errors.
                logger.error("Reading byte array from Redis failed.", exception)
                None
        }
    }

    /**
      * Get value stored under the key as a string.
      * @param key the key.
      */
    private def getStringValue(key: String): Option[String] = {

        // try to get the string sored under the key
        val redisResponseTry = Try {
            val response: Option[String] = r.get[String](key)
            response
        }

        redisResponseTry match {
            case Success(value) => value
            case Failure(exception) =>
                // Log any errors.
                logger.error("Reading string from Redis failed.", exception)
                None
        }
    }

    /**
      * Store byte array value under key.
      * @param key the key.
      * @param value the byte array value.
      */
    private def setBytesValue(key: String, value: Array[Byte]): Boolean = {

        // try to set the byte array under the supplied key
        val redisResponseTry = Try {
            val response = r.set(key, value)
            response
        }

        redisResponseTry match {
            case Success(value) => value
            case Failure(exception) =>
                // Log any errors.
                logger.error("Writing byte array to Redis failed.", exception)
                false
        }
    }

    /**
      * Store string value under key.
      * @param key the key.
      * @param value the string value.
      */
    private def setStringValue(key: String, value: String): Boolean = {

        // try to set the string under the supplied key
        val redisResponseTry = Try {
            val response: Boolean = r.set(key, value)
            response
        }

        redisResponseTry match {
            case Success(value) => value
            case Failure(exception) =>
                // Log any errors.
                logger.error("Writing string to Redis failed.", exception)
                false
        }
    }

    /**
      * Removes values for the provided keys. Any invalid keys are ignored.
      *
      * @param keys the keys.
      */
    private def removeValues(keys: Seq[String]): Boolean = {
        // try to remove all values
        val redisResponseTry = Try {
            // Returns the number of deleted values. Any unknown keys are ignored.
            val response: Option[Long] = r.del(keys)
            if (response.isDefined) {
                true
            } else {
                false
            }
        }

        redisResponseTry match {
            case Success(value) => value
            case Failure(exception) =>
                // Log any errors.
                logger.error("Writing string to Redis failed.", exception)
                false
        }
    }
}