/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.e2e.v1

import java.io.File
import java.nio.file.{Files, Paths}

import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpEntity, _}
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.v1.responder.resourcemessages.{CreateResourceApiRequestV1, CreateResourceValueV1}
import org.knora.webapi.messages.v1.responder.valuemessages.CreateRichtextV1
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.{FileWriteException, ITSpec}
import spray.json._

import scala.concurrent.duration._


object SipiV1ITSpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing sipi integration. A running SIPI server is needed!
  */
class SipiV1ITSpec extends ITSpec(SipiV1ITSpec.config) with TriplestoreJsonProtocol {

    private val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/ontologies/anything-onto.ttl", name = "http://www.knora.org/ontology/anything"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")
    )

    private val username = "root"
    private val password = "test"

    "Check if SIPI is running" in {
        // Contact the SIPI '/info' route, to see if SIPI is running
        val request = Get(baseSipiUrl + "/info")
        val response = singleAwaitingRequest(request, 1.second)
        assert(response.status == StatusCodes.OK, s"SIPI is probably not running!")
    }

    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        val request = Post(baseApiUrl + "/v1/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    object RequestParams {

        val createResourceParams = CreateResourceApiRequestV1(
            restype_id = "http://www.knora.org/ontology/incunabula#page",
            properties = Map(
                "http://www.knora.org/ontology/incunabula#pagenum" -> Seq(CreateResourceValueV1(
                    richtext_value = Some(CreateRichtextV1(
                        utf8str = "test_page",
                        textattr = None,
                        resource_reference = None
                    ))
                )),
                "http://www.knora.org/ontology/incunabula#origname" -> Seq(CreateResourceValueV1(
                    richtext_value = Some(CreateRichtextV1(
                        utf8str = "test",
                        textattr = None,
                        resource_reference = None
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

        "create an 'incunabula:page' with binary data" in {

            /* for live testing do:
             * inside sipi folder: ./local/bin/sipi -config config/sipi.knora-config.lua
             * inside webapi folder ./_test_data/test_route/create_page_with_binaries.py
             */

            val fileToSend = new File(RequestParams.pathToFile)
            // check if the file exists
            assert(fileToSend.exists(), s"File ${RequestParams.pathToFile} does not exist")

            val formData = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "json",
                    HttpEntity(ContentTypes.`application/json`, RequestParams.createResourceParams.toJsValue.compactPrint)
                ),
                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(MediaTypes.`image/jpeg`, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
            )

            RequestParams.createTmpFileDir()
            val request = Post(baseApiUrl + "/v1/resources", formData) ~> addCredentials(BasicHttpCredentials(username, password))
            val response = singleAwaitingRequest(request, 20.seconds)
            assert(response.status === StatusCodes.OK)
        }

        "create an 'incunabula:page' with parameters" in {



        }

        "change an 'incunabula:page' with binary data" in {}

        "change an 'incunabula:page' with parameters" in {}

        "create an 'anything:thing'" in {}
    }
}
