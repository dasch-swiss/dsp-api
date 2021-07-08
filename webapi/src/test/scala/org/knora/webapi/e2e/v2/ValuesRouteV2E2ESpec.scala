/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

import java.net.URLEncoder
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.unmarshalling.Unmarshal
import org.knora.webapi._
import org.knora.webapi.e2e.v2.ResponseCheckerV2.compareJSONLDForResourcesResponse
import org.knora.webapi.e2e.{ClientTestDataCollector, TestDataFileContent, TestDataFilePath}
import org.knora.webapi.exceptions.AssertionException
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.messages.util.search.SparqlQueryConstants
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util._
import org.xmlunit.builder.{DiffBuilder, Input}
import org.xmlunit.diff.Diff

import scala.concurrent.Await
import scala.concurrent.duration._

class ValuesRouteV2E2ESpec extends E2ESpec {

  private implicit def default(implicit system: ActorSystem): RouteTestTimeout =
    RouteTestTimeout(settings.defaultTimeout)

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private val anythingUserEmail = SharedTestDataADM.anythingUser1.email
  private val password = SharedTestDataADM.testPass

  private val intValueIri = new MutableTestIri
  private val intValueWithCustomPermissionsIri = new MutableTestIri
  private val intValueForRsyncIri = new MutableTestIri
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
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  // If true, writes some API responses to test data files. If false, compares the API responses to the existing test data files.
  private val writeTestDataFiles = false

  // Directory path for generated client test data
  private val clientTestDataPath: Seq[String] = Seq("v2", "values")

  // Collects client test data
  private val clientTestDataCollector = new ClientTestDataCollector(settings)

  object AThing {
    val iri: IRI = "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA"
    val iriEncoded: String = URLEncoder.encode(iri, "UTF-8")
  }

  object TestDing {
    val iri: IRI = "http://rdfh.ch/resources/H6gBWUuJSuuO-CilHV8kQw"
    val iriEncoded: String = URLEncoder.encode(iri, "UTF-8")

    val intValueIri: IRI = "http://rdfh.ch/resources/H6gBWUuJSuuO-CilHV8kQw/values/dJ1ES8QTQNepFKF5-EAqdg"
    val decimalValueIri: IRI = "http://rdfh.ch/resources/H6gBWUuJSuuO-CilHV8kQw/values/bXMwnrHvQH2DMjOFrGmNzg"
    val dateValueIri: IRI = "http://rdfh.ch/resources/H6gBWUuJSuuO-CilHV8kQw/values/-rG4F5FTTu2iB5mTBPVn5Q"
    val booleanValueIri: IRI = "http://rdfh.ch/resources/H6gBWUuJSuuO-CilHV8kQw/values/IN4R19yYR0ygi3K2VEHpUQ"
    val uriValueIri: IRI = "http://rdfh.ch/resources/H6gBWUuJSuuO-CilHV8kQw/values/uBAmWuRhR-eo1u1eP7qqNg"
    val intervalValueIri: IRI = "http://rdfh.ch/resources/H6gBWUuJSuuO-CilHV8kQw/values/RbDKPKHWTC-0lkRKae-E6A"
    val timeValueIri: IRI = "http://rdfh.ch/resources/H6gBWUuJSuuO-CilHV8kQw/values/l6DhS5SCT9WhXSoYEZRTRw"
    val colorValueIri: IRI = "http://rdfh.ch/resources/H6gBWUuJSuuO-CilHV8kQw/values/TAziKNP8QxuyhC4Qf9-b6w"
    val geomValueIri: IRI = "http://rdfh.ch/resources/H6gBWUuJSuuO-CilHV8kQw/values/we-ybmj-SRen-91n4RaDOQ"
    val geonameValueIri: IRI = "http://rdfh.ch/resources/H6gBWUuJSuuO-CilHV8kQw/values/hty-ONF8SwKN2RKU7rLKDg"
    val textValueWithStandoffIri: IRI = "http://rdfh.ch/resources/H6gBWUuJSuuO-CilHV8kQw/values/rvB4eQ5MTF-Qxq0YgkwaDg"
    val textValueWithoutStandoffIri: IRI =
      "http://rdfh.ch/resources/H6gBWUuJSuuO-CilHV8kQw/values/SZyeLLmOTcCCuS3B0VksHQ"
    val listValueIri: IRI = "http://rdfh.ch/resources/H6gBWUuJSuuO-CilHV8kQw/values/XAhEeE3kSVqM4JPGdLt4Ew"
    val linkValueIri: IRI = "http://rdfh.ch/resources/H6gBWUuJSuuO-CilHV8kQw/values/uvRVxzL1RD-t9VIQ1TpfUw"

    val intValueUuid = "dJ1ES8QTQNepFKF5-EAqdg"
    val decimalValueUuid = "bXMwnrHvQH2DMjOFrGmNzg"
    val dateValueUuid = "-rG4F5FTTu2iB5mTBPVn5Q"
    val booleanValueUuid = "IN4R19yYR0ygi3K2VEHpUQ"
    val uriValueUuid = "uBAmWuRhR-eo1u1eP7qqNg"
    val intervalValueUuid = "RbDKPKHWTC-0lkRKae-E6A"
    val timeValueUuid = "l6DhS5SCT9WhXSoYEZRTRw"
    val colorValueUuid = "TAziKNP8QxuyhC4Qf9-b6w"
    val geomValueUuid = "we-ybmj-SRen-91n4RaDOQ"
    val geonameValueUuid = "hty-ONF8SwKN2RKU7rLKDg"
    val textValueWithStandoffUuid = "rvB4eQ5MTF-Qxq0YgkwaDg"
    val textValueWithoutStandoffUuid = "SZyeLLmOTcCCuS3B0VksHQ"
    val listValueUuid = "XAhEeE3kSVqM4JPGdLt4Ew"
    val linkValueUuid = "uvRVxzL1RD-t9VIQ1TpfUw"
  }

  object AThingPicture {
    val iri: IRI = "http://rdfh.ch/resources/h2TEa725XPK7CfG15Bk0bA"
    val iriEncoded: String = URLEncoder.encode(iri, "UTF-8")
    val stillImageFileValueUuid: IRI = "goZ7JFRNSeqF-dNxsqAS7Q"
  }

  private def getResourceWithValues(resourceIri: IRI,
                                    propertyIrisForGravsearch: Seq[SmartIri],
                                    userEmail: String): JsonLDDocument = {
    // Make a Gravsearch query from a template.
    val gravsearchQuery: String = org.knora.webapi.messages.twirl.queries.gravsearch.txt
      .getResourceWithSpecifiedProperties(
        resourceIri = resourceIri,
        propertyIris = propertyIrisForGravsearch
      )
      .toString()

    // Run the query.

    val request = Post(baseApiUrl + "/v2/searchextended",
                       HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(
      BasicHttpCredentials(userEmail, password))
    val response: HttpResponse = singleAwaitingRequest(request)
    assert(response.status == StatusCodes.OK, response.toString)
    responseToJsonLDDocument(response)
  }

  private def getValuesFromResource(resource: JsonLDDocument, propertyIriInResult: SmartIri): JsonLDArray = {
    resource.requireArray(propertyIriInResult.toString)
  }

  private def getValueFromResource(resource: JsonLDDocument,
                                   propertyIriInResult: SmartIri,
                                   expectedValueIri: IRI): JsonLDObject = {
    val resourceIri: IRI = resource.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
    val propertyValues: JsonLDArray =
      getValuesFromResource(resource = resource, propertyIriInResult = propertyIriInResult)

    val matchingValues: Seq[JsonLDObject] = propertyValues.value.collect {
      case jsonLDObject: JsonLDObject
          if jsonLDObject.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri) == expectedValueIri =>
        jsonLDObject
    }

    if (matchingValues.isEmpty) {
      throw AssertionException(
        s"Property <$propertyIriInResult> of resource <$resourceIri> does not have value <$expectedValueIri>")
    }

    if (matchingValues.size > 1) {
      throw AssertionException(
        s"Property <$propertyIriInResult> of resource <$resourceIri> has more than one value with the IRI <$expectedValueIri>")
    }

    matchingValues.head
  }

