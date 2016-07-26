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
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.e2e.E2ESpec
import org.knora.webapi.messages.v1.responder.resourcemessages.PropsGetForRegionV1
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourceV1JsonProtocol._
import org.knora.webapi.messages.v1.store.triplestoremessages._
import org.knora.webapi.responders._
import org.knora.webapi.responders.v1.ResponderManagerV1
import org.knora.webapi.routing.v1.{ResourcesRouteV1, ValuesRouteV1}
import org.knora.webapi.store._
import org.knora.webapi.util.{MutableTestIri, ScalaPrettyPrinter}
import spray.http.MediaTypes._
import spray.http.{HttpEntity, _}
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._


/**
  * End-to-end test specification for the resources endpoint. This specification uses the Spray Testkit as documented
  * here: http://spray.io/documentation/1.2.2/spray-testkit/
  */
class ResourcesV1E2ESpec extends E2ESpec {

    override def testConfigSource =
        """
         # akka.loglevel = "DEBUG"
         # akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val resourcesPath = ResourcesRouteV1.rapierPath(system, settings, log)
    val valuesPath = ValuesRouteV1.rapierPath(system, settings, log)

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
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images"),
        RdfDataObject(path = "_test_data/ontologies/anything-onto.ttl", name = "http://www.knora.org/ontology/anything"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")
    )

    "Load test data" in {
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 300.seconds)
    }

    private val firstThingIri = new MutableTestIri
    private val firstTextValueIRI = new MutableTestIri
    private val secondThingIri = new MutableTestIri
    private val thirdThingIri = new MutableTestIri

    val incunabulaBookBiechlin = "http://data.knora.org/9935159f67" // incunabula book with title "Eyn biechlin ..."
    val incunabulaBookQuadra = "http://data.knora.org/861b5644b302" // incunabula book with title Quadragesimale


    /**
      * Gets the field `res_id` from a JSON response to resource creation.
      *
      * @param response the response sent back from the API.
      * @return the value of `res_id`.
      */
    private def getResIriFromJsonResponse(response: HttpResponse) = {

        JsonParser(response.entity.asString).asJsObject.fields.get("res_id") match {
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

        JsonParser(response.entity.asString).asJsObject.fields.get("id") match {
            case Some(JsString(resourceId)) => resourceId
            case None => throw InvalidApiJsonException(s"The response does not contain a field called 'res_id'")
            case other => throw InvalidApiJsonException(s"The response does not contain a res_id of type JsString, but ${other}")
        }

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

                val response: Map[String, JsValue] = responseAs[String].parseJson.asJsObject.fields

                val resourceContext = response("resource_context").asJsObject.fields

                val resinfo: Map[String, JsValue] = resourceContext("resinfo").asJsObject.fields

                val regions = resinfo.get("regions") match {
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

              {
              	"restype_id": "http://www.knora.org/ontology/images#person",
              	"label": "Testperson",
              	"project_id": "http://data.knora.org/projects/images",
              	"properties": {
              		"http://www.knora.org/ontology/images#lastname": [{"richtext_value":{"textattr":"{}","resource_reference" :[],"utf8str":"Testname"}}],
                    "http://www.knora.org/ontology/images#firstname": [{"richtext_value":{"textattr":"{}","resource_reference" :[],"utf8str":"Name"}}]
              	}
              }


                """

