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

import java.net.URLEncoder

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.ontologymessages.LoadOntologiesRequest
import org.knora.webapi.messages.v1.responder.resourcemessages.PropsGetForRegionV1
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourceV1JsonProtocol._
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v1.store.triplestoremessages._
import org.knora.webapi.responders._
import org.knora.webapi.responders.v1.ResponderManagerV1
import org.knora.webapi.routing.v1.{ResourcesRouteV1, ValuesRouteV1}
import org.knora.webapi.store._
import org.knora.webapi.util.{AkkaHttpUtils, MutableTestIri}
import spray.json._
import org.xmlunit.builder.{DiffBuilder, Input}
import org.xmlunit.diff.Diff

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.xml.{Node, NodeSeq, XML}


/**
  * End-to-end test specification for the resources endpoint. This specification uses the Spray Testkit as documented
  * here: http://spray.io/documentation/1.2.2/spray-testkit/
  */
class ResourcesV1R2RSpec extends R2RSpec {

    override def testConfigSource =
        """
         # akka.loglevel = "DEBUG"
         # akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    private val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val resourcesPath = ResourcesRouteV1.knoraApiPath(system, settings, log)
    private val valuesPath = ValuesRouteV1.knoraApiPath(system, settings, log)

    private val incunabulaUsername = "root"
    private val anythingUsername = "anything-user"
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
        RdfDataObject(path = "_test_data/all_data/standoff-data.ttl", name = "http://www.knora.org/data/standoff"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/ontologies/images-demo-onto.ttl", name = "http://www.knora.org/ontology/images"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images"),
        RdfDataObject(path = "_test_data/ontologies/anything-onto.ttl", name = "http://www.knora.org/ontology/anything"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")
    )

    "Load test data" in {
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 360.seconds)
        Await.result(responderManager ? LoadOntologiesRequest(incunabulaUser), 10.seconds)
    }

    private val firstThingIri = new MutableTestIri
    private val firstTextValueIRI = new MutableTestIri
    private val secondThingIri = new MutableTestIri
    private val thirdThingIri = new MutableTestIri
    private val fourthThingIri = new MutableTestIri
    private val fifthThingIri = new MutableTestIri
    private val sixthThingIri = new MutableTestIri
    private val seventhThingIri = new MutableTestIri

    private val incunabulaBookBiechlin = "http://data.knora.org/9935159f67"
    // incunabula book with title "Eyn biechlin ..."
    private val incunabulaBookQuadra = "http://data.knora.org/861b5644b302" // incunabula book with title Quadragesimale

    private val notTheMostBoringComment = "This is not the most boring comment I have seen."

    private val mappingIri = "http://data.knora.org/projects/standoff/mappings/StandardMapping"

    private val xml1 =
        """<?xml version="1.0" encoding="UTF-8"?>
           <text><strong>Test</strong> text</text>
        """

    private val xml2 =
        """<?xml version="1.0" encoding="UTF-8"?>
           <text>a <strong>new</strong> value</text>
        """

    private val xml3 =
        s"""<?xml version="1.0" encoding="UTF-8"?>
           <text>
                This text links to <a href="http://www.google.ch">Google</a> and a Knora <a class="salsah-link" href="$incunabulaBookBiechlin">resource</a>.
           </text>
        """

    private val xml4 =
        s"""<?xml version="1.0" encoding="UTF-8"?>
           <text>
                This text links to <a href="http://www.google.ch">Google</a> and a Knora <a class="salsah-link" href="$incunabulaBookBiechlin">resource</a> and another Knora resource <a class="salsah-link" href="$incunabulaBookQuadra">resource</a>.
           </text>
        """

    /**
      * Gets the field `res_id` from a JSON response to resource creation.
      *
      * @param response the response sent back from the API.
      * @return the value of `res_id`.
      */
    private def getResIriFromJsonResponse(response: HttpResponse) = {

        AkkaHttpUtils.httpResponseToJson(response).fields.get("res_id") match {
            case Some(JsString(resourceId)) => resourceId
            case None => throw InvalidApiJsonException(s"The response does not contain a field called 'res_id'")
            case other => throw InvalidApiJsonException(s"The response does not contain a res_id of type JsString, but ${other}")
        }


    }

    /**
      * Gets the field `id` from a JSON response to value creation (new value).
      *
      * @param response the response sent back from the API.
      * @return the value of `res_id`.
      */
    private def getNewValueIriFromJsonResponse(response: HttpResponse) = {

        AkkaHttpUtils.httpResponseToJson(response).fields.get("id") match {
            case Some(JsString(resourceId)) => resourceId
            case None => throw InvalidApiJsonException(s"The response does not contain a field called 'res_id'")
            case other => throw InvalidApiJsonException(s"The response does not contain a res_id of type JsString, but $other")
        }

    }

    /**
      * Gets the given property's values from a resource full response.
      *
      * @param response the response to a resource full request.
      * @param prop     the given property IRI.
      * @return the property's values.
      */
    private def getValuesForProp(response: HttpResponse, prop: IRI): JsValue = {

        AkkaHttpUtils.httpResponseToJson(response).fields("props").asJsObject.fields(prop).asJsObject.fields("values")

    }


    /**
      * Gets the given property's comments from a resource full response.
      *
      * @param response the response to a resource full request.
      * @param prop     the given property IRI.
      * @return the property's comments.
      */
    private def getCommentsForProp(response: HttpResponse, prop: IRI): JsValue = {

        AkkaHttpUtils.httpResponseToJson(response).fields("props").asJsObject.fields(prop).asJsObject.fields("comments")

    }

    /**
      * Creates a SPARQL query string to get the standoff links (direct links) for a given resource.
      *
      * @param resIri the resource whose standoff links are to be queried.
      * @return SPARQL query string.
      */
    private def getDirectLinksSPARQL(resIri: IRI): String = {

        s"""
          PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          SELECT ?referredResourceIRI WHERE {
              BIND(IRI("$resIri") as ?resIRI)

              ?resIRI knora-base:hasStandoffLinkTo ?referredResourceIRI .

          }
        """

    }

    /**
      * Creates a SPARQL query to get the standoff links reifications to check for the target resource and the reference count.
      *
      * @param resIri the resource whose standoff reifications are to be queried.
      * @return SPARQL query string.
      */
    private def getRefCountsSPARQL(resIri: IRI): String = {

        s"""
           PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
           PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>

