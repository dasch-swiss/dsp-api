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

import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.util.TestDataFileContent
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

/**
 * Collects E2E test requests and responses for use as client test data.
 *
 * @param settings the application settings.
 */
class ClientTestDataCollector(settings: KnoraSettingsImpl) {
    private val redisHashName: String = "client-test-data"
    private val jedisPool: JedisPool = new JedisPool(new JedisPoolConfig(), settings.clientTestDataRedisHost, settings.clientTestDataRedisPort, 30999)

    /**
     * Stores a client test data file.
     *
     * @param fileContent the content of the file to be stored.
     */
    def addFile(fileContent: TestDataFileContent): Unit = {
        val jedis: Jedis = jedisPool.getResource

        try {
            jedis.hset(redisHashName, fileContent.filePath.toString, fileContent.text)
        } finally {
            jedis.close()
        }
    }
}
