/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.e2e

import org.knora.webapi.exceptions.TestConfigurationException
import org.knora.webapi.settings.KnoraSettingsImpl
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

/**
 * Collects E2E test requests and responses for use as client test data.
 *
 * @param settings the application settings.
 */
class ClientTestDataCollector(settings: KnoraSettingsImpl) {
    private val redisHashName: String = "client-test-data"

    // Are we configured to collect client test data?
    private val maybeJedisPool: Option[JedisPool] = if (settings.collectClientTestData) {
        // Yes. Make a connection pool for connecting to Redis.

        val redisHost: String = settings.clientTestDataRedisHost.getOrElse(throw TestConfigurationException(s"No Redis host configured for client test data"))
        val redisPort: Int = settings.clientTestDataRedisPort.getOrElse(throw TestConfigurationException(s"No Redis port configured for client test data"))
        Some(new JedisPool(new JedisPoolConfig(), redisHost, redisPort, 30999))
    } else {
        None
    }

    /**
     * Stores a client test data file.
     *
     * @param fileContent the content of the file to be stored.
     */
    def addFile(fileContent: TestDataFileContent): Unit = {
        // Are we configured to collect client test data?
        maybeJedisPool match {
            case Some(jedisPool) =>
                // Yes. Store the file.

                val jedis: Jedis = jedisPool.getResource

                try {
                    jedis.hset(redisHashName, fileContent.filePath.toString, fileContent.text)
                } finally {
                    jedis.close()
                }

            case None =>
                // No. Do nothing.
                ()
        }
    }
}

/**
 * Represents a file containing generated client API test data.
 *
 * @param filePath the file path in which the test data should be saved.
 * @param text     the source code.
 */
case class TestDataFileContent(filePath: TestDataFilePath, text: String)

/**
 * Represents the filesystem path of a file containing generated test data.
 *
 * @param directoryPath the path of the directory containing the file,
 *                      relative to the root directory of the source tree.
 * @param filename      the filename, without the file extension.
 * @param fileExtension the file extension.
 */
case class TestDataFilePath(directoryPath: Seq[String], filename: String, fileExtension: String) {
    override def toString: String = {
        (directoryPath :+ filename + "." + fileExtension).mkString("/")
    }
}
