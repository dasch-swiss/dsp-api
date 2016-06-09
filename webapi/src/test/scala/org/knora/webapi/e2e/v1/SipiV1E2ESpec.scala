/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.messages.v1.responder.valuemessages.{ChangeFileValueApiRequestV1, CreateFileV1, CreateRichtextV1}
import org.knora.webapi.messages.v1.responder.resourcemessages.{CreateResourceApiRequestV1, CreateResourceValueV1}
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent}
import org.knora.webapi.responders._
import org.knora.webapi.responders.v1._
import org.knora.webapi.routing.v1.{ResourcesRouteV1, ValuesRouteV1}
import org.knora.webapi.store._
import org.knora.webapi.{FileWriteException, LiveActorMaker, R2RSpec, SharedTestData}
import spray.http._

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * End-to-end test specification for the resources endpoint. This specification uses the Spray Testkit as documented
  * here: http://spray.io/documentation/1.2.2/spray-testkit/
  */
class SipiV1E2ESpec extends R2RSpec {

    override def testConfigSource =
        """
         # akka.loglevel = "DEBUG"
         # akka.stdout-loglevel = "DEBUG"
        """.stripMargin



    val responderManager = system.actorOf(Props(new TestResponderManagerV1(Map(SIPI_ROUTER_ACTOR_NAME -> system.actorOf(Props(new MockSipiResponderV1))))), name = RESPONDER_MANAGER_ACTOR_NAME)

    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val resourcesPath = ResourcesRouteV1.rapierPath(system, settings, log)
    val valuesPath = ValuesRouteV1.rapierPath(system, settings, log)

