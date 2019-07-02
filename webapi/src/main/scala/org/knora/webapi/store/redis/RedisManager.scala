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

import com.redis._
import com.redis.serialization.Parse.Implicits.parseByteArray
import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.store.redismessages.{RedisGetProjectADM, RedisPutProjectADM, RedisRequest}
import org.knora.webapi.{RedisException, SettingsImpl, UnexpectedMessageException}

import scala.util.{Success, Try}

class RedisManager(host: String, port: Int) extends LazyLogging {

    val r = new RedisClient(host, port)

    def receive(msg: RedisRequest) = msg match {
        case RedisPutProjectADM(key, value) => redisPutProjectADM(key, value)
        case RedisGetProjectADM(key) => redisGetProjectADM(key)
        case other => throw UnexpectedMessageException(s"RedisManager received an unexpected message: $other")
    }


    /**
      * Stores the project under the key.
      *
      * @param key   the key under which the value will be stored
      * @param value the stored value
      */
    private def redisPutProjectADM(key: String, value: ProjectADM): Try[Boolean] = {

        val start = System.currentTimeMillis()

        val result: Try[Boolean] = redisSet(key,RedisSerialization.serialize(value))

        val took = System.currentTimeMillis() - start
        logger.info(s"Redis write project: ${took}ms")

        result
    }

    /**
      * Retrieves the project stored under the key.
      *
      * @param key the key.
      */
    private def redisGetProjectADM(key: String): Try[Option[ProjectADM]] = {

        val start = System.currentTimeMillis()
        val result: Option[Array[Byte]]= redisGet(key)

        val res = result match {
            case Some(value) => Success(Some(RedisSerialization.deserialize[ProjectADM](value)))
            case None => Success(None)
        }

        val took = System.currentTimeMillis() - start
        logger.info(s"Redis read project: ${took}ms")

        res
    }

    /**
      * Get value stored under key.
      * @param key the key.
      */
    private def redisGet(key: String): Option[Array[Byte]] = {

        val start = System.currentTimeMillis()

        val response: Option[Array[Byte]] = r.get[Array[Byte]](key)

        val took = System.currentTimeMillis() - start

        logger.info(s"Redis read: ${took}ms")

        response
    }

    /**
      * Store value under key.
      * @param key the key.
      * @param value the value.
      */
    private def redisSet(key: String, value: Array[Byte]): Try[Boolean] = {

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