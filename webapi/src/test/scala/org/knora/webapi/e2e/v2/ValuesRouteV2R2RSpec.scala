package org.knora.webapi.e2e.v2

import java.net.URLEncoder
import java.time.Instant

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{HttpEntity, StatusCodes}
import akka.http.scaladsl.testkit.RouteTestTimeout
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.routing.v2.{ResourcesRouteV2, SearchRouteV2, ValuesRouteV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util._
import org.knora.webapi.util.jsonld._
import org.knora.webapi.util.search.SparqlQueryConstants

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt

class ValuesRouteV2R2RSpec extends R2RSpec {

    private val valuesPath = ValuesRouteV2.knoraApiPath(system, settings, log)
    private val resourcesPath = ResourcesRouteV2.knoraApiPath(system, settings, log)
    private val searchPath = SearchRouteV2.knoraApiPath(system, settings, log)

    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(new DurationInt(15).second)

    implicit val ec: ExecutionContextExecutor = system.dispatcher

    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val zeitglöckleinIri: IRI = "http://rdfh.ch/c5058f3a"
    private val aThingIri: IRI = "http://rdfh.ch/0001/a-thing"
    private val standardMappingIri: IRI = "http://rdfh.ch/standoff/mappings/StandardMapping"

    private val incunabulaUserEmail = SharedTestDataADM.incunabulaMemberUser.email
    private val anythingUserEmail = SharedTestDataADM.anythingUser1.email
    private val password = "test"

    private val intValueIri = new MutableTestIri
    private val commentValueIri = new MutableTestIri
    private val thingTextValueIri = new MutableTestIri
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


    override lazy val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/responders.v2.ValuesResponderV2Spec/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    private def getResourceWithValues(resourceIri: IRI,
                                      propertyIrisForGravsearch: Seq[SmartIri],
                                      userEmail: String): JsonLDDocument = {
        // Make a Gravsearch query from a template.
        val gravsearchQuery: String = queries.gravsearch.txt.getResourceWithSpecifiedProperties(
            resourceIri = resourceIri,
            propertyIris = propertyIrisForGravsearch
        ).toString()

        // Run the query.

        Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(userEmail, password)) ~> searchPath ~> check {
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
        val resourceIri: IRI = resource.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
        val propertyValues: JsonLDArray = getValuesFromResource(resource = resource, propertyIriInResult = propertyIriInResult)

        val matchingValues: Seq[JsonLDObject] = propertyValues.value.collect {
            case jsonLDObject: JsonLDObject if jsonLDObject.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri) == expectedValueIri => jsonLDObject
        }

        if (matchingValues.isEmpty) {
            throw AssertionException(s"Property <$propertyIriInResult> of resource <$resourceIri> does not have value <$expectedValueIri>")
        }

        if (matchingValues.size > 1) {
            throw AssertionException(s"Property <$propertyIriInResult> of resource <$resourceIri> has more than one value with the IRI <$expectedValueIri>")
        }

        matchingValues.head
    }

    private def parseResourceLastModificationDate(resource: JsonLDDocument): Option[Instant] = {
        resource.maybeObject(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate).map {
            jsonLDObject =>
                jsonLDObject.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.validateAndEscapeIri) should ===(OntologyConstants.Xsd.DateTimeStamp)
                jsonLDObject.requireStringWithValidation(JsonLDConstants.VALUE, stringFormatter.toInstant)
        }
    }

    private def getResourceLastModificationDate(resourceIri: IRI, userEmail: String): Option[Instant] = {
        Get(s"/v2/resourcespreview/${URLEncoder.encode(resourceIri, "UTF-8")}") ~> addCredentials(BasicHttpCredentials(userEmail, password)) ~> resourcesPath ~> check {
            assert(status == StatusCodes.OK, response.toString)
            val resource: JsonLDDocument = responseToJsonLDDocument(response)
            parseResourceLastModificationDate(resource)
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
                         expectedValueIri: IRI,
                         userEmail: String): JsonLDObject = {
        val resource = getResourceWithValues(
            resourceIri = resourceIri,
            propertyIrisForGravsearch = Seq(propertyIriForGravsearch),
            userEmail = userEmail
        )

        val resourceLastModDate: Option[Instant] = parseResourceLastModificationDate(resource)

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

    "The values v2 endpoint" should {

        "create an integer value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue: Int = 4
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

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
                val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
                intValueIri.set(valueIri)
                val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
                valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.IntValue.toSmartIri)

                val savedValue: JsonLDObject = getValue(
                    resourceIri = resourceIri,
                    maybePreviousLastModDate = maybeResourceLastModDate,
                    propertyIriForGravsearch = propertyIri,
                    propertyIriInResult = propertyIri,
                    expectedValueIri = intValueIri.get,
                    userEmail = anythingUserEmail
                )

                val intValueAsInt: Int = savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.IntValueAsInt).value
                intValueAsInt should ===(intValue)
            }
        }

        "create an integer value with custom permissions" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue: Int = 1
            val customPermissions: String = "M knora-base:Creator|V http://rdfh.ch/groups/0001/thing-searcher"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLDEntity =
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

            Post("/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> valuesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
                val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
                intValueIri.set(valueIri)
                val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
                valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.IntValue.toSmartIri)

                val savedValue: JsonLDObject = getValue(
                    resourceIri = resourceIri,
                    maybePreviousLastModDate = maybeResourceLastModDate,
                    propertyIriForGravsearch = propertyIri,
                    propertyIriInResult = propertyIri,
                    expectedValueIri = intValueIri.get,
                    userEmail = anythingUserEmail
                )

                val intValueAsInt: Int = savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.IntValueAsInt).value
                intValueAsInt should ===(intValue)
                val hasPermissions = savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions)
                hasPermissions should ===(customPermissions)
            }
        }

        "create a text value without standoff" in {
            val resourceIri: IRI = zeitglöckleinIri
            val valueAsString: String = "Comment 1a"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUserEmail)

            val jsonLDEntity =
                s"""
                  |{
                  |  "@id" : "$resourceIri",
                  |  "@type" : "incunabula:book",
                  |  "incunabula:book_comment" : {
                  |    "@type" : "knora-api:TextValue",
                  |    "knora-api:valueAsString" : "$valueAsString"
                  |  },
                  |  "@context" : {
                  |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                  |    "incunabula" : "http://0.0.0.0:3333/ontology/0803/incunabula/v2#"
                  |  }
                  |}
                """.stripMargin

            Post("/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)) ~> valuesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
                val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
                commentValueIri.set(valueIri)
                val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
                valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.TextValue.toSmartIri)

                val savedValue: JsonLDObject = getValue(
                    resourceIri = resourceIri,
                    maybePreviousLastModDate = maybeResourceLastModDate,
                    propertyIriForGravsearch = propertyIri,
                    propertyIriInResult = propertyIri,
                    expectedValueIri = commentValueIri.get,
                    userEmail = incunabulaUserEmail
                )

                val savedValueAsString: String = savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString)
                savedValueAsString should ===(valueAsString)
            }
        }

        "create a text value with standoff" in {
            val resourceIri: IRI = aThingIri

            val textValueAsXml: String =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<text>
                  |   This text links to another <a class="salsah-link" href="http://rdfh.ch/0001/another-thing">resource</a>.
                  |</text>
                """.stripMargin

            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, anythingUserEmail)

            val jsonLDEntity =
                s"""
                   |{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasText" : {
                   |    "@type" : "knora-api:TextValue",
                   |    "knora-api:textValueAsXml" : ${stringFormatter.toJsonEncodedString(textValueAsXml)},
                   |    "knora-api:textValueHasMapping" : {
                   |      "@id": "$standardMappingIri"
                   |    }
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}
                """.stripMargin

            Post("/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> valuesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
                val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
                thingTextValueIri.set(valueIri)
                val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
                valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.TextValue.toSmartIri)

                val savedValue: JsonLDObject = getValue(
                    resourceIri = resourceIri,
                    maybePreviousLastModDate = maybeResourceLastModDate,
                    propertyIriForGravsearch = propertyIri,
                    propertyIriInResult = propertyIri,
                    expectedValueIri = thingTextValueIri.get,
                    userEmail = anythingUserEmail
                )

                val savedTextValueAsXml: String = savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsXml)
                savedTextValueAsXml.contains("salsah-link") should ===(true)
            }
        }

        "create a text value with a comment" in {
            val resourceIri: IRI = zeitglöckleinIri
            val valueAsString: String = "this is a text value that has a comment"
            val valueHasComment: String = "this is a comment"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUserEmail)

            val jsonLDEntity =
                s"""
                   |{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "incunabula:book",
                   |  "incunabula:book_comment" : {
                   |    "@type" : "knora-api:TextValue",
                   |    "knora-api:valueAsString" : "$valueAsString",
                   |    "knora-api:valueHasComment" : "$valueHasComment"
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "incunabula" : "http://0.0.0.0:3333/ontology/0803/incunabula/v2#"
                   |  }
                   |}
                """.stripMargin

            Post("/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)) ~> valuesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
                val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
                commentValueIri.set(valueIri)
                val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
                valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.TextValue.toSmartIri)

                val savedValue: JsonLDObject = getValue(
                    resourceIri = resourceIri,
                    maybePreviousLastModDate = maybeResourceLastModDate,
                    propertyIriForGravsearch = propertyIri,
                    propertyIriInResult = propertyIri,
                    expectedValueIri = commentValueIri.get,
                    userEmail = incunabulaUserEmail
                )

                val savedValueAsString: String = savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString)
                savedValueAsString should ===(valueAsString)
                val savedValueHasComment: String = savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.ValueHasComment)
                savedValueHasComment should ===(valueHasComment)
            }
        }

        "not create an empty text value" in {
            val resourceIri: IRI = zeitglöckleinIri
            val valueAsString: String = ""

            val jsonLDEntity =
                s"""
                   |{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "incunabula:book",
                   |  "incunabula:book_comment" : {
                   |    "@type" : "knora-api:TextValue",
                   |    "knora-api:valueAsString" : "$valueAsString"
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "incunabula" : "http://0.0.0.0:3333/ontology/0803/incunabula/v2#"
                   |  }
                   |}
                """.stripMargin

            Post("/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)) ~> valuesPath ~> check {
                assert(status == StatusCodes.BadRequest, response.toString)
            }
        }

        "create a decimal value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
            val decimalValueAsDecimal = BigDecimal(4.3)
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""
                   |{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasDecimal" : {
                   |    "@type" : "knora-api:DecimalValue",
                   |    "knora-api:decimalValueAsDecimal" : {
                   |      "@type" : "xsd:decimal",
                   |      "@value" : "$decimalValueAsDecimal"
                   |    }
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}
                """.stripMargin

            Post("/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> valuesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
                val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
                decimalValueIri.set(valueIri)
                val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
                valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.DecimalValue.toSmartIri)

                val savedValue: JsonLDObject = getValue(
                    resourceIri = resourceIri,
                    maybePreviousLastModDate = maybeResourceLastModDate,
                    propertyIriForGravsearch = propertyIri,
                    propertyIriInResult = propertyIri,
                    expectedValueIri = decimalValueIri.get,
                    userEmail = anythingUserEmail
                )

                val savedDecimalValueAsDecimal: BigDecimal = savedValue.requireDatatypeValueInObject(
                    key = OntologyConstants.KnoraApiV2WithValueObjects.DecimalValueAsDecimal,
                    expectedDatatype = OntologyConstants.Xsd.Decimal.toSmartIri,
                    validationFun = stringFormatter.validateBigDecimal
                )

                savedDecimalValueAsDecimal should ===(decimalValueAsDecimal)
            }
        }
    }
}
