package org.knora.webapi.e2e.v2

import java.net.URLEncoder
import java.time.Instant

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{HttpEntity, StatusCodes}
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.SharedTestDataADM.{ANYTHING_PROJECT_IRI, INCUNABULA_PROJECT_IRI}
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent}
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.routing.v2.{ResourcesRouteV2, SearchRouteV2, ValuesRouteV2}
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util._
import org.knora.webapi.util.jsonld._
import org.knora.webapi.util.search.SparqlQueryConstants

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor}

class ValuesRouteV2R2RSpec extends R2RSpec {

    private val valuesPath = ValuesRouteV2.knoraApiPath(system, settings, log)
    private val resourcesPath = ResourcesRouteV2.knoraApiPath(system, settings, log)
    private val searchPath = SearchRouteV2.knoraApiPath(system, settings, log)

    implicit private val timeout: Timeout = settings.defaultRestoreTimeout

    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(new DurationInt(15).second)

    implicit val ec: ExecutionContextExecutor = system.dispatcher

    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val incunabulaProjectIri = INCUNABULA_PROJECT_IRI
    private val anythingProjectIri = ANYTHING_PROJECT_IRI

    private val zeitglöckleinIri = "http://rdfh.ch/c5058f3a"
    private val miscResourceIri = "http://rdfh.ch/miscResource"
    private val aThingIri = "http://rdfh.ch/0001/a-thing"

    private val incunabulaUser = SharedTestDataADM.incunabulaMemberUser
    private val imagesUser = SharedTestDataADM.imagesUser01
    private val anythingUser = SharedTestDataADM.anythingUser1
    private val anythingUserEmail = anythingUser.email
    private val password = "test"

    private val intValueIri = new MutableTestIri
    private val commentValueIri = new MutableTestIri
    private val decimalValueIri = new MutableTestIri
    private val dateValueIri = new MutableTestIri
    private val booleanValueIri = new MutableTestIri
    private val geometryValueIri = new MutableTestIri
    private val intervalValueIri = new MutableTestIri
    private val listValueIri = new MutableTestIri
    private val colorValueIri = new MutableTestIri
    private val uriValueIri = new MutableTestIri
    private val geonameValueIri = new MutableTestIri
    private val linkValueIri = new MutableTestIri


    val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/responders.v2.ValuesResponderV2Spec/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    private def getResourceWithValues(resourceIri: IRI,
                                      propertyIrisForGravsearch: Seq[SmartIri]): JsonLDDocument = {
        // Make a Gravsearch query from a template.
        val gravsearchQuery: String = queries.gravsearch.txt.getResourceWithSpecifiedProperties(
            resourceIri = resourceIri,
            propertyIris = propertyIrisForGravsearch
        ).toString()

        // Run the query.

        Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {
            assert(status == StatusCodes.OK, response.toString)
            responseToJsonLDDocument(response)
        }
    }

    private def getValuesFromResource(resource: JsonLDDocument,
                                      propertyIriInResult: SmartIri): JsonLDArray = {
        resource.requireArray(propertyIriInResult.toString)
    }

