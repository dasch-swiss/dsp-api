package org.knora.webapi.e2e.v2

import java.io.File
import java.net.URLEncoder
import java.time.Instant

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.testkit.RouteTestTimeout
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util._
import org.knora.webapi.util.jsonld._
import org.knora.webapi.util.search.SparqlQueryConstants

class ValuesRouteV2E2ESpec extends E2ESpec {

    private implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout)

    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val zeitglöckleinIri: IRI = "http://rdfh.ch/c5058f3a"
    private val generationeIri = "http://rdfh.ch/c3f913666f"
    private val aThingIri: IRI = "http://rdfh.ch/0001/a-thing"
    private val standardMappingIri: IRI = "http://rdfh.ch/standoff/mappings/StandardMapping"

    private val incunabulaUserEmail = SharedTestDataADM.incunabulaMemberUser.email
    private val anythingUserEmail = SharedTestDataADM.anythingUser1.email
    private val password = "test"

    private val intValueIri = new MutableTestIri
    private val commentValueIri = new MutableTestIri
    private val thingTextValueIri = new MutableTestIri
    private val thingTextValueWithEscapeIri = new MutableTestIri
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

        val request = Post(baseApiUrl + "/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(userEmail, password))
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status == StatusCodes.OK, response.toString)
        responseToJsonLDDocument(response)
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
        val request = Get(baseApiUrl + s"/v2/resourcespreview/${URLEncoder.encode(resourceIri, "UTF-8")}") ~> addCredentials(BasicHttpCredentials(userEmail, password))
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status == StatusCodes.OK, response.toString)
        val resource: JsonLDDocument = responseToJsonLDDocument(response)
        parseResourceLastModificationDate(resource)
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
        val resource: JsonLDDocument = getResourceWithValues(
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
                s"""{
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
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
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

            val intValueAsInt: Int = savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.IntValueAsInt)
            intValueAsInt should ===(intValue)
        }

        "not create an integer value if @id is given" in {
            val resourceIri: IRI = aThingIri
            val intValue: Int = 4

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInteger" : {
                   |    "@id" : "${intValueIri.get}",
                   |    "@type" : "knora-api:IntValue",
                   |    "knora-api:intValueAsInt" : $intValue
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.BadRequest, response.toString)
        }

        "create an integer value with custom permissions" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue: Int = 1
            val customPermissions: String = "CR knora-base:Creator|V http://rdfh.ch/groups/0001/thing-searcher"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLDEntity =
                s"""{
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
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
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

            val intValueAsInt: Int = savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.IntValueAsInt)
            intValueAsInt should ===(intValue)
            val hasPermissions = savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions)
            hasPermissions should ===(customPermissions)
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

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
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

        "create a text value with standoff" in {
            val resourceIri: IRI = aThingIri

            val textValueAsXml: String =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<text>
                  |   This text links to another <a class="salsah-link" href="http://rdfh.ch/0001/another-thing">resource</a>.
                  |</text>
                """.stripMargin

            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(aThingIri, anythingUserEmail)

            val jsonLDEntity =
                s"""{
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
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
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

        "create a text value with standoff containing a URL" in {
            val resourceIri: IRI = aThingIri

            val textValueAsXml: String =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<text>
                  |   This text links to <a href="http://www.knora.org">a web site</a>.
                  |</text>
                """.stripMargin

            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(aThingIri, anythingUserEmail)

            val jsonLDEntity =
                s"""{
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
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.TextValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = valueIri,
                userEmail = anythingUserEmail
            )

            val savedTextValueAsXml: String = savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsXml)
            savedTextValueAsXml.contains("href") should ===(true)
        }

        "create a text value with standoff containing escaped text" in {
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(aThingIri, anythingUserEmail)
            val jsonLDEntity = FileUtil.readTextFile(new File("src/test/resources/test-data/valuesE2EV2/CreateValueWithEscape.jsonld"))
            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            thingTextValueWithEscapeIri.set(valueIri)
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri

            val savedValue: JsonLDObject = getValue(
                resourceIri = aThingIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = valueIri,
                userEmail = anythingUserEmail
            )

            val savedTextValueAsXml: String = savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsXml)

            val expectedText =
                """<p>
                  | test</p>""".stripMargin

            assert(savedTextValueAsXml.contains(expectedText))
        }

        "create a text value with a comment" in {
            val resourceIri: IRI = zeitglöckleinIri
            val valueAsString: String = "this is a text value that has a comment"
            val valueHasComment: String = "this is a comment"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUserEmail)

            val jsonLDEntity =
                s"""{
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
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
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

        "not create an empty text value" in {
            val resourceIri: IRI = zeitglöckleinIri
            val valueAsString: String = ""

            val jsonLDEntity =
                s"""{
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
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.BadRequest, response.toString)
        }

        "create a decimal value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
            val decimalValueAsDecimal = BigDecimal(4.3)
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
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
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
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

        "create a date value representing a range with day precision" in {
            val resourceIri: IRI = aThingIri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2018
            val dateValueHasStartMonth = 10
            val dateValueHasStartDay = 5
            val dateValueHasStartEra = "CE"
            val dateValueHasEndYear = 2018
            val dateValueHasEndMonth = 10
            val dateValueHasEndDay = 6
            val dateValueHasEndEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasDate" : {
                   |    "@type" : "knora-api:DateValue",
                   |    "knora-api:dateValueHasCalendar" : "$dateValueHasCalendar",
                   |    "knora-api:dateValueHasStartYear" : $dateValueHasStartYear,
                   |    "knora-api:dateValueHasStartMonth" : $dateValueHasStartMonth,
                   |    "knora-api:dateValueHasStartDay" : $dateValueHasStartDay,
                   |    "knora-api:dateValueHasStartEra" : "$dateValueHasStartEra",
                   |    "knora-api:dateValueHasEndYear" : $dateValueHasEndYear,
                   |    "knora-api:dateValueHasEndMonth" : $dateValueHasEndMonth,
                   |    "knora-api:dateValueHasEndDay" : $dateValueHasEndDay,
                   |    "knora-api:dateValueHasEndEra" : "$dateValueHasEndEra"
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString) should ===("GREGORIAN:2018-10-05 CE:2018-10-06 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartMonth) should ===(dateValueHasStartMonth)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartDay) should ===(dateValueHasStartDay)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndYear) should ===(dateValueHasEndYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndMonth) should ===(dateValueHasEndMonth)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndDay) should ===(dateValueHasEndDay)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndEra) should ===(dateValueHasEndEra)
        }

        "create a date value representing a range with month precision" in {
            val resourceIri: IRI = aThingIri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2018
            val dateValueHasStartMonth = 10
            val dateValueHasStartEra = "CE"
            val dateValueHasEndYear = 2018
            val dateValueHasEndMonth = 11
            val dateValueHasEndEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasDate" : {
                   |    "@type" : "knora-api:DateValue",
                   |    "knora-api:dateValueHasCalendar" : "$dateValueHasCalendar",
                   |    "knora-api:dateValueHasStartYear" : $dateValueHasStartYear,
                   |    "knora-api:dateValueHasStartMonth" : $dateValueHasStartMonth,
                   |    "knora-api:dateValueHasStartEra" : "$dateValueHasStartEra",
                   |    "knora-api:dateValueHasEndYear" : $dateValueHasEndYear,
                   |    "knora-api:dateValueHasEndMonth" : $dateValueHasEndMonth,
                   |    "knora-api:dateValueHasEndEra" : "$dateValueHasEndEra"
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString) should ===("GREGORIAN:2018-10 CE:2018-11 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartMonth) should ===(dateValueHasStartMonth)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndYear) should ===(dateValueHasEndYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndMonth) should ===(dateValueHasEndMonth)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndEra) should ===(dateValueHasEndEra)
        }

        "create a date value representing a range with year precision" in {
            val resourceIri: IRI = aThingIri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2018
            val dateValueHasStartEra = "CE"
            val dateValueHasEndYear = 2019
            val dateValueHasEndEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasDate" : {
                   |    "@type" : "knora-api:DateValue",
                   |    "knora-api:dateValueHasCalendar" : "$dateValueHasCalendar",
                   |    "knora-api:dateValueHasStartYear" : $dateValueHasStartYear,
                   |    "knora-api:dateValueHasStartEra" : "$dateValueHasStartEra",
                   |    "knora-api:dateValueHasEndYear" : $dateValueHasEndYear,
                   |    "knora-api:dateValueHasEndEra" : "$dateValueHasEndEra"
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString) should ===("GREGORIAN:2018 CE:2019 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartMonth) should ===(None)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndYear) should ===(dateValueHasEndYear)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndMonth) should ===(None)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndEra) should ===(dateValueHasEndEra)
        }


        "create a date value representing a single date with day precision" in {
            val resourceIri: IRI = aThingIri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2018
            val dateValueHasStartMonth = 10
            val dateValueHasStartDay = 5
            val dateValueHasStartEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasDate" : {
                   |    "@type" : "knora-api:DateValue",
                   |    "knora-api:dateValueHasCalendar" : "$dateValueHasCalendar",
                   |    "knora-api:dateValueHasStartYear" : $dateValueHasStartYear,
                   |    "knora-api:dateValueHasStartMonth" : $dateValueHasStartMonth,
                   |    "knora-api:dateValueHasStartDay" : $dateValueHasStartDay,
                   |    "knora-api:dateValueHasStartEra" : "$dateValueHasStartEra",
                   |    "knora-api:dateValueHasEndYear" : $dateValueHasStartYear,
                   |    "knora-api:dateValueHasEndMonth" : $dateValueHasStartMonth,
                   |    "knora-api:dateValueHasEndDay" : $dateValueHasStartDay,
                   |    "knora-api:dateValueHasEndEra" : "$dateValueHasStartEra"
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString) should ===("GREGORIAN:2018-10-05 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartMonth) should ===(dateValueHasStartMonth)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartDay) should ===(dateValueHasStartDay)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndMonth) should ===(dateValueHasStartMonth)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndDay) should ===(dateValueHasStartDay)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndEra) should ===(dateValueHasStartEra)
        }

        "create a date value representing a single date with month precision" in {
            val resourceIri: IRI = aThingIri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2018
            val dateValueHasStartMonth = 10
            val dateValueHasStartEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasDate" : {
                   |    "@type" : "knora-api:DateValue",
                   |    "knora-api:dateValueHasCalendar" : "$dateValueHasCalendar",
                   |    "knora-api:dateValueHasStartYear" : $dateValueHasStartYear,
                   |    "knora-api:dateValueHasStartMonth" : $dateValueHasStartMonth,
                   |    "knora-api:dateValueHasStartEra" : "$dateValueHasStartEra",
                   |    "knora-api:dateValueHasEndYear" : $dateValueHasStartYear,
                   |    "knora-api:dateValueHasEndMonth" : $dateValueHasStartMonth,
                   |    "knora-api:dateValueHasEndEra" : "$dateValueHasStartEra"
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString) should ===("GREGORIAN:2018-10 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartMonth) should ===(dateValueHasStartMonth)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndMonth) should ===(dateValueHasStartMonth)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndEra) should ===(dateValueHasStartEra)
        }

        "create a date value representing a single date with year precision" in {
            val resourceIri: IRI = aThingIri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2018
            val dateValueHasStartEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasDate" : {
                   |    "@type" : "knora-api:DateValue",
                   |    "knora-api:dateValueHasCalendar" : "$dateValueHasCalendar",
                   |    "knora-api:dateValueHasStartYear" : $dateValueHasStartYear,
                   |    "knora-api:dateValueHasStartEra" : "$dateValueHasStartEra",
                   |    "knora-api:dateValueHasEndYear" : $dateValueHasStartYear,
                   |    "knora-api:dateValueHasEndEra" : "$dateValueHasStartEra"
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString) should ===("GREGORIAN:2018 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartMonth) should ===(None)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndYear) should ===(dateValueHasStartYear)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndMonth) should ===(None)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndEra) should ===(dateValueHasStartEra)
        }

        "create a boolean value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri
            val booleanValue: Boolean = true
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasBoolean" : {
                   |    "@type" : "knora-api:BooleanValue",
                   |    "knora-api:booleanValueAsBoolean" : $booleanValue
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            booleanValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.BooleanValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = booleanValueIri.get,
                userEmail = anythingUserEmail
            )

            val booleanValueAsBoolean: Boolean = savedValue.requireBoolean(OntologyConstants.KnoraApiV2WithValueObjects.BooleanValueAsBoolean)
            booleanValueAsBoolean should ===(booleanValue)
        }

        "create a geometry value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
            val geometryValue = """{"status":"active","lineColor":"#ff3333","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasGeometry" : {
                   |    "@type" : "knora-api:GeomValue",
                   |    "knora-api:geometryValueAsGeometry" : ${stringFormatter.toJsonEncodedString(geometryValue)}
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            geometryValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.GeomValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = geometryValueIri.get,
                userEmail = anythingUserEmail
            )

            val geometryValueAsGeometry: String = savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.GeometryValueAsGeometry)
            geometryValueAsGeometry should ===(geometryValue)
        }

        "create an interval value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
            val intervalStart = BigDecimal("1.2")
            val intervalEnd = BigDecimal("3.4")
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInterval" : {
                   |    "@type" : "knora-api:IntervalValue",
                   |    "knora-api:intervalValueHasStart" : {
                   |      "@type" : "xsd:decimal",
                   |      "@value" : "$intervalStart"
                   |    },
                   |    "knora-api:intervalValueHasEnd" : {
                   |      "@type" : "xsd:decimal",
                   |      "@value" : "$intervalEnd"
                   |    }
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            intervalValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.IntervalValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = intervalValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedIntervalValueHasStart: BigDecimal = savedValue.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2WithValueObjects.IntervalValueHasStart,
                expectedDatatype = OntologyConstants.Xsd.Decimal.toSmartIri,
                validationFun = stringFormatter.validateBigDecimal
            )

            savedIntervalValueHasStart should ===(intervalStart)

            val savedIntervalValueHasEnd: BigDecimal = savedValue.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2WithValueObjects.IntervalValueHasEnd,
                expectedDatatype = OntologyConstants.Xsd.Decimal.toSmartIri,
                validationFun = stringFormatter.validateBigDecimal
            )

            savedIntervalValueHasEnd should ===(intervalEnd)
        }

        "create a list value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
            val listNode = "http://rdfh.ch/lists/0001/treeList03"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasListItem" : {
                   |    "@type" : "knora-api:ListValue",
                   |    "knora-api:listValueAsListNode" : {
                   |      "@id" : "$listNode"
                   |    }
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            listValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.ListValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = listValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedListValueHasListNode: IRI = savedValue.requireIriInObject(OntologyConstants.KnoraApiV2WithValueObjects.ListValueAsListNode, stringFormatter.validateAndEscapeIri)
            savedListValueHasListNode should ===(listNode)
        }

        "create a color value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
            val color = "#ff3333"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasColor" : {
                   |    "@type" : "knora-api:ColorValue",
                   |    "knora-api:colorValueAsColor" : "$color"
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            colorValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.ColorValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = colorValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedColor: String = savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.ColorValueAsColor)
            savedColor should ===(color)
        }

        "create a URI value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
            val uri = "https://www.knora.org"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasUri" : {
                   |    "@type" : "knora-api:UriValue",
                   |    "knora-api:uriValueAsUri" : {
                   |      "@type" : "xsd:anyURI",
                   |      "@value" : "$uri"
                   |    }
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            uriValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.UriValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = uriValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedUri: IRI = savedValue.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2WithValueObjects.UriValueAsUri,
                expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
                validationFun = stringFormatter.validateAndEscapeIri
            )

            savedUri should ===(uri)
        }

        "create a geoname value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
            val geonameCode = "2661604"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasGeoname" : {
                   |    "@type" : "knora-api:GeonameValue",
                   |    "knora-api:geonameValueAsGeonameCode" : "$geonameCode"
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            geonameValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.GeonameValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = geonameValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedGeonameCode: String = savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.GeonameValueAsGeonameCode)
            savedGeonameCode should ===(geonameCode)
        }

        "create a link between two resources" in {
            val resourceIri: IRI = "http://rdfh.ch/cb1a74e3e2f6"
            val linkPropertyIri: SmartIri = OntologyConstants.KnoraApiV2WithValueObjects.HasLinkTo.toSmartIri
            val linkValuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2WithValueObjects.HasLinkToValue.toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "knora-api:LinkObj",
                   |  "knora-api:hasLinkToValue" : {
                   |    "@type" : "knora-api:LinkValue",
                   |    "knora-api:linkValueHasTargetIri" : {
                   |      "@id" : "$zeitglöckleinIri"
                   |    }
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "incunabula" : "http://0.0.0.0:3333/ontology/0803/incunabula/v2#"
                   |  }
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)

            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            linkValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.LinkValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = linkPropertyIri,
                propertyIriInResult = linkValuePropertyIri,
                expectedValueIri = linkValueIri.get,
                userEmail = incunabulaUserEmail
            )

            val savedTarget: JsonLDObject = savedValue.requireObject(OntologyConstants.KnoraApiV2WithValueObjects.LinkValueHasTarget)
            val savedTargetIri: IRI = savedTarget.requireString(JsonLDConstants.ID)
            savedTargetIri should ===(zeitglöckleinIri)
        }

        "update an integer value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue: Int = 5
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInteger" : {
                   |    "@id" : "${intValueIri.get}",
                   |    "@type" : "knora-api:IntValue",
                   |    "knora-api:intValueAsInt" : $intValue
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
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

            val intValueAsInt: Int = savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.IntValueAsInt)
            intValueAsInt should ===(intValue)
        }

        "update an integer value with custom permissions" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue: Int = 6
            val customPermissions: String = "CR http://rdfh.ch/groups/0001/thing-searcher"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLDEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInteger" : {
                   |    "@id" : "${intValueIri.get}",
                   |    "@type" : "knora-api:IntValue",
                   |    "knora-api:intValueAsInt" : $intValue,
                   |    "knora-api:hasPermissions" : "$customPermissions"
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
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

            val intValueAsInt: Int = savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.IntValueAsInt)
            intValueAsInt should ===(intValue)
            val hasPermissions = savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions)
            hasPermissions should ===(customPermissions)
        }

        "update a text value without standoff" in {
            val resourceIri: IRI = zeitglöckleinIri
            val valueAsString: String = "Comment 1a updated"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUserEmail)

            val jsonLDEntity =
                s"""
                   |{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "incunabula:book",
                   |  "incunabula:book_comment" : {
                   |    "@id" : "${commentValueIri.get}",
                   |    "@type" : "knora-api:TextValue",
                   |    "knora-api:valueAsString" : "$valueAsString"
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "incunabula" : "http://0.0.0.0:3333/ontology/0803/incunabula/v2#"
                   |  }
                   |}
                """.stripMargin

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
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

        "update a text value with standoff" in {
            val resourceIri: IRI = aThingIri

            val textValueAsXml: String =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<text>
                  |   This updated text links to another <a class="salsah-link" href="http://rdfh.ch/0001/another-thing">resource</a>.
                  |</text>
                """.stripMargin

            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(aThingIri, anythingUserEmail)

            val jsonLDEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasText" : {
                   |    "@id" : "${thingTextValueIri.get}",
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
                   |}""".stripMargin

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
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
            savedTextValueAsXml.contains("updated text") should ===(true)
            savedTextValueAsXml.contains("salsah-link") should ===(true)
        }

        "update a text value with standoff containing escaped text" in {
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(aThingIri, anythingUserEmail)
            val jsonLDEntity = FileUtil.readTextFile(new File("src/test/resources/test-data/valuesE2EV2/UpdateValueWithEscape.jsonld"))
            val jsonLDEntityWithResourceValueIri = jsonLDEntity.replace("VALUE_IRI", thingTextValueWithEscapeIri.get)
            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntityWithResourceValueIri)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            thingTextValueWithEscapeIri.set(valueIri)
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri

            val savedValue: JsonLDObject = getValue(
                resourceIri = aThingIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = valueIri,
                userEmail = anythingUserEmail
            )

            val savedTextValueAsXml: String = savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsXml)

            val expectedText =
                """<p>
                  | test update</p>""".stripMargin

            assert(savedTextValueAsXml.contains(expectedText))
        }

        "update a text value with a comment" in {
            val resourceIri: IRI = zeitglöckleinIri
            val valueAsString: String = "this is a text value that has an updated comment"
            val valueHasComment: String = "this is an updated comment"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUserEmail)

            val jsonLDEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "incunabula:book",
                   |  "incunabula:book_comment" : {
                   |    "@id" : "${commentValueIri.get}",
                   |    "@type" : "knora-api:TextValue",
                   |    "knora-api:valueAsString" : "$valueAsString",
                   |    "knora-api:valueHasComment" : "$valueHasComment"
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "incunabula" : "http://0.0.0.0:3333/ontology/0803/incunabula/v2#"
                   |  }
                   |}""".stripMargin

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
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

        "not update a text value so it's empty" in {
            val resourceIri: IRI = zeitglöckleinIri
            val valueAsString: String = ""

            val jsonLDEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "incunabula:book",
                   |  "incunabula:book_comment" : {
                   |    "@id" : "${commentValueIri.get}",
                   |    "@type" : "knora-api:TextValue",
                   |    "knora-api:valueAsString" : "$valueAsString"
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "incunabula" : "http://0.0.0.0:3333/ontology/0803/incunabula/v2#"
                   |  }
                   |}""".stripMargin

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.BadRequest, response.toString)
        }

        "update a date value representing a range with day precision" in {
            val resourceIri: IRI = aThingIri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2018
            val dateValueHasStartMonth = 10
            val dateValueHasStartDay = 5
            val dateValueHasStartEra = "CE"
            val dateValueHasEndYear = 2018
            val dateValueHasEndMonth = 12
            val dateValueHasEndDay = 6
            val dateValueHasEndEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasDate" : {
                   |    "@id" : "${dateValueIri.get}",
                   |    "@type" : "knora-api:DateValue",
                   |    "knora-api:dateValueHasCalendar" : "$dateValueHasCalendar",
                   |    "knora-api:dateValueHasStartYear" : $dateValueHasStartYear,
                   |    "knora-api:dateValueHasStartMonth" : $dateValueHasStartMonth,
                   |    "knora-api:dateValueHasStartDay" : $dateValueHasStartDay,
                   |    "knora-api:dateValueHasStartEra" : "$dateValueHasStartEra",
                   |    "knora-api:dateValueHasEndYear" : $dateValueHasEndYear,
                   |    "knora-api:dateValueHasEndMonth" : $dateValueHasEndMonth,
                   |    "knora-api:dateValueHasEndDay" : $dateValueHasEndDay,
                   |    "knora-api:dateValueHasEndEra" : "$dateValueHasEndEra"
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString) should ===("GREGORIAN:2018-10-05 CE:2018-12-06 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartMonth) should ===(dateValueHasStartMonth)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartDay) should ===(dateValueHasStartDay)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndYear) should ===(dateValueHasEndYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndMonth) should ===(dateValueHasEndMonth)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndDay) should ===(dateValueHasEndDay)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndEra) should ===(dateValueHasEndEra)
        }

        "update a date value representing a range with month precision" in {
            val resourceIri: IRI = aThingIri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2018
            val dateValueHasStartMonth = 9
            val dateValueHasStartEra = "CE"
            val dateValueHasEndYear = 2018
            val dateValueHasEndMonth = 12
            val dateValueHasEndEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasDate" : {
                   |    "@id" : "${dateValueIri.get}",
                   |    "@type" : "knora-api:DateValue",
                   |    "knora-api:dateValueHasCalendar" : "$dateValueHasCalendar",
                   |    "knora-api:dateValueHasStartYear" : $dateValueHasStartYear,
                   |    "knora-api:dateValueHasStartMonth" : $dateValueHasStartMonth,
                   |    "knora-api:dateValueHasStartEra" : "$dateValueHasStartEra",
                   |    "knora-api:dateValueHasEndYear" : $dateValueHasEndYear,
                   |    "knora-api:dateValueHasEndMonth" : $dateValueHasEndMonth,
                   |    "knora-api:dateValueHasEndEra" : "$dateValueHasEndEra"
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString) should ===("GREGORIAN:2018-09 CE:2018-12 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartMonth) should ===(dateValueHasStartMonth)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndYear) should ===(dateValueHasEndYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndMonth) should ===(dateValueHasEndMonth)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndEra) should ===(dateValueHasEndEra)
        }

        "update a date value representing a range with year precision" in {
            val resourceIri: IRI = aThingIri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2018
            val dateValueHasStartEra = "CE"
            val dateValueHasEndYear = 2020
            val dateValueHasEndEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasDate" : {
                   |    "@id" : "${dateValueIri.get}",
                   |    "@type" : "knora-api:DateValue",
                   |    "knora-api:dateValueHasCalendar" : "$dateValueHasCalendar",
                   |    "knora-api:dateValueHasStartYear" : $dateValueHasStartYear,
                   |    "knora-api:dateValueHasStartEra" : "$dateValueHasStartEra",
                   |    "knora-api:dateValueHasEndYear" : $dateValueHasEndYear,
                   |    "knora-api:dateValueHasEndEra" : "$dateValueHasEndEra"
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString) should ===("GREGORIAN:2018 CE:2020 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartMonth) should ===(None)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndYear) should ===(dateValueHasEndYear)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndMonth) should ===(None)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndEra) should ===(dateValueHasEndEra)
        }

        "update a date value representing a single date with day precision" in {
            val resourceIri: IRI = aThingIri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2018
            val dateValueHasStartMonth = 10
            val dateValueHasStartDay = 6
            val dateValueHasStartEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasDate" : {
                   |    "@id" : "${dateValueIri.get}",
                   |    "@type" : "knora-api:DateValue",
                   |    "knora-api:dateValueHasCalendar" : "$dateValueHasCalendar",
                   |    "knora-api:dateValueHasStartYear" : $dateValueHasStartYear,
                   |    "knora-api:dateValueHasStartMonth" : $dateValueHasStartMonth,
                   |    "knora-api:dateValueHasStartDay" : $dateValueHasStartDay,
                   |    "knora-api:dateValueHasStartEra" : "$dateValueHasStartEra",
                   |    "knora-api:dateValueHasEndYear" : $dateValueHasStartYear,
                   |    "knora-api:dateValueHasEndMonth" : $dateValueHasStartMonth,
                   |    "knora-api:dateValueHasEndDay" : $dateValueHasStartDay,
                   |    "knora-api:dateValueHasEndEra" : "$dateValueHasStartEra"
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString) should ===("GREGORIAN:2018-10-06 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartMonth) should ===(dateValueHasStartMonth)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartDay) should ===(dateValueHasStartDay)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndMonth) should ===(dateValueHasStartMonth)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndDay) should ===(dateValueHasStartDay)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndEra) should ===(dateValueHasStartEra)
        }

        "update a date value representing a single date with month precision" in {
            val resourceIri: IRI = aThingIri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2018
            val dateValueHasStartMonth = 7
            val dateValueHasStartEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasDate" : {
                   |    "@id" : "${dateValueIri.get}",
                   |    "@type" : "knora-api:DateValue",
                   |    "knora-api:dateValueHasCalendar" : "$dateValueHasCalendar",
                   |    "knora-api:dateValueHasStartYear" : $dateValueHasStartYear,
                   |    "knora-api:dateValueHasStartMonth" : $dateValueHasStartMonth,
                   |    "knora-api:dateValueHasStartEra" : "$dateValueHasStartEra",
                   |    "knora-api:dateValueHasEndYear" : $dateValueHasStartYear,
                   |    "knora-api:dateValueHasEndMonth" : $dateValueHasStartMonth,
                   |    "knora-api:dateValueHasEndEra" : "$dateValueHasStartEra"
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString) should ===("GREGORIAN:2018-07 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartMonth) should ===(dateValueHasStartMonth)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndMonth) should ===(dateValueHasStartMonth)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndEra) should ===(dateValueHasStartEra)
        }

        "update a date value representing a single date with year precision" in {
            val resourceIri: IRI = aThingIri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2019
            val dateValueHasStartEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasDate" : {
                   |    "@id" : "${dateValueIri.get}",
                   |    "@type" : "knora-api:DateValue",
                   |    "knora-api:dateValueHasCalendar" : "$dateValueHasCalendar",
                   |    "knora-api:dateValueHasStartYear" : $dateValueHasStartYear,
                   |    "knora-api:dateValueHasStartEra" : "$dateValueHasStartEra",
                   |    "knora-api:dateValueHasEndYear" : $dateValueHasStartYear,
                   |    "knora-api:dateValueHasEndEra" : "$dateValueHasStartEra"
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString) should ===("GREGORIAN:2019 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartMonth) should ===(None)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndYear) should ===(dateValueHasStartYear)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndMonth) should ===(None)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndEra) should ===(dateValueHasStartEra)
        }

        "update a boolean value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri
            val booleanValue: Boolean = false
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasBoolean" : {
                   |    "@id" : "${booleanValueIri.get}",
                   |    "@type" : "knora-api:BooleanValue",
                   |    "knora-api:booleanValueAsBoolean" : $booleanValue
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            booleanValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.BooleanValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = booleanValueIri.get,
                userEmail = anythingUserEmail
            )

            val booleanValueAsBoolean: Boolean = savedValue.requireBoolean(OntologyConstants.KnoraApiV2WithValueObjects.BooleanValueAsBoolean)
            booleanValueAsBoolean should ===(booleanValue)
        }

        "update a geometry value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
            val geometryValue = """{"status":"active","lineColor":"#ff3344","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasGeometry" : {
                   |    "@id" : "${geometryValueIri.get}",
                   |    "@type" : "knora-api:GeomValue",
                   |    "knora-api:geometryValueAsGeometry" : ${stringFormatter.toJsonEncodedString(geometryValue)}
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            geometryValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.GeomValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = geometryValueIri.get,
                userEmail = anythingUserEmail
            )

            val geometryValueAsGeometry: String = savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.GeometryValueAsGeometry)
            geometryValueAsGeometry should ===(geometryValue)
        }

        "update an interval value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
            val intervalStart = BigDecimal("5.6")
            val intervalEnd = BigDecimal("7.8")
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInterval" : {
                   |    "@id" : "${intervalValueIri.get}",
                   |    "@type" : "knora-api:IntervalValue",
                   |    "knora-api:intervalValueHasStart" : {
                   |      "@type" : "xsd:decimal",
                   |      "@value" : "$intervalStart"
                   |    },
                   |    "knora-api:intervalValueHasEnd" : {
                   |      "@type" : "xsd:decimal",
                   |      "@value" : "$intervalEnd"
                   |    }
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            intervalValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.IntervalValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = intervalValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedIntervalValueHasStart: BigDecimal = savedValue.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2WithValueObjects.IntervalValueHasStart,
                expectedDatatype = OntologyConstants.Xsd.Decimal.toSmartIri,
                validationFun = stringFormatter.validateBigDecimal
            )

            savedIntervalValueHasStart should ===(intervalStart)

            val savedIntervalValueHasEnd: BigDecimal = savedValue.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2WithValueObjects.IntervalValueHasEnd,
                expectedDatatype = OntologyConstants.Xsd.Decimal.toSmartIri,
                validationFun = stringFormatter.validateBigDecimal
            )

            savedIntervalValueHasEnd should ===(intervalEnd)
        }

        "update a list value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
            val listNode = "http://rdfh.ch/lists/0001/treeList02"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasListItem" : {
                   |    "@id" : "${listValueIri.get}",
                   |    "@type" : "knora-api:ListValue",
                   |    "knora-api:listValueAsListNode" : {
                   |      "@id" : "$listNode"
                   |    }
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            listValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.ListValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = listValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedListValueHasListNode: IRI = savedValue.requireIriInObject(OntologyConstants.KnoraApiV2WithValueObjects.ListValueAsListNode, stringFormatter.validateAndEscapeIri)
            savedListValueHasListNode should ===(listNode)
        }

        "update a color value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
            val color = "#ff3344"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasColor" : {
                   |    "@id" : "${colorValueIri.get}",
                   |    "@type" : "knora-api:ColorValue",
                   |    "knora-api:colorValueAsColor" : "$color"
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            colorValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.ColorValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = colorValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedColor: String = savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.ColorValueAsColor)
            savedColor should ===(color)
        }

        "update a URI value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
            val uri = "https://docs.knora.org"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasUri" : {
                   |    "@id" : "${uriValueIri.get}",
                   |    "@type" : "knora-api:UriValue",
                   |    "knora-api:uriValueAsUri" : {
                   |      "@type" : "xsd:anyURI",
                   |      "@value" : "$uri"
                   |    }
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            uriValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.UriValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = uriValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedUri: IRI = savedValue.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2WithValueObjects.UriValueAsUri,
                expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
                validationFun = stringFormatter.validateAndEscapeIri
            )

            savedUri should ===(uri)
        }

        "update a geoname value" in {
            val resourceIri: IRI = aThingIri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
            val geonameCode = "2988507"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasGeoname" : {
                   |    "@id" : "${geonameValueIri.get}",
                   |    "@type" : "knora-api:GeonameValue",
                   |    "knora-api:geonameValueAsGeonameCode" : "$geonameCode"
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            geonameValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.GeonameValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = geonameValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedGeonameCode: String = savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.GeonameValueAsGeonameCode)
            savedGeonameCode should ===(geonameCode)
        }

        "update a link between two resources" in {
            val resourceIri: IRI = "http://rdfh.ch/cb1a74e3e2f6"
            val linkPropertyIri: SmartIri = OntologyConstants.KnoraApiV2WithValueObjects.HasLinkTo.toSmartIri
            val linkValuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2WithValueObjects.HasLinkToValue.toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "knora-api:LinkObj",
                   |  "knora-api:hasLinkToValue" : {
                   |    "@id" : "${linkValueIri.get}",
                   |    "@type" : "knora-api:LinkValue",
                   |    "knora-api:linkValueHasTargetIri" : {
                   |      "@id" : "$generationeIri"
                   |    }
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "incunabula" : "http://0.0.0.0:3333/ontology/0803/incunabula/v2#"
                   |  }
                   |}""".stripMargin

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)

            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            linkValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2WithValueObjects.LinkValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = linkPropertyIri,
                propertyIriInResult = linkValuePropertyIri,
                expectedValueIri = linkValueIri.get,
                userEmail = incunabulaUserEmail
            )

            val savedTarget: JsonLDObject = savedValue.requireObject(OntologyConstants.KnoraApiV2WithValueObjects.LinkValueHasTarget)
            val savedTargetIri: IRI = savedTarget.requireString(JsonLDConstants.ID)
            savedTargetIri should ===(generationeIri)
        }

        "delete an integer value" in {
            val resourceIriEnc: String = URLEncoder.encode(aThingIri, "UTF-8")
            val propertyIriEnc: String = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger", "UTF-8")
            val valueIriEnc: String = URLEncoder.encode(intValueIri.get, "UTF-8")
            val deleteComment: String = URLEncoder.encode("this value was incorrect", "UTF-8")

            val request = Delete(s"$baseApiUrl/v2/values/$resourceIriEnc/$propertyIriEnc/$valueIriEnc?deleteComment=$deleteComment") ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
        }

        "delete a link between two resources" in {
            val resourceIriEnc: String = URLEncoder.encode("http://rdfh.ch/cb1a74e3e2f6", "UTF-8")
            val propertyIriEnc: String = URLEncoder.encode(OntologyConstants.KnoraApiV2WithValueObjects.HasLinkToValue, "UTF-8")
            val valueIriEnc: String = URLEncoder.encode(linkValueIri.get, "UTF-8")

            val request = Delete(s"$baseApiUrl/v2/values/$resourceIriEnc/$propertyIriEnc/$valueIriEnc") ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
        }
    }
}
