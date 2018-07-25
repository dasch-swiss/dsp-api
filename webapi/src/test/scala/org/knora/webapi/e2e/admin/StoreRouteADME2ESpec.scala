/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.e2e.admin

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.ConfigFactory
import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.app.appmessages.SetAllowReloadOverHTTPState
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import spray.json._

import scala.concurrent.duration._

object StoreRouteADME2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing the 'v1/store' route.
  *
  * This spec tests the 'v1/store' route.
  */
class StoreRouteADME2ESpec extends E2ESpec(StoreRouteADME2ESpec.config) with TriplestoreJsonProtocol {
    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(120.seconds)

	/**
      * The marshaling to Json is done automatically by spray, hence the import of the 'TriplestoreJsonProtocol'.
      * The Json which spray generates looks like this:
      *
      *  [
      *     {"path": "_test_data/all_data/incunabula-data.ttl", "name": "http://www.knora.org/data/0803/incunabula"},
      *     {"path": "_test_data/demo_data/images-demo-data.ttl", "name": "http://www.knora.org/data/00FF/images"}
      *  ]
      *
      * and could have been supplied to the post request instead of the scala object.
      */
    override protected val rdfDataObjects: List[RdfDataObject] = List(
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images")
    )

    override def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit = {}

    "The ResetTriplestoreContent Route ('admin/store/ResetTriplestoreContent')" should {

        "succeed with resetting if startup flag is set" in {
            /**
              * This test corresponds to the following curl call:
              * curl -H "Content-Type: application/json" -X POST -d '[{"path":"../knora-ontologies/knora-base.ttl","name":"http://www.knora.org/ontology/knora-base"}]' http://localhost:3333/admin/store/ResetTriplestoreContent
              */

            log.debug("==>>")
			applicationStateActor ! SetAllowReloadOverHTTPState(true)
            log.debug("==>>")
            val request = Post(baseApiUrl + "/admin/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
            val response = singleAwaitingRequest(request, 300.seconds)
            // log.debug("==>> " + response.toString)
            assert(response.status === StatusCodes.OK)
        }


        "fail with resetting if startup flag is not set" in {
            applicationStateActor ! SetAllowReloadOverHTTPState(false)
            val request = Post(baseApiUrl + "/admin/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
            val response = singleAwaitingRequest(request, 300.seconds)
            // log.debug("==>> " + response.toString)
            assert(response.status === StatusCodes.Forbidden)
        }
    }
}
