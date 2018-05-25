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

package org.knora.webapi.e2e.v2

import java.net.URLEncoder

import akka.actor.{ActorSystem, Props}
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent}
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.routing.v2.ListsRouteV2
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.{KnoraSystemInstances, LiveActorMaker, R2RSpec, SharedTestDataADM}
import org.knora.webapi.util.{AkkaHttpUtils, FileUtil}
import java.io.File

import spray.json.{JsObject, JsValue, JsonParser}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor}

/**
  * End-to-end test specification for the lists endpoint. This specification uses the Spray Testkit as documented
  * here: http://spray.io/documentation/1.2.2/spray-testkit/
  */
class ListsRouteV2R2Spec extends R2RSpec {

    override def testConfigSource: String =
        """
          |# akka.loglevel = "DEBUG"
          |# akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val listsPath = ListsRouteV2.knoraApiPath(system, settings, log)

    implicit private val timeout: Timeout = settings.defaultRestoreTimeout

    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(new DurationInt(15).second)

    implicit val ec: ExecutionContextExecutor = system.dispatcher

    private val imagesUser01 = SharedTestDataADM.imagesUser01

    private val password = "test"

    private val rdfDataObjects = List(

        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")

    )

    "Load test data" in {
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 360.seconds)
        Await.result(responderManager ? LoadOntologiesRequestV2(KnoraSystemInstances.Users.SystemUser), 30.seconds)
    }

    "The lists v2 endpoint" should {

        "perform a request for a list" in {

            Get(s"/v2/lists/${URLEncoder.encode("http://rdfh.ch/lists/00FF/73d0ec0302", "UTF-8")}") ~> listsPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/listsR2RV2/imagesList.jsonld")))

                val responseJson: JsObject = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == expectedAnswerJSONLD)

            }
        }

        "perform a request for a node" in {

            Get(s"/v2/node/${URLEncoder.encode("http://rdfh.ch/lists/00FF/4348fb82f2", "UTF-8")}") ~> listsPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD: JsValue = JsonParser(FileUtil.readTextFile(new File("src/test/resources/test-data/listsR2RV2/imagesListNode.jsonld")))

                val responseJson: JsObject = AkkaHttpUtils.httpResponseToJson(response)
                assert(responseJson == expectedAnswerJSONLD)

            }


        }


    }
}