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

package org.knora.webapi.e2e.v1

import java.io.File
import java.net.URLEncoder
import java.nio.file.{Files, Paths}

import akka.actor._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.RouteTestTimeout
import org.knora.webapi._
import org.knora.webapi.app.{APPLICATION_MANAGER_ACTOR_NAME, ApplicationActor}
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.v1.responder.resourcemessages.{CreateResourceApiRequestV1, CreateResourceValueV1}
import org.knora.webapi.messages.v1.responder.valuemessages.{ChangeFileValueApiRequestV1, CreateRichtextV1}
import org.knora.webapi.routing.v1.{ResourcesRouteV1, ValuesRouteV1}
import org.knora.webapi.testing.tags.E2ETest

/**
  * End-to-end test specification for the resources endpoint. This specification uses the Spray Testkit as documented
  * here: http://spray.io/documentation/1.2.2/spray-testkit/
  */
@E2ETest
class SipiV1R2RSpec extends R2RSpec {

    override def testConfigSource: String =
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    private val resourcesPath = new ResourcesRouteV1(routeData).knoraApiPath
    private val valuesPath = new ValuesRouteV1(routeData).knoraApiPath

    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout)

    private val incunabulaProjectAdminEmail = SharedTestDataV1.incunabulaProjectAdminUser.userData.email.get
    private val testPass = "test"

    override lazy val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images")
    )

    /* we need to run our app with the mocked sipi actor */
    override lazy val appActor: ActorRef = system.actorOf(Props(new ApplicationActor with ManagersWithMockedSipi).withDispatcher(KnoraDispatchers.KnoraActorDispatcher), name = APPLICATION_MANAGER_ACTOR_NAME)

    object RequestParams {

        val createResourceParams = CreateResourceApiRequestV1(
            restype_id = "http://www.knora.org/ontology/0803/incunabula#page",
            properties = Map(
                "http://www.knora.org/ontology/0803/incunabula#pagenum" -> Seq(CreateResourceValueV1(
                    richtext_value = Some(CreateRichtextV1(
                        utf8str = Some("test_page")
                    ))
                )),
                "http://www.knora.org/ontology/0803/incunabula#origname" -> Seq(CreateResourceValueV1(
                    richtext_value = Some(CreateRichtextV1(
                        utf8str = Some("test")
                    ))
                )),
                "http://www.knora.org/ontology/0803/incunabula#partOf" -> Seq(CreateResourceValueV1(
                    link_value = Some("http://rdfh.ch/0803/5e77e98d2603")
                )),
                "http://www.knora.org/ontology/0803/incunabula#seqnum" -> Seq(CreateResourceValueV1(
                    int_value = Some(999)
                ))
            ),
            label = "test",
            project_id = "http://rdfh.ch/projects/0803"
        )

        val pathToFile = "_test_data/test_route/images/Chlaus.jpg"

        def createTmpFileDir(): Unit = {
            // check if tmp datadir exists and create it if not
            if (!Files.exists(Paths.get(settings.tmpDataDir))) {
                try {
                    val tmpDir = new File(settings.tmpDataDir)
                    tmpDir.mkdir()
                } catch {
                    case e: Throwable => throw FileWriteException(s"Tmp data directory ${settings.tmpDataDir} could not be created: ${e.getMessage}")
                }
            }
        }

    }

    "The Resources Endpoint" should {

        "create a resource with a digital representation" in {
            val internalFilename = "IQUO3t1AABm-FSLC0vNvVpr.jp2"

            val params = RequestParams.createResourceParams.copy(
                file = Some(internalFilename)
            )

            Post("/v1/resources", HttpEntity(MediaTypes.`application/json`, params.toJsValue.compactPrint)) ~> addCredentials(BasicHttpCredentials(incunabulaProjectAdminEmail, testPass)) ~> resourcesPath ~> check {
                assert(status == StatusCodes.OK, "Status code is not set to OK, Knora says:\n" + responseAs[String])
            }
        }

    }

    "The Values endpoint" should {

        "change the file value of an existing page" in {
            val internalFilename ="FSLC0vNvVpr-IQUO3t1AABm.jp2"

            val params = ChangeFileValueApiRequestV1(
                file = internalFilename
            )

            val resIri = URLEncoder.encode("http://rdfh.ch/0803/8a0b1e75", "UTF-8")

            Put("/v1/filevalue/" + resIri, HttpEntity(MediaTypes.`application/json`, params.toJsValue.compactPrint)) ~> addCredentials(BasicHttpCredentials(incunabulaProjectAdminEmail, testPass)) ~> valuesPath ~> check {
                assert(status == StatusCodes.OK, "Status code is not set to OK, Knora says:\n" + responseAs[String])
            }

        }
    }
}
