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

import akka.actor.ActorSystem
import akka.http.scaladsl.testkit.RouteTestTimeout
import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.util.StringFormatter

import scala.concurrent.ExecutionContextExecutor

/**
  * Tests [[InstanceChecker]].
  */
class InstanceCheckerSpec extends E2ESpec {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout)

    implicit val ec: ExecutionContextExecutor = system.dispatcher

    override lazy val rdfDataObjects: List[RdfDataObject] = List(
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")

    )

    private val instanceChecker: InstanceChecker = InstanceChecker.getJsonLDChecker

    "The InstanceChecker" should {

    }
}
