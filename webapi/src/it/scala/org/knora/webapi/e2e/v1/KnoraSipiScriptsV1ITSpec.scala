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

package org.knora.webapi.e2e.v1

import java.io.File

import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpEntity, _}
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.ITKnoraFakeSpec
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.util.{MutableTestIri, TestingUtilities}
import spray.json._


object KnoraSipiScriptsV1ITSpec {
    val config: Config = ConfigFactory.parseString(
        """
          |akka.loglevel = "DEBUG"
          |akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing Knora-Sipi scripts. Sipi must be running with the config file
  * `sipi.knora-config.lua`. This spec uses the KnoraFakeService to start a faked `webapi` server that always allows
  * access to files.
  */
class KnoraSipiScriptsV1ITSpec extends ITKnoraFakeSpec(KnoraSipiScriptsV1ITSpec.config) with TriplestoreJsonProtocol with TestingUtilities {

    implicit override lazy val log = akka.event.Logging(system, this.getClass())

    private val username = "root@example.com"
    private val password = "test"
    private val pathToChlaus = "_test_data/test_route/images/Chlaus.jpg"
    private val pathToMarbles = "_test_data/test_route/images/marbles.tif"
    private val firstPageIri = new MutableTestIri
    private val secondPageIri = new MutableTestIri

    // creates tmp directory if not found
    createTmpFileDir()

    "Check if Sipi is running" in {
        // This requires that (1) fileserver.docroot is set in Sipi's config file and (2) it contains a file test.html.
        val request = Get(baseSipiUrl + "/server/test.html")
        val response = singleAwaitingRequest(request)
        assert(response.status == StatusCodes.OK, s"Sipi is probably not running: ${response.status}")
    }

    "Calling Knora Sipi Scripts" should {

        "successfully call make_thumbnail.lua sipi script" in {

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
            val sipiPostRequest = Post(baseSipiUrl + "/make_thumbnail", sipiFormData) ~> addCredentials(BasicHttpCredentials(username, password))
            val sipiPostResponseJson = getResponseJson(sipiPostRequest)

            /* sipiResponseJson will be something like this
            {
                "mimetype_thumb":"image/jpeg",
                "original_mimetype":"image/jpeg",
                "nx_thumb":93,
                "preview_path":"http://localhost:1024/thumbs/CjwDMhlrctI-BG7gms08BJ4.jpg/full/full/0/default.jpg",
                "filename":"CjwDMhlrctI-BG7gms08BJ4",
                "file_type":"IMAGE",
                "original_filename":"Chlaus.jpg",
                "ny_thumb":128
            }
            */

            // get the preview_path
            val previewPath = sipiPostResponseJson.fields("preview_path").asInstanceOf[JsString].value

            // get the filename
            val filename = sipiPostResponseJson.fields("filename").asInstanceOf[JsString].value

            // Send a GET request to Sipi, asking for the preview image
            val sipiGetRequest01 = Get(previewPath)
            val sipiGetResponseJson01 = getResponseString(sipiGetRequest01)

            // Send a GET request to Sipi, asking for the info.json of the image
            val sipiGetRequest02 = Get(baseSipiUrl + "/thumbs/" + filename + ".jpg/info.json" )
            val sipiGetResponseJson = getResponseJson(sipiGetRequest02)
        }



        "successfully call convert_from_file.lua sipi script" in {

            /* This is the case where the file is already stored on the sipi server as part of make_thumbnail*/

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
            val sipiMakeThumbnailRequest = Post(baseSipiUrl + "/make_thumbnail", sipiFormData)
            val sipiMakeThumbnailResponseJson = getResponseJson(sipiMakeThumbnailRequest)


            val originalFilename = sipiMakeThumbnailResponseJson.fields("original_filename").asInstanceOf[JsString].value
            val originalMimeType = sipiMakeThumbnailResponseJson.fields("original_mimetype").asInstanceOf[JsString].value
            val filename = sipiMakeThumbnailResponseJson.fields("filename").asInstanceOf[JsString].value

            // ToDo: Find out why this way of sending json is not working with sipi
            /*
            val params =
                s"""
                   |{
                   |    "originalfilename": "$originalFilename",
                   |    "originalmimetype": "$originalMimeType",
                   |    "filename": "$filename"
                   |}
                """.stripMargin

            val convertFromFileRequest = Post(baseSipiUrl + "/convert_from_file", HttpEntity(ContentTypes.`application/json`, params))
            val convertFromFileResponseJson = getResponseJson(convertFromFileRequest)
            */

            // A form-data request containing the payload for convert_from_file.

            val sipiFormData02 = FormData(
                Map(
                    "originalFilename" -> originalFilename,
                    "originalMimeType" -> originalMimeType,
                    "filename" -> filename
                )
            )

            val convertFromFileRequest = Post(baseSipiUrl + "/convert_from_file", sipiFormData02)
            val convertFromFileResponseJson = getResponseJson(convertFromFileRequest)

            val filenameFull = convertFromFileResponseJson.fields("filename_full").asInstanceOf[JsString].value

            // Running with KnoraFakeService which always allows access to files.
            // Send a GET request to Sipi, asking for full image
            // not possible as authentication is required and file needs to be known by knora to be able to authenticate the request
            val sipiGetImageRequest = Get(baseSipiUrl + "/knora/" + filenameFull + "/full/full/0/default.jpg") ~> addCredentials(BasicHttpCredentials(username, password))
            checkResponseOK(sipiGetImageRequest)

            // Send a GET request to Sipi, asking for the info.json of the image
            val sipiGetInfoRequest = Get(baseSipiUrl + "/knora/" + filenameFull + "/info.json" ) ~> addCredentials(BasicHttpCredentials(username, password))
            val sipiGetInfoResponseJson = getResponseJson(sipiGetInfoRequest)
            log.debug("sipiGetInfoResponseJson: {}", sipiGetInfoResponseJson)


        }

        "successfully call convert_from_binaries.lua sipi script" in {

            // The image to be uploaded.
            val fileToSend = new File(pathToChlaus)
            assert(fileToSend.exists(), s"File $pathToChlaus does not exist")

            // A multipart/form-data request containing the image.
            val sipiFormData = FormData(
                Map(
                    "originalFilename" -> fileToSend.getName,
                    "originalMimeType" -> "image/jpeg",
                    "source" -> fileToSend.getAbsolutePath
                )
            )

            // Send a POST request to Sipi, asking it to make a thumbnail of the image.
            val sipiConvertFromBinariesPostRequest = Post(baseSipiUrl + "/convert_from_binaries", sipiFormData)
            val sipiConvertFromBinariesPostResponseJson = getResponseJson(sipiConvertFromBinariesPostRequest)

            val filenameFull = sipiConvertFromBinariesPostResponseJson.fields("filename_full").asInstanceOf[JsString].value

            //log.debug("sipiConvertFromBinariesPostResponseJson: {}", sipiConvertFromBinariesPostResponseJson)

            // Running with KnoraFakeService which always allows access to files.
            val sipiGetImageRequest = Get(baseSipiUrl + "/knora/" + filenameFull + "/full/full/0/default.jpg") ~> addCredentials(BasicHttpCredentials(username, password))
            checkResponseOK(sipiGetImageRequest)

            // Send a GET request to Sipi, asking for the info.json of the image
            val sipiGetInfoRequest = Get(baseSipiUrl + "/knora/" + filenameFull + "/info.json" ) ~> addCredentials(BasicHttpCredentials(username, password))
            val sipiGetInfoResponseJson = getResponseJson(sipiGetInfoRequest)
            log.debug("sipiGetInfoResponseJson: {}", sipiGetInfoResponseJson)
        }

    }
}


