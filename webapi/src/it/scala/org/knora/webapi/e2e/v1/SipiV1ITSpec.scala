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
  * End-to-End (E2E) test specification for testing Knora-Sipi integration. Sipi must be running with the config file
  * `sipi.knora-config.lua`.
  */
class SipiV1ITSpec extends ITSpec(SipiV1ITSpec.config) with TriplestoreJsonProtocol {

    private val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")
    )

    private val username = "root@example.com"
    private val password = "test"
    private val pathToChlaus = "_test_data/test_route/images/Chlaus.jpg"
    private val pathToMarbles = "_test_data/test_route/images/marbles.tif"
    private val firstPageIri = new MutableTestIri
    private val secondPageIri = new MutableTestIri

    "Check if Sipi is running" in {
        // This requires that (1) fileserver.docroot is set in Sipi's config file and (2) it contains a file test.html.
        val request = Get(baseSipiUrl + "/server/test.html")
        val response = singleAwaitingRequest(request)
        assert(response.status == StatusCodes.OK, s"Sipi is probably not running: ${response.status}")
    }

    "Load test data" in {
        val request = Post(baseApiUrl + "/v1/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    "Knora and Sipi" should {

        "create an 'incunabula:page' with binary data" in {
            createTmpFileDir()

            // JSON describing the resource to be created.
            val paramsPageWithBinaries =
                s"""
                   |{
                   |     "restype_id": "http://www.knora.org/ontology/incunabula#page",
                   |     "label": "test",
                   |     "project_id": "http://data.knora.org/projects/77275339",
                   |     "properties": {
                   |         "http://www.knora.org/ontology/incunabula#pagenum": [
                   |             {
                   |                 "richtext_value": {
                   |                     "utf8str": "test_page"
                   |                 }
                   |             }
                   |         ],
                   |         "http://www.knora.org/ontology/incunabula#origname": [
                   |             {
                   |                 "richtext_value": {
                   |                     "utf8str": "test"
                   |                 }
                   |             }
                   |         ],
                   |         "http://www.knora.org/ontology/incunabula#partOf": [
                   |             {
                   |                 "link_value": "http://data.knora.org/5e77e98d2603"
                   |             }
                   |         ],
                   |         "http://www.knora.org/ontology/incunabula#seqnum": [
                   |             {
                   |                 "int_value": 999
                   |             }
                   |         ]
                   |     }
                   |}
                 """.stripMargin

            // The image to be uploaded.
            val fileToSend = new File(pathToChlaus)
            assert(fileToSend.exists(), s"File $pathToChlaus does not exist")

            // A multipart/form-data request containing the image and the JSON.
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

            // Send the multipart/form-data request to the Knora API server.
            val knoraPostRequest = Post(baseApiUrl + "/v1/resources", formData) ~> addCredentials(BasicHttpCredentials(username, password))
            val knoraPostResponse = singleAwaitingRequest(knoraPostRequest)
            val knoraPostResponseBodyFuture: Future[String] = knoraPostResponse.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
            val knoraPostResponseBodyStr = Await.result(knoraPostResponseBodyFuture, 5.seconds)
            assert(knoraPostResponse.status === StatusCodes.OK, knoraPostResponseBodyStr)

            // Get the IRI of the newly created resource.
            val resourceIri: String = knoraPostResponseBodyStr.parseJson.asJsObject.fields("res_id").asInstanceOf[JsString].value
            firstPageIri.set(resourceIri)

            // Request the resource from the Knora API server.
            val knoraRequestNewResource = Get(baseApiUrl + "/v1/resources/" + URLEncoder.encode(resourceIri, "UTF-8")) ~> addCredentials(BasicHttpCredentials(username, password))
            val knoraResponseNewResource = singleAwaitingRequest(knoraRequestNewResource)
            val knoraResponseNewResourceBodyFuture: Future[String] = knoraResponseNewResource.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
            val knoraResponseNewResourceStr: String = Await.result(knoraResponseNewResourceBodyFuture, 5.seconds)
            assert(knoraResponseNewResource.status == StatusCodes.OK, knoraResponseNewResourceStr)

            // Get the URL of the image that was uploaded.
            val iiifUrl = knoraResponseNewResourceStr.parseJson.asJsObject.fields.get("resinfo") match {
                case Some(resinfo: JsObject) =>
                    resinfo.fields.get("locdata") match {
                        case Some(locdata: JsObject) =>
                            locdata.fields.get("path") match {
                                case Some(JsString(path)) => path
                                case None => throw InvalidApiJsonException("no 'path' given")
                                case other => throw InvalidApiJsonException("'path' could not pe parsed correctly")
                            }
                        case None => throw InvalidApiJsonException("no 'locdata' given")

                        case _ => throw InvalidApiJsonException("'locdata' could not pe parsed correctly")
                    }

                case None => throw InvalidApiJsonException("no 'resinfo' given")

                case _ => throw InvalidApiJsonException("'resinfo' could not pe parsed correctly")
            }

            // Request the image from Sipi.
            val sipiGetRequest = Get(iiifUrl) ~> addCredentials(BasicHttpCredentials(username, password))
            val sipiGetResponse = singleAwaitingRequest(sipiGetRequest)
            val sipiGetResponseBodyFuture: Future[String] = sipiGetResponse.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
            val sipiGetResponseStr: String = Await.result(sipiGetResponseBodyFuture, 5.seconds)
            assert(sipiGetResponse.status == StatusCodes.OK, sipiGetResponseStr)
        }

        "change an 'incunabula:page' with binary data" in {
            // The image to be uploaded.
            val fileToSend = new File(pathToMarbles)
            assert(fileToSend.exists(), s"File $pathToMarbles does not exist")

            // A multipart/form-data request containing the image.
            val formData = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(MediaTypes.`image/tiff`, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
            )

            // Send the image in a PUT request to the Knora API server.
            val request = Put(baseApiUrl + "/v1/filevalue/" + URLEncoder.encode(firstPageIri.get, "UTF-8"), formData) ~> addCredentials(BasicHttpCredentials(username, password))
            val response = singleAwaitingRequest(request)
            assert(response.status === StatusCodes.OK)
        }

        "create an 'incunabula:page' with parameters" in {
            // The image to be uploaded.
            val fileToSend = new File(pathToChlaus)
            assert(fileToSend.exists(), s"File $pathToChlaus does not exist")

            // A multipart/form-data request containing the image.
            val sipiFormData = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(MediaTypes.`image/jpeg`, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
            )

            // Send a POST request to Sipi, asking it to make a thumbnail of the image.
            val sipiRequest = Post(baseSipiUrl + "/make_thumbnail", sipiFormData) ~> addCredentials(BasicHttpCredentials(username, password))
            val sipiResponse = singleAwaitingRequest(sipiRequest)
            val sipiResponseBodyFuture: Future[String] = sipiResponse.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
            val sipiResponseBodyStr = Await.result(sipiResponseBodyFuture, 5.seconds)
            assert(sipiResponse.status === StatusCodes.OK, sipiResponseBodyStr)

            // Request the thumbnail from Sipi.
            val jsonFields = sipiResponseBodyStr.parseJson.asJsObject.fields
            val previewUrl = jsonFields("preview_path").asInstanceOf[JsString].value
            val sipiGetRequest = Get(previewUrl) ~> addCredentials(BasicHttpCredentials(username, password))
            val sipiGetResponse = singleAwaitingRequest(sipiGetRequest)
            val sipiGetResponseBodyFuture: Future[String] = sipiGetResponse.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
            val sipiGetResponseStr: String = Await.result(sipiGetResponseBodyFuture, 5.seconds)
            assert(sipiGetResponse.status == StatusCodes.OK, sipiGetResponseStr)

            val knoraParams =
                s"""
                  |{
                  |    "restype_id": "http://www.knora.org/ontology/incunabula#page",
                  |    "properties": {
                  |        "http://www.knora.org/ontology/incunabula#pagenum": [
                  |            {"richtext_value": {"utf8str": "test page"}}
                  |        ],
                  |        "http://www.knora.org/ontology/incunabula#origname": [
                  |            {"richtext_value": {"utf8str": "Chlaus"}}
                  |        ],
                  |        "http://www.knora.org/ontology/incunabula#partOf": [
                  |            {"link_value": "http://data.knora.org/5e77e98d2603"}
                  |        ],
                  |        "http://www.knora.org/ontology/incunabula#seqnum": [{"int_value": 99999999}]
                  |    },
                  |    "file": {
                  |        "originalFilename" : "${jsonFields("original_filename").asInstanceOf[JsString].value}",
                  |        "originalMimeType" : "${jsonFields("original_mimetype").asInstanceOf[JsString].value}",
                  |        "filename" : "${jsonFields("filename").asInstanceOf[JsString].value}"
                  |    },
                  |    "label": "test page",
                  |    "project_id": "http://data.knora.org/projects/77275339"
                  |}
                """.stripMargin

            // Send the JSON in a POST request to the Knora API server.
            val knoraPostRequest = Post(baseApiUrl + "/v1/resources", HttpEntity(ContentTypes.`application/json`, knoraParams)) ~> addCredentials(BasicHttpCredentials(username, password))
            val knoraPostResponse = singleAwaitingRequest(knoraPostRequest)
            val knoraPostResponseBodyFuture: Future[String] = knoraPostResponse.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
            val knoraPostResponseBodyStr = Await.result(knoraPostResponseBodyFuture, 5.seconds)
            assert(knoraPostResponse.status === StatusCodes.OK, knoraPostResponseBodyStr)

            // Get the IRI of the newly created resource.
            val resourceIri: String = knoraPostResponseBodyStr.parseJson.asJsObject.fields("res_id").asInstanceOf[JsString].value
            secondPageIri.set(resourceIri)

            // Request the resource from the Knora API server.
            val knoraRequestNewResource = Get(baseApiUrl + "/v1/resources/" + URLEncoder.encode(resourceIri, "UTF-8")) ~> addCredentials(BasicHttpCredentials(username, password))
            val knoraResponseNewResource = singleAwaitingRequest(knoraRequestNewResource)
            val knoraResponseNewResourceBodyFuture: Future[String] = knoraResponseNewResource.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
            val knoraResponseNewResourceStr: String = Await.result(knoraResponseNewResourceBodyFuture, 5.seconds)
            assert(knoraResponseNewResource.status == StatusCodes.OK, knoraResponseNewResourceStr)
        }

        "change an 'incunabula:page' with parameters" in {
            // The image to be uploaded.
            val fileToSend = new File(pathToMarbles)
            assert(fileToSend.exists(), s"File $pathToMarbles does not exist")

            // A multipart/form-data request containing the image.
            val sipiFormData = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(MediaTypes.`image/tiff`, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
            )

            // Send a POST request to Sipi, asking it to make a thumbnail of the image.
            val sipiRequest = Post(baseSipiUrl + "/make_thumbnail", sipiFormData) ~> addCredentials(BasicHttpCredentials(username, password))
            val sipiResponse = singleAwaitingRequest(sipiRequest)
            val sipiResponseBodyFuture: Future[String] = sipiResponse.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
            val sipiResponseBodyStr = Await.result(sipiResponseBodyFuture, 5.seconds)
            assert(sipiResponse.status === StatusCodes.OK, sipiResponseBodyStr)

            // Request the thumbnail from Sipi.
            val jsonFields = sipiResponseBodyStr.parseJson.asJsObject.fields
            val previewUrl = jsonFields("preview_path").asInstanceOf[JsString].value
            val sipiGetRequest = Get(previewUrl) ~> addCredentials(BasicHttpCredentials(username, password))
            val sipiGetResponse = singleAwaitingRequest(sipiGetRequest)
            val sipiGetResponseBodyFuture: Future[String] = sipiGetResponse.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
            val sipiGetResponseStr: String = Await.result(sipiGetResponseBodyFuture, 5.seconds)
            assert(sipiGetResponse.status == StatusCodes.OK, sipiGetResponseStr)

            // JSON describing the new image to Knora.
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

            // Send the JSON in a PUT request to the Knora API server.
            val knoraPutRequest = Put(baseApiUrl + "/v1/filevalue/" + URLEncoder.encode(secondPageIri.get, "UTF-8"), HttpEntity(ContentTypes.`application/json`, knoraParams.compactPrint)) ~> addCredentials(BasicHttpCredentials(username, password))
            val knoraPutResponse = singleAwaitingRequest(knoraPutRequest)
            val knoraPutResponseBodyFuture: Future[String] = knoraPutResponse.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
            val knoraPutResponseBodyStr = Await.result(knoraPutResponseBodyFuture, 5.seconds)
            assert(knoraPutResponse.status === StatusCodes.OK, knoraPutResponseBodyStr)
        }

        "create an 'anything:thing'" in {
            val standoffXml =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<text>
                  |    <u><strong>Wild thing</strong></u>, <u>you make my</u> <a class="salsah-link" href="http://data.knora.org/9935159f67">heart</a> sing
                  |</text>
                """.stripMargin

            val knoraParams = JsObject(
                Map(
                    "restype_id" -> JsString("http://www.knora.org/ontology/anything#Thing"),
                    "label" -> JsString("Wild thing"),
                    "project_id" -> JsString("http://data.knora.org/projects/anything"),
                    "properties" -> JsObject(
                        Map(
                            "http://www.knora.org/ontology/anything#hasText" -> JsArray(
                                JsObject(
                                    Map(
                                        "richtext_value" -> JsObject(
                                            "xml" -> JsString(standoffXml),
                                            "mapping_id" -> JsString("http://data.knora.org/projects/standoff/mappings/StandardMapping")
                                        )
                                    )
                                )
                            ),
                            "http://www.knora.org/ontology/anything#hasInteger" -> JsArray(
                                JsObject(
                                    Map(
                                        "int_value" -> JsNumber(12345)
                                    )
                                )
                            )
                        )
                    )
                )
            )

            // Send the JSON in a POST request to the Knora API server.
            val knoraPostRequest = Post(baseApiUrl + "/v1/resources", HttpEntity(ContentTypes.`application/json`, knoraParams.compactPrint)) ~> addCredentials(BasicHttpCredentials(username, password))
            val knoraPostResponse = singleAwaitingRequest(knoraPostRequest)
            val knoraPostResponseBodyFuture: Future[String] = knoraPostResponse.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
            val knoraPostResponseBodyStr = Await.result(knoraPostResponseBodyFuture, 5.seconds)
            assert(knoraPostResponse.status === StatusCodes.OK, knoraPostResponseBodyStr)
        }

    }

    /**
      * Creates the Knora API server's temporary upload directory if it doesn't exist.
      */
    def createTmpFileDir(): Unit = {
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


