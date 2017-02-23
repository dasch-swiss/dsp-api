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
import java.net.URLEncoder
import java.nio.file.{Files, Paths}

import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpEntity, _}
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.util.MutableTestIri
import org.knora.webapi.{FileWriteException, ITSpec, InvalidApiJsonException}
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


object SipiV1ITSpec {
    val config: Config = ConfigFactory.parseString(
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
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")
    )

    private val username = "root@example.com"
    private val password = "test"

    "Check if SIPI is running" in {
        // Contact the SIPI fileserver to see if Sipi is running
        // Plase make sure that 1) fileserver.docroot is set in config file and 2) it contains a file test.html
        val request = Get(baseSipiUrl + "/server/test.html")
        val response = singleAwaitingRequest(request, 5.second)
        assert(response.status == StatusCodes.OK, s"SIPI is probably not running! ${response.status}")
    }

    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        val request = Post(baseApiUrl + "/v1/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    val paramsPageWithBinaries =
        s"""
           {
                "restype_id": "http://www.knora.org/ontology/incunabula#page",
                "label": "test",
                "project_id": "http://data.knora.org/projects/77275339",
                "properties": {
                    "http://www.knora.org/ontology/incunabula#pagenum": [
                        {
                            "richtext_value": {
                                "utf8str": "test_page"
                            }
                        }
                    ],
                    "http://www.knora.org/ontology/incunabula#origname": [
                        {
                            "richtext_value": {
                                "utf8str": "test"
                            }
                        }
                    ],
                    "http://www.knora.org/ontology/incunabula#partOf": [
                        {
                            "link_value": "http://data.knora.org/5e77e98d2603"
                        }
                    ],
                    "http://www.knora.org/ontology/incunabula#seqnum": [
                        {
                            "int_value": 999
                        }
                    ]
                }
           }
         """

    val pathToChlaus = "_test_data/test_route/images/Chlaus.jpg"
    val pathToMarbles = "_test_data/test_route/images/marbles.tif"
    val pageIri = new MutableTestIri

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

    "Knora and Sipi" should {

        "create an 'incunabula:page' with binary data" in {

            /* for live testing do:
             * inside sipi folder: ./local/bin/sipi --config config/sipi.knora-config.lua
             * inside webapi folder ./_test_data/test_route/create_page_with_binaries.py
             */

            val fileToSend = new File(pathToChlaus)
            // check if the file exists
            assert(fileToSend.exists(), s"File $pathToChlaus does not exist")

            val formData = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "json",
                    HttpEntity(ContentTypes.`application/json`, paramsPageWithBinaries)
                ),
                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(MediaTypes.`image/jpeg`, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
            )

            createTmpFileDir()
            val knoraRequest = Post(baseApiUrl + "/v1/resources", formData) ~> addCredentials(BasicHttpCredentials(username, password))
            val knoraResponse = singleAwaitingRequest(knoraRequest, 20.seconds)
            val knoraResponseBodyFuture: Future[String] = knoraResponse.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
            val knoraResponseBodyStr = Await.result(knoraResponseBodyFuture, 5.seconds)
            assert(knoraResponse.status === StatusCodes.OK, knoraResponseBodyStr)

            val resourceIri: String = knoraResponseBodyStr.parseJson.asJsObject.fields("res_id").asInstanceOf[JsString].value
            pageIri.set(resourceIri)

            val requestNewResource = Get(baseApiUrl + "/v1/resources/" + URLEncoder.encode(pageIri.get, "UTF-8")) ~> addCredentials(BasicHttpCredentials(username, password))
            val responseNewResource = singleAwaitingRequest(requestNewResource, 5.seconds)
            val responseNewResourceBodyFuture: Future[String] = responseNewResource.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
            val responseNewResourceStr: String = Await.result(responseNewResourceBodyFuture, 5.seconds)

            assert(responseNewResource.status == StatusCodes.OK, responseNewResourceStr)

            val iiifPath = responseNewResourceStr.parseJson.asJsObject.fields.get("resinfo") match {
                case Some(resinfo: JsObject) =>
                    resinfo.fields.get("locdata") match {
                        case Some(locdata: JsObject) =>
                            locdata.fields.get("path") match {
                                case Some(JsString(path)) => path
                                case None => throw InvalidApiJsonException("no 'path' given")
                                case other => throw InvalidApiJsonException("'path' could not pe parsed correctly")
                            }
                        case None => throw InvalidApiJsonException("no 'locdata' given")

                        case other => throw InvalidApiJsonException("'locdata' could not pe parsed correctly")
                    }

                case None => throw InvalidApiJsonException("no 'resinfo' given")

                case other => throw InvalidApiJsonException("'resinfo' could not pe parsed correctly")
            }

            // TODO: now we could try to request the path from Sipi
            // TODO: we should run Sipi in test mode so it does not do run the preflight request

            //println(IIIFPath)
        }

        "change an 'incunabula:page' with binary data" in {

            val fileToSend = new File(pathToMarbles)
            // check if the file exists
            assert(fileToSend.exists(), s"File $pathToMarbles does not exist")

            val formData = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(MediaTypes.`image/tiff`, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
            )

            createTmpFileDir()
            val request = Put(baseApiUrl + "/v1/filevalue/" + URLEncoder.encode(pageIri.get, "UTF-8"), formData) ~> addCredentials(BasicHttpCredentials(username, password))
            val response = singleAwaitingRequest(request, 5.seconds)

            assert(response.status === StatusCodes.OK)
        }

        // "create an 'incunabula:page' with parameters" in {}

        "change an 'incunabula:page' with parameters" in {
            val fileToSend = new File(pathToChlaus)
            // check if the file exists
            assert(fileToSend.exists(), s"File $pathToChlaus does not exist")

            val sipiFormData = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(MediaTypes.`image/jpeg`, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
            )

            val sipiRequest = Post(baseSipiUrl + "/make_thumbnail", sipiFormData) ~> addCredentials(BasicHttpCredentials(username, password))
            val sipiResponse = singleAwaitingRequest(sipiRequest, 10.seconds)

            val sipiResponseBodyFuture: Future[String] = sipiResponse.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
            val sipiResponseBodyStr = Await.result(sipiResponseBodyFuture, 5.seconds)
            assert(sipiResponse.status === StatusCodes.OK, sipiResponseBodyStr)

            val jsonFields = sipiResponseBodyStr.parseJson.asJsObject.fields

            val knoraParams = JsObject(
                Map(
                    "file" -> JsObject(
                        Map(
                            "originalFilename" -> jsonFields("original_filename"),
                            "originalMimeType" -> jsonFields("original_mimetype"),
                            "filename" -> jsonFields("filename")
                        )
                    )
                )
            )

            val knoraRequest = Put(baseApiUrl + "/v1/filevalue/" + URLEncoder.encode("http://data.knora.org/8a0b1e75", "UTF-8"), HttpEntity(ContentTypes.`application/json`, knoraParams.compactPrint)) ~> addCredentials(BasicHttpCredentials(username, password))
            val knoraResponse = singleAwaitingRequest(knoraRequest, 5.seconds)
            val knoraResponseBodyFuture: Future[String] = knoraResponse.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
            val knoraResponseBodyStr = Await.result(knoraResponseBodyFuture, 5.seconds)
            assert(knoraResponse.status === StatusCodes.OK, knoraResponseBodyStr)
        }

        // "create an 'anything:thing'" in {}

    }

}