           SELECT DISTINCT ?reificationIRI ?object ?refCnt where {
                BIND(IRI("$resIri") as ?resIRI)

                ?resIRI knora-base:hasStandoffLinkToValue ?reificationIRI .

                ?reificationIRI rdf:object ?object .

                ?reificationIRI knora-base:valueHasRefCount ?refCnt .

           }
        """

    }


    "The Resources Endpoint" should {
        "provide a HTML representation of the resource properties " in {
            /* Incunabula resources*/

            /* A Book without a preview image */
            Get("/v1/resources.html/http%3A%2F%2Fdata.knora.org%2Fc5058f3a?noresedit=true&reqtype=properties") ~> resourcesPath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
                assert(responseAs[String] contains "Phyiscal description")
                assert(responseAs[String] contains "Location")
                assert(responseAs[String] contains "Publication location")
                assert(responseAs[String] contains "URI")
                assert(responseAs[String] contains "Title")
                assert(responseAs[String] contains "Datum der Herausgabe")
                assert(responseAs[String] contains "Citation/reference")
                assert(responseAs[String] contains "Publisher")
            }

            /* A Page with a preview image */
            Get("/v1/resources.html/http%3A%2F%2Fdata.knora.org%2Fde6c38ce3401?noresedit=true&reqtype=properties") ~> resourcesPath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
                assert(responseAs[String] contains "preview")
                assert(responseAs[String] contains "Ursprünglicher Dateiname")
                assert(responseAs[String] contains "Page identifier")
            }
        }

        "get the regions of a page when doing a context query with resinfo set to true" in {

            Get("/v1/resources/http%3A%2F%2Fdata.knora.org%2F9d626dc76c03?resinfo=true&reqtype=context") ~> resourcesPath ~> check {

                val responseJson: Map[String, JsValue] = responseAs[String].parseJson.asJsObject.fields
                val resourceContext: Map[String, JsValue] = responseJson("resource_context").asJsObject.fields
                val resinfo: Map[String, JsValue] = resourceContext("resinfo").asJsObject.fields

                resinfo.get("regions") match {
                    case Some(JsArray(regionsVector)) =>
                        val regions: Vector[PropsGetForRegionV1] = regionsVector.map(_.convertTo[PropsGetForRegionV1])

                        val region1 = regions.filter {
                            region => region.res_id == "http://data.knora.org/021ec18f1735"
                        }

                        val region2 = regions.filter {
                            region => region.res_id == "http://data.knora.org/b6b64a62b006"
                        }

                        assert(region1.length == 1, "No region found with Iri 'http://data.knora.org/021ec18f1735'")

                        assert(region2.length == 1, "No region found with Iri 'http://data.knora.org/b6b64a62b006'")

                    case None => assert(false, "No regions given, but 2 were expected")
                    case _ => assert(false, "No valid regions given")

                }

                assert(status == StatusCodes.OK, response.toString)
            }
        }

        "create a resource of type images:person" in {

            val params =
                """
                  |{
                  |    "restype_id": "http://www.knora.org/ontology/images#person",
                  |    "label": "Testperson",
                  |    "project_id": "http://data.knora.org/projects/images",
                  |    "properties": {
                  |        "http://www.knora.org/ontology/images#lastname": [{"richtext_value":{"textattr":"{}","resource_reference" :[],"utf8str":"Testname"}}],
                  |        "http://www.knora.org/ontology/images#firstname": [{"richtext_value":{"textattr":"{}","resource_reference" :[],"utf8str":"Name"}}]
                  |    }
                  |}
                """.stripMargin

            Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(incunabulaUsername, password)) ~> resourcesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
            }

        }

        "create a first resource of type anything:Thing" in {

            val params =
                s"""
                   |{
                   |    "restype_id": "http://www.knora.org/ontology/anything#Thing",
                   |    "label": "A thing",
                   |    "project_id": "http://data.knora.org/projects/anything",
                   |    "properties": {
                   |        "http://www.knora.org/ontology/anything#hasText": [{"richtext_value":{"xml": ${xml1.toJson.compactPrint}, "mapping_id": "$mappingIri"}}],
                   |        "http://www.knora.org/ontology/anything#hasInteger": [{"int_value":12345}],
                   |        "http://www.knora.org/ontology/anything#hasDecimal": [{"decimal_value":5.6}],
                   |        "http://www.knora.org/ontology/anything#hasUri": [{"uri_value":"http://dhlab.unibas.ch"}],
                   |        "http://www.knora.org/ontology/anything#hasDate": [{"date_value":"JULIAN:1291-08-01:1291-08-01"}],
                   |        "http://www.knora.org/ontology/anything#hasColor": [{"color_value":"#4169E1"}],
                   |        "http://www.knora.org/ontology/anything#hasListItem": [{"hlist_value":"http://data.knora.org/anything/treeList10"}],
                   |        "http://www.knora.org/ontology/anything#hasInterval": [{"interval_value": [1000000000000000.0000000000000001, 1000000000000000.0000000000000002]}],
                   |        "http://www.knora.org/ontology/anything#hasBoolean": [{"boolean_value":true}]
                   |    }
                   |}
                """.stripMargin

            // TODO: these properties have been commented out in the thing test ontology because of compatibility with the GUI
            // "http://www.knora.org/ontology/anything#hasGeoname": [{"geoname_value": "2661602"}]
            // "http://www.knora.org/ontology/anything#hasGeometry": [{"geom_value":"{\"status\":\"active\",\"lineColor\":\"#ff3333\",\"lineWidth\":2,\"points\":[{\"x\":0.5516074450084602,\"y\":0.4444444444444444},{\"x\":0.2791878172588832,\"y\":0.5}],\"type\":\"rectangle\",\"original_index\":0}"}],

            Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> resourcesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)

                val resId = getResIriFromJsonResponse(response)

                firstThingIri.set(resId)

            }
        }

        "get the created resource and check its standoff in the response" in {

            Get("/v1/resources/" + URLEncoder.encode(firstThingIri.get, "UTF-8")) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val text: JsValue = getValuesForProp(response, "http://www.knora.org/ontology/anything#hasText")

                val xml: String = text match {
                    case vals: JsArray =>
                        vals.elements.head.asJsObject.fields("xml") match {
                            case JsString(xml: String) => xml
                            case _ => throw new InvalidApiJsonException("member 'xml' not given")
                        }
                    case _ =>
                        throw new InvalidApiJsonException("values is not an array")
                }

                // Compare the original XML with the regenerated XML.
                val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(xml1)).withTest(Input.fromString(xml)).build()

                xmlDiff.hasDifferences should be(false)


            }

        }

        "create a new text value for the first thing resource" in {

            val newValueParams =
                s"""
                        {
                          "project_id": "http://data.knora.org/projects/anything",
                          "res_id": "${firstThingIri.get}",
                          "prop": "http://www.knora.org/ontology/anything#hasText",
                          "richtext_value": {
                                "xml": ${xml2.toJson.compactPrint},
                                "mapping_id": "$mappingIri"
                          }
                        }
                        """

            Post("/v1/values", HttpEntity(ContentTypes.`application/json`, newValueParams)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> valuesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val xml = AkkaHttpUtils.httpResponseToJson(response).fields.get("value") match {
                    case Some(value: JsObject) => value.fields.get("xml") match {
                        case Some(JsString(xml: String)) => xml
                        case _ => throw new InvalidApiJsonException("member 'xml' not given")
                    }
                    case _ => throw new InvalidApiJsonException("member 'value' not given")
                }

                // Compare the original XML with the regenerated XML.
                val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(xml2)).withTest(Input.fromString(xml)).build()

                xmlDiff.hasDifferences should be(false)

                val resId = getNewValueIriFromJsonResponse(response)

                firstTextValueIRI.set(resId)


            }

        }

        "change the created text value above for the first thing resource so it has a standoff link to incunabulaBookBiechlin" in {

            val xml =
                s"""<?xml version="1.0" encoding="UTF-8"?>
                   <text>a <u>new</u> value with a standoff <a class="salsah-link" href="$incunabulaBookBiechlin">link</a></text>
                """

            val newValueParams =
                s"""
                    {
                      "project_id": "http://data.knora.org/projects/anything",
                      "richtext_value": {
                            "xml": ${xml.toJson.compactPrint},
                            "mapping_id": "$mappingIri"
                      }
                    }
                """

            Put("/v1/values/" + URLEncoder.encode(firstTextValueIRI.get, "UTF-8"), HttpEntity(ContentTypes.`application/json`, newValueParams)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> valuesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val resId = getNewValueIriFromJsonResponse(response)

                firstTextValueIRI.set(resId)

            }

        }

        "make sure that the first thing resource contains a direct standoff link to incunabulaBookBiechlin now" in {

            val sparqlQuery = getDirectLinksSPARQL(firstThingIri.get)

            Await.result(storeManager ? SparqlSelectRequest(sparqlQuery), 30.seconds) match {

                case response: SparqlSelectResponse =>

                    val ref: Boolean = response.results.bindings.exists {
                        row: VariableResultsRow =>
                            row.rowMap("referredResourceIRI") == incunabulaBookBiechlin
                    }

                    assert(ref, s"No direct link to '$incunabulaBookBiechlin' found")

                case _ => throw TriplestoreResponseException("Expected a SparqlSelectResponse")

            }

        }

        "check that the first thing resource's standoff link reification has the correct reference count" in {

            val sparqlQuery = getRefCountsSPARQL(firstThingIri.get)

            Await.result(storeManager ? SparqlSelectRequest(sparqlQuery), 30.seconds) match {

                case response: SparqlSelectResponse =>

                    val refCnt: Boolean = response.results.bindings.exists {
                        row: VariableResultsRow =>
                            row.rowMap("object") == incunabulaBookBiechlin &&
                                row.rowMap("refCnt").toInt == 1
                    }

                    assert(refCnt, s"Ref count for '$incunabulaBookBiechlin' should be 1")

                case _ => throw TriplestoreResponseException("Expected a SparqlSelectResponse")

            }

        }

        "create a second resource of type anything:Thing linking to the first thing via standoff" in {

            val xml =
                s"""<?xml version="1.0" encoding="UTF-8"?>
                  <text>This text <a class="salsah-link" href="${firstThingIri.get}">links</a> to a thing</text>
                """

            val params =
                s"""
                {
                  "restype_id": "http://www.knora.org/ontology/anything#Thing",
                  "label": "A second thing",
                  "project_id": "http://data.knora.org/projects/anything",
                  "properties": {
                      "http://www.knora.org/ontology/anything#hasText": [{"richtext_value":{"xml":${xml.toJson.compactPrint},"mapping_id" :"$mappingIri"}}],
                    "http://www.knora.org/ontology/anything#hasInteger": [{"int_value":12345}],
                    "http://www.knora.org/ontology/anything#hasDecimal": [{"decimal_value":5.6}],
                    "http://www.knora.org/ontology/anything#hasUri": [{"uri_value":"http://dhlab.unibas.ch"}],
                    "http://www.knora.org/ontology/anything#hasDate": [{"date_value":"JULIAN:1291-08-01:1291-08-01"}],
                    "http://www.knora.org/ontology/anything#hasColor": [{"color_value":"#4169E1"}],
                    "http://www.knora.org/ontology/anything#hasListItem": [{"hlist_value":"http://data.knora.org/anything/treeList10"}],
                    "http://www.knora.org/ontology/anything#hasInterval": [{"interval_value": [1000000000000000.0000000000000001, 1000000000000000.0000000000000002]}]
                  }
                }
              """

            Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(incunabulaUsername, password)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val resId = getResIriFromJsonResponse(response)

                secondThingIri.set(resId)

            }

        }

        "get the second resource of type anything:Thing, containing the correct standoff link" in {
            Get("/v1/resources/" + URLEncoder.encode(secondThingIri.get, "UTF-8")) ~> addCredentials(BasicHttpCredentials(incunabulaUsername, password)) ~> resourcesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)

                val text: JsValue = getValuesForProp(response, "http://www.knora.org/ontology/anything#hasText")

                val xmlString: String = text match {
                    case vals: JsArray =>
                        vals.elements.head.asJsObject.fields("xml") match {
                            case JsString(xml: String) => xml
                            case _ => throw new InvalidApiJsonException("member 'xml' not given")
                        }
                    case _ =>
                        throw new InvalidApiJsonException("values is not an array")
                }

                // make sure that the xml contains a link to "firstThingIri"
                val xml = XML.loadString(xmlString)

                val link: NodeSeq = xml \ "a"

                assert(link.nonEmpty)

                val target: Seq[Node] = link.head.attributes("href")

                assert(target.nonEmpty && target.head.text == firstThingIri.get)

            }
        }

        "get the first thing resource that is referred to by the second thing resource" in {

            Get("/v1/resources/" + URLEncoder.encode(firstThingIri.get, "UTF-8")) ~> addCredentials(BasicHttpCredentials(incunabulaUsername, password)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                // check if this resource is referred to by the second thing resource
                val incoming = AkkaHttpUtils.httpResponseToJson(response).fields.get("incoming") match {
                    case Some(incomingRefs: JsArray) => incomingRefs
                    case None => throw InvalidApiJsonException(s"The response does not contain a field called 'incoming'")
                    case other => throw InvalidApiJsonException(s"The response does not contain a res_id of type JsObject, but $other")
                }

                val firstElement = incoming.elements.headOption match {
                    case Some(incomingRef: JsObject) => incomingRef
                    case None => throw NotFoundException("Field 'incoming' is empty, but one incoming reference is expected")
                    case other => throw InvalidApiJsonException("First element in 'incoming' is not a JsObject")
                }

                firstElement.fields.get("ext_res_id") match {
                    case Some(extResObj: JsObject) =>
                        // get the Iri of the referring resource
                        val idJsString = extResObj.fields.getOrElse("id", throw InvalidApiJsonException("No member 'id' given"))

                        // get the Iri of the property pointing to this resource
                        val propIriJsString = extResObj.fields.getOrElse("pid", throw InvalidApiJsonException("No member 'pid' given"))

                        idJsString match {
                            case JsString(id) =>
                                assert(id == secondThingIri.get, "This resource should be referred to by the second thing resource")
                            case other => throw InvalidApiJsonException("Id is not a JsString")
                        }

                        propIriJsString match {
                            case JsString(pid) =>
                                assert(pid == OntologyConstants.KnoraBase.HasStandoffLinkTo, s"This resource should be referred to by ${OntologyConstants.KnoraBase.HasStandoffLinkTo}")
                            case other => throw InvalidApiJsonException("pid is not a JsString")
                        }


                    case None => throw InvalidApiJsonException("Element in 'incoming' does not have a member 'ext_res_id'")
                    case other => throw InvalidApiJsonException("Element in 'incoming' is not a JsObject")
                }

            }

        }

        "attempt to create a resource of type thing with an invalid standoff tag name" in {

            // use a tag name that is not defined in the standard mapping ("trong" instead of "strong")
            val xml =
                """<?xml version="1.0" encoding="UTF-8"?>
                  <text>This <trong>text</trong></text>
                """

            val params =
                s"""
                  {
                      "restype_id": "http://www.knora.org/ontology/anything#Thing",
                      "label": "A second thing",
                      "project_id": "http://data.knora.org/projects/anything",
                      "properties": {
                          "http://www.knora.org/ontology/anything#hasText": [{"richtext_value":{"xml":${xml.toJson.compactPrint}, "mapping_id": "$mappingIri"}}],
                        "http://www.knora.org/ontology/anything#hasInteger": [{"int_value":12345}],
                        "http://www.knora.org/ontology/anything#hasDecimal": [{"decimal_value":5.6}],
                        "http://www.knora.org/ontology/anything#hasUri": [{"uri_value":"http://dhlab.unibas.ch"}],
                        "http://www.knora.org/ontology/anything#hasDate": [{"date_value":"JULIAN:1291-08-01:1291-08-01"}],
                        "http://www.knora.org/ontology/anything#hasColor": [{"color_value":"#4169E1"}],
                        "http://www.knora.org/ontology/anything#hasListItem": [{"hlist_value":"http://data.knora.org/anything/treeList10"}],
                        "http://www.knora.org/ontology/anything#hasInterval": [{"interval_value": [1000000000000000.0000000000000001, 1000000000000000.0000000000000002]}]
                      }
                  }
                """

            Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(incunabulaUsername, password)) ~> resourcesPath ~> check {

                // the route should reject the request because `trong` is not a tag name supported by the standard mapping
                assert(status == StatusCodes.BadRequest, response.toString)

            }

        }

        "attempt to create a resource of type thing submitting a wrong standoff link" in {

            val xml =
                s"""<?xml version="1.0" encoding="UTF-8"?>
                   <text><u><strong>This</strong></u> <u>text</u> <a class="salsah-link" href="$incunabulaBookQuadra">links</a> to <a class="salsah-link" href="http://data.knora.org/9935159f">two</a> things</text>
                """.stripMargin

            val params =
                s"""
                  {
                      "restype_id": "http://www.knora.org/ontology/anything#Thing",
                      "label": "A second thing",
                      "project_id": "http://data.knora.org/projects/anything",
                      "properties": {
                          "http://www.knora.org/ontology/anything#hasText": [{"richtext_value":{"xml":${xml.toJson.compactPrint},"mapping_id": "$mappingIri"}}],
                        "http://www.knora.org/ontology/anything#hasInteger": [{"int_value":12345}],
                        "http://www.knora.org/ontology/anything#hasDecimal": [{"decimal_value":5.6}],
                        "http://www.knora.org/ontology/anything#hasUri": [{"uri_value":"http://dhlab.unibas.ch"}],
                        "http://www.knora.org/ontology/anything#hasDate": [{"date_value":"JULIAN:1291-08-01:1291-08-01"}],
                        "http://www.knora.org/ontology/anything#hasColor": [{"color_value":"#4169E1"}],
                        "http://www.knora.org/ontology/anything#hasListItem": [{"hlist_value":"http://data.knora.org/anything/treeList10"}],
                        "http://www.knora.org/ontology/anything#hasInterval": [{"interval_value": [1000000000000000.0000000000000001, 1000000000000000.0000000000000002]}]
                      }
                  }
                """

            Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(incunabulaUsername, password)) ~> resourcesPath ~> check {

                //println(response)

                // the route should reject the request because an IRI is wrong (formally valid though)
                assert(status == StatusCodes.NotFound, response.toString)



            }

        }



        "create a third resource of type thing with two standoff links to the same resource and a standoff link to another one" in {

            val firstXML =
                s"""<?xml version="1.0" encoding="UTF-8"?>
                    <text><u><strong>This</strong></u> <u>text</u> <a class="salsah-link" href="$incunabulaBookQuadra">links</a> to a thing</text>
                """.stripMargin

            val secondXML =
                s"""<?xml version="1.0" encoding="UTF-8"?>
                   <text><u><strong>This</strong></u> <u>text</u> <a class="salsah-link" href="$incunabulaBookBiechlin">links</a> to the same thing <a class="salsah-link" href="$incunabulaBookBiechlin">twice</a> and to another <a class="salsah-link" href="$incunabulaBookQuadra">thing</a></text>
                """.stripMargin

            val params =
                s"""
                  {
                      "restype_id": "http://www.knora.org/ontology/anything#Thing",
                      "label": "A second thing",
                      "project_id": "http://data.knora.org/projects/anything",
                      "properties": {
                          "http://www.knora.org/ontology/anything#hasText": [{"richtext_value":{"xml":${firstXML.toJson.compactPrint},"mapping_id": "$mappingIri"}}, {"richtext_value":{"xml":${secondXML.toJson.compactPrint},"mapping_id": "$mappingIri"}}],
                        "http://www.knora.org/ontology/anything#hasInteger": [{"int_value":12345}],
                        "http://www.knora.org/ontology/anything#hasDecimal": [{"decimal_value":5.6}],
                        "http://www.knora.org/ontology/anything#hasUri": [{"uri_value":"http://dhlab.unibas.ch"}],
                        "http://www.knora.org/ontology/anything#hasDate": [{"date_value":"JULIAN:1291-08-01:1291-08-01"}],
                        "http://www.knora.org/ontology/anything#hasColor": [{"color_value":"#4169E1"}],
                        "http://www.knora.org/ontology/anything#hasListItem": [{"hlist_value":"http://data.knora.org/anything/treeList10"}],
                        "http://www.knora.org/ontology/anything#hasInterval": [{"interval_value": [1000000000000000.0000000000000001, 1000000000000000.0000000000000002]}]
                      }
                  }
                """

            Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val resId = getResIriFromJsonResponse(response)

                thirdThingIri.set(resId)

            }

        }

        "check that the third thing resource has two direct standoff links" in {

            val sparqlQuery = getDirectLinksSPARQL(thirdThingIri.get)

            Await.result(storeManager ? SparqlSelectRequest(sparqlQuery), 30.seconds) match {

                case response: SparqlSelectResponse =>

                    val ref1: Boolean = response.results.bindings.exists {
                        row: VariableResultsRow =>
                            row.rowMap("referredResourceIRI") == incunabulaBookQuadra
                    }

                    val ref2: Boolean = response.results.bindings.exists {
                        row: VariableResultsRow =>
                            row.rowMap("referredResourceIRI") == incunabulaBookBiechlin
                    }

                    assert(ref1, s"No direct link to '$incunabulaBookQuadra' found")

                    assert(ref2, s"No direct link to '$incunabulaBookBiechlin' found")

                case _ => throw TriplestoreResponseException("Expected a SparqlSelectResponse")

            }

        }

        "check that the third thing resource's standoff link reifications have the correct reference counts" in {

            val sparqlQuery = getRefCountsSPARQL(thirdThingIri.get)

            Await.result(storeManager ? SparqlSelectRequest(sparqlQuery), 30.seconds) match {

                case response: SparqlSelectResponse =>

                    val refCnt1: Boolean = response.results.bindings.exists {
                        row: VariableResultsRow =>
                            row.rowMap("object") == incunabulaBookQuadra &&
                                row.rowMap("refCnt").toInt == 2
                    }

                    val refCnt2: Boolean = response.results.bindings.exists {
                        row: VariableResultsRow =>
                            row.rowMap("object") == incunabulaBookBiechlin &&
                                row.rowMap("refCnt").toInt == 1
                    }

                    assert(refCnt1, s"Ref count for '$incunabulaBookQuadra' should be 2")

                    assert(refCnt2, s"Ref count for '$incunabulaBookBiechlin' should be 1")

                case _ => throw TriplestoreResponseException("Expected a SparqlSelectResponse")

            }

        }

        "mark a resource as deleted" in {
            Delete("/v1/resources/http%3A%2F%2Fdata.knora.org%2F9d626dc76c03?deleteComment=deleted%20for%20testing") ~> addCredentials(BasicHttpCredentials(incunabulaUsername, password)) ~> resourcesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
            }
        }


        "create a fourth resource of type anything:Thing with a hyperlink in standoff" in {

            val xml =
                """<?xml version="1.0" encoding="UTF-8"?>
                   <text>
                        This text links to <a href="http://www.google.ch">Google</a>.
                   </text>
                """

            val params =
                s"""
                  {
                      "restype_id": "http://www.knora.org/ontology/anything#Thing",
                      "label": "A second thing",
                      "project_id": "http://data.knora.org/projects/anything",
                      "properties": {
                          "http://www.knora.org/ontology/anything#hasText": [{"richtext_value":{"xml":${xml.toJson.compactPrint},"mapping_id":"$mappingIri"}}],
                        "http://www.knora.org/ontology/anything#hasInteger": [{"int_value":12345}],
                        "http://www.knora.org/ontology/anything#hasDecimal": [{"decimal_value":5.6}],
                        "http://www.knora.org/ontology/anything#hasUri": [{"uri_value":"http://dhlab.unibas.ch"}],
                        "http://www.knora.org/ontology/anything#hasDate": [{"date_value":"JULIAN:1291-08-01:1291-08-01"}],
                        "http://www.knora.org/ontology/anything#hasColor": [{"color_value":"#4169E1"}],
                        "http://www.knora.org/ontology/anything#hasListItem": [{"hlist_value":"http://data.knora.org/anything/treeList10"}],
                        "http://www.knora.org/ontology/anything#hasInterval": [{"interval_value": [1000000000000000.0000000000000001, 1000000000000000.0000000000000002]}]
                      }
                  }
                """

            Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(incunabulaUsername, password)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val resId = getResIriFromJsonResponse(response)

                fourthThingIri.set(resId)

            }

        }

        "get the fourth resource of type anything:Thing, containing the hyperlink in standoff" in {
            Get("/v1/resources/" + URLEncoder.encode(fourthThingIri.get, "UTF-8")) ~> addCredentials(BasicHttpCredentials(incunabulaUsername, password)) ~> resourcesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)

                val text: JsValue = getValuesForProp(response, "http://www.knora.org/ontology/anything#hasText")

                val xmlString: String = text match {
                    case vals: JsArray =>
                        vals.elements.head.asJsObject.fields("xml") match {
                            case JsString(xml: String) => xml
                            case _ => throw new InvalidApiJsonException("member 'xml' not given")
                        }
                    case _ =>
                        throw new InvalidApiJsonException("values is not an array")
                }

                // make sure that the xml contains a link to http://www.google.ch
                val xml = XML.loadString(xmlString)

                val link: NodeSeq = xml \ "a"

                assert(link.nonEmpty)

                val target: Seq[Node] = link.head.attributes("href")

                assert(target.nonEmpty && target.head.text == "http://www.google.ch")


            }
        }


        "create a fifth resource of type anything:Thing with various standoff markup including internal links and hyperlinks" in {

            // xml3 contains a link to google.ch and to incunabulaBookBiechlin

            val params =
                s"""
              {
                  "restype_id": "http://www.knora.org/ontology/anything#Thing",
                  "label": "A second thing",
                  "project_id": "http://data.knora.org/projects/anything",
                  "properties": {
                      "http://www.knora.org/ontology/anything#hasText": [{"richtext_value":{"xml":${xml3.toJson.compactPrint}, "mapping_id": "$mappingIri"}}],
                    "http://www.knora.org/ontology/anything#hasInteger": [{"int_value":12345}],
                    "http://www.knora.org/ontology/anything#hasDecimal": [{"decimal_value":5.6}],
                    "http://www.knora.org/ontology/anything#hasUri": [{"uri_value":"http://dhlab.unibas.ch"}],
                    "http://www.knora.org/ontology/anything#hasDate": [{"date_value":"JULIAN:1291-08-01:1291-08-01"}],
                    "http://www.knora.org/ontology/anything#hasColor": [{"color_value":"#4169E1"}],
                    "http://www.knora.org/ontology/anything#hasListItem": [{"hlist_value":"http://data.knora.org/anything/treeList10"}],
                    "http://www.knora.org/ontology/anything#hasInterval": [{"interval_value": [1000000000000000.0000000000000001, 1000000000000000.0000000000000002]}]
                  }
              }
            """

            Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(incunabulaUsername, password)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val resId = getResIriFromJsonResponse(response)

                fifthThingIri.set(resId)

            }

        }

        "get the fifth resource of type anything:Thing, containing various standoff markup" in {
            Get("/v1/resources/" + URLEncoder.encode(fifthThingIri.get, "UTF-8")) ~> addCredentials(BasicHttpCredentials(incunabulaUsername, password)) ~> resourcesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)

                val text: JsValue = getValuesForProp(response, "http://www.knora.org/ontology/anything#hasText")

                val xmlString: String = text match {
                    case vals: JsArray =>
                        vals.elements.head.asJsObject.fields("xml") match {
                            case JsString(xml: String) => xml
                            case _ => throw new InvalidApiJsonException("member 'xml' not given")
                        }
                    case _ =>
                        throw new InvalidApiJsonException("values is not an array")
                }

                // make sure that the correct standoff links and references
                // xml3 contains a link to google.ch and to incunabulaBookBiechlin
                val xml = XML.loadString(xmlString)

                val links: NodeSeq = xml \ "a"

                // there should be two links
                assert(links.length == 2)

                val linkToGoogle: Seq[Node] = links.head.attributes("href")

                assert(linkToGoogle.nonEmpty && linkToGoogle.head.text == "http://www.google.ch")

                val linkKnoraResource: Seq[Node] = links(1).attributes("href")

                assert(linkKnoraResource.nonEmpty && linkKnoraResource.head.text == incunabulaBookBiechlin)

                // Compare the original XML with the regenerated XML.
                val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(xmlString)).withTest(Input.fromString(xml3)).build()

                xmlDiff.hasDifferences should be(false)

            }
        }

        "create a sixth resource of type anything:Thing with internal links to two different resources" in {

            // xml4 contains a link to google.ch, to incunabulaBookBiechlin and to incunabulaBookQuadra

            val params =
                s"""
                  {
                      "restype_id": "http://www.knora.org/ontology/anything#Thing",
                      "label": "A second thing",
                      "project_id": "http://data.knora.org/projects/anything",
                      "properties": {
                          "http://www.knora.org/ontology/anything#hasText": [{"richtext_value":{"xml": ${xml4.toJson.compactPrint},"mapping_id": "$mappingIri"}}],
                        "http://www.knora.org/ontology/anything#hasInteger": [{"int_value":12345}],
                        "http://www.knora.org/ontology/anything#hasDecimal": [{"decimal_value":5.6}],
                        "http://www.knora.org/ontology/anything#hasUri": [{"uri_value":"http://dhlab.unibas.ch"}],
                        "http://www.knora.org/ontology/anything#hasDate": [{"date_value":"JULIAN:1291-08-01:1291-08-01"}],
                        "http://www.knora.org/ontology/anything#hasColor": [{"color_value":"#4169E1"}],
                        "http://www.knora.org/ontology/anything#hasListItem": [{"hlist_value":"http://data.knora.org/anything/treeList10"}],
                        "http://www.knora.org/ontology/anything#hasInterval": [{"interval_value": [1000000000000000.0000000000000001, 1000000000000000.0000000000000002]}]
                      }
                  }
                    """

            Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(incunabulaUsername, password)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val resId = getResIriFromJsonResponse(response)

                sixthThingIri.set(resId)

            }

        }

        "get the sixth resource of type anything:Thing with internal links to two different resources" in {
            Get("/v1/resources/" + URLEncoder.encode(sixthThingIri.get, "UTF-8")) ~> addCredentials(BasicHttpCredentials(incunabulaUsername, password)) ~> resourcesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)


                val text: JsValue = getValuesForProp(response, "http://www.knora.org/ontology/anything#hasText")

                val xmlString: String = text match {
                    case vals: JsArray =>
                        vals.elements.head.asJsObject.fields("xml") match {
                            case JsString(xml: String) => xml
                            case _ => throw new InvalidApiJsonException("member 'xml' not given")
                        }
                    case _ =>
                        throw new InvalidApiJsonException("values is not an array")
                }

                // make sure that the correct standoff links and references
                // xml4 contains a link to google.ch, to incunabulaBookBiechlin and to incunabulaBookQuadra
                val xml = XML.loadString(xmlString)

                val links: NodeSeq = xml \ "a"

                // there should be three links
                assert(links.length == 3)

                val linkToGoogle: Seq[Node] = links.head.attributes("href")

                assert(linkToGoogle.nonEmpty && linkToGoogle.head.text == "http://www.google.ch")

                val linkKnoraResource: Seq[Node] = links(1).attributes("href")

                assert(linkKnoraResource.nonEmpty && linkKnoraResource.head.text == incunabulaBookBiechlin)

                val linkKnoraResource2: Seq[Node] = links(2).attributes("href")

                assert(linkKnoraResource2.nonEmpty && linkKnoraResource2.head.text == incunabulaBookQuadra)

                // Compare the original XML with the regenerated XML.
                val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(xmlString)).withTest(Input.fromString(xml4)).build()

                xmlDiff.hasDifferences should be(false)

            }
        }

        "change a resource's label" in {

            val newLabel = "my new label"

            val params =
                s"""
                  {
                    "label": "$newLabel"
                  }
                """.stripMargin

            Put("/v1/resources/label/" + URLEncoder.encode("http://data.knora.org/c5058f3a", "UTF-8"), HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(incunabulaUsername, password)) ~> resourcesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)

                val label = AkkaHttpUtils.httpResponseToJson(response).fields.get("label") match {
                    case Some(JsString(str)) => str
                    case None => throw InvalidApiJsonException(s"The response does not contain a field called 'label'")
                    case other => throw InvalidApiJsonException(s"The response does not contain a label of type JsString, but $other")
                }

                assert(label == newLabel, "label has not been updated correctly")


            }
        }

        "create a resource of type anything:Thing with a link (containing a comment) to another resource" in {

            val params =
                s"""
                   |{
                   |    "restype_id": "http://www.knora.org/ontology/anything#Thing",
                   |    "label": "A thing with a link value that has a comment",
                   |    "project_id": "http://data.knora.org/projects/anything",
                   |    "properties": {
                   |        "http://www.knora.org/ontology/anything#hasText": [{"richtext_value": {"utf8str": "simple text"}}],
                   |        "http://www.knora.org/ontology/anything#hasOtherThing": [{"link_value":"${sixthThingIri.get}", "comment":"$notTheMostBoringComment"}]
                   |    }
                   }
                """.stripMargin

            Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> resourcesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)

                val resId = getResIriFromJsonResponse(response)

                seventhThingIri.set(resId)
            }
        }

        "get the created resource and check the comment on the link value" in {

            Get("/v1/resources/" + URLEncoder.encode(seventhThingIri.get, "UTF-8")) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val targetResourceIri: String = getValuesForProp(response, "http://www.knora.org/ontology/anything#hasOtherThing") match {
                    case vals: JsArray =>
                        vals.elements.head.asInstanceOf[JsString].value
                    case _ =>
                        throw new InvalidApiJsonException("values is not an array")
                }

                assert(targetResourceIri == sixthThingIri.get)

                val linkValueComment: String = getCommentsForProp(response, "http://www.knora.org/ontology/anything#hasOtherThing") match {
                    case vals: JsArray =>
                        vals.elements.head.asInstanceOf[JsString].value
                    case _ =>
                        throw new InvalidApiJsonException("comments is not an array")
                }

                assert(linkValueComment == notTheMostBoringComment)
            }
        }

        "add a simple TextValue to the seventh resource" in {

            val newValueParams =
                s"""
                        {
                          "project_id": "http://data.knora.org/projects/anything",
                          "res_id": "${seventhThingIri.get}",
                          "prop": "http://www.knora.org/ontology/anything#hasText",
                          "richtext_value": {
                                "utf8str": "another simple text"
                          }
                        }
                        """

            Post("/v1/values", HttpEntity(ContentTypes.`application/json`, newValueParams)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> valuesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val utf8str = AkkaHttpUtils.httpResponseToJson(response).fields.get("value") match {
                    case Some(value: JsObject) => value.fields.get("utf8str") match {
                        case Some(JsString(xml: String)) => xml
                        case _ => throw new InvalidApiJsonException("member 'utf8str' not given")
                    }
                    case _ => throw new InvalidApiJsonException("member 'value' not given")
                }

                assert(utf8str == "another simple text")


            }



        }
    }
}