    implicit val timeout: Timeout = 300.seconds

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(15).second)

    val user = "root"
    val password = "test"

    val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/all_data/admin-data.ttl", name = "http://www.knora.org/data/admin"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/ontologies/images-demo-onto.ttl", name = "http://www.knora.org/ontology/images"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images")
    )

    "Load test data" in {
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 300.seconds)
    }

    object RequestParams {

        val createResourceParams = CreateResourceApiRequestV1(
            restype_id = "http://www.knora.org/ontology/incunabula#page",
            properties = Map(
                "http://www.knora.org/ontology/incunabula#pagenum" -> Seq(CreateResourceValueV1(
                    richtext_value = Some(CreateRichtextV1(
                        utf8str = "test_page",
                        textattr = "{}",
                        resource_reference = List.empty[String]
                    ))
                )),
                "http://www.knora.org/ontology/incunabula#origname" -> Seq(CreateResourceValueV1(
                    richtext_value = Some(CreateRichtextV1(
                        utf8str = "test",
                        textattr = "{}",
                        resource_reference = List.empty[String]
                    ))
                )),
                "http://www.knora.org/ontology/incunabula#partOf" -> Seq(CreateResourceValueV1(
                    link_value = Some("http://data.knora.org/5e77e98d2603")
                )),
                "http://www.knora.org/ontology/incunabula#seqnum" -> Seq(CreateResourceValueV1(
                    int_value = Some(999)
                ))
            ),
            label = "test",
            project_id = "http://data.knora.org/projects/77275339"
        )

        val pathToFile = "_test_data/test_route/images/Chlaus.jpg"

        def createTmpFileDir() = {
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

        "create a resource with a digital representation doing a multipart request containing the binary data (non GUI-case)" in {

            val fileToSend = new File(RequestParams.pathToFile)
            // check if the file exists
            assert(fileToSend.exists(), s"File ${RequestParams.pathToFile} does not exist")

            val formData = MultipartFormData(Seq(
                BodyPart(entity = HttpEntity(MediaTypes.`application/json`, RequestParams.createResourceParams.toJsValue.compactPrint), fieldName = "json"),
                BodyPart(file = fileToSend, fieldName = "file", ContentType(mediaType = MediaTypes.`image/jpeg`))
            ))

            RequestParams.createTmpFileDir()

            Post("/v1/resources", formData) ~> addCredentials(BasicHttpCredentials(user, password)) ~> resourcesPath ~> check {

                val tmpFile = SourcePath.getSourcePath()

                assert(!tmpFile.exists(), s"Tmp file ${tmpFile} was not deleted.")
                assert(status == StatusCodes.OK, "Status code is not set to OK, Knora says:\n" + responseAs[String])
            }
        }

        "try to create a resource sending binaries (multipart request) but fail because the mimetype is wrong" in {

            val fileToSend = new File(RequestParams.pathToFile)
            // check if the file exists
            assert(fileToSend.exists(), s"File ${RequestParams.pathToFile} does not exist")

            val formData = MultipartFormData(Seq(
                BodyPart(entity = HttpEntity(MediaTypes.`application/json`, RequestParams.createResourceParams.toJsValue.compactPrint), fieldName = "json"),
                // set mimetype tiff, but jpeg is expected
                BodyPart(file = fileToSend, fieldName = "file", ContentType(mediaType = MediaTypes.`image/tiff`))
            ))

            RequestParams.createTmpFileDir()

            Post("/v1/resources", formData) ~> addCredentials(BasicHttpCredentials(user, password)) ~> resourcesPath ~> check {

                val tmpFile = SourcePath.getSourcePath()

                // this test is expected to fail

                // check that the tmp file is also deleted in case the test fails
                assert(!tmpFile.exists(), s"Tmp file ${tmpFile} was not deleted.")
                assert(status != StatusCodes.OK, "Status code is not set to OK, Knora says:\n" + responseAs[String])
            }
        }

        "create a resource with a digital representation doing a params only request without binary data (GUI-case)" in {

            val params = RequestParams.createResourceParams.copy(
                file = Some(CreateFileV1(
                    originalFilename = "Chlaus.jpg",
                    originalMimeType = "image/jpeg",
                    filename = "./test_server/images/Chlaus.jpg"
                ))
            )

            Post("/v1/resources", HttpEntity(MediaTypes.`application/json`, params.toJsValue.compactPrint)) ~> addCredentials(BasicHttpCredentials(user, password)) ~> resourcesPath ~> check {
                assert(status == StatusCodes.OK, "Status code is not set to OK, Knora says:\n" + responseAs[String])
            }
        }

    }

    "The Values endpoint" should {

        "change the file value of an existing page (submitting binaries)" in {

            val fileToSend = new File(RequestParams.pathToFile)
            // check if the file exists
            assert(fileToSend.exists(), s"File ${RequestParams.pathToFile} does not exist")

            val formData = MultipartFormData(Seq(
                BodyPart(file = fileToSend, fieldName = "file", ContentType(mediaType = MediaTypes.`image/jpeg`))
            ))

            RequestParams.createTmpFileDir()

            val resIri = URLEncoder.encode("http://data.knora.org/8a0b1e75", "UTF-8")

            Put("/v1/filevalue/" + resIri, formData) ~> addCredentials(BasicHttpCredentials(user, password)) ~> valuesPath ~> check {

                val tmpFile = SourcePath.getSourcePath()

                assert(!tmpFile.exists(), s"Tmp file ${tmpFile} was not deleted.")
                assert(status == StatusCodes.OK, "Status code is not set to OK, Knora says:\n" + responseAs[String])
            }

        }

        "try to change the file value of an existing page (submitting binaries) but fail because the mimetype is wrong" in {

            val fileToSend = new File(RequestParams.pathToFile)
            // check if the file exists
            assert(fileToSend.exists(), s"File ${RequestParams.pathToFile} does not exist")

            val formData = MultipartFormData(Seq(
                // set mimetype tiff, but jpeg is expected
                BodyPart(file = fileToSend, fieldName = "file", ContentType(mediaType = MediaTypes.`image/tiff`))
            ))

            RequestParams.createTmpFileDir()

            val resIri = URLEncoder.encode("http://data.knora.org/8a0b1e75", "UTF-8")

            Put("/v1/filevalue/" + resIri, formData) ~> addCredentials(BasicHttpCredentials(user, password)) ~> valuesPath ~> check {

                val tmpFile = SourcePath.getSourcePath()

                // this test is expected to fail

                // check that the tmp file is also deleted in case the test fails
                assert(!tmpFile.exists(), s"Tmp file ${tmpFile} was not deleted.")
                assert(status != StatusCodes.OK, "Status code is not set to OK, Knora says:\n" + responseAs[String])
            }

        }


        "change the file value of an existing page (submitting params only, no binaries)" in {

            val params = ChangeFileValueApiRequestV1(
                file = CreateFileV1(
                    originalFilename = "Chlaus.jpg",
                    originalMimeType = "image/jpeg",
                    filename = "./test_server/images/Chlaus.jpg"
                )
            )

            val resIri = URLEncoder.encode("http://data.knora.org/8a0b1e75", "UTF-8")

            Put("/v1/filevalue/" + resIri, HttpEntity(MediaTypes.`application/json`, params.toJsValue.compactPrint)) ~> addCredentials(BasicHttpCredentials(user, password)) ~> valuesPath ~> check {
                assert(status == StatusCodes.OK, "Status code is not set to OK, Knora says:\n" + responseAs[String])
            }

        }
    }
}
