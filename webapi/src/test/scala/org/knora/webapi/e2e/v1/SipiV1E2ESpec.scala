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
import java.nio.file.{Files, Paths}

import akka.actor.ActorDSL._
import akka.actor._
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.e2e.E2ESpec
import org.knora.webapi.messages.v1respondermessages.resourcemessages.{CreateResourceApiRequestV1, CreateResourceValueV1}
import org.knora.webapi.messages.v1respondermessages.sipimessages._
import org.knora.webapi.messages.v1respondermessages.triplestoremessages.{RdfDataObject, ResetTriplestoreContent}
import org.knora.webapi.messages.v1respondermessages.valuemessages.{CreateFileV1, CreateRichtextV1, StillImageFileValueV1}
import org.knora.webapi.responders._
import org.knora.webapi.responders.v1._
import org.knora.webapi.routing.v1.ResourcesRouteV1
import org.knora.webapi.store._
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.InputValidation
import org.knora.webapi.{FileWriteException, LiveActorMaker}
import spray.http._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * End-to-end test specification for the resources endpoint. This specification uses the Spray Testkit as documented
  * here: http://spray.io/documentation/1.2.2/spray-testkit/
  */
class SipiV1E2ESpec extends E2ESpec {

    override def testConfigSource =
        """
         # akka.loglevel = "DEBUG"
         # akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    /**
      * Imitates the Sipi server by returning a [[SipiResponderConversionResponseV1]] representing an image conversion request.
      *
      * @param conversionRequest the conversion request to be handled.
      * @return a [[SipiResponderConversionResponseV1]] imitating the answer from Sipi.
      */
    private def imageConversionResponse(conversionRequest: SipiResponderConversionRequestV1): Future[SipiResponderConversionResponseV1] = {

        // delete tmp file (depending on the kind of request given: only necessary if Knora stored the file - non GUI-case)
        def deleteTmpFile(conversionRequest: SipiResponderConversionRequestV1): Unit = {
            conversionRequest match {
                case (conversionPathRequest: SipiResponderConversionPathRequestV1) =>
                    // a tmp file has been created by the resources route (non GUI-case), delete it
                    InputValidation.deleteFileFromTmpLocation(conversionPathRequest.source)
                case _ => ()
            }
        }

        val originalFilename = conversionRequest.originalFilename
        val originalMimeType: String = conversionRequest.originalMimeType

        val fileValuesV1 = Vector(StillImageFileValueV1(// full representation
            internalMimeType = "image/jp2",
            originalFilename = originalFilename,
            originalMimeType = Some(originalMimeType),
            dimX = 800,
            dimY = 800,
            internalFilename = "full.jp2",
            qualityLevel = 100,
            qualityName = Some("full")
        ),
            StillImageFileValueV1(// thumbnail representation
                internalMimeType = "image/jpeg",
                originalFilename = originalFilename,
                originalMimeType = Some(originalMimeType),
                dimX = 80,
                dimY = 80,
                internalFilename = "thumb.jpg",
                qualityLevel = 10,
                qualityName = Some("thumbnail"),
                isPreview = true
            ))

        deleteTmpFile(conversionRequest)

        Future(SipiResponderConversionResponseV1(fileValuesV1, file_type = SipiConstants.FileType.IMAGE))
    }

    val sipiMock = actor("mocksipi")(new Act {
        become {
            case sipiResponderConversionFileRequest: SipiResponderConversionFileRequestV1 => future2Message(sender(), imageConversionResponse(sipiResponderConversionFileRequest), log)
            case sipiResponderConversionPathRequest: SipiResponderConversionPathRequestV1 => future2Message(sender(), imageConversionResponse(sipiResponderConversionPathRequest), log)
        }
    })

    val responderManager = system.actorOf(Props(new TestResponderManagerV1(Map(SIPI_ROUTER_ACTOR_NAME -> sipiMock))), name = RESPONDER_MANAGER_ACTOR_NAME)

    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val resourcesPath = ResourcesRouteV1.rapierPath(system, settings, log)

    implicit val timeout: Timeout = 300.seconds

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(15).second)

    val user = "root"
    val password = "test"

    val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/ontologies/images-demo-onto.ttl", name = "http://www.knora.org/ontology/images"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images")
    )

    "Load test data" in {
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 300.seconds)
    }


    "The Resources Endpoint" should {

        "create a resource with a digital representation doing a multipart request containing the binary data (non GUI-case)" in {

            val params = CreateResourceApiRequestV1(
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
            val fileToSend = new File(pathToFile)
            // check if the file exists
            assert(fileToSend.exists(), s"File ${pathToFile} does not exist")

            val formData = MultipartFormData(Seq(
                BodyPart(entity = HttpEntity(MediaTypes.`application/json`, params.toJsValue.compactPrint), fieldName = "json"),
                BodyPart(file = fileToSend, fieldName = "file", ContentType(mediaType = MediaTypes.`image/jpeg`))
            ))

            // check if tmp datadir exists and create it if not
            if (!Files.exists(Paths.get(settings.tmpDataDir))) {
                try {
                    val tmpDir = new File(settings.tmpDataDir)
                    tmpDir.mkdir()
                } catch {
                    case e: Throwable => throw FileWriteException(s"Tmp data directory ${settings.tmpDataDir} could not be created: ${e.getMessage}")
                }
            }

            Post("/v1/resources", formData) ~> addCredentials(BasicHttpCredentials(user, password)) ~> resourcesPath ~> check {
                assert(status == StatusCodes.OK, "Status code is not set to OK, Knora says:\n" + responseAs[String])
            }
        }

        "create a resource with a digital representation doing a params only request without binary data (GUI-case)" in {

            val params = CreateResourceApiRequestV1(
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
                project_id = "http://data.knora.org/projects/77275339",
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

}
