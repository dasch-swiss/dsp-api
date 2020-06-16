/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.e2e.v2

import java.io.File
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.RouteTestTimeout
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.responders.v2.search.SparqlQueryConstants
import org.knora.webapi.testing.tags.E2ETest
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util._
import org.knora.webapi.util.jsonld._
import org.xmlunit.builder.{DiffBuilder, Input}
import org.xmlunit.diff.Diff

@E2ETest
class ValuesRouteV2E2ESpec extends E2ESpec {

    private implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout)

    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val anythingUserEmail = SharedTestDataADM.anythingUser1.email
    private val password = "test"

    private val intValueIri = new MutableTestIri
    private val textValueWithoutStandoffIri = new MutableTestIri
    private val textValueWithStandoffIri = new MutableTestIri
    private val textValueWithEscapeIri = new MutableTestIri
    private val decimalValueIri = new MutableTestIri
    private val dateValueIri = new MutableTestIri
    private val booleanValueIri = new MutableTestIri
    private val geometryValueIri = new MutableTestIri
    private val intervalValueIri = new MutableTestIri
    private val timeValueIri = new MutableTestIri
    private val listValueIri = new MutableTestIri
    private val colorValueIri = new MutableTestIri
    private val uriValueIri = new MutableTestIri
    private val geonameValueIri = new MutableTestIri
    private val linkValueIri = new MutableTestIri

    private var integerValueUUID = UUID.randomUUID
    private var linkValueUUID = UUID.randomUUID

    override lazy val rdfDataObjects = List(
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
        resource.maybeObject(OntologyConstants.KnoraApiV2Complex.LastModificationDate).map {
            jsonLDObject =>
                jsonLDObject.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.validateAndEscapeIri) should ===(OntologyConstants.Xsd.DateTimeStamp)
                jsonLDObject.requireStringWithValidation(JsonLDConstants.VALUE, stringFormatter.xsdDateTimeStampToInstant)
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

        val receivedResourceIri: IRI = resource.requireIDAsKnoraDataIri.toString

        if (receivedResourceIri != resourceIri) {
            throw AssertionException(s"Expected resource $resourceIri, received $receivedResourceIri")
        }

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
        "get the latest version of a value, given its UUID" in {
            val resourceIri = URLEncoder.encode("http://rdfh.ch/0001/thing-with-history", "UTF-8")
            val valueUuid = "pLlW4ODASumZfZFbJdpw1g"

            val request = Get(baseApiUrl + s"/v2/values/$resourceIri/$valueUuid") ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)

            val value: JsonLDObject = getValueFromResource(
                resource = responseJsonDoc,
                propertyIriInResult = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri,
                expectedValueIri = "http://rdfh.ch/0001/thing-with-history/values/1c"
            )

            val intValueAsInt: Int = value.requireInt(OntologyConstants.KnoraApiV2Complex.IntValueAsInt)
            intValueAsInt should ===(3)
        }

        "get a past version of a value, given its UUID and a timestamp" in {
            val resourceIri = URLEncoder.encode("http://rdfh.ch/0001/thing-with-history", "UTF-8")
            val valueUuid = "pLlW4ODASumZfZFbJdpw1g"
            val timestamp = "20190212T090510Z"

            val request = Get(baseApiUrl + s"/v2/values/$resourceIri/$valueUuid?version=$timestamp") ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)

            val value: JsonLDObject = getValueFromResource(
                resource = responseJsonDoc,
                propertyIriInResult = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri,
                expectedValueIri = "http://rdfh.ch/0001/thing-with-history/values/1b"
            )

            val intValueAsInt: Int = value.requireInt(OntologyConstants.KnoraApiV2Complex.IntValueAsInt)
            intValueAsInt should ===(2)
        }

        "create an integer value" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue: Int = 4
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.createIntValueRequest(
                resourceIri = resourceIri,
                intValue = intValue
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            intValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = intValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedIntValue: Int = savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.IntValueAsInt)
            savedIntValue should ===(intValue)
        }

        "create an integer value with a custom valueIri" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue: Int = 30
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)
            val customValueIri: IRI = "http://rdfh.ch/0001/a-customized-thing/values/int-with-valueIRI"
            val jsonLdEntity = SharedTestDataADM.createIntValueWithCustomValueIriRequest(
                resourceIri = resourceIri,
                intValue = intValue,
                valueIri = customValueIri
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)

            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            assert(valueIri == customValueIri)
        }

        "create an integer value with a custom UUID" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue: Int = 45
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)
            val customValueUUID = "IN4R19yYR0ygi3K2VEHpUQ"
            val jsonLdEntity = SharedTestDataADM.createIntValueWithCustomUUIDRequest(
                resourceIri = resourceIri,
                intValue = intValue,
                valueUUID = customValueUUID
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)

            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueUUID = responseJsonDoc.body.requireString(OntologyConstants.KnoraApiV2Complex.ValueHasUUID)
            assert(valueUUID == customValueUUID)
        }

        "create an integer value with a custom creation date" in {
            val customCreationDate: Instant = Instant.parse("2020-06-04T11:36:54.502951Z")
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue: Int = 25
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)
            val jsonLdEntity = SharedTestDataADM.createIntValueWithCustomCreationDateRequest(resourceIri = resourceIri, intValue = intValue, creationDate = customCreationDate)

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)

            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val savedCreationDate: Instant = responseJsonDoc.body.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2Complex.ValueCreationDate,
                expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                validationFun = stringFormatter.xsdDateTimeStampToInstant
            )
            assert(savedCreationDate == customCreationDate)
        }

        "create an integer value with custom Iri, UUID, and creation Date" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue: Int = 10
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)
            val customValueIri: IRI = "http://rdfh.ch/0001/a-thing/values/int-with-IRI"
            val customValueUUID = "IN4R19yYR0ygi3K2VEHpUQ"
            val customCreationDate: Instant = Instant.parse("2020-06-04T12:58:54.502951Z")
            val jsonLdEntity = SharedTestDataADM.createIntValueWithCustomIRIRequest(
                            resourceIri = resourceIri,
                            intValue = intValue,
                            valueIri = customValueIri,
                            valueUUID = customValueUUID,
                            valueCreationDate = customCreationDate
                        )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)

            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            assert(valueIri == customValueIri)
            val valueUUID = responseJsonDoc.body.requireString(OntologyConstants.KnoraApiV2Complex.ValueHasUUID)
            assert(valueUUID == customValueUUID)
            val savedCreationDate: Instant = responseJsonDoc.body.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2Complex.ValueCreationDate,
                expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                validationFun = stringFormatter.xsdDateTimeStampToInstant
            )
            assert(savedCreationDate == customCreationDate)
        }

        "not create an integer value if the simple schema is submitted" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val intValue: Int = 10

            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInteger" : $intValue,
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/simple/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#"
                   |  }
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.BadRequest, responseAsString)
        }

        "create an integer value with custom permissions" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue: Int = 1
            val customPermissions: String = "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.createIntValueWithCustomPermissionsRequest(
                resourceIri = resourceIri,
                intValue = intValue,
                customPermissions = customPermissions
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            intValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri)
            integerValueUUID = responseJsonDoc.body.requireStringWithValidation(OntologyConstants.KnoraApiV2Complex.ValueHasUUID, stringFormatter.validateBase64EncodedUuid)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = intValueIri.get,
                userEmail = anythingUserEmail
            )

            val intValueAsInt: Int = savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.IntValueAsInt)
            intValueAsInt should ===(intValue)
            val hasPermissions = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.HasPermissions)
            hasPermissions should ===(customPermissions)
        }

        "create a text value without standoff and without a comment" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val valueAsString: String = "text without standoff"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLDEntity = SharedTestDataADM.createTextValueWithoutStandoffRequest(
                resourceIri = resourceIri,
                valueAsString = valueAsString
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            textValueWithoutStandoffIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = textValueWithoutStandoffIri.get,
                userEmail = anythingUserEmail
            )

            val savedValueAsString: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString)
            savedValueAsString should ===(valueAsString)
        }
        
        "not update a text value without a comment without changing it" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val valueAsString: String = "text without standoff"

            val jsonLDEntity = SharedTestDataADM.updateTextValueWithoutStandoffRequest(
                resourceIri = resourceIri,
                valueIri = textValueWithoutStandoffIri.get,
                valueAsString = valueAsString
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.BadRequest, response.toString)
        }

        "not update a text value so it's empty" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val valueAsString: String = ""

            val jsonLDEntity = SharedTestDataADM.updateTextValueWithoutStandoffRequest(
                resourceIri = resourceIri,
                valueIri = textValueWithoutStandoffIri.get,
                valueAsString = valueAsString
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.BadRequest, response.toString)
        }

        "update a text value without standoff" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val valueAsString: String = "text without standoff updated"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLDEntity = SharedTestDataADM.updateTextValueWithoutStandoffRequest(
                resourceIri = resourceIri,
                valueIri = textValueWithoutStandoffIri.get,
                valueAsString = valueAsString
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            textValueWithoutStandoffIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = textValueWithoutStandoffIri.get,
                userEmail = anythingUserEmail
            )

            val savedValueAsString: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString)
            savedValueAsString should ===(valueAsString)
        }

        "update a text value without standoff, adding a comment" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val valueAsString: String = "text without standoff updated"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLDEntity = SharedTestDataADM.updateTextValueWithCommentRequest(
                resourceIri = resourceIri,
                valueIri = textValueWithoutStandoffIri.get,
                valueAsString = valueAsString,
                valueHasComment = "Adding a comment"
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            textValueWithoutStandoffIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = textValueWithoutStandoffIri.get,
                userEmail = anythingUserEmail
            )

            val savedValueAsString: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString)
            savedValueAsString should ===(valueAsString)
        }

        "not update a text value without standoff and with a comment without changing it" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val valueAsString: String = "text without standoff updated"

            val jsonLDEntity = SharedTestDataADM.updateTextValueWithCommentRequest(
                resourceIri = resourceIri,
                valueIri = textValueWithoutStandoffIri.get,
                valueAsString = valueAsString,
                valueHasComment = "Adding a comment"
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.BadRequest, response.toString)
        }

        "update a text value without standoff, changing only the a comment" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val valueAsString: String = "text without standoff updated"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLDEntity = SharedTestDataADM.updateTextValueWithCommentRequest(
                resourceIri = resourceIri,
                valueIri = textValueWithoutStandoffIri.get,
                valueAsString = valueAsString,
                valueHasComment = "Updated comment"
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            textValueWithoutStandoffIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = textValueWithoutStandoffIri.get,
                userEmail = anythingUserEmail
            )

            val savedValueAsString: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString)
            savedValueAsString should ===(valueAsString)
        }

        "create a text value without standoff and with a comment" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val valueAsString: String = "this is a text value that has a comment"
            val valueHasComment: String = "this is a comment"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLDEntity = SharedTestDataADM.createTextValueWithCommentRequest(
                resourceIri = resourceIri,
                valueAsString = valueAsString,
                valueHasComment = valueHasComment
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            textValueWithoutStandoffIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = textValueWithoutStandoffIri.get,
                userEmail = anythingUserEmail
            )

            val savedValueAsString: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString)
            savedValueAsString should ===(valueAsString)
            val savedValueHasComment: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueHasComment)
            savedValueHasComment should ===(valueHasComment)
        }

        "create a text value with standoff" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri

            val textValueAsXml: String =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<text>
                  |   This text links to another <a class="salsah-link" href="http://rdfh.ch/0001/another-thing">resource</a>.
                  |   And this <strong id="link_id">strong value</strong> is linked by this <a class="internal-link" href="#link_id">link</a>
                  |</text>
                """.stripMargin

            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLDEntity = SharedTestDataADM.createTextValueWithStandoffRequest(
                resourceIri = resourceIri,
                textValueAsXml = textValueAsXml,
                mappingIri = SharedTestDataADM.standardMappingIri
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            textValueWithStandoffIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = textValueWithStandoffIri.get,
                userEmail = anythingUserEmail
            )

            val savedTextValueAsXml: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.TextValueAsXml)

            // Compare the original XML with the regenerated XML.
            val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(textValueAsXml)).withTest(Input.fromString(savedTextValueAsXml)).build()
            xmlDiff.hasDifferences should be(false)
        }

        "create a very long text value with standoff and linked tags" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri

            val textValueAsXml: String =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<text>
                  |   <p>This <a class="internal-link" href="#link_id">ref</a> is a link to an out of page tag.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>Many lines to force to create a page.</p>
                  |   <p>This <strong id="link_id">strong value</strong> is linked by an out of page anchor link at the top.</p>
                  |</text>
                """.stripMargin

            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLDEntity = SharedTestDataADM.createTextValueWithStandoffRequest(
                resourceIri = resourceIri,
                textValueAsXml = textValueAsXml,
                mappingIri = SharedTestDataADM.standardMappingIri
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            textValueWithStandoffIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = textValueWithStandoffIri.get,
                userEmail = anythingUserEmail
            )

            val savedTextValueAsXml: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.TextValueAsXml)

            // Compare the original XML with the regenerated XML.
            val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(textValueAsXml)).withTest(Input.fromString(savedTextValueAsXml)).build()
            xmlDiff.hasDifferences should be(false)
        }

        "create a text value with standoff containing a URL" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri

            val textValueAsXml: String =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<text>
                  |   This text links to <a href="http://www.knora.org">a web site</a>.
                  |</text>
                """.stripMargin

            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLDEntity = SharedTestDataADM.createTextValueWithStandoffRequest(
                resourceIri = resourceIri,
                textValueAsXml = textValueAsXml,
                mappingIri = SharedTestDataADM.standardMappingIri
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = valueIri,
                userEmail = anythingUserEmail
            )

            val savedTextValueAsXml: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.TextValueAsXml)
            savedTextValueAsXml.contains("href") should ===(true)
        }

        "create a text value with standoff containing escaped text" in {
            val resourceIri = SharedTestDataADM.AThing.iri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)
            val jsonLDEntity = FileUtil.readTextFile(new File("src/test/resources/test-data/valuesE2EV2/CreateValueWithEscape.jsonld"))
            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            textValueWithEscapeIri.set(valueIri)
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri

            val savedValue: JsonLDObject = getValue(
                resourceIri = SharedTestDataADM.AThing.iri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = valueIri,
                userEmail = anythingUserEmail
            )

            val savedTextValueAsXml: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.TextValueAsXml)

            val expectedText =
                """<p>
                  | test</p>""".stripMargin

            assert(savedTextValueAsXml.contains(expectedText))
        }

        "create a TextValue from XML representing HTML with an attribute containing escaped quotes" in {
            // Create the mapping.

            val xmlFileToSend = new File("_test_data/test_route/texts/mappingForHTML.xml")

            val mappingParams =
                s"""{
                   |    "knora-api:mappingHasName": "HTMLMapping",
                   |    "knora-api:attachedToProject": {
                   |      "@id": "${SharedTestDataADM.ANYTHING_PROJECT_IRI}"
                   |    },
                   |    "rdfs:label": "HTML mapping",
                   |    "@context": {
                   |        "rdfs": "${OntologyConstants.Rdfs.RdfsPrefixExpansion}",
                   |        "knora-api": "${OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion}"
                   |    }
                   |}""".stripMargin

            val formDataMapping = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "json",
                    HttpEntity(ContentTypes.`application/json`, mappingParams)
                ),
                Multipart.FormData.BodyPart(
                    "xml",
                    HttpEntity.fromPath(MediaTypes.`text/xml`.toContentType(HttpCharsets.`UTF-8`), xmlFileToSend.toPath),
                    Map("filename" -> "HTMLMapping.xml")
                )
            )

            // create standoff from XML
            val mappingRequest = Post(baseApiUrl + "/v2/mapping", formDataMapping) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val mappingResponse: HttpResponse = singleAwaitingRequest(mappingRequest)
            assert(mappingResponse.status == StatusCodes.OK, mappingResponse.toString)

            // Create the text value.

            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val textValueAsXml =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<text documentType="html">
                  |    <p>This an <span data-description="an &quot;event&quot;" data-date="GREGORIAN:2017-01-27 CE" class="event">event</span>.</p>
                  |</text>""".stripMargin

            val jsonLDEntity = SharedTestDataADM.createTextValueWithStandoffRequest(
                resourceIri = resourceIri,
                textValueAsXml = textValueAsXml,
                mappingIri = s"${SharedTestDataADM.ANYTHING_PROJECT_IRI}/mappings/HTMLMapping"
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            textValueWithStandoffIri.set(valueIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = valueIri,
                userEmail = anythingUserEmail
            )

            val savedTextValueAsXml: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.TextValueAsXml)
            assert(savedTextValueAsXml.contains(textValueAsXml))
        }

        "not create an empty text value" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val valueAsString: String = ""

            val jsonLDEntity = SharedTestDataADM.createTextValueWithoutStandoffRequest(
                resourceIri = resourceIri,
                valueAsString = valueAsString
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.BadRequest, response.toString)
        }

        "create a decimal value" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
            val decimalValueAsDecimal = BigDecimal(4.3)
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.createDecimalValueRequest(
                resourceIri = resourceIri,
                decimalValue = decimalValueAsDecimal
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            decimalValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.DecimalValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = decimalValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedDecimalValueAsDecimal: BigDecimal = savedValue.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2Complex.DecimalValueAsDecimal,
                expectedDatatype = OntologyConstants.Xsd.Decimal.toSmartIri,
                validationFun = stringFormatter.validateBigDecimal
            )

            savedDecimalValueAsDecimal should ===(decimalValueAsDecimal)
        }

        "create a date value representing a range with day precision" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
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

            val jsonLdEntity = SharedTestDataADM.createDateValueWithDayPrecisionRequest(
                resourceIri = resourceIri,
                dateValueHasCalendar = dateValueHasCalendar,
                dateValueHasStartYear = dateValueHasStartYear,
                dateValueHasStartMonth = dateValueHasStartMonth,
                dateValueHasStartDay = dateValueHasStartDay,
                dateValueHasStartEra = dateValueHasStartEra,
                dateValueHasEndYear = dateValueHasEndYear,
                dateValueHasEndMonth = dateValueHasEndMonth,
                dateValueHasEndDay = dateValueHasEndDay,
                dateValueHasEndEra = dateValueHasEndEra
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should ===("GREGORIAN:2018-10-05 CE:2018-10-06 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(dateValueHasStartMonth)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(dateValueHasStartDay)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasEndYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(dateValueHasEndMonth)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(dateValueHasEndDay)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasEndEra)
        }

        "create a date value representing a range with month precision" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2018
            val dateValueHasStartMonth = 10
            val dateValueHasStartEra = "CE"
            val dateValueHasEndYear = 2018
            val dateValueHasEndMonth = 11
            val dateValueHasEndEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.createDateValueWithMonthPrecisionRequest(
                resourceIri = resourceIri,
                dateValueHasCalendar = dateValueHasCalendar,
                dateValueHasStartYear = dateValueHasStartYear,
                dateValueHasStartMonth = dateValueHasStartMonth,
                dateValueHasStartEra = dateValueHasStartEra,
                dateValueHasEndYear = dateValueHasEndYear,
                dateValueHasEndMonth = dateValueHasEndMonth,
                dateValueHasEndEra = dateValueHasEndEra
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should ===("GREGORIAN:2018-10 CE:2018-11 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(dateValueHasStartMonth)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasEndYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(dateValueHasEndMonth)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasEndEra)
        }

        "create a date value representing a range with year precision" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2018
            val dateValueHasStartEra = "CE"
            val dateValueHasEndYear = 2019
            val dateValueHasEndEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.createDateValueWithYearPrecisionRequest(
                resourceIri = resourceIri,
                dateValueHasCalendar = dateValueHasCalendar,
                dateValueHasStartYear = dateValueHasStartYear,
                dateValueHasStartEra = dateValueHasStartEra,
                dateValueHasEndYear = dateValueHasEndYear,
                dateValueHasEndEra = dateValueHasEndEra
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should ===("GREGORIAN:2018 CE:2019 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(None)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasEndYear)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(None)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasEndEra)
        }


        "create a date value representing a single date with day precision" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2018
            val dateValueHasStartMonth = 10
            val dateValueHasStartDay = 5
            val dateValueHasStartEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.createDateValueWithDayPrecisionRequest(
                resourceIri = resourceIri,
                dateValueHasCalendar = dateValueHasCalendar,
                dateValueHasStartYear = dateValueHasStartYear,
                dateValueHasStartMonth = dateValueHasStartMonth,
                dateValueHasStartDay = dateValueHasStartDay,
                dateValueHasStartEra = dateValueHasStartEra,
                dateValueHasEndYear = dateValueHasStartYear,
                dateValueHasEndMonth = dateValueHasStartMonth,
                dateValueHasEndDay = dateValueHasStartDay,
                dateValueHasEndEra = dateValueHasStartEra
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should ===("GREGORIAN:2018-10-05 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(dateValueHasStartMonth)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(dateValueHasStartDay)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(dateValueHasStartMonth)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(dateValueHasStartDay)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasStartEra)
        }

        "create a date value representing a single date with month precision" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2018
            val dateValueHasStartMonth = 10
            val dateValueHasStartEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.createDateValueWithMonthPrecisionRequest(
                resourceIri = resourceIri,
                dateValueHasCalendar = dateValueHasCalendar,
                dateValueHasStartYear = dateValueHasStartYear,
                dateValueHasStartMonth = dateValueHasStartMonth,
                dateValueHasStartEra = dateValueHasStartEra,
                dateValueHasEndYear = dateValueHasStartYear,
                dateValueHasEndMonth = dateValueHasStartMonth,
                dateValueHasEndEra = dateValueHasStartEra
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should ===("GREGORIAN:2018-10 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(dateValueHasStartMonth)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(dateValueHasStartMonth)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasStartEra)
        }

        "create a date value representing a single date with year precision" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2018
            val dateValueHasStartEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.createDateValueWithYearPrecisionRequest(
                resourceIri = resourceIri,
                dateValueHasCalendar = dateValueHasCalendar,
                dateValueHasStartYear = dateValueHasStartYear,
                dateValueHasStartEra = dateValueHasStartEra,
                dateValueHasEndYear = dateValueHasStartYear,
                dateValueHasEndEra = dateValueHasStartEra
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should ===("GREGORIAN:2018 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(None)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasStartYear)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(None)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasStartEra)
        }

        "create a boolean value" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri
            val booleanValue: Boolean = true
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.createBooleanValueRequest(
                resourceIri = resourceIri,
                booleanValue = booleanValue
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            booleanValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.BooleanValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = booleanValueIri.get,
                userEmail = anythingUserEmail
            )

            val booleanValueAsBoolean: Boolean = savedValue.requireBoolean(OntologyConstants.KnoraApiV2Complex.BooleanValueAsBoolean)
            booleanValueAsBoolean should ===(booleanValue)
        }

        "create a geometry value" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.createGeometryValueRequest(
                resourceIri = resourceIri,
                geometryValue = SharedTestDataADM.geometryValue1
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            geometryValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.GeomValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = geometryValueIri.get,
                userEmail = anythingUserEmail
            )

            val geometryValueAsGeometry: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.GeometryValueAsGeometry)
            geometryValueAsGeometry should ===(SharedTestDataADM.geometryValue1)
        }

        "create an interval value" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
            val intervalStart = BigDecimal("1.2")
            val intervalEnd = BigDecimal("3.4")
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.createIntervalValueRequest(
                resourceIri = resourceIri,
                intervalStart = intervalStart,
                intervalEnd = intervalEnd
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            intervalValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.IntervalValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = intervalValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedIntervalValueHasStart: BigDecimal = savedValue.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2Complex.IntervalValueHasStart,
                expectedDatatype = OntologyConstants.Xsd.Decimal.toSmartIri,
                validationFun = stringFormatter.validateBigDecimal
            )

            savedIntervalValueHasStart should ===(intervalStart)

            val savedIntervalValueHasEnd: BigDecimal = savedValue.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2Complex.IntervalValueHasEnd,
                expectedDatatype = OntologyConstants.Xsd.Decimal.toSmartIri,
                validationFun = stringFormatter.validateBigDecimal
            )

            savedIntervalValueHasEnd should ===(intervalEnd)
        }

        "create a time value" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasTimeStamp".toSmartIri
            val timeStamp = Instant.parse("2019-08-28T15:59:12.725007Z")
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.createTimeValueRequest(
                resourceIri = resourceIri,
                timeStamp = timeStamp
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            timeValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.TimeValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = timeValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedTimeStamp: Instant = savedValue.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2Complex.TimeValueAsTimeStamp,
                expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                validationFun = stringFormatter.xsdDateTimeStampToInstant
            )

            savedTimeStamp should ===(timeStamp)
        }

        "create a list value" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
            val listNode = "http://rdfh.ch/lists/0001/treeList03"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.createListValueRequest(
                resourceIri = resourceIri,
                listNode = listNode
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            listValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.ListValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = listValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedListValueHasListNode: IRI = savedValue.requireIriInObject(OntologyConstants.KnoraApiV2Complex.ListValueAsListNode, stringFormatter.validateAndEscapeIri)
            savedListValueHasListNode should ===(listNode)
        }

        "create a color value" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
            val color = "#ff3333"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.createColorValueRequest(
                resourceIri = resourceIri,
                color = color
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            colorValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.ColorValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = colorValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedColor: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ColorValueAsColor)
            savedColor should ===(color)
        }

        "create a URI value" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
            val uri = "https://www.knora.org"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.createUriValueRequest(
                resourceIri = resourceIri,
                uri = uri
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            uriValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.UriValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = uriValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedUri: IRI = savedValue.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2Complex.UriValueAsUri,
                expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
                validationFun = stringFormatter.validateAndEscapeIri
            )

            savedUri should ===(uri)
        }

        "create a geoname value" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
            val geonameCode = "2661604"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.createGeonameValueRequest(
                resourceIri = resourceIri,
                geonameCode = geonameCode
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            geonameValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.GeonameValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = geonameValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedGeonameCode: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.GeonameValueAsGeonameCode)
            savedGeonameCode should ===(geonameCode)
        }

        "create a link between two resources, without a comment" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val linkPropertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThing".toSmartIri
            val linkValuePropertyIri: SmartIri = linkPropertyIri.fromLinkPropToLinkValueProp
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.createLinkValueRequest(
                resourceIri = resourceIri,
                targetResourceIri = SharedTestDataADM.TestDing.iri
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)

            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            linkValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.LinkValue.toSmartIri)
            linkValueUUID = responseJsonDoc.body.requireStringWithValidation(OntologyConstants.KnoraApiV2Complex.ValueHasUUID, stringFormatter.validateBase64EncodedUuid)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = linkPropertyIri,
                propertyIriInResult = linkValuePropertyIri,
                expectedValueIri = linkValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedTarget: JsonLDObject = savedValue.requireObject(OntologyConstants.KnoraApiV2Complex.LinkValueHasTarget)
            val savedTargetIri: IRI = savedTarget.requireString(JsonLDConstants.ID)
            savedTargetIri should ===(SharedTestDataADM.TestDing.iri)
        }

        "create a link between two resources with a custom link value Iri, UUID, creationDate" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val targetResourceIri: IRI = "http://rdfh.ch/0001/CNhWoNGGT7iWOrIwxsEqvA"
            val customValueIri: IRI = "http://rdfh.ch/0001/a-thing/values/link-Value-With-IRI"
            val customValueUUID = "IN4R19yYR0ygi3K2VEHpUQ"
            val customCreationDate: Instant = Instant.parse("2020-06-04T11:36:54.502951Z")
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.createLinkValueWithCustomIriRequest(
                resourceIri = resourceIri,
                targetResourceIri = targetResourceIri,
                customValueIri = customValueIri,
                customValueUUID = customValueUUID,
                customValueCreationDate = customCreationDate
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)

            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            assert(valueIri == customValueIri)
            val valueUUID: IRI = responseJsonDoc.body.requireString(OntologyConstants.KnoraApiV2Complex.ValueHasUUID)
            assert(valueUUID == customValueUUID)
            val savedCreationDate: Instant = responseJsonDoc.body.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2Complex.ValueCreationDate,
                expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                validationFun = stringFormatter.xsdDateTimeStampToInstant
            )
            assert(savedCreationDate == customCreationDate)
        }

        "update an integer value" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue: Int = 5
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.updateIntValueRequest(
                resourceIri = resourceIri,
                valueIri = intValueIri.get,
                intValue = intValue
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            intValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri)
            val newIntegerValueUUID: UUID = responseJsonDoc.body.requireStringWithValidation(OntologyConstants.KnoraApiV2Complex.ValueHasUUID, stringFormatter.validateBase64EncodedUuid)
            assert(newIntegerValueUUID == integerValueUUID) // The new version should have the same UUID.
            integerValueUUID = newIntegerValueUUID

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = intValueIri.get,
                userEmail = anythingUserEmail
            )

            val intValueAsInt: Int = savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.IntValueAsInt)
            intValueAsInt should ===(intValue)
        }

        "not update an integer value if the simple schema is submitted" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val intValue: Int = 10

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
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/simple/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#"
                   |  }
                   |}""".stripMargin

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.BadRequest, responseAsString)
        }

        "update an integer value with custom permissions" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val intValue: Int = 6
            val customPermissions: String = "CR http://rdfh.ch/groups/0001/thing-searcher"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLDEntity = SharedTestDataADM.updateIntValueWithCustomPermissionsRequest(
                resourceIri = resourceIri,
                valueIri = intValueIri.get,
                intValue = intValue,
                customPermissions = customPermissions
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            intValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = intValueIri.get,
                userEmail = anythingUserEmail
            )

            val intValueAsInt: Int = savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.IntValueAsInt)
            intValueAsInt should ===(intValue)
            val hasPermissions = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.HasPermissions)
            hasPermissions should ===(customPermissions)
        }

        "update an integer value, changing only the permissions" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
            val customPermissions: String = "CR http://rdfh.ch/groups/0001/thing-searcher|V knora-admin:KnownUser"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLDEntity = SharedTestDataADM.updateIntValuePermissionsOnlyRequest(
                resourceIri = resourceIri,
                valueIri = intValueIri.get,
                customPermissions = customPermissions
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            intValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = intValueIri.get,
                userEmail = anythingUserEmail
            )

            val hasPermissions = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.HasPermissions)
            hasPermissions should ===(customPermissions)
        }

        "update a decimal value" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
            val decimalValue = BigDecimal(5.6)
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.updateDecimalValueRequest(
                resourceIri = resourceIri,
                valueIri = decimalValueIri.get,
                decimalValue = decimalValue
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            decimalValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.DecimalValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = decimalValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedDecimalValue: BigDecimal = savedValue.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2Complex.DecimalValueAsDecimal,
                expectedDatatype = OntologyConstants.Xsd.Decimal.toSmartIri,
                stringFormatter.validateBigDecimal
            )

            savedDecimalValue should ===(decimalValue)
        }

        "update a text value with standoff" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri

            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLDEntity = SharedTestDataADM.updateTextValueWithStandoffRequest(
                resourceIri = resourceIri,
                valueIri = textValueWithStandoffIri.get,
                textValueAsXml = SharedTestDataADM.textValue2AsXmlWithStandardMapping,
                mappingIri = SharedTestDataADM.standardMappingIri
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            textValueWithStandoffIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = textValueWithStandoffIri.get,
                userEmail = anythingUserEmail
            )

            val savedTextValueAsXml: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.TextValueAsXml)
            savedTextValueAsXml.contains("updated text") should ===(true)
            savedTextValueAsXml.contains("salsah-link") should ===(true)
        }

        "update a text value with standoff containing escaped text" in {
            val resourceIri = SharedTestDataADM.AThing.iri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)
            val jsonLDEntity = FileUtil.readTextFile(new File("src/test/resources/test-data/valuesE2EV2/UpdateValueWithEscape.jsonld"))
            val jsonLDEntityWithResourceValueIri = jsonLDEntity.replace("VALUE_IRI", textValueWithEscapeIri.get)
            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntityWithResourceValueIri)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            textValueWithEscapeIri.set(valueIri)
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = valueIri,
                userEmail = anythingUserEmail
            )

            val savedTextValueAsXml: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.TextValueAsXml)

            val expectedText =
                """<p>
                  | test update</p>""".stripMargin

            assert(savedTextValueAsXml.contains(expectedText))
        }

        "update a text value with a comment" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val valueAsString: String = "this is a text value that has an updated comment"
            val valueHasComment: String = "this is an updated comment"
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLDEntity = SharedTestDataADM.updateTextValueWithCommentRequest(
                resourceIri = resourceIri,
                valueIri = textValueWithoutStandoffIri.get,
                valueAsString = valueAsString,
                valueHasComment = valueHasComment
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            textValueWithoutStandoffIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = textValueWithoutStandoffIri.get,
                userEmail = anythingUserEmail
            )

            val savedValueAsString: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString)
            savedValueAsString should ===(valueAsString)
            val savedValueHasComment: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueHasComment)
            savedValueHasComment should ===(valueHasComment)
        }

        "update a date value representing a range with day precision" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
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

            val jsonLdEntity = SharedTestDataADM.updateDateValueWithDayPrecisionRequest(
                resourceIri = resourceIri,
                valueIri = dateValueIri.get,
                dateValueHasCalendar = dateValueHasCalendar,
                dateValueHasStartYear = dateValueHasStartYear,
                dateValueHasStartMonth = dateValueHasStartMonth,
                dateValueHasStartDay = dateValueHasStartDay,
                dateValueHasStartEra = dateValueHasStartEra,
                dateValueHasEndYear = dateValueHasEndYear,
                dateValueHasEndMonth = dateValueHasEndMonth,
                dateValueHasEndDay = dateValueHasEndDay,
                dateValueHasEndEra = dateValueHasEndEra
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should ===("GREGORIAN:2018-10-05 CE:2018-12-06 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(dateValueHasStartMonth)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(dateValueHasStartDay)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasEndYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(dateValueHasEndMonth)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(dateValueHasEndDay)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasEndEra)
        }

        "update a date value representing a range with month precision" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2018
            val dateValueHasStartMonth = 9
            val dateValueHasStartEra = "CE"
            val dateValueHasEndYear = 2018
            val dateValueHasEndMonth = 12
            val dateValueHasEndEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.updateDateValueWithMonthPrecisionRequest(
                resourceIri = resourceIri,
                valueIri = dateValueIri.get,
                dateValueHasCalendar = dateValueHasCalendar,
                dateValueHasStartYear = dateValueHasStartYear,
                dateValueHasStartMonth = dateValueHasStartMonth,
                dateValueHasStartEra = dateValueHasStartEra,
                dateValueHasEndYear = dateValueHasEndYear,
                dateValueHasEndMonth = dateValueHasEndMonth,
                dateValueHasEndEra = dateValueHasEndEra
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should ===("GREGORIAN:2018-09 CE:2018-12 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(dateValueHasStartMonth)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasEndYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(dateValueHasEndMonth)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasEndEra)
        }

        "update a date value representing a range with year precision" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2018
            val dateValueHasStartEra = "CE"
            val dateValueHasEndYear = 2020
            val dateValueHasEndEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.updateDateValueWithYearPrecisionRequest(
                resourceIri = resourceIri,
                valueIri = dateValueIri.get,
                dateValueHasCalendar = dateValueHasCalendar,
                dateValueHasStartYear = dateValueHasStartYear,
                dateValueHasStartEra = dateValueHasStartEra,
                dateValueHasEndYear = dateValueHasEndYear,
                dateValueHasEndEra = dateValueHasEndEra
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should ===("GREGORIAN:2018 CE:2020 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(None)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasEndYear)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(None)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasEndEra)
        }

        "update a date value representing a single date with day precision" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2018
            val dateValueHasStartMonth = 10
            val dateValueHasStartDay = 6
            val dateValueHasStartEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.updateDateValueWithDayPrecisionRequest(
                resourceIri = resourceIri,
                valueIri = dateValueIri.get,
                dateValueHasCalendar = dateValueHasCalendar,
                dateValueHasStartYear = dateValueHasStartYear,
                dateValueHasStartMonth = dateValueHasStartMonth,
                dateValueHasStartDay = dateValueHasStartDay,
                dateValueHasStartEra = dateValueHasStartEra,
                dateValueHasEndYear = dateValueHasStartYear,
                dateValueHasEndMonth = dateValueHasStartMonth,
                dateValueHasEndDay = dateValueHasStartDay,
                dateValueHasEndEra = dateValueHasStartEra
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should ===("GREGORIAN:2018-10-06 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(dateValueHasStartMonth)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(dateValueHasStartDay)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(dateValueHasStartMonth)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(dateValueHasStartDay)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasStartEra)
        }

        "update a date value representing a single date with month precision" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2018
            val dateValueHasStartMonth = 7
            val dateValueHasStartEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.updateDateValueWithMonthPrecisionRequest(
                resourceIri = resourceIri,
                valueIri = dateValueIri.get,
                dateValueHasCalendar = dateValueHasCalendar,
                dateValueHasStartYear = dateValueHasStartYear,
                dateValueHasStartMonth = dateValueHasStartMonth,
                dateValueHasStartEra = dateValueHasStartEra,
                dateValueHasEndYear = dateValueHasStartYear,
                dateValueHasEndMonth = dateValueHasStartMonth,
                dateValueHasEndEra = dateValueHasStartEra
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should ===("GREGORIAN:2018-07 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(dateValueHasStartMonth)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasStartYear)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(dateValueHasStartMonth)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasStartEra)
        }

        "update a date value representing a single date with year precision" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
            val dateValueHasCalendar = "GREGORIAN"
            val dateValueHasStartYear = 2019
            val dateValueHasStartEra = "CE"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.updateDateValueWithYearPrecisionRequest(
                resourceIri = resourceIri,
                valueIri = dateValueIri.get,
                dateValueHasCalendar = dateValueHasCalendar,
                dateValueHasStartYear = dateValueHasStartYear,
                dateValueHasStartEra = dateValueHasStartEra,
                dateValueHasEndYear = dateValueHasStartYear,
                dateValueHasEndEra = dateValueHasStartEra
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            dateValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.DateValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = dateValueIri.get,
                userEmail = anythingUserEmail
            )

            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should ===("GREGORIAN:2019 CE")
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(dateValueHasCalendar)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(None)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(dateValueHasStartEra)
            savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasStartYear)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(None)
            savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(None)
            savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasStartEra)
        }

        "update a boolean value" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri
            val booleanValue: Boolean = false
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.updateBooleanValueRequest(
                resourceIri = resourceIri,
                valueIri = booleanValueIri.get,
                booleanValue = booleanValue
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            booleanValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.BooleanValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = booleanValueIri.get,
                userEmail = anythingUserEmail
            )

            val booleanValueAsBoolean: Boolean = savedValue.requireBoolean(OntologyConstants.KnoraApiV2Complex.BooleanValueAsBoolean)
            booleanValueAsBoolean should ===(booleanValue)
        }

        "update a geometry value" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.updateGeometryValueRequest(
                resourceIri = resourceIri,
                valueIri = geometryValueIri.get,
                geometryValue = SharedTestDataADM.geometryValue2
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            geometryValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.GeomValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = geometryValueIri.get,
                userEmail = anythingUserEmail
            )

            val geometryValueAsGeometry: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.GeometryValueAsGeometry)
            geometryValueAsGeometry should ===(SharedTestDataADM.geometryValue2)
        }

        "update an interval value" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
            val intervalStart = BigDecimal("5.6")
            val intervalEnd = BigDecimal("7.8")
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.updateIntervalValueRequest(
                resourceIri = resourceIri,
                valueIri = intervalValueIri.get,
                intervalStart = intervalStart,
                intervalEnd = intervalEnd
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            intervalValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.IntervalValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = intervalValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedIntervalValueHasStart: BigDecimal = savedValue.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2Complex.IntervalValueHasStart,
                expectedDatatype = OntologyConstants.Xsd.Decimal.toSmartIri,
                validationFun = stringFormatter.validateBigDecimal
            )

            savedIntervalValueHasStart should ===(intervalStart)

            val savedIntervalValueHasEnd: BigDecimal = savedValue.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2Complex.IntervalValueHasEnd,
                expectedDatatype = OntologyConstants.Xsd.Decimal.toSmartIri,
                validationFun = stringFormatter.validateBigDecimal
            )

            savedIntervalValueHasEnd should ===(intervalEnd)
        }

        "update a time value" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasTimeStamp".toSmartIri
            val timeStamp = Instant.parse("2019-12-16T09:14:56.409249Z")
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.updateTimeValueRequest(
                resourceIri = resourceIri,
                valueIri = timeValueIri.get,
                timeStamp = timeStamp
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            timeValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.TimeValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = timeValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedTimeStamp: Instant = savedValue.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2Complex.TimeValueAsTimeStamp,
                expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                validationFun = stringFormatter.xsdDateTimeStampToInstant
            )

            savedTimeStamp should ===(timeStamp)
        }

        "update a list value" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
            val listNode = "http://rdfh.ch/lists/0001/treeList02"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.updateListValueRequest(
                resourceIri = resourceIri,
                valueIri = listValueIri.get,
                listNode = listNode
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            listValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.ListValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = listValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedListValueHasListNode: IRI = savedValue.requireIriInObject(OntologyConstants.KnoraApiV2Complex.ListValueAsListNode, stringFormatter.validateAndEscapeIri)
            savedListValueHasListNode should ===(listNode)
        }

        "update a color value" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
            val color = "#ff3344"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.updateColorValueRequest(
                resourceIri = resourceIri,
                valueIri = colorValueIri.get,
                color = color
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            colorValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.ColorValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = colorValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedColor: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ColorValueAsColor)
            savedColor should ===(color)
        }

        "update a URI value" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
            val uri = "https://docs.knora.org"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.updateUriValueRequest(
                resourceIri = resourceIri,
                valueIri = uriValueIri.get,
                uri = uri
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            uriValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.UriValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = uriValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedUri: IRI = savedValue.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2Complex.UriValueAsUri,
                expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
                validationFun = stringFormatter.validateAndEscapeIri
            )

            savedUri should ===(uri)
        }

        "update a geoname value" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
            val geonameCode = "2988507"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.updateGeonameValueRequest(
                resourceIri = resourceIri,
                valueIri = geonameValueIri.get,
                geonameCode = geonameCode
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            geonameValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.GeonameValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = propertyIri,
                propertyIriInResult = propertyIri,
                expectedValueIri = geonameValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedGeonameCode: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.GeonameValueAsGeonameCode)
            savedGeonameCode should ===(geonameCode)
        }

        "update a link between two resources" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val linkPropertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThing".toSmartIri
            val linkValuePropertyIri: SmartIri = linkPropertyIri.fromLinkPropToLinkValueProp
            val linkTargetIri: IRI = "http://rdfh.ch/0001/5IEswyQFQp2bxXDrOyEfEA"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.updateLinkValueRequest(
                resourceIri = resourceIri,
                valueIri = linkValueIri.get,
                targetResourceIri = linkTargetIri
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)

            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            linkValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.LinkValue.toSmartIri)

            // When you change a link value's target, it gets a new UUID.
            val newLinkValueUUID: UUID = responseJsonDoc.body.requireStringWithValidation(OntologyConstants.KnoraApiV2Complex.ValueHasUUID, stringFormatter.validateBase64EncodedUuid)
            assert(newLinkValueUUID != linkValueUUID)
            linkValueUUID = newLinkValueUUID

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = linkPropertyIri,
                propertyIriInResult = linkValuePropertyIri,
                expectedValueIri = linkValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedTarget: JsonLDObject = savedValue.requireObject(OntologyConstants.KnoraApiV2Complex.LinkValueHasTarget)
            val savedTargetIri: IRI = savedTarget.requireString(JsonLDConstants.ID)
            savedTargetIri should ===(linkTargetIri)
        }

        "not update a link without a comment without changing it" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val linkTargetIri: IRI = "http://rdfh.ch/0001/5IEswyQFQp2bxXDrOyEfEA"

            val jsonLdEntity = SharedTestDataADM.updateLinkValueRequest(
                resourceIri = resourceIri,
                valueIri = linkValueIri.get,
                targetResourceIri = linkTargetIri
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.BadRequest, response.toString)
        }

        "update a link between two resources, adding a comment" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val linkPropertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThing".toSmartIri
            val linkValuePropertyIri: SmartIri = linkPropertyIri.fromLinkPropToLinkValueProp
            val linkTargetIri: IRI = "http://rdfh.ch/0001/5IEswyQFQp2bxXDrOyEfEA"
            val comment = "adding a comment"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.updateLinkValueRequest(
                resourceIri = resourceIri,
                valueIri = linkValueIri.get,
                targetResourceIri = linkTargetIri,
                comment = Some(comment)
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)

            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            linkValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.LinkValue.toSmartIri)

            // Since we only changed metadata, the UUID should be the same.
            val newLinkValueUUID: UUID = responseJsonDoc.body.requireStringWithValidation(OntologyConstants.KnoraApiV2Complex.ValueHasUUID, stringFormatter.validateBase64EncodedUuid)
            assert(newLinkValueUUID == linkValueUUID)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = linkPropertyIri,
                propertyIriInResult = linkValuePropertyIri,
                expectedValueIri = linkValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedComment: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueHasComment)
            savedComment should ===(comment)
        }

        "update a link between two resources, changing only the comment" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val linkPropertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThing".toSmartIri
            val linkValuePropertyIri: SmartIri = linkPropertyIri.fromLinkPropToLinkValueProp
            val linkTargetIri: IRI = "http://rdfh.ch/0001/5IEswyQFQp2bxXDrOyEfEA"
            val comment = "changing only the comment"
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

            val jsonLdEntity = SharedTestDataADM.updateLinkValueRequest(
                resourceIri = resourceIri,
                valueIri = linkValueIri.get,
                targetResourceIri = linkTargetIri,
                comment = Some(comment)
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)

            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            linkValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.LinkValue.toSmartIri)

            // Since we only changed metadata, the UUID should be the same.
            val newLinkValueUUID: UUID = responseJsonDoc.body.requireStringWithValidation(OntologyConstants.KnoraApiV2Complex.ValueHasUUID, stringFormatter.validateBase64EncodedUuid)
            assert(newLinkValueUUID == linkValueUUID)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = linkPropertyIri,
                propertyIriInResult = linkValuePropertyIri,
                expectedValueIri = linkValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedComment: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueHasComment)
            savedComment should ===(comment)
        }

        "not update a link with a comment without changing it" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val linkTargetIri: IRI = "http://rdfh.ch/0001/5IEswyQFQp2bxXDrOyEfEA"
            val comment = "changing only the comment"

            val jsonLdEntity = SharedTestDataADM.updateLinkValueRequest(
                resourceIri = resourceIri,
                valueIri = linkValueIri.get,
                targetResourceIri = linkTargetIri,
                comment = Some(comment)
            )

            val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.BadRequest, response.toString)
        }

        "create a link between two resources, with a comment" in {
            val resourceIri: IRI = SharedTestDataADM.AThing.iri
            val linkPropertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThing".toSmartIri
            val linkValuePropertyIri: SmartIri = linkPropertyIri.fromLinkPropToLinkValueProp
            val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)
            val comment = "Initial comment"

            val jsonLdEntity = SharedTestDataADM.createLinkValueRequest(
                resourceIri = resourceIri,
                targetResourceIri = SharedTestDataADM.TestDing.iri,
                valueHasComment = Some(comment)
            )

            val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)

            val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            linkValueIri.set(valueIri)
            val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
            valueType should ===(OntologyConstants.KnoraApiV2Complex.LinkValue.toSmartIri)

            val savedValue: JsonLDObject = getValue(
                resourceIri = resourceIri,
                maybePreviousLastModDate = maybeResourceLastModDate,
                propertyIriForGravsearch = linkPropertyIri,
                propertyIriInResult = linkValuePropertyIri,
                expectedValueIri = linkValueIri.get,
                userEmail = anythingUserEmail
            )

            val savedTarget: JsonLDObject = savedValue.requireObject(OntologyConstants.KnoraApiV2Complex.LinkValueHasTarget)
            val savedTargetIri: IRI = savedTarget.requireString(JsonLDConstants.ID)
            savedTargetIri should ===(SharedTestDataADM.TestDing.iri)

            val savedComment: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueHasComment)
            savedComment should ===(comment)
        }

        "delete an integer value" in {
            val jsonLdEntity = SharedTestDataADM.deleteIntValueRequest(
                resourceIri = SharedTestDataADM.AThing.iri,
                valueIri = intValueIri.get,
                maybeDeleteComment = Some("this value was incorrect")
            )

            val request = Post(baseApiUrl + "/v2/values/delete", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
        }

        "not delete an integer value if the simple schema is submitted" in {
            val jsonLdEntity =
                s"""{
                   |  "@id" : "${SharedTestDataADM.AThing.iri}",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInteger" : {
                   |    "@id" : "${intValueIri.get}",
                   |    "@type" : "knora-api:IntValue"
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/simple/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#"
                   |  }
                   |}""".stripMargin

            val request = Post(baseApiUrl + "/v2/values/delete", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.BadRequest, responseAsString)
        }

        "delete an integer value without supplying a delete comment" in {
            val resourceIri: IRI = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw"
            val valueIri: IRI = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/dJ1ES8QTQNepFKF5-EAqdg"

            val jsonLdEntity = SharedTestDataADM.deleteIntValueRequest(
                resourceIri = resourceIri,
                valueIri = valueIri,
                maybeDeleteComment = None
            )

            val deleteRequest = Post(baseApiUrl + "/v2/values/delete", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.anythingUser2.email, password))
            val deleteResponse: HttpResponse = singleAwaitingRequest(deleteRequest)
            assert(deleteResponse.status == StatusCodes.OK, deleteResponse.toString)

            // Request the resource as it was before the value was deleted.

            val getRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}?version=${URLEncoder.encode("2018-05-28T15:52:03.897Z", "UTF-8")}")
            val getResponse: HttpResponse = singleAwaitingRequest(getRequest)
            val getResponseAsString = responseToString(getResponse)
            assert(getResponse.status == StatusCodes.OK, getResponseAsString)
        }

        "delete a link between two resources" in {
            val jsonLdEntity = SharedTestDataADM.deleteLinkValueRequest(
                resourceIri = SharedTestDataADM.AThing.iri,
                valueIri = linkValueIri.get
            )

            val request = Post(baseApiUrl + "/v2/values/delete", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
        }
    }
}
