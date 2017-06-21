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

package org.knora.webapi.other.v1

import java.io.File
import java.net.URLEncoder

import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model._
import arq.iri
import com.typesafe.config.ConfigFactory
import org.knora.webapi.{ITSpec, InvalidApiJsonException}
import org.knora.webapi.messages.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.util.MutableTestIri
import spray.json._

import scala.concurrent.duration._

object DrawingsGodsV1ITSpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for additional testing of permissions.
  */
class DrawingsGodsV1ITSpec extends ITSpec(DrawingsGodsV1ITSpec.config) with TriplestoreJsonProtocol {

    implicit override val log = akka.event.Logging(system, this.getClass())

    private val rdfDataObjects: List[RdfDataObject] = List(
        RdfDataObject(path = "_test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_admin-data.ttl", name = "http://www.knora.org/data/admin"),
        RdfDataObject(path = "_test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_permissions-data.ttl", name = "http://www.knora.org/data/permissions"),
        RdfDataObject(path = "_test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_ontology.ttl", name = "http://www.knora.org/ontology/drawings-gods"),
        RdfDataObject(path = "_test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_data.ttl", name = "http://www.knora.org/data/drawings-gods")
    )

    "Check if Sipi is running" in {
        // This requires that (1) fileserver.docroot is set in Sipi's config file and (2) it contains a file test.html.
        val request = Get(baseSipiUrl + "/server/test.html")
        val response = singleAwaitingRequest(request)
        assert(response.status == StatusCodes.OK, s"Sipi is probably not running: ${response.status}")
    }

    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        val request = Post(baseApiUrl + "/v1/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    "issue: https://github.com/dhlab-basel/Knora/issues/408" should {

        val drawingsOfGodsUserEmail = "ddd1@unil.ch"
        val testPass = "test"
        val pathToChlaus = "_test_data/test_route/images/Chlaus.jpg"


        "be able to create a resource, only find one DOAP (with combined resource class / property), and have permission to access the image" in {

            val params =
                s"""
                   |{
                   |    "restype_id":"http://www.knora.org/ontology/drawings-gods#Verso",
                   |    "properties":{
                   |        "http://www.knora.org/ontology/drawings-gods#hasVersoTranslatorEn":[{"hlist_value":"http://data.knora.org/lists/drawings-gods-2016-list-TranslatorList-PYB"}],
                   |        "http://www.knora.org/ontology/drawings-gods#hasCommentOriginalLanguage":[{"hlist_value":"http://data.knora.org/lists/drawings-gods-2016-list-LanguageList-Buriat"}],
                   |        "http://www.knora.org/ontology/drawings-gods#hasDescriptionOriginalLanguage":[{"hlist_value":"http://data.knora.org/lists/drawings-gods-2016-list-LanguageList-Buriat"}],
                   |        "http://www.knora.org/ontology/drawings-gods#hasDescriptionAuthor":[{"hlist_value":"http://data.knora.org/lists/drawings-gods-2016-list-DescriptionAuthorList-child"}],
                   |        "http://www.knora.org/ontology/drawings-gods#hasInstructionRestitutionOriginalLanguage":[{"hlist_value":"http://data.knora.org/lists/drawings-gods-2016-list-LanguageList-Buriat"}],
                   |        "http://www.knora.org/ontology/drawings-gods#hasVersoTranslatorFr":[{"hlist_value":"http://data.knora.org/lists/drawings-gods-2016-list-TranslatorList-PYB"}],
                   |        "http://www.knora.org/ontology/drawings-gods#hasCommentAuthor":[{"hlist_value":"http://data.knora.org/lists/drawings-gods-2016-list-CommentAuthorList-child"}],
                   |        "http://www.knora.org/ontology/drawings-gods#hasCodeVerso":[{"richtext_value":{"utf8str":"dayyad"}}]
                   |    },
                   |    "project_id":"http://data.knora.org/projects/drawings-gods",
                   |    "label":"dayyad"
                   |}
             """.stripMargin

            // The image to be uploaded.
            val fileToSend = new File(pathToChlaus)
            assert(fileToSend.exists(), s"File $pathToChlaus does not exist")

            // A multipart/form-data request containing the image and the JSON.
            val formData = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "json",
                    HttpEntity(ContentTypes.`application/json`, params)
                ),
                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(MediaTypes.`image/jpeg`, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
            )

            // Send the multipart/form-data request to the Knora API server.
            val knoraPostRequest = Post(baseApiUrl + "/v1/resources", formData) ~> addCredentials(BasicHttpCredentials(drawingsOfGodsUserEmail, testPass))
            val knoraPostResponseJson = getResponseJson(knoraPostRequest)

            // Get the IRI of the newly created resource.
            val resourceIri: String = knoraPostResponseJson.fields("res_id").asInstanceOf[JsString].value

            // Request the resource from the Knora API server.
            val knoraRequestNewResource = Get(baseApiUrl + "/v1/resources/" + URLEncoder.encode(resourceIri, "UTF-8")) ~> addCredentials(BasicHttpCredentials(drawingsOfGodsUserEmail, testPass))
            val knoraNewResourceJson = getResponseJson(knoraRequestNewResource)

            // Get the URL of the image that was uploaded.
            val iiifUrl = knoraNewResourceJson.fields.get("resinfo") match {
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
            log.debug("iiiURL: {}", iiifUrl)

            // Request the image from Sipi.
            val sipiGetRequest = Get(iiifUrl) ~> addCredentials(BasicHttpCredentials(drawingsOfGodsUserEmail, testPass))
            checkResponseOK(sipiGetRequest)
        }
    }

}
