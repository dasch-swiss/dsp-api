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

import java.io

import com.redis._
import com.redis.serialization.Parse.Implicits.parseByteArray
import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectIdentifierADM, ProjectIdentifierType}
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserIdentifierADM, UserIdentifierType}
import org.knora.webapi.messages.store.redismessages.{RedisGetProjectADM, RedisGetUserADM, RedisPutProjectADM, RedisPutUserADM, RedisRequest}
import org.knora.webapi.{IRI, RedisException, SettingsImpl, UnexpectedMessageException}

import scala.util.{Failure, Success, Try}

class RedisManager(host: String, port: Int) extends LazyLogging {

    val r = new RedisClient(host, port)

    def receive(msg: RedisRequest) = msg match {
        case RedisPutUserADM(value) => redisPutUserADM(value)
        case RedisGetUserADM(identifier) => redisGetUserADM(identifier)
        case RedisPutProjectADM(value) => redisPutProjectADM(value)
        case RedisGetProjectADM(identifier) => redisGetProjectADM(identifier)
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
    private def redisPutUserADM(value: UserADM): Try[Boolean] = {

        val start = System.currentTimeMillis()

        val result: Try[Boolean] = redisSetBytes(value.id, RedisSerialization.serialize(value))

        //FIXME: failure of these is not checked
        redisSetString(value.username, value.id)
        redisSetString(value.email, value.id)

        val took = System.currentTimeMillis() - start
        logger.info(s"Redis write user: ${took}ms")

        result
    }

    /**
      * Retrieves the user stored under the identifier (either iri, username, or email).
      *
      * @param identifier the project identifier.
      */
    private def redisGetUserADM(identifier: UserIdentifierADM): Try[Option[UserADM]] = {

        val start = System.currentTimeMillis()

        // The data is stored under the IRI key.
        // Additionally, the SHORTNAME and SHORTCODE keys point to the IRI key
        val result: Try[Option[io.Serializable]] = identifier.hasType match {
            case UserIdentifierType.IRI =>
                redisGetBytes(identifier.toIri)
            case UserIdentifierType.USERNAME =>
                redisGetString(identifier.toEmail).map(maybeIriKey => maybeIriKey.map(iriKey => redisGetBytes(iriKey)))
            case UserIdentifierType.EMAIL =>
                redisGetString(identifier.toEmail).map(maybeIriKey => maybeIriKey.map(iriKey => redisGetBytes(iriKey)))
        }

        val took = System.currentTimeMillis() - start
        logger.info(s"Redis read project: ${took}ms")

        result.map(maybeBytes => maybeBytes.map(bytes => RedisSerialization.deserialize[UserADM](bytes)))
    }


    /**
      * Stores the project under the IRI and additionally the IRI under the keys of SHORTCODE and SHORTNAME:
      * IRI -> byte array
      * shortname -> IRI
      * shortcode -> IRI
      *
      * @param value the stored value
      */
    private def redisPutProjectADM(value: ProjectADM): Try[Boolean] = {

        val start = System.currentTimeMillis()

        val result: Try[Boolean] = redisSetBytes(value.id, RedisSerialization.serialize(value))

        //FIXME: failure of these is not checked
        redisSetString(value.shortcode, value.id)
        redisSetString(value.shortname, value.id)

        val took = System.currentTimeMillis() - start
        logger.info(s"Redis write project: ${took}ms")

        result
    }

    /**
      * Retrieves the project stored under the identifier (either iri, shortname, or shortcode).
      *
      * @param identifier the project identifier.
      */
    private def redisGetProjectADM(identifier: ProjectIdentifierADM): Try[Option[ProjectADM]] = {

        val start = System.currentTimeMillis()

        // The data is stored under the IRI key.
        // Additionally, the SHORTNAME and SHORTCODE keys point to the IRI key
        val result: Try[Option[Array[Byte]]] = identifier.hasType match {
            case ProjectIdentifierType.IRI =>
                redisGetBytes(identifier.toIri)
            case ProjectIdentifierType.SHORTCODE =>
                val res: Try[Option[String]] = redisGetString(identifier.toShortcode)
                res.flatMap(maybeIriKey => maybeIriKey.flatMap((iriKey => redisGetBytes(iriKey)))
            case ProjectIdentifierType.SHORTNAME =>
                redisGetString(identifier.toShortname).map(maybeIriKey => maybeIriKey.map((iriKey: String) => redisGetBytes(iriKey)))
        }

        val took = System.currentTimeMillis() - start
        logger.info(s"Redis read project: ${took}ms")

        result.map(maybeBytes => maybeBytes.map(bytes => RedisSerialization.deserialize[ProjectADM](bytes)))
    }

    /**
      * Get value stored under the key as a byte array.
      * @param key the key.
      */
    private def redisGetBytes(key: String): Try[Option[Array[Byte]]] = {

        val redisResponseTry = Try {
            val start = System.currentTimeMillis()

            val response: Option[Array[Byte]] = r.get[Array[Byte]](key)

            val took = System.currentTimeMillis() - start

            logger.info(s"Redis read byte array: ${took}ms")

            response
        }

        redisResponseTry.recover {
            case e: Exception =>
                throw RedisException(s"Reading from Redis failed.", Some(e))
        }
    }

    /**
      * Get value stored under the key as a string.
      * @param key the key.
      */
    private def redisGetString(key: String): Try[Option[String]] = {

        val redisResponseTry = Try {
            val start = System.currentTimeMillis()

            val response: Option[String] = r.get[String](key)

            val took = System.currentTimeMillis() - start

            logger.info(s"Redis read string: ${took}ms")

            response
        }

        redisResponseTry.recover {
            case e: Exception =>
                throw RedisException(s"Reading from Redis failed.", Some(e))
        }
    }

    /**
      * Store byte array value under key.
      * @param key the key.
      * @param value the byte array value.
      */
    private def redisSetBytes(key: String, value: Array[Byte]): Try[Boolean] = {

        val redisResponseTry = Try {

            val start = System.currentTimeMillis()

            val response = r.set(key, value)

            val took = System.currentTimeMillis() - start
            logger.info(s"Redis write: ${took}ms")

            response
        }

        redisResponseTry.recover {
            case e: Exception =>
                throw RedisException(s"Writing to Redis failed.", Some(e))
        }
    }

    /**
      * Store string value under key.
      * @param key the key.
      * @param value the string value.
      */
    private def redisSetString(key: String, value: String): Try[Boolean] = {

        val redisResponseTry = Try {

            val start = System.currentTimeMillis()

            val response = r.set(key, value)

            val took = System.currentTimeMillis() - start
            logger.info(s"Redis write: ${took}ms")

            response
        }

        redisResponseTry.recover {
            case e: Exception =>
                throw RedisException(s"Writing to Redis failed.", Some(e))
        }
    }

}