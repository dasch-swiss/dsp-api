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
import java.nio.file.{Files, Paths}

import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpEntity, _}
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.util.MutableTestIri
import org.knora.webapi.{FileWriteException, ITSpec}
import spray.json._

import scala.concurrent.duration._


object KnoraSipiScriptsV1ITSpec {
    val config: Config = ConfigFactory.parseString(
        """
          |akka.loglevel = "DEBUG"
          |akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing Knora-Sipi integration. Sipi must be running with the config file
  * `sipi.knora-config.lua`.
  */
class KnoraSipiScriptsV1ITSpec extends ITSpec(KnoraSipiIntegrationV1ITSpec.config) with TriplestoreJsonProtocol {

    implicit override val log = akka.event.Logging(system, this.getClass())

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

            // Send a GET request to Sipi, asking for the preview image
            val sipiGetRequest = Get(previewPath)
            val sipiGetResponseJson = getResponseString(sipiGetRequest)
        }

        "successfully call convert_from_binaries.lua sipi script" ignore {}

        "successfully call convert_from_file.lua sipi script" ignore {}

    }

    /**
      * Creates the Knora API server's temporary upload directory if it doesn't exist.
      */
    private def createTmpFileDir(): Unit = {
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