  private def parseResourceLastModificationDate(resource: JsonLDDocument): Option[Instant] = {
    resource.maybeObject(OntologyConstants.KnoraApiV2Complex.LastModificationDate).map { jsonLDObject =>
      jsonLDObject.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.validateAndEscapeIri) should ===(
        OntologyConstants.Xsd.DateTimeStamp)
      jsonLDObject.requireStringWithValidation(JsonLDKeywords.VALUE, stringFormatter.xsdDateTimeStampToInstant)
    }
  }

  private def getResourceLastModificationDate(resourceIri: IRI, userEmail: String): Option[Instant] = {
    val request = Get(baseApiUrl + s"/v2/resourcespreview/${URLEncoder.encode(resourceIri, "UTF-8")}") ~> addCredentials(
      BasicHttpCredentials(userEmail, password))
    val response: HttpResponse = singleAwaitingRequest(request)
    assert(response.status == StatusCodes.OK, response.toString)
    val resource: JsonLDDocument = responseToJsonLDDocument(response)
    parseResourceLastModificationDate(resource)
  }

  private def checkLastModDate(resourceIri: IRI,
                               maybePreviousLastModDate: Option[Instant],
                               maybeUpdatedLastModDate: Option[Instant]): Unit = {
    maybeUpdatedLastModDate match {
      case Some(updatedLastModDate) =>
        maybePreviousLastModDate match {
          case Some(previousLastModDate) => assert(updatedLastModDate.isAfter(previousLastModDate))
          case None                      => ()
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

  private def createTextValueWithoutStandoffRequest(resourceIri: IRI, valueAsString: String): String = {
    s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasText" : {
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:valueAsString" : "$valueAsString"
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
  }

  private val textValue1AsXmlWithStandardMapping: String =
    """<?xml version="1.0" encoding="UTF-8"?>
          |<text>
          |   This text links to another <a class="salsah-link" href="http://rdfh.ch/resources/4ExjVniQehNpL3hQsU3Dgw">resource</a>.
          |   And this <strong id="link_id">strong value</strong> is linked by this <a class="internal-link" href="#link_id">link</a>
          |</text>""".stripMargin

  private val standardMappingIri: IRI = "http://rdfh.ch/standoff/mappings/StandardMapping"

  private val geometryValue1 =
    """{"status":"active","lineColor":"#ff3333","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""

  private def createTextValueWithStandoffRequest(resourceIri: IRI, textValueAsXml: String, mappingIri: String)(
      implicit stringFormatter: StringFormatter): String = {
    s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasText" : {
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:textValueAsXml" : ${stringFormatter.toJsonEncodedString(textValueAsXml)},
           |    "knora-api:textValueHasMapping" : {
           |      "@id": "$mappingIri"
           |    }
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
  }

  private def createDateValueWithDayPrecisionRequest(resourceIri: IRI,
                                                     dateValueHasCalendar: String,
                                                     dateValueHasStartYear: Int,
                                                     dateValueHasStartMonth: Int,
                                                     dateValueHasStartDay: Int,
                                                     dateValueHasStartEra: String,
                                                     dateValueHasEndYear: Int,
                                                     dateValueHasEndMonth: Int,
                                                     dateValueHasEndDay: Int,
                                                     dateValueHasEndEra: String): String = {
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
  }

  private def createIslamicDateValueWithDayPrecisionRequest(resourceIri: IRI,
                                                            dateValueHasCalendar: String,
                                                            dateValueHasStartYear: Int,
                                                            dateValueHasStartMonth: Int,
                                                            dateValueHasStartDay: Int,
                                                            dateValueHasEndYear: Int,
                                                            dateValueHasEndMonth: Int,
                                                            dateValueHasEndDay: Int): String = {
    s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasDate" : {
           |    "@type" : "knora-api:DateValue",
           |    "knora-api:dateValueHasCalendar" : "$dateValueHasCalendar",
           |    "knora-api:dateValueHasStartYear" : $dateValueHasStartYear,
           |    "knora-api:dateValueHasStartMonth" : $dateValueHasStartMonth,
           |    "knora-api:dateValueHasStartDay" : $dateValueHasStartDay,
           |    "knora-api:dateValueHasEndYear" : $dateValueHasEndYear,
           |    "knora-api:dateValueHasEndMonth" : $dateValueHasEndMonth,
           |    "knora-api:dateValueHasEndDay" : $dateValueHasEndDay
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
  }

  private def createDateValueWithMonthPrecisionRequest(resourceIri: IRI,
                                                       dateValueHasCalendar: String,
                                                       dateValueHasStartYear: Int,
                                                       dateValueHasStartMonth: Int,
                                                       dateValueHasStartEra: String,
                                                       dateValueHasEndYear: Int,
                                                       dateValueHasEndMonth: Int,
                                                       dateValueHasEndEra: String): String = {
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
  }

  private def createDateValueWithYearPrecisionRequest(resourceIri: IRI,
                                                      dateValueHasCalendar: String,
                                                      dateValueHasStartYear: Int,
                                                      dateValueHasStartEra: String,
                                                      dateValueHasEndYear: Int,
                                                      dateValueHasEndEra: String): String = {
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
  }

  private def updateTextValueWithoutStandoffRequest(resourceIri: IRI, valueIri: IRI, valueAsString: String): String = {
    s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasText" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:valueAsString" : "$valueAsString"
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
  }

  private val textValue2AsXmlWithStandardMapping: String =
    """<?xml version="1.0" encoding="UTF-8"?>
          |<text>
          |   This updated text links to another <a class="salsah-link" href="http://rdfh.ch/resources/4ExjVniQehNpL3hQsU3Dgw">resource</a>.
          |</text>""".stripMargin

  private def updateTextValueWithCommentRequest(resourceIri: IRI,
                                                valueIri: IRI,
                                                valueAsString: String,
                                                valueHasComment: String): String = {
    s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasText" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:valueAsString" : "$valueAsString",
           |    "knora-api:valueHasComment" : "$valueHasComment"
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
  }

  private def updateDateValueWithDayPrecisionRequest(resourceIri: IRI,
                                                     valueIri: IRI,
                                                     dateValueHasCalendar: String,
                                                     dateValueHasStartYear: Int,
                                                     dateValueHasStartMonth: Int,
                                                     dateValueHasStartDay: Int,
                                                     dateValueHasStartEra: String,
                                                     dateValueHasEndYear: Int,
                                                     dateValueHasEndMonth: Int,
                                                     dateValueHasEndDay: Int,
                                                     dateValueHasEndEra: String): String = {
    s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasDate" : {
           |    "@id" : "$valueIri",
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
  }

  private def updateDateValueWithMonthPrecisionRequest(resourceIri: IRI,
                                                       valueIri: IRI,
                                                       dateValueHasCalendar: String,
                                                       dateValueHasStartYear: Int,
                                                       dateValueHasStartMonth: Int,
                                                       dateValueHasStartEra: String,
                                                       dateValueHasEndYear: Int,
                                                       dateValueHasEndMonth: Int,
                                                       dateValueHasEndEra: String): String = {
    s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasDate" : {
           |    "@id" : "$valueIri",
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
  }

  private def updateDateValueWithYearPrecisionRequest(resourceIri: IRI,
                                                      valueIri: IRI,
                                                      dateValueHasCalendar: String,
                                                      dateValueHasStartYear: Int,
                                                      dateValueHasStartEra: String,
                                                      dateValueHasEndYear: Int,
                                                      dateValueHasEndEra: String): String = {
    s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasDate" : {
           |    "@id" : "$valueIri",
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
  }

  private val geometryValue2 =
    """{"status":"active","lineColor":"#ff3344","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""

  private def updateLinkValueRequest(resourceIri: IRI,
                                     valueIri: IRI,
                                     targetResourceIri: IRI,
                                     comment: Option[String] = None): String = {
    comment match {
      case Some(definedComment) =>
        s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasOtherThingValue" : {
                   |    "@id" : "$valueIri",
                   |    "@type" : "knora-api:LinkValue",
                   |    "knora-api:linkValueHasTargetIri" : {
                   |      "@id" : "$targetResourceIri"
                   |    },
                   |    "knora-api:valueHasComment" : "$definedComment"
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

      case None =>
        s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasOtherThingValue" : {
                   |    "@id" : "$valueIri",
                   |    "@type" : "knora-api:LinkValue",
                   |    "knora-api:linkValueHasTargetIri" : {
                   |      "@id" : "$targetResourceIri"
                   |    }
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin
    }
  }

  private def deleteIntValueRequest(resourceIri: IRI, valueIri: IRI, maybeDeleteComment: Option[String]): String = {
    maybeDeleteComment match {
      case Some(deleteComment) =>
        s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInteger" : {
                   |    "@id" : "$valueIri",
                   |    "@type" : "knora-api:IntValue",
                   |    "knora-api:deleteComment" : "$deleteComment"
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

      case None =>
        s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInteger" : {
                   |    "@id" : "$valueIri",
                   |    "@type" : "knora-api:IntValue"
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin
    }
  }

  private def updateIntValueWithCustomNewValueVersionIriRequest(resourceIri: IRI,
                                                                valueIri: IRI,
                                                                intValue: Int,
                                                                newValueVersionIri: IRI): String = {
    s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasInteger" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:IntValue",
           |    "knora-api:newValueVersionIri" : {
           |      "@id" : "$newValueVersionIri"
           |    },
           |    "knora-api:intValueAsInt" : $intValue
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
           |  }
           |}""".stripMargin
  }

  /**
    * Gets a value from a resource by UUID, compares the response to the expected response, and
    * adds the response to the client test data.
    *
    * @param resourceIri  the resource IRI.
    * @param valueUuid    the value UUID.
    * @param fileBasename the basename of the test data file.
    */
  private def testValue(resourceIri: IRI, valueUuid: String, fileBasename: String): Unit = {
    val resourceIriEncoded = URLEncoder.encode(resourceIri, "UTF-8")
    val request = Get(s"$baseApiUrl/v2/values/$resourceIriEncoded/$valueUuid") ~> addCredentials(
      BasicHttpCredentials(SharedTestDataADM.anythingUser1.email, SharedTestDataADM.testPass))
    val response: HttpResponse = singleAwaitingRequest(request)
    val responseStr = responseToString(response)
    assert(response.status == StatusCodes.OK, responseStr)
    val expectedResponseStr =
      readOrWriteTextFile(responseStr, Paths.get(s"test_data/valuesE2EV2/$fileBasename.jsonld"), writeTestDataFiles)
    compareJSONLDForResourcesResponse(expectedJSONLD = expectedResponseStr, receivedJSONLD = responseStr)

    clientTestDataCollector.addFile(
      TestDataFileContent(
        filePath = TestDataFilePath(
          directoryPath = clientTestDataPath,
          filename = fileBasename,
          fileExtension = "json"
        ),
        text = responseStr
      )
    )
  }
  private val customValueUUID = "CpO1TIDf1IS55dQbyIuDsA"
  private val customValueIri: IRI = s"http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/$customValueUUID"

  "The values v2 endpoint" should {
    "get the latest versions of values, given their UUIDs" in {
      // The UUIDs of values in TestDing.
      val testDingValues: Map[String, String] = Map(
        "int-value" -> TestDing.intValueUuid,
        "decimal-value" -> TestDing.decimalValueUuid,
        "date-value" -> TestDing.dateValueUuid,
        "boolean-value" -> TestDing.booleanValueUuid,
        "uri-value" -> TestDing.uriValueUuid,
        "interval-value" -> TestDing.intervalValueUuid,
        "time-value" -> TestDing.timeValueUuid,
        "color-value" -> TestDing.colorValueUuid,
        "geom-value" -> TestDing.geomValueUuid,
        "geoname-value" -> TestDing.geonameValueUuid,
        "text-value-with-standoff" -> TestDing.textValueWithStandoffUuid,
        "text-value-without-standoff" -> TestDing.textValueWithoutStandoffUuid,
        "list-value" -> TestDing.listValueUuid,
        "link-value" -> TestDing.linkValueUuid
      )

      testDingValues.foreach {
        case (valueTypeName, valueUuid) =>
          testValue(
            resourceIri = TestDing.iri,
            valueUuid = valueUuid,
            fileBasename = s"get-$valueTypeName-response"
          )
      }

      testValue(
        resourceIri = AThingPicture.iri,
        valueUuid = AThingPicture.stillImageFileValueUuid,
        fileBasename = "get-still-image-file-value-response"
      )
    }

    "get a past version of a value, given its UUID and a timestamp" in {
      val resourceIri = URLEncoder.encode("http://rdfh.ch/resources/0xfMLw0jVhmBIoAxuTbVxw", "UTF-8")
      val valueUuid = "pLlW4ODASumZfZFbJdpw1g"
      val timestamp = "20190212T090510Z"

      val request = Get(baseApiUrl + s"/v2/values/$resourceIri/$valueUuid?version=$timestamp") ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)

      val value: JsonLDObject = getValueFromResource(
        resource = responseJsonDoc,
        propertyIriInResult = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri,
        expectedValueIri = "http://rdfh.ch/resources/0xfMLw0jVhmBIoAxuTbVxw/values/1b"
      )

      val intValueAsInt: Int = value.requireInt(OntologyConstants.KnoraApiV2Complex.IntValueAsInt)
      intValueAsInt should ===(2)
    }

    "create an integer value" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue: Int = 4
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-int-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseStr: String = responseToString(response)
      assert(response.status == StatusCodes.OK, responseStr)
      val responseJsonDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(responseStr)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      intValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri)
      integerValueUUID =
        responseJsonDoc.body.requireStringWithValidation(OntologyConstants.KnoraApiV2Complex.ValueHasUUID,
                                                         stringFormatter.validateBase64EncodedUuid)

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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-value-response",
            fileExtension = "json"
          ),
          text = responseStr
        )
      )
    }

    "create an integer value with a custom value IRI" in {
      val resourceIri: IRI = AThing.iri
      val intValue: Int = 30

      val jsonLDEntity =
        s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInteger" : {
                   |    "@id" : "$customValueIri",
                   |    "@type" : "knora-api:IntValue",
                   |    "knora-api:intValueAsInt" : $intValue
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
                   |  }
                   |}""".stripMargin

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-int-value-with-custom-Iri-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(valueIri == customValueIri)
      val valueUUID = responseJsonDoc.body.requireString(OntologyConstants.KnoraApiV2Complex.ValueHasUUID)
      assert(valueUUID == customValueUUID)
    }

    "return a DuplicateValueException during value creation when the supplied value IRI is not unique" in {

      // duplicate value IRI
      val params =
        s"""{
                   |  "@id" : "${AThing.iri}",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInteger" : {
                   |    "@id" : "$customValueIri",
                   |    "@type" : "knora-api:IntValue",
                   |    "knora-api:intValueAsInt" : 43
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
                   |  }
                   |}""".stripMargin

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.BadRequest, response.toString)

      val errorMessage: String = Await.result(Unmarshal(response.entity).to[String], 1.second)
      val invalidIri: Boolean = errorMessage.contains(s"IRI: '$customValueIri' already exists, try another one.")
      invalidIri should be(true)
    }

    "create an integer value with a custom UUID" in {
      val resourceIri: IRI = AThing.iri
      val intValue: Int = 45
      val intValueCustomUUID = "IN4R19yYR0ygi3K2VEHpUQ"

      val jsonLDEntity =
        s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInteger" : {
                   |    "@type" : "knora-api:IntValue",
                   |    "knora-api:intValueAsInt" : $intValue,
                   |    "knora-api:valueHasUUID" : "$intValueCustomUUID"
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
                   |  }
                   |}""".stripMargin

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-int-value-with-custom-UUID-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)

      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueUUID: String = responseJsonDoc.body.requireString(OntologyConstants.KnoraApiV2Complex.ValueHasUUID)
      assert(valueUUID == intValueCustomUUID)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(valueIri.endsWith(valueUUID))

    }

    "do not create an integer value if the custom UUID is not part of the custom IRI" in {
      val resourceIri: IRI = AThing.iri
      val intValue: Int = 45
      val aUUID = "IN4R19yYR0ygi3K2VEHpUQ"
      val valueIri = s"http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/IN4R19yYR0ygi3K2VEHpNN"
      val jsonLDEntity =
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasInteger" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:IntValue",
           |    "knora-api:intValueAsInt" : $intValue,
           |    "knora-api:valueHasUUID" : "$aUUID"
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
           |  }
           |}""".stripMargin

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-int-value-with-custom-UUID-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)

      assert(response.status == StatusCodes.BadRequest, response.toString)
    }

    "create an integer value with a custom creation date" in {
      val customCreationDate: Instant = Instant.parse("2020-06-04T11:36:54.502951Z")
      val resourceIri: IRI = AThing.iri
      val intValue: Int = 25

      val jsonLDEntity =
        s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInteger" : {
                   |    "@type" : "knora-api:IntValue",
                   |    "knora-api:intValueAsInt" : $intValue,
                   |    "knora-api:valueCreationDate" : {
                   |        "@type" : "xsd:dateTimeStamp",
                   |        "@value" : "$customCreationDate"
                   |      }
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
                   |  }
                   |}""".stripMargin

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-int-value-with-custom-creationDate-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)

      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      intValueForRsyncIri.set(valueIri)

      val savedCreationDate: Instant = responseJsonDoc.body.requireDatatypeValueInObject(
        key = OntologyConstants.KnoraApiV2Complex.ValueCreationDate,
        expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
        validationFun = stringFormatter.xsdDateTimeStampToInstant
      )

      assert(savedCreationDate == customCreationDate)
    }

    "create an integer value with custom IRI, UUID, and creation date" in {
      val resourceIri: IRI = AThing.iri
      val intValue: Int = 10
      val customValueIri: IRI = "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/7VDvMOnuitf_r1Ju7BglsQ"
      val customValueUUID = "7VDvMOnuitf_r1Ju7BglsQ"
      val customCreationDate: Instant = Instant.parse("2020-06-04T12:58:54.502951Z")

      val jsonLDEntity =
        s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInteger" : {
                   |    "@id" : "$customValueIri",
                   |    "@type" : "knora-api:IntValue",
                   |    "knora-api:intValueAsInt" : $intValue,
                   |    "knora-api:valueHasUUID" : "$customValueUUID",
                   |    "knora-api:valueCreationDate" : {
                   |        "@type" : "xsd:dateTimeStamp",
                   |        "@value" : "$customCreationDate"
                   |      }
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
                   |  }
                   |}""".stripMargin

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-int-value-with-custom-Iri-UUID-CreationDate-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
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
      val resourceIri: IRI = AThing.iri
      val intValue: Int = 10

      val jsonLDEntity =
        s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInteger" : $intValue,
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/simple/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#"
                   |  }
                   |}""".stripMargin

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.BadRequest, responseAsString)
    }

    "create an integer value with custom permissions" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue: Int = 1
      val customPermissions: String = "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher"
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-int-value-with-custom-permissions-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      intValueWithCustomPermissionsIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueWithCustomPermissionsIri.get,
        userEmail = anythingUserEmail
      )

      val intValueAsInt: Int = savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.IntValueAsInt)
      intValueAsInt should ===(intValue)
      val hasPermissions = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.HasPermissions)
      hasPermissions should ===(customPermissions)
    }

    "create a text value without standoff and without a comment" in {
      val resourceIri: IRI = AThing.iri
      val valueAsString: String = "text without standoff"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity: String = createTextValueWithoutStandoffRequest(
        resourceIri = resourceIri,
        valueAsString = valueAsString
      )

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-text-value-without-standoff-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      textValueWithoutStandoffIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      val resourceIri: IRI = AThing.iri
      val valueAsString: String = "text without standoff"

      val jsonLDEntity = updateTextValueWithoutStandoffRequest(
        resourceIri = resourceIri,
        valueIri = textValueWithoutStandoffIri.get,
        valueAsString = valueAsString
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.BadRequest, response.toString)
    }

    "not update a text value so it's empty" in {
      val resourceIri: IRI = AThing.iri
      val valueAsString: String = ""

      val jsonLDEntity = updateTextValueWithoutStandoffRequest(
        resourceIri = resourceIri,
        valueIri = textValueWithoutStandoffIri.get,
        valueAsString = valueAsString
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.BadRequest, response.toString)
    }

    "update a text value without standoff" in {
      val resourceIri: IRI = AThing.iri
      val valueAsString: String = "text without standoff updated"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity = updateTextValueWithoutStandoffRequest(
        resourceIri = resourceIri,
        valueIri = textValueWithoutStandoffIri.get,
        valueAsString = valueAsString
      )

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-text-value-without-standoff-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      textValueWithoutStandoffIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      val resourceIri: IRI = AThing.iri
      val valueAsString: String = "text without standoff updated"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity = updateTextValueWithCommentRequest(
        resourceIri = resourceIri,
        valueIri = textValueWithoutStandoffIri.get,
        valueAsString = valueAsString,
        valueHasComment = "Adding a comment"
      )

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-text-value-with-comment-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      textValueWithoutStandoffIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      val resourceIri: IRI = AThing.iri
      val valueAsString: String = "text without standoff updated"

      val jsonLDEntity = updateTextValueWithCommentRequest(
        resourceIri = resourceIri,
        valueIri = textValueWithoutStandoffIri.get,
        valueAsString = valueAsString,
        valueHasComment = "Adding a comment"
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.BadRequest, response.toString)
    }

    "update a text value without standoff, changing only the a comment" in {
      val resourceIri: IRI = AThing.iri
      val valueAsString: String = "text without standoff updated"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity = updateTextValueWithCommentRequest(
        resourceIri = resourceIri,
        valueIri = textValueWithoutStandoffIri.get,
        valueAsString = valueAsString,
        valueHasComment = "Updated comment"
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      textValueWithoutStandoffIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      val resourceIri: IRI = AThing.iri
      val valueAsString: String = "this is a text value that has a comment"
      val valueHasComment: String = "this is a comment"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
        s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasText" : {
                   |    "@type" : "knora-api:TextValue",
                   |    "knora-api:valueAsString" : "$valueAsString",
                   |    "knora-api:valueHasComment" : "$valueHasComment"
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-text-value-with-comment-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      textValueWithoutStandoffIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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

    "create a text value with standoff test1" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity = createTextValueWithStandoffRequest(
        resourceIri = resourceIri,
        textValueAsXml = textValue1AsXmlWithStandardMapping,
        mappingIri = standardMappingIri
      )

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-text-value-with-standoff-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseStr = responseToString(response)
      assert(response.status == StatusCodes.OK, responseStr)
      val responseJsonDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(responseStr)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      textValueWithStandoffIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      val xmlDiff: Diff = DiffBuilder
        .compare(Input.fromString(textValue1AsXmlWithStandardMapping))
        .withTest(Input.fromString(savedTextValueAsXml))
        .build()
      xmlDiff.hasDifferences should be(false)
    }

    "create a very long text value with standoff and linked tags" in {
      val resourceIri: IRI = AThing.iri

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

      val jsonLDEntity = createTextValueWithStandoffRequest(
        resourceIri = resourceIri,
        textValueAsXml = textValueAsXml,
        mappingIri = standardMappingIri
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      textValueWithStandoffIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      val xmlDiff: Diff =
        DiffBuilder.compare(Input.fromString(textValueAsXml)).withTest(Input.fromString(savedTextValueAsXml)).build()
      xmlDiff.hasDifferences should be(false)
    }

    "create a text value with standoff containing a URL" in {
      val resourceIri: IRI = AThing.iri

      val textValueAsXml: String =
        """<?xml version="1.0" encoding="UTF-8"?>
                  |<text>
                  |   This text links to <a href="http://www.knora.org">a web site</a>.
                  |</text>
                """.stripMargin

      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity = createTextValueWithStandoffRequest(
        resourceIri = resourceIri,
        textValueAsXml = textValueAsXml,
        mappingIri = standardMappingIri
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      val resourceIri = AThing.iri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)
      val jsonLDEntity = FileUtil.readTextFile(Paths.get("test_data/valuesE2EV2/CreateValueWithEscape.jsonld"))
      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      textValueWithEscapeIri.set(valueIri)
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri

      val savedValue: JsonLDObject = getValue(
        resourceIri = AThing.iri,
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

      val xmlFileToSend = Paths.get("test_data/test_route/texts/mappingForHTML.xml")

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
          HttpEntity.fromPath(MediaTypes.`text/xml`.toContentType(HttpCharsets.`UTF-8`), xmlFileToSend),
          Map("filename" -> "HTMLMapping.xml")
        )
      )

      // create standoff from XML
      val mappingRequest = Post(baseApiUrl + "/v2/mapping", formDataMapping) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val mappingResponse: HttpResponse = singleAwaitingRequest(mappingRequest)
      assert(mappingResponse.status == StatusCodes.OK, mappingResponse.toString)

      // Create the text value.

      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val textValueAsXml =
        """<?xml version="1.0" encoding="UTF-8"?>
                  |<text documentType="html">
                  |    <p>This an <span data-description="an &quot;event&quot;" data-date="GREGORIAN:2017-01-27 CE" class="event">event</span>.</p>
                  |</text>""".stripMargin

      val jsonLDEntity = createTextValueWithStandoffRequest(
        resourceIri = resourceIri,
        textValueAsXml = textValueAsXml,
        mappingIri = s"${SharedTestDataADM.ANYTHING_PROJECT_IRI}/mappings/HTMLMapping"
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
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
      val resourceIri: IRI = AThing.iri
      val valueAsString: String = ""

      val jsonLDEntity = createTextValueWithoutStandoffRequest(
        resourceIri = resourceIri,
        valueAsString = valueAsString
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.BadRequest, response.toString)
    }

    "create a decimal value" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
      val decimalValueAsDecimal = BigDecimal(4.3)
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-decimal-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      decimalValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      val resourceIri: IRI = AThing.iri
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

      val jsonLDEntity = createDateValueWithDayPrecisionRequest(
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-date-value-with-day-precision-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      dateValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.DateValue.toSmartIri)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = dateValueIri.get,
        userEmail = anythingUserEmail
      )

      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should ===(
        "GREGORIAN:2018-10-05 CE:2018-10-06 CE")
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(
        dateValueHasCalendar)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(
        dateValueHasStartMonth)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(dateValueHasStartDay)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(
        dateValueHasStartEra)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasEndYear)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(dateValueHasEndMonth)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(dateValueHasEndDay)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasEndEra)
    }

    "create a date value representing a range with month precision" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar = "GREGORIAN"
      val dateValueHasStartYear = 2018
      val dateValueHasStartMonth = 10
      val dateValueHasStartEra = "CE"
      val dateValueHasEndYear = 2018
      val dateValueHasEndMonth = 11
      val dateValueHasEndEra = "CE"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity = createDateValueWithMonthPrecisionRequest(
        resourceIri = resourceIri,
        dateValueHasCalendar = dateValueHasCalendar,
        dateValueHasStartYear = dateValueHasStartYear,
        dateValueHasStartMonth = dateValueHasStartMonth,
        dateValueHasStartEra = dateValueHasStartEra,
        dateValueHasEndYear = dateValueHasEndYear,
        dateValueHasEndMonth = dateValueHasEndMonth,
        dateValueHasEndEra = dateValueHasEndEra
      )

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-date-value-with-month-precision-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      dateValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.DateValue.toSmartIri)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = dateValueIri.get,
        userEmail = anythingUserEmail
      )

      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should ===(
        "GREGORIAN:2018-10 CE:2018-11 CE")
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(
        dateValueHasCalendar)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(
        dateValueHasStartMonth)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(None)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(
        dateValueHasStartEra)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasEndYear)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(dateValueHasEndMonth)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(None)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasEndEra)
    }

    "create a date value representing a range with year precision" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar = "GREGORIAN"
      val dateValueHasStartYear = 2018
      val dateValueHasStartEra = "CE"
      val dateValueHasEndYear = 2019
      val dateValueHasEndEra = "CE"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity = createDateValueWithYearPrecisionRequest(
        resourceIri = resourceIri,
        dateValueHasCalendar = dateValueHasCalendar,
        dateValueHasStartYear = dateValueHasStartYear,
        dateValueHasStartEra = dateValueHasStartEra,
        dateValueHasEndYear = dateValueHasEndYear,
        dateValueHasEndEra = dateValueHasEndEra
      )

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-date-value-with-year-precision-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      dateValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.DateValue.toSmartIri)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = dateValueIri.get,
        userEmail = anythingUserEmail
      )

      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should ===(
        "GREGORIAN:2018 CE:2019 CE")
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(
        dateValueHasCalendar)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(None)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(None)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(
        dateValueHasStartEra)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasEndYear)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(None)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(None)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasEndEra)
    }

    "create a date value representing a single date with day precision" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar = "GREGORIAN"
      val dateValueHasStartYear = 2018
      val dateValueHasStartMonth = 10
      val dateValueHasStartDay = 5
      val dateValueHasStartEra = "CE"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity: String = createDateValueWithDayPrecisionRequest(
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

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      dateValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(
        dateValueHasCalendar)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(
        dateValueHasStartMonth)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(dateValueHasStartDay)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(
        dateValueHasStartEra)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasStartYear)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(dateValueHasStartMonth)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(dateValueHasStartDay)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasStartEra)
    }

    "create a date value representing a single date with month precision" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar = "GREGORIAN"
      val dateValueHasStartYear = 2018
      val dateValueHasStartMonth = 10
      val dateValueHasStartEra = "CE"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity = createDateValueWithMonthPrecisionRequest(
        resourceIri = resourceIri,
        dateValueHasCalendar = dateValueHasCalendar,
        dateValueHasStartYear = dateValueHasStartYear,
        dateValueHasStartMonth = dateValueHasStartMonth,
        dateValueHasStartEra = dateValueHasStartEra,
        dateValueHasEndYear = dateValueHasStartYear,
        dateValueHasEndMonth = dateValueHasStartMonth,
        dateValueHasEndEra = dateValueHasStartEra
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      dateValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(
        dateValueHasCalendar)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(
        dateValueHasStartMonth)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(None)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(
        dateValueHasStartEra)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasStartYear)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(dateValueHasStartMonth)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(None)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasStartEra)
    }

    "create a date value representing a single date with year precision" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar = "GREGORIAN"
      val dateValueHasStartYear = 2018
      val dateValueHasStartEra = "CE"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity = createDateValueWithYearPrecisionRequest(
        resourceIri = resourceIri,
        dateValueHasCalendar = dateValueHasCalendar,
        dateValueHasStartYear = dateValueHasStartYear,
        dateValueHasStartEra = dateValueHasStartEra,
        dateValueHasEndYear = dateValueHasStartYear,
        dateValueHasEndEra = dateValueHasStartEra
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      dateValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(
        dateValueHasCalendar)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(None)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(None)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(
        dateValueHasStartEra)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasStartYear)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(None)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(None)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasStartEra)
    }

    "create a date value representing a single Islamic date with day precision" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar = "ISLAMIC"
      val dateValueHasStartYear = 1407
      val dateValueHasStartMonth = 1
      val dateValueHasStartDay = 26
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity = createIslamicDateValueWithDayPrecisionRequest(
        resourceIri = resourceIri,
        dateValueHasCalendar = dateValueHasCalendar,
        dateValueHasStartYear = dateValueHasStartYear,
        dateValueHasStartMonth = dateValueHasStartMonth,
        dateValueHasStartDay = dateValueHasStartDay,
        dateValueHasEndYear = dateValueHasStartYear,
        dateValueHasEndMonth = dateValueHasStartMonth,
        dateValueHasEndDay = dateValueHasStartDay
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      dateValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.DateValue.toSmartIri)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = dateValueIri.get,
        userEmail = anythingUserEmail
      )

      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should ===("ISLAMIC:1407-01-26")
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(
        dateValueHasCalendar)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(
        dateValueHasStartMonth)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(dateValueHasStartDay)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasStartYear)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(dateValueHasStartMonth)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(dateValueHasStartDay)
    }

    "create an Islamic date value representing a range with day precision" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar = "ISLAMIC"
      val dateValueHasStartYear = 1407
      val dateValueHasStartMonth = 1
      val dateValueHasStartDay = 15
      val dateValueHasEndYear = 1407
      val dateValueHasEndMonth = 1
      val dateValueHasEndDay = 26
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity = createIslamicDateValueWithDayPrecisionRequest(
        resourceIri = resourceIri,
        dateValueHasCalendar = dateValueHasCalendar,
        dateValueHasStartYear = dateValueHasStartYear,
        dateValueHasStartMonth = dateValueHasStartMonth,
        dateValueHasStartDay = dateValueHasStartDay,
        dateValueHasEndYear = dateValueHasEndYear,
        dateValueHasEndMonth = dateValueHasEndMonth,
        dateValueHasEndDay = dateValueHasEndDay
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      dateValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.DateValue.toSmartIri)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = dateValueIri.get,
        userEmail = anythingUserEmail
      )

      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should ===(
        "ISLAMIC:1407-01-15:1407-01-26")
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(
        dateValueHasCalendar)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(
        dateValueHasStartMonth)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(dateValueHasStartDay)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasEndYear)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(dateValueHasEndMonth)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(dateValueHasEndDay)
    }

    "create a boolean value" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri
      val booleanValue: Boolean = true
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-boolean-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      booleanValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.BooleanValue.toSmartIri)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = booleanValueIri.get,
        userEmail = anythingUserEmail
      )

      val booleanValueAsBoolean: Boolean =
        savedValue.requireBoolean(OntologyConstants.KnoraApiV2Complex.BooleanValueAsBoolean)
      booleanValueAsBoolean should ===(booleanValue)
    }

    "create a geometry value" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
        s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasGeometry" : {
                   |    "@type" : "knora-api:GeomValue",
                   |    "knora-api:geometryValueAsGeometry" : ${stringFormatter.toJsonEncodedString(geometryValue1)}
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-geometry-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      geometryValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.GeomValue.toSmartIri)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = geometryValueIri.get,
        userEmail = anythingUserEmail
      )

      val geometryValueAsGeometry: String =
        savedValue.requireString(OntologyConstants.KnoraApiV2Complex.GeometryValueAsGeometry)
      geometryValueAsGeometry should ===(geometryValue1)
    }

    "create an interval value" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
      val intervalStart = BigDecimal("1.2")
      val intervalEnd = BigDecimal("3.4")
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-interval-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      intervalValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasTimeStamp".toSmartIri
      val timeStamp = Instant.parse("2019-08-28T15:59:12.725007Z")
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
        s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasTimeStamp" : {
                   |    "@type" : "knora-api:TimeValue",
                   |    "knora-api:timeValueAsTimeStamp" : {
                   |      "@type" : "xsd:dateTimeStamp",
                   |      "@value" : "$timeStamp"
                   |    }
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-time-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      timeValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val listNode = "http://rdfh.ch/lists/0001/treeList03"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-list-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      listValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.ListValue.toSmartIri)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = listValueIri.get,
        userEmail = anythingUserEmail
      )

      val savedListValueHasListNode: IRI =
        savedValue.requireIriInObject(OntologyConstants.KnoraApiV2Complex.ListValueAsListNode,
                                      stringFormatter.validateAndEscapeIri)
      savedListValueHasListNode should ===(listNode)
    }

    "create a color value" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
      val color = "#ff3333"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-color-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      colorValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
      val uri = "https://www.knora.org"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-uri-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      uriValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
      val geonameCode = "2661604"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-geoname-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      geonameValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.GeonameValue.toSmartIri)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = geonameValueIri.get,
        userEmail = anythingUserEmail
      )

      val savedGeonameCode: String =
        savedValue.requireString(OntologyConstants.KnoraApiV2Complex.GeonameValueAsGeonameCode)
      savedGeonameCode should ===(geonameCode)
    }

    "create a link between two resources, without a comment" in {
      val resourceIri: IRI = AThing.iri
      val linkPropertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThing".toSmartIri
      val linkValuePropertyIri: SmartIri = linkPropertyIri.fromLinkPropToLinkValueProp
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity: String =
        s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasOtherThingValue" : {
                   |    "@type" : "knora-api:LinkValue",
                   |    "knora-api:linkValueHasTargetIri" : {
                   |      "@id" : "${TestDing.iri}"
                   |    }
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-link-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)

      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      linkValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.LinkValue.toSmartIri)
      linkValueUUID = responseJsonDoc.body.requireStringWithValidation(OntologyConstants.KnoraApiV2Complex.ValueHasUUID,
                                                                       stringFormatter.validateBase64EncodedUuid)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = linkPropertyIri,
        propertyIriInResult = linkValuePropertyIri,
        expectedValueIri = linkValueIri.get,
        userEmail = anythingUserEmail
      )

      val savedTarget: JsonLDObject = savedValue.requireObject(OntologyConstants.KnoraApiV2Complex.LinkValueHasTarget)
      val savedTargetIri: IRI = savedTarget.requireString(JsonLDKeywords.ID)
      savedTargetIri should ===(TestDing.iri)
    }

    "create a link between two resources with a custom link value IRI, UUID, creationDate" in {
      val resourceIri: IRI = AThing.iri
      val targetResourceIri: IRI = "http://rdfh.ch/resources/CNhWoNGGT7iWOrIwxsEqvA"
      val customValueIri: IRI = "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/mr9i2aUUJolv64V_9hYdTw"
      val customValueUUID = "mr9i2aUUJolv64V_9hYdTw"
      val customCreationDate: Instant = Instant.parse("2020-06-04T11:36:54.502951Z")

      val jsonLDEntity =
        s"""{
                   | "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasOtherThingValue" : {
                   |    "@id" : "$customValueIri",
                   |    "@type" : "knora-api:LinkValue",
                   |    "knora-api:valueHasUUID": "$customValueUUID",
                   |    "knora-api:linkValueHasTargetIri" : {
                   |      "@id" : "$targetResourceIri"
                   |    },
                   |    "knora-api:valueCreationDate" : {
                   |        "@type" : "xsd:dateTimeStamp",
                   |        "@value" : "$customCreationDate"
                   |    }
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-link-value-with-custom-Iri-UUID-CreationDate-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
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
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue: Int = 5
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-int-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseStr: String = responseToString(response)
      assert(response.status == StatusCodes.OK, responseStr)
      val responseJsonDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(responseStr)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      intValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri)
      val newIntegerValueUUID: UUID =
        responseJsonDoc.body.requireStringWithValidation(OntologyConstants.KnoraApiV2Complex.ValueHasUUID,
                                                         stringFormatter.validateBase64EncodedUuid)
      assert(newIntegerValueUUID == integerValueUUID) // The new version should have the same UUID.

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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-value-response",
            fileExtension = "json"
          ),
          text = responseStr
        )
      )
    }

    "update an integer value with a custom creation date" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue: Int = 6
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)
      val valueCreationDate = Instant.now

      val jsonLDEntity =
        s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInteger" : {
                   |    "@id" : "${intValueForRsyncIri.get}",
                   |    "@type" : "knora-api:IntValue",
                   |    "knora-api:intValueAsInt" : $intValue,
                   |    "knora-api:valueCreationDate" : {
                   |        "@type" : "xsd:dateTimeStamp",
                   |        "@value" : "$valueCreationDate"
                   |    }
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
                   |  }
                   |}""".stripMargin

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-int-value-request-with-custom-creation-date",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      intValueForRsyncIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueForRsyncIri.get,
        userEmail = anythingUserEmail
      )

      val intValueAsInt: Int = savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.IntValueAsInt)
      intValueAsInt should ===(intValue)

      val savedCreationDate: Instant = savedValue.requireDatatypeValueInObject(
        key = OntologyConstants.KnoraApiV2Complex.ValueCreationDate,
        expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
        validationFun = stringFormatter.xsdDateTimeStampToInstant
      )

      savedCreationDate should ===(valueCreationDate)
    }

    "update an integer value with a custom new value version IRI" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue: Int = 7
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)
      val newValueVersionIri: IRI = s"http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/DrXts3Up3DijGriI403nhg"

      val jsonLDEntity = updateIntValueWithCustomNewValueVersionIriRequest(
        resourceIri = resourceIri,
        valueIri = intValueForRsyncIri.get,
        intValue = intValue,
        newValueVersionIri = newValueVersionIri
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)

      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(valueIri == newValueVersionIri)
      intValueForRsyncIri.set(valueIri)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueForRsyncIri.get,
        userEmail = anythingUserEmail
      )

      val intValueAsInt: Int = savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.IntValueAsInt)
      intValueAsInt should ===(intValue)
    }

    "not update an integer value with a custom new value version IRI that is the same as the current IRI" in {
      val resourceIri: IRI = AThing.iri
      val intValue: Int = 8
      val newValueVersionIri: IRI = s"http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/DrXts3Up3DijGriI403nhg"

      val jsonLDEntity = updateIntValueWithCustomNewValueVersionIriRequest(
        resourceIri = resourceIri,
        valueIri = intValueForRsyncIri.get,
        intValue = intValue,
        newValueVersionIri = newValueVersionIri
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.BadRequest, responseAsString)
    }

    "not update an integer value with an invalid custom new value version IRI" in {
      val resourceIri: IRI = AThing.iri
      val intValue: Int = 8
      val newValueVersionIri: IRI = "foo"

      val jsonLDEntity = updateIntValueWithCustomNewValueVersionIriRequest(
        resourceIri = resourceIri,
        valueIri = intValueForRsyncIri.get,
        intValue = intValue,
        newValueVersionIri = newValueVersionIri
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.BadRequest, responseAsString)
    }

    "not update an integer value with a custom new value version IRI that refers to the wrong resource" in {
      val resourceIri: IRI = AThing.iri
      val intValue: Int = 8
      val newValueVersionIri: IRI = "http://rdfh.ch/resources/nResNuvARcWYUdWyo0GWGw/values/iEYi6E7Ntjvj2syzJZiXlg"

      val jsonLDEntity = updateIntValueWithCustomNewValueVersionIriRequest(
        resourceIri = resourceIri,
        valueIri = intValueForRsyncIri.get,
        intValue = intValue,
        newValueVersionIri = newValueVersionIri
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.BadRequest, responseAsString)
    }

    "not update an integer value if the simple schema is submitted" in {
      val resourceIri: IRI = AThing.iri
      val intValue: Int = 10

      val jsonLDEntity =
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

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.BadRequest, responseAsString)
    }

    "update an integer value with custom permissions" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue: Int = 3879
      val customPermissions: String = "CR http://rdfh.ch/groups/0001/thing-searcher"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
        s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInteger" : {
                   |    "@id" : "${intValueWithCustomPermissionsIri.get}",
                   |    "@type" : "knora-api:IntValue",
                   |    "knora-api:intValueAsInt" : $intValue,
                   |    "knora-api:hasPermissions" : "$customPermissions"
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-int-value-with-custom-permissions-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      intValueWithCustomPermissionsIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueWithCustomPermissionsIri.get,
        userEmail = anythingUserEmail
      )

      val intValueAsInt: Int = savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.IntValueAsInt)
      intValueAsInt should ===(intValue)
      val hasPermissions = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.HasPermissions)
      hasPermissions should ===(customPermissions)
    }

    "update an integer value, changing only the permissions" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val customPermissions: String = "CR http://rdfh.ch/groups/0001/thing-searcher|V knora-admin:KnownUser"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
        s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInteger" : {
                   |    "@id" : "${intValueIri.get}",
                   |    "@type" : "knora-api:IntValue",
                   |    "knora-api:hasPermissions" : "$customPermissions"
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-int-value-permissions-only-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      intValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
      val decimalValue = BigDecimal(5.6)
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
        s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasDecimal" : {
                   |    "@id" : "${decimalValueIri.get}",
                   |    "@type" : "knora-api:DecimalValue",
                   |    "knora-api:decimalValueAsDecimal" : {
                   |      "@type" : "xsd:decimal",
                   |      "@value" : "$decimalValue"
                   |    }
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-decimal-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      decimalValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      val resourceIri: IRI = AThing.iri

      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
        s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasText" : {
                   |    "@id" : "${textValueWithStandoffIri.get}",
                   |    "@type" : "knora-api:TextValue",
                   |    "knora-api:textValueAsXml" : ${stringFormatter.toJsonEncodedString(
             textValue2AsXmlWithStandardMapping)},
                   |    "knora-api:textValueHasMapping" : {
                   |      "@id": "$standardMappingIri"
                   |    }
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-text-value-with-standoff-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      textValueWithStandoffIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      val resourceIri = AThing.iri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)
      val jsonLDEntity = FileUtil.readTextFile(Paths.get("test_data/valuesE2EV2/UpdateValueWithEscape.jsonld"))
      val jsonLDEntityWithResourceValueIri = jsonLDEntity.replace("VALUE_IRI", textValueWithEscapeIri.get)
      val request = Put(
        baseApiUrl + "/v2/values",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntityWithResourceValueIri)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
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
      val resourceIri: IRI = AThing.iri
      val valueAsString: String = "this is a text value that has an updated comment"
      val valueHasComment: String = "this is an updated comment"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity = updateTextValueWithCommentRequest(
        resourceIri = resourceIri,
        valueIri = textValueWithoutStandoffIri.get,
        valueAsString = valueAsString,
        valueHasComment = valueHasComment
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      textValueWithoutStandoffIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      val resourceIri: IRI = AThing.iri
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

      val jsonLDEntity = updateDateValueWithDayPrecisionRequest(
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-date-value-with-day-precision-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      dateValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.DateValue.toSmartIri)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = dateValueIri.get,
        userEmail = anythingUserEmail
      )

      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should ===(
        "GREGORIAN:2018-10-05 CE:2018-12-06 CE")
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(
        dateValueHasCalendar)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(
        dateValueHasStartMonth)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(dateValueHasStartDay)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(
        dateValueHasStartEra)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasEndYear)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(dateValueHasEndMonth)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(dateValueHasEndDay)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasEndEra)
    }

    "update a date value representing a range with month precision" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar = "GREGORIAN"
      val dateValueHasStartYear = 2018
      val dateValueHasStartMonth = 9
      val dateValueHasStartEra = "CE"
      val dateValueHasEndYear = 2018
      val dateValueHasEndMonth = 12
      val dateValueHasEndEra = "CE"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity = updateDateValueWithMonthPrecisionRequest(
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-date-value-with-month-precision-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      dateValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.DateValue.toSmartIri)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = dateValueIri.get,
        userEmail = anythingUserEmail
      )

      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should ===(
        "GREGORIAN:2018-09 CE:2018-12 CE")
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(
        dateValueHasCalendar)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(
        dateValueHasStartMonth)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(None)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(
        dateValueHasStartEra)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasEndYear)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(dateValueHasEndMonth)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(None)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasEndEra)
    }

    "update a date value representing a range with year precision" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar = "GREGORIAN"
      val dateValueHasStartYear = 2018
      val dateValueHasStartEra = "CE"
      val dateValueHasEndYear = 2020
      val dateValueHasEndEra = "CE"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity = updateDateValueWithYearPrecisionRequest(
        resourceIri = resourceIri,
        valueIri = dateValueIri.get,
        dateValueHasCalendar = dateValueHasCalendar,
        dateValueHasStartYear = dateValueHasStartYear,
        dateValueHasStartEra = dateValueHasStartEra,
        dateValueHasEndYear = dateValueHasEndYear,
        dateValueHasEndEra = dateValueHasEndEra
      )

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-date-value-with-year-precision-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      dateValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.DateValue.toSmartIri)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = dateValueIri.get,
        userEmail = anythingUserEmail
      )

      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should ===(
        "GREGORIAN:2018 CE:2020 CE")
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(
        dateValueHasCalendar)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(None)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(None)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(
        dateValueHasStartEra)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasEndYear)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(None)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(None)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasEndEra)
    }

    "update a date value representing a single date with day precision" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar = "GREGORIAN"
      val dateValueHasStartYear = 2018
      val dateValueHasStartMonth = 10
      val dateValueHasStartDay = 6
      val dateValueHasStartEra = "CE"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity = updateDateValueWithDayPrecisionRequest(
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

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      dateValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(
        dateValueHasCalendar)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(
        dateValueHasStartMonth)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(dateValueHasStartDay)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(
        dateValueHasStartEra)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasStartYear)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(dateValueHasStartMonth)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(dateValueHasStartDay)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasStartEra)
    }

    "update a date value representing a single date with month precision" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar = "GREGORIAN"
      val dateValueHasStartYear = 2018
      val dateValueHasStartMonth = 7
      val dateValueHasStartEra = "CE"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity = updateDateValueWithMonthPrecisionRequest(
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

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      dateValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(
        dateValueHasCalendar)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(
        dateValueHasStartMonth)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(None)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(
        dateValueHasStartEra)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasStartYear)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(dateValueHasStartMonth)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(None)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasStartEra)
    }

    "update a date value representing a single date with year precision" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar = "GREGORIAN"
      val dateValueHasStartYear = 2019
      val dateValueHasStartEra = "CE"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity = updateDateValueWithYearPrecisionRequest(
        resourceIri = resourceIri,
        valueIri = dateValueIri.get,
        dateValueHasCalendar = dateValueHasCalendar,
        dateValueHasStartYear = dateValueHasStartYear,
        dateValueHasStartEra = dateValueHasStartEra,
        dateValueHasEndYear = dateValueHasStartYear,
        dateValueHasEndEra = dateValueHasStartEra
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      dateValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar) should ===(
        dateValueHasCalendar)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear) should ===(dateValueHasStartYear)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth) should ===(None)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay) should ===(None)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra) should ===(
        dateValueHasStartEra)
      savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear) should ===(dateValueHasStartYear)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth) should ===(None)
      savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay) should ===(None)
      savedValue.requireString(OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra) should ===(dateValueHasStartEra)
    }

    "update a boolean value" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri
      val booleanValue: Boolean = false
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-boolean-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      booleanValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.BooleanValue.toSmartIri)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = booleanValueIri.get,
        userEmail = anythingUserEmail
      )

      val booleanValueAsBoolean: Boolean =
        savedValue.requireBoolean(OntologyConstants.KnoraApiV2Complex.BooleanValueAsBoolean)
      booleanValueAsBoolean should ===(booleanValue)
    }

    "update a geometry value" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
        s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasGeometry" : {
                   |    "@id" : "${geometryValueIri.get}",
                   |    "@type" : "knora-api:GeomValue",
                   |    "knora-api:geometryValueAsGeometry" : ${stringFormatter.toJsonEncodedString(geometryValue2)}
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-geometry-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      geometryValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.GeomValue.toSmartIri)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = geometryValueIri.get,
        userEmail = anythingUserEmail
      )

      val geometryValueAsGeometry: String =
        savedValue.requireString(OntologyConstants.KnoraApiV2Complex.GeometryValueAsGeometry)
      geometryValueAsGeometry should ===(geometryValue2)
    }

    "update an interval value" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
      val intervalStart = BigDecimal("5.6")
      val intervalEnd = BigDecimal("7.8")
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-interval-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      intervalValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasTimeStamp".toSmartIri
      val timeStamp = Instant.parse("2019-12-16T09:14:56.409249Z")
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
        s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasTimeStamp" : {
                   |    "@id" : "${timeValueIri.get}",
                   |    "@type" : "knora-api:TimeValue",
                   |    "knora-api:timeValueAsTimeStamp" : {
                   |      "@type" : "xsd:dateTimeStamp",
                   |      "@value" : "$timeStamp"
                   |    }
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-time-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      timeValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val listNode = "http://rdfh.ch/lists/0001/treeList02"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-list-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      listValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.ListValue.toSmartIri)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = listValueIri.get,
        userEmail = anythingUserEmail
      )

      val savedListValueHasListNode: IRI =
        savedValue.requireIriInObject(OntologyConstants.KnoraApiV2Complex.ListValueAsListNode,
                                      stringFormatter.validateAndEscapeIri)
      savedListValueHasListNode should ===(listNode)
    }

    "update a color value" in {
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
      val color = "#ff3344"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-color-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      colorValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
      val uri = "https://docs.knora.org"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-uri-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      uriValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      val resourceIri: IRI = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
      val geonameCode = "2988507"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity =
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-geoname-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      geonameValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.GeonameValue.toSmartIri)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = geonameValueIri.get,
        userEmail = anythingUserEmail
      )

      val savedGeonameCode: String =
        savedValue.requireString(OntologyConstants.KnoraApiV2Complex.GeonameValueAsGeonameCode)
      savedGeonameCode should ===(geonameCode)
    }

    "update a link between two resources" in {
      val resourceIri: IRI = AThing.iri
      val linkPropertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThing".toSmartIri
      val linkValuePropertyIri: SmartIri = linkPropertyIri.fromLinkPropToLinkValueProp
      val linkTargetIri: IRI = "http://rdfh.ch/resources/5IEswyQFQp2bxXDrOyEfEA"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity = updateLinkValueRequest(
        resourceIri = resourceIri,
        valueIri = linkValueIri.get,
        targetResourceIri = linkTargetIri
      )

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-link-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)

      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      linkValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.LinkValue.toSmartIri)

      // When you change a link value's target, it gets a new UUID.
      val newLinkValueUUID: UUID =
        responseJsonDoc.body.requireStringWithValidation(OntologyConstants.KnoraApiV2Complex.ValueHasUUID,
                                                         stringFormatter.validateBase64EncodedUuid)
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
      val savedTargetIri: IRI = savedTarget.requireString(JsonLDKeywords.ID)
      savedTargetIri should ===(linkTargetIri)
    }

    "not update a link without a comment without changing it" in {
      val resourceIri: IRI = AThing.iri
      val linkTargetIri: IRI = "http://rdfh.ch/resources/5IEswyQFQp2bxXDrOyEfEA"

      val jsonLDEntity = updateLinkValueRequest(
        resourceIri = resourceIri,
        valueIri = linkValueIri.get,
        targetResourceIri = linkTargetIri
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.BadRequest, response.toString)
    }

    "update a link between two resources, adding a comment" in {
      val resourceIri: IRI = AThing.iri
      val linkPropertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThing".toSmartIri
      val linkValuePropertyIri: SmartIri = linkPropertyIri.fromLinkPropToLinkValueProp
      val linkTargetIri: IRI = "http://rdfh.ch/resources/5IEswyQFQp2bxXDrOyEfEA"
      val comment = "adding a comment"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity = updateLinkValueRequest(
        resourceIri = resourceIri,
        valueIri = linkValueIri.get,
        targetResourceIri = linkTargetIri,
        comment = Some(comment)
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)

      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      linkValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.LinkValue.toSmartIri)

      // Since we only changed metadata, the UUID should be the same.
      val newLinkValueUUID: UUID =
        responseJsonDoc.body.requireStringWithValidation(OntologyConstants.KnoraApiV2Complex.ValueHasUUID,
                                                         stringFormatter.validateBase64EncodedUuid)
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
      val resourceIri: IRI = AThing.iri
      val linkPropertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThing".toSmartIri
      val linkValuePropertyIri: SmartIri = linkPropertyIri.fromLinkPropToLinkValueProp
      val linkTargetIri: IRI = "http://rdfh.ch/resources/5IEswyQFQp2bxXDrOyEfEA"
      val comment = "changing only the comment"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)

      val jsonLDEntity = updateLinkValueRequest(
        resourceIri = resourceIri,
        valueIri = linkValueIri.get,
        targetResourceIri = linkTargetIri,
        comment = Some(comment)
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)

      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      linkValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntologyConstants.KnoraApiV2Complex.LinkValue.toSmartIri)

      // Since we only changed metadata, the UUID should be the same.
      val newLinkValueUUID: UUID =
        responseJsonDoc.body.requireStringWithValidation(OntologyConstants.KnoraApiV2Complex.ValueHasUUID,
                                                         stringFormatter.validateBase64EncodedUuid)
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
      val resourceIri: IRI = AThing.iri
      val linkTargetIri: IRI = "http://rdfh.ch/resources/5IEswyQFQp2bxXDrOyEfEA"
      val comment = "changing only the comment"

      val jsonLDEntity = updateLinkValueRequest(
        resourceIri = resourceIri,
        valueIri = linkValueIri.get,
        targetResourceIri = linkTargetIri,
        comment = Some(comment)
      )

      val request = Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.BadRequest, response.toString)
    }

    "create a link between two resources, with a comment" in {
      val resourceIri: IRI = AThing.iri
      val linkPropertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThing".toSmartIri
      val linkValuePropertyIri: SmartIri = linkPropertyIri.fromLinkPropToLinkValueProp
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUserEmail)
      val comment = "Initial comment"

      val jsonLDEntity =
        s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasOtherThingValue" : {
                   |    "@type" : "knora-api:LinkValue",
                   |    "knora-api:linkValueHasTargetIri" : {
                   |      "@id" : "${TestDing.iri}"
                   |    },
                   |    "knora-api:valueHasComment" : "$comment"
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

      val request = Post(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)

      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      linkValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
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
      val savedTargetIri: IRI = savedTarget.requireString(JsonLDKeywords.ID)
      savedTargetIri should ===(TestDing.iri)

      val savedComment: String = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.ValueHasComment)
      savedComment should ===(comment)
    }

    "delete an integer value" in {
      val jsonLDEntity = deleteIntValueRequest(
        resourceIri = AThing.iri,
        valueIri = intValueIri.get,
        maybeDeleteComment = Some("this value was incorrect")
      )

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "delete-int-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values/delete",
                         HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
    }

    "delete an integer value, supplying a custom delete date" in {
      val deleteDate = Instant.now

      val jsonLDEntity =
        s"""{
                   |  "@id" : "${AThing.iri}",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInteger" : {
                   |    "@id" : "${intValueForRsyncIri.get}",
                   |    "@type" : "knora-api:IntValue",
                   |    "knora-api:deleteDate" : {
                   |      "@type" : "xsd:dateTimeStamp",
                   |      "@value" : "$deleteDate"
                   |    }
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "delete-int-value-request-with-custom-delete-date",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values/delete",
                         HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
    }

    "not delete an integer value if the simple schema is submitted" in {
      val jsonLDEntity =
        s"""{
                   |  "@id" : "${AThing.iri}",
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

      val request = Post(baseApiUrl + "/v2/values/delete",
                         HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.BadRequest, responseAsString)
    }

    "delete an integer value without supplying a delete comment" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/H6gBWUuJSuuO-CilHV8kQw"
      val valueIri: IRI = "http://rdfh.ch/resources/H6gBWUuJSuuO-CilHV8kQw/values/dJ1ES8QTQNepFKF5-EAqdg"

      val jsonLDEntity = deleteIntValueRequest(
        resourceIri = resourceIri,
        valueIri = valueIri,
        maybeDeleteComment = None
      )

      val deleteRequest = Post(baseApiUrl + "/v2/values/delete",
                               HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(SharedTestDataADM.anythingUser2.email, password))
      val deleteResponse: HttpResponse = singleAwaitingRequest(deleteRequest)
      assert(deleteResponse.status == StatusCodes.OK, deleteResponse.toString)

      // Request the resource as it was before the value was deleted.

      val getRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}?version=${URLEncoder
        .encode("2018-05-28T15:52:03.897Z", "UTF-8")}")
      val getResponse: HttpResponse = singleAwaitingRequest(getRequest)
      val getResponseAsString = responseToString(getResponse)
      assert(getResponse.status == StatusCodes.OK, getResponseAsString)
    }

    "delete a link between two resources" in {
      val jsonLDEntity =
        s"""{
                   |  "@id" : "${AThing.iri}",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasOtherThingValue" : {
                   |    "@id": "${linkValueIri.get}",
                   |    "@type" : "knora-api:LinkValue"
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "delete-link-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(baseApiUrl + "/v2/values/delete",
                         HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
    }
  }
}