    private def getValueFromResource(resource: JsonLDDocument,
                                     propertyIriInResult: SmartIri,
                                     expectedValueIri: IRI): JsonLDObject = {
        val resourceIri: IRI = resource.requireString(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
        val propertyValues: JsonLDArray = getValuesFromResource(resource = resource, propertyIriInResult = propertyIriInResult)

        val matchingValues: Seq[JsonLDObject] = propertyValues.value.collect {
            case jsonLDObject: JsonLDObject if jsonLDObject.requireString(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri) == expectedValueIri => jsonLDObject
            case other => throw AssertionException(s"Expected JSON-LD object representing a Knora value, found: $other")
        }

        if (matchingValues.isEmpty) {
            throw AssertionException(s"Property <$propertyIriInResult> of resource <$resourceIri> does not have value <$expectedValueIri>")
        }

        if (matchingValues.size > 1) {
            throw AssertionException(s"Property <$propertyIriInResult> of resource <$resourceIri> has more than one value with the IRI <$expectedValueIri>")
        }

        matchingValues.head
    }

    private def getResourceLastModificationDate(resourceIri: IRI): Option[Instant] = {
        Get(s"/v2/resourcespreview/${URLEncoder.encode(resourceIri, "UTF-8")}") ~> resourcesPath ~> check {
            assert(status == StatusCodes.OK, response.toString)
            val resource: JsonLDDocument = responseToJsonLDDocument(response)
            resource.maybeString(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate, stringFormatter.toInstant)
        }
    }

    private def checkLastModDate(resourceIri: IRI, maybePreviousLastModDate: Option[Instant], maybeUpdatedLastModDate: Option[Instant]): Unit = {
        maybeUpdatedLastModDate match {
            case Some(updatedLastModDate) =>
                maybePreviousLastModDate match {
                    case Some(previousLastModDate) => assert(updatedLastModDate.isAfter(previousLastModDate))
                    case None => ()
                }

            case None => throw AssertionException(s"Resource $resourceIri has no knora-api:lastModificationDate")
        }
    }

    private def getValue(resourceIri: IRI,
                         maybePreviousLastModDate: Option[Instant],
                         propertyIriForGravsearch: SmartIri,
                         propertyIriInResult: SmartIri,
                         expectedValueIri: IRI): JsonLDObject = {
        val resource = getResourceWithValues(
            resourceIri = resourceIri,
            propertyIrisForGravsearch = Seq(propertyIriForGravsearch)
        )

        println(resource.toPrettyString)

        val resourceLastModDate: Option[Instant] = resource.maybeString(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate, stringFormatter.toInstant)

        checkLastModDate(
            resourceIri = resourceIri,
            maybePreviousLastModDate = maybePreviousLastModDate,
            maybeUpdatedLastModDate = resourceLastModDate
        )

        getValueFromResource(
            resource = resource,
            propertyIriInResult = propertyIriInResult,
            expectedValueIri = expectedValueIri
        )
    }

    "Load test data" in {
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 360.seconds)
        Await.result(responderManager ? LoadOntologiesRequestV2(KnoraSystemInstances.Users.SystemUser), 30.seconds)
    }

    "The values v2 endpoint" should {

        "create an integer value" in {
            val resourceIri: IRI = "http://rdfh.ch/0001/a-thing"
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue = 4
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri)

            val jsonLdEntity =
                s"""
                  |{
                  |  "@id" : "$resourceIri",
                  |  "@type" : "anything:Thing",
                  |  "anything:hasInteger" : {
                  |    "@type" : "knora-api:IntValue",
                  |    "knora-api:intValueAsInt" : $intValue
                  |  },
                  |  "@context" : {
                  |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                  |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                  |  }
                  |}
                """.stripMargin

            Post("/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> valuesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
                val valueIri: IRI = responseJsonDoc.body.requireString(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
                intValueIri.set(valueIri)
                val valueType: SmartIri = responseJsonDoc.body.requireString(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
                valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.IntValue.toSmartIri)

                val savedValue: JsonLDObject = getValue(
                    resourceIri = resourceIri,
                    maybePreviousLastModDate = maybeResourceLastModDate,
                    propertyIriForGravsearch = propertyIri,
                    propertyIriInResult = propertyIri,
                    expectedValueIri = intValueIri.get
                )

                val intValueAsInt: Int = savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.IntValueAsInt).value
                intValueAsInt should ===(intValue)
            }
        }

        "create an integer value with custom permissions" in {
            val resourceIri: IRI = "http://rdfh.ch/0001/a-thing"
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue = 1
            val customPermissions = "M knora-base:Creator|V http://rdfh.ch/groups/0001/thing-searcher"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri)

            val jsonLdEntity =
                s"""
                   |{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInteger" : {
                   |    "@type" : "knora-api:IntValue",
                   |    "knora-api:intValueAsInt" : $intValue,
                   |    "knora-api:hasPermissions" : "$customPermissions"
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}
                """.stripMargin

            Post("/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> valuesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
                val valueIri: IRI = responseJsonDoc.body.requireString(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
                intValueIri.set(valueIri)
                val valueType: SmartIri = responseJsonDoc.body.requireString(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
                valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.IntValue.toSmartIri)

                val savedValue: JsonLDObject = getValue(
                    resourceIri = resourceIri,
                    maybePreviousLastModDate = maybeResourceLastModDate,
                    propertyIriForGravsearch = propertyIri,
                    propertyIriInResult = propertyIri,
                    expectedValueIri = intValueIri.get
                )

                val intValueAsInt: Int = savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.IntValueAsInt).value
                intValueAsInt should ===(intValue)
                val hasPermissions = savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions, stringFormatter.toSparqlEncodedString)
                hasPermissions should ===(customPermissions)
            }
        }
    }

}
