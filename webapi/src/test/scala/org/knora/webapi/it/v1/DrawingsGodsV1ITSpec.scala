/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v1

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import spray.json._

import java.net.URLEncoder
import java.nio.file.Paths
import scala.concurrent.Await
import scala.concurrent.duration._

import dsp.errors.InvalidApiJsonException
import org.knora.webapi.ITKnoraLiveSpec
import org.knora.webapi.messages.store.sipimessages._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.v2.routing.authenticationmessages.AuthenticationV2JsonProtocol
import org.knora.webapi.messages.v2.routing.authenticationmessages.LoginResponse
import org.knora.webapi.testservices.FileToUpload

object DrawingsGodsV1ITSpec {
  val config: Config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * End-to-End (E2E) test specification for additional testing of permissions.
 */
class DrawingsGodsV1ITSpec
    extends ITKnoraLiveSpec(DrawingsGodsV1ITSpec.config)
    with AuthenticationV2JsonProtocol
    with TriplestoreJsonProtocol {

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_admin-data.ttl",
      name = "http://www.knora.org/data/admin"
    ),
    RdfDataObject(
      path = "test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_permissions-data.ttl",
      name = "http://www.knora.org/data/permissions"
    ),
    RdfDataObject(
      path = "test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_ontology.ttl",
      name = "http://www.knora.org/ontology/0105/drawings-gods"
    ),
    RdfDataObject(
      path = "test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_data.ttl",
      name = "http://www.knora.org/data/0105/drawings-gods"
    )
  )

  "issue: https://github.com/dhlab-basel/Knora/issues/408" should {

    val drawingsOfGodsUserEmail = "ddd1@unil.ch"
    val testPass                = "test"
    val pathToChlaus            = Paths.get("..", "test_data/test_route/images/Chlaus.jpg")
    var loginToken: String      = ""

    "log in as a Knora user" in {
      /* Correct username and correct password */

      val params =
        s"""
           |{
           |    "email": "$drawingsOfGodsUserEmail",
           |    "password": "$testPass"
           |}
                """.stripMargin

      val request                = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK)

      val lr: LoginResponse = Await.result(Unmarshal(response.entity).to[LoginResponse], 1.seconds)
      loginToken = lr.token

      loginToken.nonEmpty should be(true)
    }

    "be able to create a resource, only find one DOAP (with combined resource class / property), and have permission to access the image" in {
      // Upload the image to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(FileToUpload(path = pathToChlaus, mimeType = org.apache.http.entity.ContentType.IMAGE_TIFF))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head

      val params =
        s"""
           |{
           |    "restype_id":"http://www.knora.org/ontology/0105/drawings-gods#Verso",
           |    "properties":{
           |        "http://www.knora.org/ontology/0105/drawings-gods#hasVersoTranslatorEn":[{"hlist_value":"http://rdfh.ch/lists/0105/drawings-gods-2016-list-TranslatorList-PYB"}],
           |        "http://www.knora.org/ontology/0105/drawings-gods#hasCommentOriginalLanguage":[{"hlist_value":"http://rdfh.ch/lists/0105/drawings-gods-2016-list-LanguageList-Buriat"}],
           |        "http://www.knora.org/ontology/0105/drawings-gods#hasDescriptionOriginalLanguage":[{"hlist_value":"http://rdfh.ch/lists/0105/drawings-gods-2016-list-LanguageList-Buriat"}],
           |        "http://www.knora.org/ontology/0105/drawings-gods#hasDescriptionAuthor":[{"hlist_value":"http://rdfh.ch/lists/0105/drawings-gods-2016-list-DescriptionAuthorList-child"}],
           |        "http://www.knora.org/ontology/0105/drawings-gods#hasInstructionRestitutionOriginalLanguage":[{"hlist_value":"http://rdfh.ch/lists/0105/drawings-gods-2016-list-LanguageList-Buriat"}],
           |        "http://www.knora.org/ontology/0105/drawings-gods#hasVersoTranslatorFr":[{"hlist_value":"http://rdfh.ch/lists/0105/drawings-gods-2016-list-TranslatorList-PYB"}],
           |        "http://www.knora.org/ontology/0105/drawings-gods#hasCommentAuthor":[{"hlist_value":"http://rdfh.ch/lists/0105/drawings-gods-2016-list-CommentAuthorList-child"}],
           |        "http://www.knora.org/ontology/0105/drawings-gods#hasCodeVerso":[{"richtext_value":{"utf8str":"dayyad"}}]
           |    },
           |    "file": "${uploadedFile.internalFilename}",
           |    "project_id":"http://rdfh.ch/projects/0105",
           |    "label":"dayyad"
           |}
             """.stripMargin

      // Send the JSON in a POST request to the Knora API server.
      val knoraPostRequest =
        Post(baseApiUrl + "/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
          BasicHttpCredentials(drawingsOfGodsUserEmail, testPass)
        )
      val knoraPostResponseJson = getResponseJson(knoraPostRequest)

      // Get the IRI of the newly created resource.
      val resourceIri: String = knoraPostResponseJson.fields("res_id").asInstanceOf[JsString].value

      // Request the resource from the Knora API server.
      val knoraRequestNewResource = Get(
        baseApiUrl + "/v1/resources/" + URLEncoder.encode(resourceIri, "UTF-8")
      ) ~> addCredentials(BasicHttpCredentials(drawingsOfGodsUserEmail, testPass))
      val knoraNewResourceJson = getResponseJson(knoraRequestNewResource)

      // Get the URL of the image that was uploaded.
      val iiifUrl = knoraNewResourceJson.fields.get("resinfo") match {
        case Some(resinfo: JsObject) =>
          resinfo.fields.get("locdata") match {
            case Some(locdata: JsObject) =>
              locdata.fields.get("path") match {
                case Some(JsString(foundUrl)) => foundUrl.replace("http://0.0.0.0:1024", baseInternalSipiUrl)
                case None                     => throw InvalidApiJsonException("no 'path' given")
                case _                    => throw InvalidApiJsonException("'path' could not pe parsed correctly")
              }
            case None => throw InvalidApiJsonException("no 'locdata' given")

            case _ => throw InvalidApiJsonException("'locdata' could not pe parsed correctly")
          }

        case None => throw InvalidApiJsonException("no 'resinfo' given")

        case _ => throw InvalidApiJsonException("'resinfo' could not pe parsed correctly")
      }
      println("=====>>>> iiifURL: {}", iiifUrl)

      // Request the image from Sipi.
      val sipiGetRequest = Get(iiifUrl) ~> addCredentials(BasicHttpCredentials(drawingsOfGodsUserEmail, testPass))
      checkResponseOK(sipiGetRequest)
    }
  }

}
