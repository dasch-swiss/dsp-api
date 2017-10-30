/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.knora.webapi.util.{CacheUtil, StringFormatter}
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite, WordSpecLike}


/**
  * Created by subotic on 08.12.15.
  */
class R2RSpec extends Suite with ScalatestRouteTest with WordSpecLike with Matchers with BeforeAndAfterAll with RequestBuilding {

    def actorRefFactory = system

    val settings = Settings(system)
    StringFormatter.init(settings)

    val logger = akka.event.Logging(system, this.getClass())
    implicit val log = logger

    implicit val knoraExceptionHandler = KnoraExceptionHandler(settings, log)

    override def beforeAll {
        CacheUtil.createCaches(settings.caches)
    }

    override def afterAll {
        CacheUtil.removeAllCaches()
    }

}