            Post("/v1/resources", HttpEntity(`application/json`, params)) ~> addCredentials(BasicHttpCredentials(user, password)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

            }

        }

        "create a first resource of type anything:Thing" in {

            val textattrStringified =
                """
                  {
                      "bold": [{
                          "start": 0,
                          "end": 4
                      }]
                  }
                """.toJson.compactPrint

            val params =
                s"""
              {
              	"restype_id": "http://www.knora.org/ontology/anything#Thing",
              	"label": "A first thing",
              	"project_id": "http://data.knora.org/projects/anything",
              	"properties": {
              		"http://www.knora.org/ontology/anything#hasText": [{"richtext_value":{"textattr":$textattrStringified,"resource_reference" :[],"utf8str":"This is a test"}}],
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

            // TODO: these properties have been commented out in the thing test ontology because of compatibility with the GUI
            // "http://www.knora.org/ontology/anything#hasGeoname": [{"geoname_value": "2661602"}]
            //  "http://www.knora.org/ontology/anything#hasBoolean": [{"boolean_value":true}],
            // "http://www.knora.org/ontology/anything#hasGeometry": [{"geom_value":"{\"status\":\"active\",\"lineColor\":\"#ff3333\",\"lineWidth\":2,\"points\":[{\"x\":0.5516074450084602,\"y\":0.4444444444444444},{\"x\":0.2791878172588832,\"y\":0.5}],\"type\":\"rectangle\",\"original_index\":0}"}],

            Post("/v1/resources", HttpEntity(`application/json`, params)) ~> addCredentials(BasicHttpCredentials(user, password)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val resId = getResIriFromJsonResponse(response)

                firstThingIri.set(resId)

            }
        }

        "create a new text value for the first thing resource" in {

            val textattr =
                """
                  {
                    "bold": [{
                        "start": 2,
                        "end": 5
                    }]
                  }
                """.toJson.compactPrint

            val newValueParams =
                s"""
                {
                  "project_id": "http://data.knora.org/projects/anything",
                  "res_id": "${firstThingIri.get}",
                  "prop": "http://www.knora.org/ontology/anything#hasText",
                  "richtext_value": {
                        "utf8str": "a new value",
                        "textattr": ${textattr}
                  }
                }
                """

            Post("/v1/values", HttpEntity(`application/json`, newValueParams)) ~> addCredentials(BasicHttpCredentials(user, password)) ~> valuesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val resId = getNewValueIriFromJsonResponse(response)

                firstTextValueIRI.set(resId)


            }

        }

        "change the created text value above for the first thing resource so it has a standoff link to incunabulaBookBiechlin" in {

            val textattr =
                s"""
                  {
                    "underline": [{
                        "start": 2,
                        "end": 5
                    }],
                  "_link": [{
                        "start": 10,
                        "end": 15,
                        "resid": "$incunabulaBookBiechlin",
                        "href": "$incunabulaBookBiechlin"
                    }]
                  }
                """.toJson.compactPrint

            val newValueParams =
                s"""
                {
                  "project_id": "http://data.knora.org/projects/anything",
                  "richtext_value": {
                        "utf8str": "a new value",
                        "textattr": ${textattr},
                        "resource_reference": ["$incunabulaBookBiechlin"]
                  }
                }
                """

            Put("/v1/values/" + URLEncoder.encode(firstTextValueIRI.get, "UTF-8"), HttpEntity(`application/json`, newValueParams)) ~> addCredentials(BasicHttpCredentials(user, password)) ~> valuesPath ~> check {

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

            val textattrStringified =
                s"""
                  {
                      "_link": [{
                          "start": 10,
                          "end": 15,
                          "resid": "${firstThingIri.get}",
                          "href": "${firstThingIri.get}"
                      }]
                  }
                """.toJson.compactPrint

            val params =
                s"""
              {
              	"restype_id": "http://www.knora.org/ontology/anything#Thing",
              	"label": "A second thing",
              	"project_id": "http://data.knora.org/projects/anything",
              	"properties": {
              		"http://www.knora.org/ontology/anything#hasText": [{"richtext_value":{"textattr":$textattrStringified,"resource_reference" :["${firstThingIri.get}"],"utf8str":"This text links to a thing"}}],
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

            Post("/v1/resources", HttpEntity(`application/json`, params)) ~> addCredentials(BasicHttpCredentials(user, password)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val resId = getResIriFromJsonResponse(response)

                secondThingIri.set(resId)

            }

        }

        "get the first thing resource that is referred to by the second thing resource" in {

            Get("/v1/resources/" + URLEncoder.encode(firstThingIri.get, "UTF-8")) ~> addCredentials(BasicHttpCredentials(user, password)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                // check if this resource is referred to by the second thing resource
                val incoming = JsonParser(response.entity.asString).asJsObject.fields.get("incoming") match {
                    case Some(incomingRefs: JsArray) => incomingRefs
                    case None => throw InvalidApiJsonException(s"The response does not contain a field called 'incoming'")
                    case other => throw InvalidApiJsonException(s"The response does not contain a res_id of type JsObject, but ${other}")
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

            // use invalid standoff tag name
            val textattrStringified =
            """
                  {
                      "old": [{
                          "start": 0,
                          "end": 4
                      }]
                  }
            """.toJson.compactPrint

            val params =
                s"""
              {
              	"restype_id": "http://www.knora.org/ontology/anything#Thing",
              	"label": "A second thing",
              	"project_id": "http://data.knora.org/projects/anything",
              	"properties": {
              		"http://www.knora.org/ontology/anything#hasText": [{"richtext_value":{"textattr":$textattrStringified,"resource_reference" :[],"utf8str":"This text links to a thing"}}],
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

            Post("/v1/resources", HttpEntity(`application/json`, params)) ~> addCredentials(BasicHttpCredentials(user, password)) ~> resourcesPath ~> check {

                // the route should reject the request because `old` is not a valid standoff tag name
                assert(status == StatusCodes.BadRequest, response.toString)

            }

        }

        "create a resource of type thing with several standoff tags" in {

            val textattrStringified =
                s"""
                  {
                      "bold": [{
                          "start": 0,
                          "end": 4
                      }],
                      "underline": [
                          {
                            "start": 0,
                            "end": 4
                          },
                          {
                            "start": 5,
                            "end": 9
                          }
                      ],
                      "_link": [
                        {
                            "start": 10,
                            "end": 15,
                            "href": "$incunabulaBookQuadra",
                            "resid": "$incunabulaBookQuadra"
                        },
                        {
                            "start": 16,
                            "end": 18,
                            "href": "$incunabulaBookBiechlin",
                            "resid": "$incunabulaBookBiechlin"
                        }
                      ]
                  }
                """.toJson.compactPrint

            val params =
                s"""
              {
              	"restype_id": "http://www.knora.org/ontology/anything#Thing",
              	"label": "A second thing",
              	"project_id": "http://data.knora.org/projects/anything",
              	"properties": {
              		"http://www.knora.org/ontology/anything#hasText": [{"richtext_value":{"textattr":$textattrStringified,"resource_reference" :["$incunabulaBookQuadra", "$incunabulaBookBiechlin"],"utf8str":"This text links to a thing"}}],
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

            Post("/v1/resources", HttpEntity(`application/json`, params)) ~> addCredentials(BasicHttpCredentials(user, password)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

            }

        }

        "create a resource of type thing with several standoff tags with a missing IRI in resource_reference" in {

            val textattrStringified =
                s"""
                  {
                      "bold": [{
                          "start": 0,
                          "end": 4
                      }],
                      "underline": [
                          {
                            "start": 0,
                            "end": 4
                          },
                          {
                            "start": 5,
                            "end": 9
                          }
                      ],
                      "_link": [
                        {
                            "start": 10,
                            "end": 15,
                            "href": "$incunabulaBookQuadra",
                            "resid": "$incunabulaBookQuadra"
                        },
                        {
                            "start": 16,
                            "end": 18,
                            "href": "$incunabulaBookBiechlin",
                            "resid": "$incunabulaBookBiechlin"
                        }
                      ]
                  }
                """.toJson.compactPrint

            // IRI incunabulaBookQuadra is missing in resource_reference
            val params =
            s"""
              {
              	"restype_id": "http://www.knora.org/ontology/anything#Thing",
              	"label": "A second thing",
              	"project_id": "http://data.knora.org/projects/anything",
              	"properties": {
              		"http://www.knora.org/ontology/anything#hasText": [{"richtext_value":{"textattr":$textattrStringified,"resource_reference" :["$incunabulaBookBiechlin"],"utf8str":"This text links to a thing"}}],
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

            Post("/v1/resources", HttpEntity(`application/json`, params)) ~> addCredentials(BasicHttpCredentials(user, password)) ~> resourcesPath ~> check {

                // the route should reject the request because an IRI is missing in resource_reference
                assert(status == StatusCodes.BadRequest, response.toString)

            }

        }

        "create a resource of type thing with several standoff tags with an IRI given in resource_reference but not given in the standoff link tags" in {

            // IRI http://data.knora.org/9935159f67 (incunabulaBookBiechlin) is missing in standoff link tag
            val textattrStringified =
            s"""
                  {
                      "bold": [{
                          "start": 0,
                          "end": 4
                      }],
                      "underline": [
                          {
                            "start": 0,
                            "end": 4
                          },
                          {
                            "start": 5,
                            "end": 9
                          }
                      ],
                      "_link": [
                        {
                            "start": 10,
                            "end": 15,
                            "href": "$incunabulaBookQuadra",
                            "resid": "$incunabulaBookQuadra"
                        },
                        {
                            "start": 16,
                            "end": 18,
                            "href": "$incunabulaBookBiechlin"
                        }
                      ]
                  }
                """.toJson.compactPrint

            val params =
                s"""
              {
              	"restype_id": "http://www.knora.org/ontology/anything#Thing",
              	"label": "A second thing",
              	"project_id": "http://data.knora.org/projects/anything",
              	"properties": {
              		"http://www.knora.org/ontology/anything#hasText": [{"richtext_value":{"textattr":$textattrStringified,"resource_reference" :["$incunabulaBookBiechlin", "$incunabulaBookQuadra"],"utf8str":"This text links to a thing"}}],
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

            Post("/v1/resources", HttpEntity(`application/json`, params)) ~> addCredentials(BasicHttpCredentials(user, password)) ~> resourcesPath ~> check {

                // the route should reject the request because an IRI is missing in standoff link tags
                assert(status == StatusCodes.BadRequest, response.toString)

            }

        }


        "create a third resource of type thing with two standoff links to the same resource and a standoff link to another one" in {

            val textattrStringified1 =
                s"""
                  {
                      "bold": [{
                          "start": 0,
                          "end": 4
                      }],
                      "underline": [
                          {
                            "start": 0,
                            "end": 4
                          },
                          {
                            "start": 5,
                            "end": 9
                          }
                      ],
                      "_link": [
                        {
                            "start": 10,
                            "end": 15,
                            "href": "$incunabulaBookQuadra",
                            "resid": "$incunabulaBookQuadra"
                        }
                      ]
                  }
                """.toJson.compactPrint


            val textattrStringified2 =
                s"""
                  {
                      "bold": [{
                          "start": 0,
                          "end": 4
                      }],
                      "underline": [
                          {
                            "start": 0,
                            "end": 4
                          },
                          {
                            "start": 5,
                            "end": 9
                          }
                      ],
                      "_link": [
                        {
                            "start": 10,
                            "end": 15,
                            "href": "$incunabulaBookQuadra",
                            "resid": "$incunabulaBookQuadra"
                        },
                        {
                           "start": 10,
                           "end": 15,
                           "href": "$incunabulaBookBiechlin",
                           "resid": "$incunabulaBookBiechlin"
                        },
                        {
                           "start": 10,
                           "end": 15,
                           "href": "$incunabulaBookBiechlin",
                           "resid": "$incunabulaBookBiechlin"
                        }
                      ]
                  }
                """.toJson.compactPrint


            val params =
                s"""
              {
              	"restype_id": "http://www.knora.org/ontology/anything#Thing",
              	"label": "A second thing",
              	"project_id": "http://data.knora.org/projects/anything",
              	"properties": {
              		"http://www.knora.org/ontology/anything#hasText": [{"richtext_value":{"textattr":$textattrStringified1,"resource_reference" :["$incunabulaBookQuadra"],"utf8str":"This text links to a thing"}}, {"richtext_value":{"textattr":$textattrStringified2,"resource_reference" :["$incunabulaBookQuadra", "$incunabulaBookBiechlin"],"utf8str":"This text links to a thing"}}],
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

            Post("/v1/resources", HttpEntity(`application/json`, params)) ~> addCredentials(BasicHttpCredentials(user, password)) ~> resourcesPath ~> check {

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

    }

}
