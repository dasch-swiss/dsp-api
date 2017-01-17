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

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.{HttpEntity, _}
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.ontologymessages.LoadOntologiesRequest
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v1.store.triplestoremessages._
import org.knora.webapi.responders._
import org.knora.webapi.responders.v1.ResponderManagerV1
import org.knora.webapi.routing.v1.{ResourcesRouteV1, StandoffRouteV1, ValuesRouteV1}
import org.knora.webapi.store._
import org.knora.webapi.util.{AkkaHttpUtils, MutableTestIri}
import org.xmlunit.builder.{DiffBuilder, Input}
import org.xmlunit.diff.Diff
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.{Codec, Source}



/**
  * End-to-end test specification for the standoff endpoint. This specification uses the Spray Testkit as documented
  * here: http://spray.io/documentation/1.2.2/spray-testkit/
  */
class StandoffV1R2RSpec extends R2RSpec {

    override def testConfigSource =
        """
         # akka.loglevel = "DEBUG"
         # akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    private val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val standoffPath = StandoffRouteV1.knoraApiPath(system, settings, log)
    private val resourcesPath = ResourcesRouteV1.knoraApiPath(system, settings, log)
    private val valuesPath = ValuesRouteV1.knoraApiPath(system, settings, log)

    private val anythingUsername = "anything-user"
    private val anythingProjectIri = "http://data.knora.org/projects/anything"
    private val password = "test"

    private val incunabulaUser = UserProfileV1(
        projects = Vector("http://data.knora.org/projects/77275339"),
        groups = Nil,
        userData = UserDataV1(
            email = Some("test@test.ch"),
            lastname = Some("Test"),
            firstname = Some("User"),
            username = Some("testuser"),
            token = None,
            user_id = Some("http://data.knora.org/users/b83acc5f05"),
            lang = "de"
        )
    )

    implicit private val timeout: Timeout = settings.defaultRestoreTimeout

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(15).second)

    implicit val ec = system.dispatcher

    private val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "_test_data/ontologies/standoff-onto.ttl", name = "http://www.knora.org/ontology/standoff"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/ontologies/images-demo-onto.ttl", name = "http://www.knora.org/ontology/images"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images"),
        RdfDataObject(path = "_test_data/ontologies/anything-onto.ttl", name = "http://www.knora.org/ontology/anything"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything"),
        RdfDataObject(path = "_test_data/ontologies/beol-onto.ttl", name = "http://www.knora.org/ontology/beol"),
        RdfDataObject(path = "_test_data/all_data/beol-data.ttl", name = "http://www.knora.org/data/beol")
    )

    "Load test data" in {
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 360.seconds)
        Await.result(responderManager ? LoadOntologiesRequest(incunabulaUser), 10.seconds)
    }

    private val firstTextValueIri = new MutableTestIri
    private val secondTextValueIri = new MutableTestIri
    private val thirdTextValueIri = new MutableTestIri

    object ResponseUtils {

        def getStringMemberFromResponse(response: HttpResponse, memberName: String): IRI = {

            // get the specified string member of the response
            val resIdFuture: Future[String] = response.entity.toStrict(5.seconds).map {
                responseBody =>
                    val resBodyAsString = responseBody.data.decodeString("UTF-8")
                    resBodyAsString.parseJson.asJsObject.fields.get(memberName) match {
                        case Some(JsString(entitiyId)) => entitiyId
                        case None => throw InvalidApiJsonException(s"The response does not contain a field called '$memberName'")
                        case other => throw InvalidApiJsonException(s"The response does not contain a field '$memberName' of type JsString, but ${other}")
                    }
            }

            // wait for the Future to complete
            Await.result(resIdFuture, 5.seconds)

        }

    }

    object RequestParams {

        val paramsCreateLetterMappingFromXML =
            s"""
               |{
               |  "project_id": "$anythingProjectIri",
               |  "label": "mapping for letters",
               |  "mappingName": "LetterMapping"
               |}
             """.stripMargin

        /*def paramsCreateTextValueFromXML(mappingIri: IRI): String =
            s"""
                {
                    "resource_id": "http://data.knora.org/a-thing",
                    "property_id": "http://www.knora.org/ontology/anything#hasText",
                    "project_id": "$anythingProjectIri",
                    "mapping_id": "$mappingIri"
                }
            """

        def paramsChangeLetterFromXML(value_id: IRI, mappingIri: IRI): String =
            s"""
                {
                    "value_id": "$value_id",
                    "mapping_id": "$mappingIri"
                }
            """*/

        val pathToLetterMapping = "_test_data/test_route/texts/mappingForLetter.xml"

        val pathToLetterXML = "_test_data/test_route/texts/letter.xml"

        val pathToLetter2XML = "_test_data/test_route/texts/letter2.xml"

        val pathToLetter3XML = "_test_data/test_route/texts/letter3.xml"

        val paramsCreateHTMLMappingFromXML =
            s"""
               |{
               |  "project_id": "$anythingProjectIri",
               |  "label": "mapping for HTML",
               |  "mappingName": "HTMLMapping"
               |}
             """.stripMargin

        val pathToHTMLMapping = "_test_data/test_route/texts/mappingForHTML.xml"

        val pathToHTML = "_test_data/test_route/texts/HTML.xml"


    }

    "The Standoff Endpoint" should {

        "create a mapping resource for standoff conversion for letters" in {

            val mappingFileToSend = new File(RequestParams.pathToLetterMapping)

            val formDataMapping = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "json",
                    HttpEntity(ContentTypes.`application/json`, RequestParams.paramsCreateLetterMappingFromXML)
                ),
                Multipart.FormData.BodyPart(
                    "xml",
                    HttpEntity.fromPath(ContentTypes.`text/xml(UTF-8)`, mappingFileToSend.toPath),
                    Map("filename" -> mappingFileToSend.getName)
                )
            )

            // send mapping xml to route
            Post("/v1/mapping", formDataMapping) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> standoffPath ~> check {

                assert(status == StatusCodes.OK, "standoff mapping creation route returned a non successful HTTP status code: " + responseAs[String])

                // check if mappingIri is correct
                val mappingIri = ResponseUtils.getStringMemberFromResponse(response, "mappingIri")

                assert(mappingIri == anythingProjectIri + "/mappings/LetterMapping", "Iri of the new mapping is not correct")


            }

        }

        "create a TextValue from an XML representing a letter" in {

            val xmlFileToSend = new File(RequestParams.pathToLetterXML)

            /*val formDataStandoff = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "json",
                    HttpEntity(ContentTypes.`application/json`, RequestParams.paramsCreateTextValueFromXML(anythingProjectIri + "/mappings/LetterMapping"))
                ),
                Multipart.FormData.BodyPart(
                    "xml",
                    HttpEntity.fromPath(ContentTypes.`text/xml(UTF-8)`, xmlFileToSend.toPath),
                    Map("filename" -> xmlFileToSend.getName)
                )
            )*/


            val newValueParams =
                s"""
                        {
                          "project_id": "http://data.knora.org/projects/anything",
                          "res_id": "http://data.knora.org/a-thing",
                          "prop": "http://www.knora.org/ontology/anything#hasText",
                          "richtext_value": {
                                "xml": ${JsString(Source.fromFile(xmlFileToSend).mkString)},
                                "mapping_id": "${anythingProjectIri}/mappings/LetterMapping"
                          }
                        }
                        """

            // create standoff from XML
            Post("/v1/values", HttpEntity(ContentTypes.`application/json`, newValueParams)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> valuesPath ~> check {

                assert(status == StatusCodes.OK, "creation of a TextValue from XML returned a non successful HTTP status code: " + responseAs[String])

                firstTextValueIri.set(ResponseUtils.getStringMemberFromResponse(response, "id"))


            }


        }

        "read the XML TextValue back to XML and compare it to the XML that was originally sent" in {

            val xmlFile = new File(RequestParams.pathToLetterXML)

            Get("/v1/values/" + URLEncoder.encode(firstTextValueIri.get, "UTF-8")) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> valuesPath ~> check {

                assert(response.status == StatusCodes.OK, "reading back text value to XML failed")

                val xml = AkkaHttpUtils.httpResponseToJson(response).fields.get("value") match {
                    case Some(value: JsObject) => value.fields.get("xml") match {
                        case Some(JsString(xml: String)) => xml
                        case _ => throw new InvalidApiJsonException("member 'xml' not given")
                    }
                    case _ => throw new InvalidApiJsonException("member 'value' not given")
                }

                // Compare the original XML with the regenerated XML.
                val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(Source.fromFile(xmlFile)(Codec.UTF8).mkString)).withTest(Input.fromString(xml)).build()

                xmlDiff.hasDifferences should be(false)

            }

        }

        "change a text value from XML representing a letter" in {

            val xmlFileToSend = new File(RequestParams.pathToLetter2XML)

            /*val formData = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "json",
                    HttpEntity(ContentTypes.`application/json`, RequestParams.paramsChangeLetterFromXML(value_id = firstTextValueIri.get, mappingIri = anythingProjectIri + "/mappings/LetterMapping"))
                ),
                Multipart.FormData.BodyPart(
                    "xml",
                    HttpEntity.fromPath(ContentTypes.`text/xml(UTF-8)`, xmlFileToSend.toPath),
                    Map("filename" -> xmlFileToSend.getName)
                )
            )*/

            val newValueParams =
                s"""
                    {
                      "project_id": "http://data.knora.org/projects/anything",
                      "richtext_value": {
                            "xml": ${JsString(Source.fromFile(xmlFileToSend).mkString)},
                            "mapping_id": "${anythingProjectIri}/mappings/LetterMapping"
                      }
                    }
                """

            // change standoff from XML
            Put("/v1/values/" + URLEncoder.encode(firstTextValueIri.get, "UTF-8"), HttpEntity(ContentTypes.`application/json`, newValueParams)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> valuesPath ~> check {

                assert(status == StatusCodes.OK, "standoff creation route returned a non successful HTTP status code: " + responseAs[String])

                firstTextValueIri.set(ResponseUtils.getStringMemberFromResponse(response, "id"))

            }

        }

        "read the changed TextValue back to XML and compare it to the XML that was originally sent" in {

            val xmlFile = new File(RequestParams.pathToLetter2XML)

            Get("/v1/values/" + URLEncoder.encode(firstTextValueIri.get, "UTF-8")) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> valuesPath ~> check {

                assert(response.status == StatusCodes.OK, "reading back text value to XML failed")

                val xml = AkkaHttpUtils.httpResponseToJson(response).fields.get("value") match {
                    case Some(value: JsObject) => value.fields.get("xml") match {
                        case Some(JsString(xml: String)) => xml
                        case _ => throw new InvalidApiJsonException("member 'xml' not given")
                    }
                    case _ => throw new InvalidApiJsonException("member 'value' not given")
                }


                // Compare the original XML with the regenerated XML.
                val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(Source.fromFile(xmlFile)(Codec.UTF8).mkString)).withTest(Input.fromString(xml)).build()

                xmlDiff.hasDifferences should be(false)

            }

        }

        "create a TextValue from complex XML representing a letter" in {

            val xmlFileToSend = new File(RequestParams.pathToLetter3XML)

            /*val formDataStandoff = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "json",
                    HttpEntity(ContentTypes.`application/json`, RequestParams.paramsCreateTextValueFromXML(anythingProjectIri + "/mappings/LetterMapping"))
                ),
                Multipart.FormData.BodyPart(
                    "xml",
                    HttpEntity.fromPath(ContentTypes.`text/xml(UTF-8)`, xmlFileToSend.toPath),
                    Map("filename" -> xmlFileToSend.getName)
                )
            )*/

            val newValueParams =
                s"""
                {
                  "project_id": "http://data.knora.org/projects/anything",
                  "res_id": "http://data.knora.org/a-thing",
                  "prop": "http://www.knora.org/ontology/anything#hasText",
                  "richtext_value": {
                        "xml": ${JsString(Source.fromFile(xmlFileToSend).mkString)},
                        "mapping_id": "${anythingProjectIri}/mappings/LetterMapping"
                  }
                }
                """

            // create standoff from XML
            Post("/v1/values", HttpEntity(ContentTypes.`application/json`, newValueParams)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> valuesPath ~> check {

                assert(status == StatusCodes.OK, "creation of a TextValue from XML returned a non successful HTTP status code: " + responseAs[String])

                secondTextValueIri.set(ResponseUtils.getStringMemberFromResponse(response, "id"))


            }


        }

        "read the complex TextValue back to XML and compare it to the XML that was originally sent" in {

            val xmlFile = new File(RequestParams.pathToLetter3XML)

            Get("/v1/values/" + URLEncoder.encode(secondTextValueIri.get, "UTF-8")) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> valuesPath ~> check {

                assert(response.status == StatusCodes.OK, "reading back text value to XML failed")

                val xml = AkkaHttpUtils.httpResponseToJson(response).fields.get("value") match {
                    case Some(value: JsObject) => value.fields.get("xml") match {
                        case Some(JsString(xml: String)) => xml
                        case _ => throw new InvalidApiJsonException("member 'xml' not given")
                    }
                    case _ => throw new InvalidApiJsonException("member 'value' not given")
                }


                // Compare the original XML with the regenerated XML.
                val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(Source.fromFile(xmlFile)(Codec.UTF8).mkString)).withTest(Input.fromString(xml)).build()

                xmlDiff.hasDifferences should be(false)

            }

        }

        "create a mapping resource for standoff conversion for HTML" in {

            val mappingFileToSend = new File(RequestParams.pathToHTMLMapping)

            val formDataMapping = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "json",
                    HttpEntity(ContentTypes.`application/json`, RequestParams.paramsCreateHTMLMappingFromXML)
                ),
                Multipart.FormData.BodyPart(
                    "xml",
                    HttpEntity.fromPath(ContentTypes.`text/xml(UTF-8)`, mappingFileToSend.toPath),
                    Map("filename" -> mappingFileToSend.getName)
                )
            )

            // send mapping xml to route
            Post("/v1/mapping", formDataMapping) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> standoffPath ~> check {

                assert(status == StatusCodes.OK, "standoff mapping creation route returned a non successful HTTP status code: " + responseAs[String])

                // check if mappingIri is correct
                val mappingIri = ResponseUtils.getStringMemberFromResponse(response, "mappingIri")

                assert(mappingIri == anythingProjectIri + "/mappings/HTMLMapping", "Iri of the new mapping is not correct")


            }

        }

        "create a TextValue from XML representing HTML (in strict XML notation)" in {

            val xmlFileToSend = new File(RequestParams.pathToHTML)

            /*val formDataStandoff = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "json",
                    HttpEntity(ContentTypes.`application/json`, RequestParams.paramsCreateTextValueFromXML(anythingProjectIri + "/mappings/HTMLMapping"))
                ),
                Multipart.FormData.BodyPart(
                    "xml",
                    HttpEntity.fromPath(ContentTypes.`text/xml(UTF-8)`, xmlFileToSend.toPath),
                    Map("filename" -> xmlFileToSend.getName)
                )
            )*/

            val newValueParams =
                s"""
                {
                  "project_id": "http://data.knora.org/projects/anything",
                  "res_id": "http://data.knora.org/a-thing",
                  "prop": "http://www.knora.org/ontology/anything#hasText",
                  "richtext_value": {
                        "xml": ${JsString(Source.fromFile(xmlFileToSend).mkString)},
                        "mapping_id": "${anythingProjectIri}/mappings/HTMLMapping"
                  }
                }
                """

            // create standoff from XML
            Post("/v1/values", HttpEntity(ContentTypes.`application/json`, newValueParams)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> valuesPath ~> check {

                assert(status == StatusCodes.OK, "creation of a TextValue from XML returned a non successful HTTP status code: " + responseAs[String])

                thirdTextValueIri.set(ResponseUtils.getStringMemberFromResponse(response, "id"))


            }

        }

        "read the TextValue back to XML and compare it to the HTML that was originally sent" in {

            val htmlFile = new File(RequestParams.pathToHTML)

            Get("/v1/values/" + URLEncoder.encode(thirdTextValueIri.get, "UTF-8")) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> valuesPath ~> check {

                assert(response.status == StatusCodes.OK, "reading back text value to XML failed")

                val xml = AkkaHttpUtils.httpResponseToJson(response).fields.get("value") match {
                    case Some(value: JsObject) => value.fields.get("xml") match {
                        case Some(JsString(xml: String)) => xml
                        case _ => throw new InvalidApiJsonException("member 'xml' not given")
                    }
                    case _ => throw new InvalidApiJsonException("member 'value' not given")
                }

                // Compare the original XML with the regenerated XML.
                val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(Source.fromFile(htmlFile)(Codec.UTF8).mkString)).withTest(Input.fromString(xml)).build()

                xmlDiff.hasDifferences should be(false)

            }

        }


    }
}