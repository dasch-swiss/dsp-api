/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.Input
import org.xmlunit.diff.Diff
import sttp.client4.*
import zio.*
import zio.json.*
import zio.json.ast.*
import zio.test.*

import java.net.URI
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID
import scala.xml.XML

import dsp.errors.AssertionException
import dsp.errors.BadRequestException
import dsp.valueobjects.UuidUtil
import org.knora.webapi.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.OntologyConstants.Xsd
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.common.KnoraIris.ResourceIri
import org.knora.webapi.testservices.RequestsUpdates
import org.knora.webapi.testservices.RequestsUpdates.addVersionQueryParam
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.ResponseOps.assert400
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.testservices.TestResourcesApiClient
import org.knora.webapi.util.*

object ValuesEndpointsE2ESpec extends E2EZSpec { self =>

  private val intValueIri                      = new MutableTestIri
  private val intValueWithCustomPermissionsIri = new MutableTestIri
  private val intValueForRsyncIri              = new MutableTestIri
  private val textValueWithoutStandoffIri      = new MutableTestIri
  private val textValueWithStandoffIri         = new MutableTestIri
  private val textValueWithEscapeIri           = new MutableTestIri
  private val decimalValueIri                  = new MutableTestIri
  private val dateValueIri                     = new MutableTestIri
  private val booleanValueIri                  = new MutableTestIri
  private val geometryValueIri                 = new MutableTestIri
  private val intervalValueIri                 = new MutableTestIri
  private val timeValueIri                     = new MutableTestIri
  private val listValueIri                     = new MutableTestIri
  private val colorValueIri                    = new MutableTestIri
  private val uriValueIri                      = new MutableTestIri
  private val geonameValueIri                  = new MutableTestIri
  private val linkValueIri                     = new MutableTestIri

  private var integerValueUUID = UUID.randomUUID
  private var linkValueUUID    = UUID.randomUUID

  override val rdfDataObjects = List(
    RdfDataObject(path = "test_data/project_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
  )

  object AThing {
    val iri: IRI = "http://rdfh.ch/0001/a-thing"
  }

  object TestDing {
    val iri: IRI = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw"

    val intValueIri: IRI      = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/dJ1ES8QTQNepFKF5-EAqdg"
    val decimalValueIri: IRI  = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/bXMwnrHvQH2DMjOFrGmNzg"
    val dateValueIri: IRI     = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/-rG4F5FTTu2iB5mTBPVn5Q"
    val booleanValueIri: IRI  = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/IN4R19yYR0ygi3K2VEHpUQ"
    val uriValueIri: IRI      = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/uBAmWuRhR-eo1u1eP7qqNg"
    val intervalValueIri: IRI = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/RbDKPKHWTC-0lkRKae-E6A"
    val timeValueIri: IRI     = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/l6DhS5SCT9WhXSoYEZRTRw"
    val colorValueIri: IRI    = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/TAziKNP8QxuyhC4Qf9-b6w"
    val geomValueIri: IRI =
      "http://rdfh.ch/0001/http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/we-ybmj-SRen-91n4RaDOQ"
    val geonameValueIri: IRI             = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/hty-ONF8SwKN2RKU7rLKDg"
    val textValueWithStandoffIri: IRI    = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/rvB4eQ5MTF-Qxq0YgkwaDg"
    val textValueWithoutStandoffIri: IRI = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/SZyeLLmOTcCCuS3B0VksHQ"
    val listValueIri: IRI                = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/XAhEeE3kSVqM4JPGdLt4Ew"
    val linkValueIri: IRI                = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/uvRVxzL1RD-t9VIQ1TpfUw"

    val intValueUuid                 = "dJ1ES8QTQNepFKF5-EAqdg"
    val decimalValueUuid             = "bXMwnrHvQH2DMjOFrGmNzg"
    val dateValueUuid                = "-rG4F5FTTu2iB5mTBPVn5Q"
    val booleanValueUuid             = "IN4R19yYR0ygi3K2VEHpUQ"
    val uriValueUuid                 = "uBAmWuRhR-eo1u1eP7qqNg"
    val intervalValueUuid            = "RbDKPKHWTC-0lkRKae-E6A"
    val timeValueUuid                = "l6DhS5SCT9WhXSoYEZRTRw"
    val colorValueUuid               = "TAziKNP8QxuyhC4Qf9-b6w"
    val geomValueUuid                = "we-ybmj-SRen-91n4RaDOQ"
    val geonameValueUuid             = "hty-ONF8SwKN2RKU7rLKDg"
    val textValueWithStandoffUuid    = "rvB4eQ5MTF-Qxq0YgkwaDg"
    val textValueWithoutStandoffUuid = "SZyeLLmOTcCCuS3B0VksHQ"
    val listValueUuid                = "XAhEeE3kSVqM4JPGdLt4Ew"
    val linkValueUuid                = "uvRVxzL1RD-t9VIQ1TpfUw"
  }

  object AThingPicture {
    val iri: IRI                     = "http://rdfh.ch/0001/a-thing-picture"
    val stillImageFileValueUuid: IRI = "goZ7JFRNSeqF-dNxsqAS7Q"
  }

  private def getResourceWithValues(resourceIri: IRI, propertyIrisForGravsearch: Seq[SmartIri]) = {
    // Make a Gravsearch query from a template.
    val gravsearchQuery: String = org.knora.webapi.messages.twirl.queries.gravsearch.txt
      .getResourceWithSpecifiedProperties(
        resourceIri = resourceIri,
        propertyIris = propertyIrisForGravsearch,
      )
      .toString()
    TestApiClient
      .postSparql(uri"/v2/searchextended", gravsearchQuery, anythingUser1)
      .flatMap(_.assert200)
      .mapAttempt(JsonLDUtil.parseJsonLD)
  }

  private def getValueFromResource(
    resource: JsonLDDocument,
    propertyIriInResult: SmartIri,
    expectedValueIri: IRI,
  ) = for {
    resourceIri    <- ZIO.fromEither(resource.body.getRequiredString(JsonLDKeywords.ID))
    propertyValues <- ZIO.fromEither(resource.body.getRequiredArray(propertyIriInResult.toString))
    matchingValues = propertyValues.value.collect {
                       case jsonLDObject: JsonLDObject
                           if jsonLDObject.getRequiredString(JsonLDKeywords.ID).toOption.contains(expectedValueIri) =>
                         jsonLDObject
                     }
    _ <- ZIO
           .when(matchingValues.isEmpty) {
             val msg =
               s"Property <$propertyIriInResult> of resource <$resourceIri> does not have value <$expectedValueIri>"
             ZIO.fail(msg)
           }
    _ <- ZIO
           .when(matchingValues.size > 1) {
             val msg =
               s"Property <$propertyIriInResult> of resource <$resourceIri> has more than one value with the IRI <$expectedValueIri>"
             ZIO.fail(msg)
           }
  } yield matchingValues.head

  private def parseResourceLastModificationDate(resource: JsonLDDocument): Option[Instant] =
    resource.body
      .getObject(KA.LastModificationDate)
      .fold(e => throw BadRequestException(e), identity)
      .map { jsonLDObject =>
        jsonLDObject.requireStringWithValidation(
          JsonLDKeywords.VALUE,
          (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
        )
      }

  private def getResourceLastModificationDate(resourceIri: IRI): ZIO[TestApiClient, Throwable, Option[Instant]] =
    TestApiClient
      .getJsonLdDocument(uri"/v2/resourcespreview/$resourceIri", anythingUser1)
      .flatMap(_.assert200)
      .mapAttempt(parseResourceLastModificationDate)

  private def checkLastModDate(
    resourceIri: IRI,
    maybePreviousLastModDate: Option[Instant],
    maybeUpdatedLastModDate: Option[Instant],
  ): IO[AssertionException, Unit] = ZIO
    .fromOption(maybeUpdatedLastModDate)
    .orElseFail(AssertionException(s"Resource $resourceIri has no knora-api:lastModificationDate"))
    .flatMap { updatedLastModDate =>
      maybePreviousLastModDate match {
        case Some(previousLastModDate) =>
          ZIO
            .fail(AssertionException(s"Last ModificationDate $updatedLastModDate is before $previousLastModDate"))
            .when(updatedLastModDate.isBefore(previousLastModDate))
            .unit
        case None => ZIO.unit
      }
    }

  private def getValue(
    resourceIri: IRI,
    maybePreviousLastModDate: Option[Instant],
    propertyIriForGravsearch: SmartIri,
    propertyIriInResult: SmartIri,
    expectedValueIri: IRI,
  ): ZIO[StringFormatter & TestApiClient, Serializable, JsonLDObject] = for {
    resource            <- getResourceWithValues(resourceIri, Seq(propertyIriForGravsearch))
    receivedResourceIri <- resource.body.getRequiredIdValueAsKnoraDataIri
    _ <- ZIO
           .fail(AssertionException(s"Expected resource $resourceIri, received $receivedResourceIri"))
           .when(receivedResourceIri.toString != resourceIri)
    resourceLastModDate = parseResourceLastModificationDate(resource)
    _                  <- checkLastModDate(resourceIri, maybePreviousLastModDate, resourceLastModDate)
    value              <- getValueFromResource(resource, propertyIriInResult, expectedValueIri)
  } yield value

  private def createTextValueWithoutStandoffRequest(resourceIri: IRI, valueAsString: String): String =
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

  private val textValue1AsXmlWithStandardMapping: String =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<text>
      |   This text links to another <a class="salsah-link" href="http://rdfh.ch/0001/another-thing">resource</a>.
      |   And this <strong id="link_id">strong value</strong> is linked by this <a class="internal-link" href="#link_id">link</a>
      |</text>""".stripMargin

  private val standardMappingIri: IRI = "http://rdfh.ch/standoff/mappings/StandardMapping"

  private val geometryValue1 =
    """{"status":"active","lineColor":"#ff3333","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""

  private def createTextValueWithStandoffRequest(resourceIri: IRI, textValueAsXml: String, mappingIri: String): String =
    s"""{
       |  "@id" : "$resourceIri",
       |  "@type" : "anything:Thing",
       |  "anything:hasText" : {
       |    "@type" : "knora-api:TextValue",
       |    "knora-api:textValueAsXml" : ${textValueAsXml.toJson},
       |    "knora-api:textValueHasMapping" : {
       |      "@id": "$mappingIri"
       |    }
       |  },
       |  "@context" : {
       |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
       |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
       |  }
       |}""".stripMargin

  private def createDateValueWithDayPrecisionRequest(
    resourceIri: IRI,
    dateValueHasCalendar: String,
    dateValueHasStartYear: Int,
    dateValueHasStartMonth: Int,
    dateValueHasStartDay: Int,
    dateValueHasStartEra: String,
    dateValueHasEndYear: Int,
    dateValueHasEndMonth: Int,
    dateValueHasEndDay: Int,
    dateValueHasEndEra: String,
  ): String =
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

  private def createIslamicDateValueWithDayPrecisionRequest(
    resourceIri: IRI,
    dateValueHasCalendar: String,
    dateValueHasStartYear: Int,
    dateValueHasStartMonth: Int,
    dateValueHasStartDay: Int,
    dateValueHasEndYear: Int,
    dateValueHasEndMonth: Int,
    dateValueHasEndDay: Int,
  ): String =
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

  private def createDateValueWithMonthPrecisionRequest(
    resourceIri: IRI,
    dateValueHasCalendar: String,
    dateValueHasStartYear: Int,
    dateValueHasStartMonth: Int,
    dateValueHasStartEra: String,
    dateValueHasEndYear: Int,
    dateValueHasEndMonth: Int,
    dateValueHasEndEra: String,
  ): String =
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

  private def createDateValueWithYearPrecisionRequest(
    resourceIri: IRI,
    dateValueHasCalendar: String,
    dateValueHasStartYear: Int,
    dateValueHasStartEra: String,
    dateValueHasEndYear: Int,
    dateValueHasEndEra: String,
  ): String =
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

  private def updateTextValueWithoutStandoffRequest(resourceIri: IRI, valueIri: IRI, valueAsString: String): String =
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

  private val textValue2AsXmlWithStandardMapping: String =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<text>
      |   This updated text links to another <a class="salsah-link" href="http://rdfh.ch/0001/another-thing">resource</a>.
      |</text>""".stripMargin

  private def updateTextValueWithCommentRequest(
    resourceIri: IRI,
    valueIri: IRI,
    valueAsString: String,
    valueHasComment: String,
  ): String =
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

  private def updateDateValueWithDayPrecisionRequest(
    resourceIri: IRI,
    valueIri: IRI,
    dateValueHasCalendar: String,
    dateValueHasStartYear: Int,
    dateValueHasStartMonth: Int,
    dateValueHasStartDay: Int,
    dateValueHasStartEra: String,
    dateValueHasEndYear: Int,
    dateValueHasEndMonth: Int,
    dateValueHasEndDay: Int,
    dateValueHasEndEra: String,
  ): String =
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

  private def updateDateValueWithMonthPrecisionRequest(
    resourceIri: IRI,
    valueIri: IRI,
    dateValueHasCalendar: String,
    dateValueHasStartYear: Int,
    dateValueHasStartMonth: Int,
    dateValueHasStartEra: String,
    dateValueHasEndYear: Int,
    dateValueHasEndMonth: Int,
    dateValueHasEndEra: String,
  ): String =
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

  private def updateDateValueWithYearPrecisionRequest(
    resourceIri: IRI,
    valueIri: IRI,
    dateValueHasCalendar: String,
    dateValueHasStartYear: Int,
    dateValueHasStartEra: String,
    dateValueHasEndYear: Int,
    dateValueHasEndEra: String,
  ): String =
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

  private val geometryValue2 =
    """{"status":"active","lineColor":"#ff3344","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""

  private def updateLinkValueRequest(
    resourceIri: IRI,
    valueIri: IRI,
    targetResourceIri: IRI,
    comment: Option[String] = None,
  ): String =
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

  private def deleteIntValueRequest(resourceIri: IRI, valueIri: IRI, deleteComment: String): String =
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

  private def deleteIntValueRequest(resourceIri: IRI, valueIri: IRI): String =
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

  private def updateIntValueWithCustomNewValueVersionIriRequest(
    resourceIri: IRI,
    valueIri: IRI,
    intValue: Int,
    newValueVersionIri: IRI,
  ): String =
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

  private val customValueUUID     = "CpO1TIDf1IS55dQbyIuDsA"
  private val customValueIri: IRI = s"http://rdfh.ch/0001/a-thing/values/$customValueUUID"

  override val e2eSpec = suite("The values v2 endpoint")(
    test("get the latest versions of values, given their UUIDs") {
      val testCases = Gen.fromIterable(
        Seq(
          (TestDing.iri, "int-value", TestDing.intValueUuid),
          (TestDing.iri, "decimal-value", TestDing.decimalValueUuid),
          (TestDing.iri, "date-value", TestDing.dateValueUuid),
          (TestDing.iri, "boolean-value", TestDing.booleanValueUuid),
          (TestDing.iri, "uri-value", TestDing.uriValueUuid),
          (TestDing.iri, "interval-value", TestDing.intervalValueUuid),
          (TestDing.iri, "time-value", TestDing.timeValueUuid),
          (TestDing.iri, "color-value", TestDing.colorValueUuid),
          (TestDing.iri, "geom-value", TestDing.geomValueUuid),
          (TestDing.iri, "geoname-value", TestDing.geonameValueUuid),
          (TestDing.iri, "text-value-with-standoff", TestDing.textValueWithStandoffUuid),
          (TestDing.iri, "text-value-without-standoff", TestDing.textValueWithoutStandoffUuid),
          (TestDing.iri, "list-value", TestDing.listValueUuid),
          (TestDing.iri, "link-value", TestDing.linkValueUuid),
          (AThingPicture.iri, "still-image-file-value", AThingPicture.stillImageFileValueUuid),
        ),
      )
      checkAll(testCases) { case (iri, valueTypeName, valueUuid) =>
        for {
          actual   <- TestApiClient.getJsonLd(uri"/v2/values/$iri/$valueUuid", anythingUser1).flatMap(_.assert200)
          expected <- TestDataFileUtil.readTestData("valuesE2EV2", s"get-$valueTypeName-response.jsonld")
        } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
      }
    },
    test("get a past version of a value, given its UUID and a timestamp") {
      val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing-with-history".toSmartIri)
      val valueUuid   = "pLlW4ODASumZfZFbJdpw1g"
      val timestamp   = "20190212T090510Z"
      for {
        responseJsonDoc <-
          TestApiClient
            .getJsonLdDocument(uri"/v2/values/$resourceIri/$valueUuid", anythingUser1, addVersionQueryParam(timestamp))
            .flatMap(_.assert200)
        value <- getValueFromResource(
                   resource = responseJsonDoc,
                   propertyIriInResult = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri,
                   expectedValueIri = "http://rdfh.ch/0001/thing-with-history/values/1b",
                 )
        actual <- ZIO.fromEither(value.getRequiredInt(KA.IntValueAsInt))
      } yield assertTrue(actual == 2)
    },
    test("create an integer value") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue: Int         = 4

      val jsonLd =
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

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = intValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        _ <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(KA.ValueHasUUID)).tap { uuid =>
               ZIO.succeed(self.integerValueUUID = UuidUtil.decode(uuid))
             }
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = intValueIri.get,
                      )
        savedIntValue <- ZIO.fromEither(savedValue.getRequiredInt(KA.IntValueAsInt))
      } yield assertTrue(savedIntValue == intValue, valueType == KA.IntValue)
    },
    test("create an integer value with a custom value IRI") {
      val resourceIri: IRI = AThing.iri
      val intValue: Int    = 30

      val jsonLd =
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
      for {
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        valueUUID       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(KA.ValueHasUUID))
      } yield assertTrue(valueIri == customValueIri, valueUUID == customValueUUID)
    },
    test("return a DuplicateValueException during value creation when the supplied value IRI is not unique") {
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
      TestApiClient
        .postJsonLd(uri"/v2/values", params, anythingUser1)
        .flatMap(_.assert400)
        .map(errorMessage =>
          assertTrue(errorMessage.contains(s"IRI: '$customValueIri' already exists, try another one.")),
        )
    },
    test("create an integer value with a custom UUID") {
      val resourceIri: IRI   = AThing.iri
      val intValue: Int      = 45
      val intValueCustomUUID = "IN4R19yYR0ygi3K2VEHpUQ"
      val jsonLd =
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
      for {
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueUUID       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(KA.ValueHasUUID))
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
      } yield assertTrue(valueUUID == intValueCustomUUID, valueIri.endsWith(valueUUID))
    },
    test("do not create an integer value if the custom UUID is not part of the custom IRI") {
      val resourceIri: IRI = AThing.iri
      val intValue: Int    = 45
      val aUUID            = "IN4R19yYR0ygi3K2VEHpUQ"
      val valueIri         = s"http://rdfh.ch/0001/a-thing/values/IN4R19yYR0ygi3K2VEHpNN"
      val jsonLd =
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
      TestApiClient.postJsonLd(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert400).as(assertCompletes)
    },
    test("create an integer value with a custom creation date") {
      val customCreationDate: Instant = Instant.parse("2020-06-04T11:36:54.502951Z")
      val resourceIri: IRI            = AThing.iri
      val intValue: Int               = 25

      val jsonLd =
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

      for {
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = intValueForRsyncIri.set(valueIri)
        savedCreationDate = responseJsonDoc.body.requireDatatypeValueInObject(
                              key = KA.ValueCreationDate,
                              expectedDatatype = Xsd.DateTimeStamp.toSmartIri,
                              validationFun =
                                (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
                            )
      } yield assertTrue(savedCreationDate == customCreationDate)
    },
    test("create an integer value with custom IRI, UUID, and creation date") {
      val resourceIri: IRI            = AThing.iri
      val intValue: Int               = 10
      val customValueIri: IRI         = "http://rdfh.ch/0001/a-thing/values/7VDvMOnuitf_r1Ju7BglsQ"
      val customValueUUID             = "7VDvMOnuitf_r1Ju7BglsQ"
      val customCreationDate: Instant = Instant.parse("2020-06-04T12:58:54.502951Z")

      val jsonLd =
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
      for {
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        valueUUID       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(KA.ValueHasUUID))
        savedCreationDate: Instant = responseJsonDoc.body.requireDatatypeValueInObject(
                                       key = KA.ValueCreationDate,
                                       expectedDatatype = Xsd.DateTimeStamp.toSmartIri,
                                       validationFun = (s, errorFun) =>
                                         ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
                                     )
      } yield assertTrue(
        valueIri == customValueIri,
        valueUUID == customValueUUID,
        savedCreationDate == customCreationDate,
      )
    },
    test("not create an integer value if the simple schema is submitted") {
      val jsonLd =
        s"""{
           |  "@id" : "${AThing.iri}",
           |  "@type" : "anything:Thing",
           |  "anything:hasInteger" : 10,
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/simple/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#"
           |  }
           |}""".stripMargin
      TestApiClient.postJsonLd(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert400).as(assertCompletes)
    },
    test("create an integer value with custom permissions") {
      val resourceIri: IRI          = AThing.iri
      val propertyIri: SmartIri     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue: Int             = 1
      val customPermissions: String = "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher"

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd =
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
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = intValueWithCustomPermissionsIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = intValueWithCustomPermissionsIri.get,
                      )
        intValueAsInt  <- ZIO.fromEither(savedValue.getRequiredInt(KA.IntValueAsInt))
        hasPermissions <- ZIO.fromEither(savedValue.getRequiredString(KA.HasPermissions))
      } yield assertTrue(valueType == KA.IntValue, intValueAsInt == intValue, hasPermissions == customPermissions)
    },
    test("create a text value without standoff and without a comment") {
      val resourceIri: IRI      = AThing.iri
      val valueAsString: String = "text without standoff"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd                    = createTextValueWithoutStandoffRequest(resourceIri, valueAsString)
        responseJsonDoc          <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = textValueWithoutStandoffIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = textValueWithoutStandoffIri.get,
                      )
        savedValueAsString <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueAsString))
      } yield assertTrue(valueType == KA.TextValue, savedValueAsString == valueAsString)
    },
    test("not update a text value so it's empty") {
      val jsonLd = updateTextValueWithoutStandoffRequest(
        resourceIri = AThing.iri,
        valueIri = textValueWithoutStandoffIri.get,
        valueAsString = "",
      )
      TestApiClient.putJsonLd(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert400).as(assertCompletes)
    },
    test("update a text value without standoff") {
      val resourceIri: IRI      = AThing.iri
      val valueAsString: String = "text without standoff updated"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd = updateTextValueWithoutStandoffRequest(
                   resourceIri = resourceIri,
                   valueIri = textValueWithoutStandoffIri.get,
                   valueAsString = valueAsString,
                 )
        responseJsonDoc <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = textValueWithoutStandoffIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = textValueWithoutStandoffIri.get,
                      )
        savedValueAsString <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueAsString))
      } yield assertTrue(valueType == KA.TextValue, savedValueAsString == valueAsString)
    },
    test("update a text value without standoff, adding a comment") {
      val resourceIri: IRI      = AThing.iri
      val valueAsString: String = "text without standoff updated"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd = updateTextValueWithCommentRequest(
                   resourceIri = resourceIri,
                   valueIri = textValueWithoutStandoffIri.get,
                   valueAsString = valueAsString,
                   valueHasComment = "Adding a comment",
                 )
        responseJsonDoc <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = textValueWithoutStandoffIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = textValueWithoutStandoffIri.get,
                      )
        savedValueAsString <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueAsString))
      } yield assertTrue(valueType == KA.TextValue, savedValueAsString == valueAsString)
    },
    test("update a text value without standoff, changing only the a comment") {
      val resourceIri: IRI      = AThing.iri
      val valueAsString: String = "text without standoff updated"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd = updateTextValueWithCommentRequest(
                   resourceIri = resourceIri,
                   valueIri = textValueWithoutStandoffIri.get,
                   valueAsString = valueAsString,
                   valueHasComment = "Updated comment",
                 )
        responseJsonDoc <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = textValueWithoutStandoffIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = textValueWithoutStandoffIri.get,
                      )
        savedValueAsString <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueAsString))
      } yield assertTrue(
        valueType == KA.TextValue,
        savedValueAsString == valueAsString,
      )
    },
    test("create a text value without standoff and with a comment") {
      val resourceIri: IRI        = AThing.iri
      val valueAsString: String   = "this is a text value that has a comment"
      val valueHasComment: String = "this is a comment"
      val propertyIri: SmartIri   = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd =
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
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = textValueWithoutStandoffIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = textValueWithoutStandoffIri.get,
                      )
        savedValueAsString   <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueAsString))
        savedValueHasComment <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueHasComment))
      } yield assertTrue(
        savedValueAsString == valueAsString,
        valueType == KA.TextValue,
        savedValueHasComment == valueHasComment,
      )
    },
    test("create a text value with standoff test1") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd = createTextValueWithStandoffRequest(
                   resourceIri = resourceIri,
                   textValueAsXml = textValue1AsXmlWithStandardMapping,
                   mappingIri = standardMappingIri,
                 )
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = textValueWithStandoffIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = textValueWithStandoffIri.get,
                      )
        savedTextValueAsXml <- ZIO.fromEither(savedValue.getRequiredString(KA.TextValueAsXml))
        // Compare the original XML with the regenerated XML.
        xmlDiff = DiffBuilder
                    .compare(Input.fromString(textValue1AsXmlWithStandardMapping))
                    .withTest(Input.fromString(savedTextValueAsXml))
                    .build()
      } yield assertTrue(
        !xmlDiff.hasDifferences,
        valueType == KA.TextValue,
      )
    },
    test("create a very long text value with standoff and linked tags") {
      val resourceIri: IRI  = AThing.iri
      val repeatedParagraph = "<p>Many lines to force to create a page.</p>\n" * 400

      val textValueAsXml: String =
        s"""<?xml version="1.0" encoding="UTF-8"?>
           |<text>
           |   <p>This <a class="internal-link" href="#link_id">ref</a> is a link to an out of page tag.</p>
           |   $repeatedParagraph
           |   <p>This <strong id="link_id">strong value</strong> is linked by an out of page anchor link at the top.</p>
           |</text>
           |""".stripMargin

      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd = createTextValueWithStandoffRequest(
                   resourceIri = resourceIri,
                   textValueAsXml = textValueAsXml,
                   mappingIri = standardMappingIri,
                 )
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = textValueWithStandoffIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = textValueWithStandoffIri.get,
                      )
        savedTextValueAsXml <- ZIO.fromEither(savedValue.getRequiredString(KA.TextValueAsXml))
        xmlDiff =
          DiffBuilder.compare(Input.fromString(textValueAsXml)).withTest(Input.fromString(savedTextValueAsXml)).build()
      } yield assertTrue(valueType == KA.TextValue, !xmlDiff.hasDifferences)
    },
    test("create a text value with standoff containing a URL") {
      val resourceIri: IRI = AThing.iri

      val textValueAsXml: String =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<text>
          |   This text links to <a href="http://www.knora.org">a web site</a>.
          |</text>
                """.stripMargin

      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd = createTextValueWithStandoffRequest(
                   resourceIri = resourceIri,
                   textValueAsXml = textValueAsXml,
                   mappingIri = standardMappingIri,
                 )
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        _               <- ZIO.attempt(assertTrue(valueType == KA.TextValue))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = valueIri,
                      )
        savedTextValueAsXml <- ZIO.fromEither(savedValue.getRequiredString(KA.TextValueAsXml))
      } yield assertTrue(savedTextValueAsXml.contains("href"))
    },
    test("create a text value with standoff containing escaped text") {
      val resourceIri = AThing.iri
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd                   <- TestDataFileUtil.readTestData("valuesE2EV2", "CreateValueWithEscape.jsonld")
        responseJsonDoc          <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = textValueWithEscapeIri.set(valueIri)
        propertyIri               = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
        savedValue <- getValue(
                        resourceIri = AThing.iri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = valueIri,
                      )
        savedTextValueAsXml <- ZIO.fromEither(savedValue.getRequiredString(KA.TextValueAsXml))
        expectedText = """<p>
                         | test</p>""".stripMargin
      } yield assertTrue(savedTextValueAsXml.contains(expectedText))
    },
    test("create a text value with standoff containing a footnote") {
      val resourceIri: IRI = AThing.iri
      val textValueAsXml: String =
        """|<?xml version="1.0" encoding="UTF-8"?>
           |<text>
           |  <p>
           |    Some text with a
           |    footnote<footnote content="Text with &lt;a href=&quot;...&quot;&gt;markup&lt;/a&gt;." />
           |    in it.
           |  </p>
           |</text>
           |""".stripMargin

      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd = createTextValueWithStandoffRequest(
                   resourceIri = resourceIri,
                   textValueAsXml = textValueAsXml,
                   mappingIri = standardMappingIri,
                 )
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        _               <- ZIO.attempt(assertTrue(valueType == KA.TextValue))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = valueIri,
                      )
        actual  <- ZIO.fromEither(savedValue.getRequiredString(KA.TextValueAsXml))
        areEqual = XML.loadString(actual) == XML.loadString(textValueAsXml)
      } yield assertTrue(areEqual)
    },
    test("not create a text value with standoff containing a footnote without content") {
      val resourceIri: IRI = AThing.iri
      val textValueAsXml: String =
        """|<?xml version="1.0" encoding="UTF-8"?>
           |<text>
           |   This text has a footnote<footnote /> without content.
           |</text>
           |""".stripMargin
      val jsonLd = createTextValueWithStandoffRequest(
        resourceIri = resourceIri,
        textValueAsXml = textValueAsXml,
        mappingIri = standardMappingIri,
      )
      TestApiClient.postJsonLd(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert400).as(assertCompletes)
    },
    test("create a TextValue from XML representing HTML with an attribute containing escaped quotes") {
      // Create the mapping.
      val xmlFileToSend = Paths.get("test_data/test_route/texts/mappingForHTML.xml")
      val mappingParams =
        s"""{
           |    "knora-api:mappingHasName": "HTMLMapping",
           |    "knora-api:attachedToProject": {
           |      "@id": "$anythingProjectIri"
           |    },
           |    "rdfs:label": "HTML mapping",
           |    "@context": {
           |        "rdfs": "${Rdfs.RdfsPrefixExpansion}",
           |        "knora-api": "${KA.KnoraApiV2PrefixExpansion}"
           |    }
           |}""".stripMargin
      val multipartBody = Seq(
        multipart("json", mappingParams).contentType("application/json"),
        multipartFile("xml", xmlFileToSend).contentType("text/xml(UTF-8)"),
      )

      // Create the text value.
      val resourceIri: IRI      = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        textValueAsXml =
          """<?xml version="1.0" encoding="UTF-8"?>
            |<text documentType="html">
            |    <p>This an <span data-description="an &quot;event&quot;" data-date="GREGORIAN:2017-01-27 CE" class="event">event</span>.</p>
            |</text>""".stripMargin

        jsonLd = createTextValueWithStandoffRequest(
                   resourceIri = resourceIri,
                   textValueAsXml = textValueAsXml,
                   mappingIri = s"$anythingProjectIri/mappings/HTMLMapping",
                 )
        _               <- TestApiClient.postMultiPart[Json](uri"/v2/mapping", multipartBody, anythingUser1).flatMap(_.assert200)
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = textValueWithStandoffIri.set(valueIri)
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = valueIri,
                      )
        savedTextValueAsXml <- ZIO.fromEither(savedValue.getRequiredString(KA.TextValueAsXml))
      } yield assertTrue(savedTextValueAsXml.contains(textValueAsXml))
    },
    test("not create an empty text value") {
      TestApiClient
        .postJsonLd(uri"/v2/values", createTextValueWithoutStandoffRequest(AThing.iri, ""), anythingUser1)
        .flatMap(_.assert400)
        .as(assertCompletes)
    },
    test("create a decimal value") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri           = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
      val decimalValueAsDecimal = BigDecimal(4.3)
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd =
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
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = decimalValueIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = decimalValueIri.get,
                      )
        savedDecimalValueAsDecimal = savedValue.requireDatatypeValueInObject(
                                       key = KA.DecimalValueAsDecimal,
                                       expectedDatatype = Xsd.Decimal.toSmartIri,
                                       validationFun =
                                         (s, errorFun) => ValuesValidator.validateBigDecimal(s).getOrElse(errorFun),
                                     )
      } yield assertTrue(valueType == KA.DecimalValue, savedDecimalValueAsDecimal == decimalValueAsDecimal)
    },
    test("create a date value representing a range with day precision") {
      val resourceIri: IRI       = AThing.iri
      val propertyIri            = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar   = "GREGORIAN"
      val dateValueHasStartYear  = 2018
      val dateValueHasStartMonth = 10
      val dateValueHasStartDay   = 5
      val dateValueHasStartEra   = "CE"
      val dateValueHasEndYear    = 2018
      val dateValueHasEndMonth   = 10
      val dateValueHasEndDay     = 6
      val dateValueHasEndEra     = "CE"
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd = createDateValueWithDayPrecisionRequest(
                   resourceIri = resourceIri,
                   dateValueHasCalendar = dateValueHasCalendar,
                   dateValueHasStartYear = dateValueHasStartYear,
                   dateValueHasStartMonth = dateValueHasStartMonth,
                   dateValueHasStartDay = dateValueHasStartDay,
                   dateValueHasStartEra = dateValueHasStartEra,
                   dateValueHasEndYear = dateValueHasEndYear,
                   dateValueHasEndMonth = dateValueHasEndMonth,
                   dateValueHasEndDay = dateValueHasEndDay,
                   dateValueHasEndEra = dateValueHasEndEra,
                 )
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = dateValueIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = dateValueIri.get,
                      )
        valueAsString <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueAsString))
        calendar      <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasCalendar))
        startYear     <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartYear))
        startMonth    <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartMonth))
        startDay      <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartDay))
        startEra      <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasStartEra))
        endYear       <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndYear))
        endMonth      <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndMonth))
        endDay        <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndDay))
        endEra        <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasEndEra))
      } yield assertTrue(
        valueType == KA.DateValue,
        valueAsString == "GREGORIAN:2018-10-05 CE:2018-10-06 CE",
        calendar == dateValueHasCalendar,
        startYear == dateValueHasStartYear,
        startMonth == dateValueHasStartMonth,
        startDay == dateValueHasStartDay,
        startEra == dateValueHasStartEra,
        endYear == dateValueHasEndYear,
        endMonth == dateValueHasEndMonth,
        endDay == dateValueHasEndDay,
        endEra == dateValueHasEndEra,
      )
    },
    test("create a date value representing a range with month precision") {
      val resourceIri: IRI       = AThing.iri
      val propertyIri            = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar   = "GREGORIAN"
      val dateValueHasStartYear  = 2018
      val dateValueHasStartMonth = 10
      val dateValueHasStartEra   = "CE"
      val dateValueHasEndYear    = 2018
      val dateValueHasEndMonth   = 11
      val dateValueHasEndEra     = "CE"
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd = createDateValueWithMonthPrecisionRequest(
                   resourceIri = resourceIri,
                   dateValueHasCalendar = dateValueHasCalendar,
                   dateValueHasStartYear = dateValueHasStartYear,
                   dateValueHasStartMonth = dateValueHasStartMonth,
                   dateValueHasStartEra = dateValueHasStartEra,
                   dateValueHasEndYear = dateValueHasEndYear,
                   dateValueHasEndMonth = dateValueHasEndMonth,
                   dateValueHasEndEra = dateValueHasEndEra,
                 )
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = dateValueIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = dateValueIri.get,
                      )
        valueAsString <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueAsString))
        calendar      <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasCalendar))
        startYear     <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartYear))
        startMonth    <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartMonth))
        startDay      <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasStartDay))
        startEra      <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasStartEra))
        endYear       <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndYear))
        endMonth      <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndMonth))
        endDay        <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasEndDay))
        endEra        <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasEndEra))
      } yield assertTrue(
        valueType == KA.DateValue,
        valueAsString == "GREGORIAN:2018-10 CE:2018-11 CE",
        calendar == dateValueHasCalendar,
        startYear == dateValueHasStartYear,
        startMonth == dateValueHasStartMonth,
        startDay.isEmpty,
        startEra == dateValueHasStartEra,
        endYear == dateValueHasEndYear,
        endMonth == dateValueHasEndMonth,
        endDay.isEmpty,
        endEra == dateValueHasEndEra,
      )
    },
    test("create a date value representing a range with year precision") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri           = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar  = "GREGORIAN"
      val dateValueHasStartYear = 2018
      val dateValueHasStartEra  = "CE"
      val dateValueHasEndYear   = 2019
      val dateValueHasEndEra    = "CE"
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd = createDateValueWithYearPrecisionRequest(
                   resourceIri = resourceIri,
                   dateValueHasCalendar = dateValueHasCalendar,
                   dateValueHasStartYear = dateValueHasStartYear,
                   dateValueHasStartEra = dateValueHasStartEra,
                   dateValueHasEndYear = dateValueHasEndYear,
                   dateValueHasEndEra = dateValueHasEndEra,
                 )
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = dateValueIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = dateValueIri.get,
                      )
        valueAsString <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueAsString))
        calendar      <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasCalendar))
        startYear     <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartYear))
        startMonth    <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasStartMonth))
        startDay      <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasStartDay))
        startEra      <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasStartEra))
        endYear       <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndYear))
        endMonth      <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasEndMonth))
        endDay        <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasEndDay))
        endEra        <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasEndEra))
      } yield assertTrue(
        valueType == KA.DateValue,
        valueAsString == "GREGORIAN:2018 CE:2019 CE",
        calendar == dateValueHasCalendar,
        startYear == dateValueHasStartYear,
        startMonth.isEmpty,
        startDay.isEmpty,
        startEra == dateValueHasStartEra,
        endYear == dateValueHasEndYear,
        endMonth.isEmpty,
        endDay.isEmpty,
        endEra == dateValueHasEndEra,
      )
    },
    test("create a date value representing a single date with day precision") {
      val resourceIri: IRI       = AThing.iri
      val propertyIri            = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar   = "GREGORIAN"
      val dateValueHasStartYear  = 2018
      val dateValueHasStartMonth = 10
      val dateValueHasStartDay   = 5
      val dateValueHasStartEra   = "CE"
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd: String = createDateValueWithDayPrecisionRequest(
                           resourceIri = resourceIri,
                           dateValueHasCalendar = dateValueHasCalendar,
                           dateValueHasStartYear = dateValueHasStartYear,
                           dateValueHasStartMonth = dateValueHasStartMonth,
                           dateValueHasStartDay = dateValueHasStartDay,
                           dateValueHasStartEra = dateValueHasStartEra,
                           dateValueHasEndYear = dateValueHasStartYear,
                           dateValueHasEndMonth = dateValueHasStartMonth,
                           dateValueHasEndDay = dateValueHasStartDay,
                           dateValueHasEndEra = dateValueHasStartEra,
                         )
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = dateValueIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = dateValueIri.get,
                      )
        valueAsString <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueAsString))
        calendar      <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasCalendar))
        startYear     <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartYear))
        startMonth    <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartMonth))
        startDay      <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartDay))
        startEra      <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasStartEra))
        endYear       <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndYear))
        endMonth      <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndMonth))
        endDay        <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndDay))
        endEra        <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasEndEra))
      } yield assertTrue(
        valueType == KA.DateValue,
        valueAsString == "GREGORIAN:2018-10-05 CE",
        calendar == dateValueHasCalendar,
        startYear == dateValueHasStartYear,
        startMonth == dateValueHasStartMonth,
        startDay == dateValueHasStartDay,
        startEra == dateValueHasStartEra,
        endYear == dateValueHasStartYear,
        endMonth == dateValueHasStartMonth,
        endDay == dateValueHasStartDay,
        endEra == dateValueHasStartEra,
      )
    },
    test("create a text value with standoff containing a footnote with apostrophe (no extra slashes)") {
      val resourceIri: IRI = AThing.iri
      val textValueAsXml: String =
        """|<?xml version="1.0" encoding="UTF-8"?>
           |<text>Text <footnote content="Apostrophe start ' end"/> end text</text>
           |""".stripMargin

      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri

      for {
        maybeResourceLastModDate: Option[Instant] <- getResourceLastModificationDate(resourceIri)

        jsonLd = createTextValueWithStandoffRequest(
                   resourceIri = resourceIri,
                   textValueAsXml = textValueAsXml,
                   mappingIri = standardMappingIri,
                 )

        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))

        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = valueIri,
                      )

        savedTextValueAsXml <- ZIO.fromEither(
                                 savedValue.getRequiredString(KA.TextValueAsXml),
                               )
      } yield {
        assertTrue(savedTextValueAsXml.contains("Apostrophe start &apos; end"))
      }
    },
    test("create a date value representing a single date with month precision") {
      val resourceIri: IRI       = AThing.iri
      val propertyIri            = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar   = "GREGORIAN"
      val dateValueHasStartYear  = 2018
      val dateValueHasStartMonth = 10
      val dateValueHasStartEra   = "CE"

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd = createDateValueWithMonthPrecisionRequest(
                   resourceIri = resourceIri,
                   dateValueHasCalendar = dateValueHasCalendar,
                   dateValueHasStartYear = dateValueHasStartYear,
                   dateValueHasStartMonth = dateValueHasStartMonth,
                   dateValueHasStartEra = dateValueHasStartEra,
                   dateValueHasEndYear = dateValueHasStartYear,
                   dateValueHasEndMonth = dateValueHasStartMonth,
                   dateValueHasEndEra = dateValueHasStartEra,
                 )
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = dateValueIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = dateValueIri.get,
                      )
        actualValueAsString        <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueAsString))
        actualDateValueHasCalendar <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasCalendar))
        startYear                  <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartYear))
        startMonth                 <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartMonth))
        startDay                   <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasStartDay))
        startEra                   <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasStartEra))
        endYear                    <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndYear))
        endMonth                   <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndMonth))
        endDay                     <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasEndDay))
        endEra                     <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasEndEra))
      } yield assertTrue(
        actualValueAsString == "GREGORIAN:2018-10 CE",
        valueType == KA.DateValue,
        actualDateValueHasCalendar == dateValueHasCalendar,
        startYear == dateValueHasStartYear,
        startMonth == dateValueHasStartMonth,
        startDay.isEmpty,
        startEra == dateValueHasStartEra,
        endYear == dateValueHasStartYear,
        endMonth == dateValueHasStartMonth,
        endDay.isEmpty,
        endEra == dateValueHasStartEra,
      )
    },
    test("create a date value representing a single date with year precision") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri           = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar  = "GREGORIAN"
      val dateValueHasStartYear = 2018
      val dateValueHasStartEra  = "CE"

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd = createDateValueWithYearPrecisionRequest(
                   resourceIri = resourceIri,
                   dateValueHasCalendar = dateValueHasCalendar,
                   dateValueHasStartYear = dateValueHasStartYear,
                   dateValueHasStartEra = dateValueHasStartEra,
                   dateValueHasEndYear = dateValueHasStartYear,
                   dateValueHasEndEra = dateValueHasStartEra,
                 )
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = dateValueIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = dateValueIri.get,
                      )
        actualValueAsString        <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueAsString))
        actualDateValueHasCalendar <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasCalendar))
        startYear                  <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartYear))
        startMonth                 <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasStartMonth))
        startDay                   <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasStartDay))
        startEra                   <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasStartEra))
        endYear                    <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndYear))
        endMonth                   <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasEndMonth))
        endDay                     <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasEndDay))
        endEra                     <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasEndEra))
      } yield assertTrue(
        actualValueAsString == "GREGORIAN:2018 CE",
        actualDateValueHasCalendar == dateValueHasCalendar,
        startYear == dateValueHasStartYear,
        startMonth.isEmpty,
        startDay.isEmpty,
        startEra == dateValueHasStartEra,
        endYear == dateValueHasStartYear,
        endMonth.isEmpty,
        endDay.isEmpty,
        endEra == dateValueHasStartEra,
        valueType == KA.DateValue,
      )
    },
    test("create a date value representing a single Islamic date with day precision") {
      val resourceIri: IRI       = AThing.iri
      val propertyIri            = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar   = "ISLAMIC"
      val dateValueHasStartYear  = 1407
      val dateValueHasStartMonth = 1
      val dateValueHasStartDay   = 26

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd = createIslamicDateValueWithDayPrecisionRequest(
                   resourceIri = resourceIri,
                   dateValueHasCalendar = dateValueHasCalendar,
                   dateValueHasStartYear = dateValueHasStartYear,
                   dateValueHasStartMonth = dateValueHasStartMonth,
                   dateValueHasStartDay = dateValueHasStartDay,
                   dateValueHasEndYear = dateValueHasStartYear,
                   dateValueHasEndMonth = dateValueHasStartMonth,
                   dateValueHasEndDay = dateValueHasStartDay,
                 )
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = dateValueIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = dateValueIri.get,
                      )
        actualValueAsString        <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueAsString))
        actualDateValueHasCalendar <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasCalendar))
        startYear                  <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartYear))
        startMonth                 <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartMonth))
        startDay                   <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartDay))
        endYear                    <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndYear))
        endMonth                   <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndMonth))
        endDay                     <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndDay))
      } yield assertTrue(
        actualValueAsString == "ISLAMIC:1407-01-26",
        actualDateValueHasCalendar == dateValueHasCalendar,
        startYear == dateValueHasStartYear,
        startMonth == dateValueHasStartMonth,
        startDay == dateValueHasStartDay,
        endYear == dateValueHasStartYear,
        endMonth == dateValueHasStartMonth,
        endDay == dateValueHasStartDay,
        valueType == KA.DateValue,
      )
    },
    test("create an Islamic date value representing a range with day precision") {
      val resourceIri: IRI       = AThing.iri
      val propertyIri            = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar   = "ISLAMIC"
      val dateValueHasStartYear  = 1407
      val dateValueHasStartMonth = 1
      val dateValueHasStartDay   = 15
      val dateValueHasEndYear    = 1407
      val dateValueHasEndMonth   = 1
      val dateValueHasEndDay     = 26

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd = createIslamicDateValueWithDayPrecisionRequest(
                   resourceIri = resourceIri,
                   dateValueHasCalendar = dateValueHasCalendar,
                   dateValueHasStartYear = dateValueHasStartYear,
                   dateValueHasStartMonth = dateValueHasStartMonth,
                   dateValueHasStartDay = dateValueHasStartDay,
                   dateValueHasEndYear = dateValueHasEndYear,
                   dateValueHasEndMonth = dateValueHasEndMonth,
                   dateValueHasEndDay = dateValueHasEndDay,
                 )
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = dateValueIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = dateValueIri.get,
                      )
        actualValueAsString        <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueAsString))
        actualDateValueHasCalendar <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasCalendar))
        startYear                  <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartYear))
        startMonth                 <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartMonth))
        startDay                   <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartDay))
        endYear                    <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndYear))
        endMonth                   <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndMonth))
        endDay                     <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndDay))
      } yield assertTrue(
        actualValueAsString == "ISLAMIC:1407-01-15:1407-01-26",
        actualDateValueHasCalendar == dateValueHasCalendar,
        startYear == dateValueHasStartYear,
        startMonth == dateValueHasStartMonth,
        startDay == dateValueHasStartDay,
        endYear == dateValueHasEndYear,
        endMonth == dateValueHasEndMonth,
        endDay == dateValueHasEndDay,
        valueType == KA.DateValue,
      )
    },
    test("create a boolean value") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri
      val booleanValue: Boolean = true

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd =
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
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = booleanValueIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = booleanValueIri.get,
                      )
        booleanValueAsBoolean <- ZIO.fromEither(savedValue.getRequiredBoolean(KA.BooleanValueAsBoolean))
      } yield assertTrue(valueType == KA.BooleanValue, booleanValueAsBoolean == booleanValue)
    },
    test("create a geometry value") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd =
          s"""{
             |  "@id" : "$resourceIri",
             |  "@type" : "anything:Thing",
             |  "anything:hasGeometry" : {
             |    "@type" : "knora-api:GeomValue",
             |    "knora-api:geometryValueAsGeometry" : ${Json.Str(geometryValue1).toJson}
             |  },
             |  "@context" : {
             |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
             |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
             |  }
             |}""".stripMargin
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = geometryValueIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = geometryValueIri.get,
                      )
        geometryValueAsGeometry <- ZIO.fromEither(savedValue.getRequiredString(KA.GeometryValueAsGeometry))
      } yield assertTrue(geometryValueAsGeometry == geometryValue1, valueType == KA.GeomValue)
    },
    test("create an interval value") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
      val intervalStart         = BigDecimal("1.2")
      val intervalEnd           = BigDecimal("3.4")

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd =
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

        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = intervalValueIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = intervalValueIri.get,
                      )
        savedIntervalValueHasStart = savedValue.requireDatatypeValueInObject(
                                       key = KA.IntervalValueHasStart,
                                       expectedDatatype = Xsd.Decimal.toSmartIri,
                                       validationFun =
                                         (s, errorFun) => ValuesValidator.validateBigDecimal(s).getOrElse(errorFun),
                                     )
        savedIntervalValueHasEnd: BigDecimal = savedValue.requireDatatypeValueInObject(
                                                 key = KA.IntervalValueHasEnd,
                                                 expectedDatatype = Xsd.Decimal.toSmartIri,
                                                 validationFun = (s, errorFun) =>
                                                   ValuesValidator.validateBigDecimal(s).getOrElse(errorFun),
                                               )
      } yield assertTrue(
        valueType == KA.IntervalValue,
        savedIntervalValueHasEnd == intervalEnd,
        savedIntervalValueHasStart == intervalStart,
      )
    },
    test("create a time value") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasTimeStamp".toSmartIri
      val timeStamp             = Instant.parse("2019-08-28T15:59:12.725007Z")
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd =
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
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = timeValueIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = timeValueIri.get,
                      )
        savedTimeStamp = savedValue.requireDatatypeValueInObject(
                           key = KA.TimeValueAsTimeStamp,
                           expectedDatatype = Xsd.DateTimeStamp.toSmartIri,
                           validationFun =
                             (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
                         )
      } yield assertTrue(valueType == KA.TimeValue, savedTimeStamp == timeStamp)
    },
    test("create a list value") {
      val resourceIri = AThing.iri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val listNode    = "http://rdfh.ch/lists/0001/treeList03"
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd =
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

        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = listValueIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = listValueIri.get,
                      )
        savedListValueHasListNode <-
          ZIO.fromEither(savedValue.getRequiredObject(KA.ListValueAsListNode).flatMap(_.getIri))
      } yield assertTrue(valueType == KA.ListValue, savedListValueHasListNode == listNode)
    },
    test("create a color value") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
      val color                 = "#ff3333"
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd =
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
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = colorValueIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = colorValueIri.get,
                      )
        savedColor <- ZIO.fromEither(savedValue.getRequiredString(KA.ColorValueAsColor))
      } yield assertTrue(valueType == KA.ColorValue, savedColor == color)
    },
    test("create a URI value") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
      val uri                   = URI.create("https://www.knora.org")
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd =
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
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = uriValueIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = uriValueIri.get,
                      )
        savedUri <- ZIO.fromEither(savedValue.getRequiredUri(KA.UriValueAsUri))
      } yield assertTrue(valueType == KA.UriValue, savedUri == uri)
    },
    test("create a geoname value") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
      val geonameCode           = "2661604"
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd =
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
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = geonameValueIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = geonameValueIri.get,
                      )
        savedGeonameCode <- ZIO.fromEither(savedValue.getRequiredString(KA.GeonameValueAsGeonameCode))
      } yield assertTrue(valueType == KA.GeonameValue, savedGeonameCode == geonameCode)
    },
    test("create a link between two resources, without a comment") {
      val resourceIri: IRI               = AThing.iri
      val linkPropertyIri: SmartIri      = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThing".toSmartIri
      val linkValuePropertyIri: SmartIri = linkPropertyIri.fromLinkPropToLinkValueProp
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        jsonLd: String =
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
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                = linkValueIri.set(valueIri)
        valueType       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        _ <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(KA.ValueHasUUID)).tap { uuid =>
               ZIO.succeed(self.linkValueUUID = UuidUtil.decode(uuid))
             }
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = linkPropertyIri,
                        propertyIriInResult = linkValuePropertyIri,
                        expectedValueIri = linkValueIri.get,
                      )
        savedTarget    <- ZIO.fromEither(savedValue.getRequiredObject(KA.LinkValueHasTarget))
        savedTargetIri <- ZIO.fromEither(savedTarget.getRequiredString(JsonLDKeywords.ID))
      } yield assertTrue(valueType == KA.LinkValue, savedTargetIri == TestDing.iri)
    },
    test("create a link between two resources with a custom link value IRI, UUID, creationDate") {
      val resourceIri: IRI            = AThing.iri
      val targetResourceIri: IRI      = "http://rdfh.ch/0001/CNhWoNGGT7iWOrIwxsEqvA"
      val customValueIri: IRI         = "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw"
      val customValueUUID             = "mr9i2aUUJolv64V_9hYdTw"
      val customCreationDate: Instant = Instant.parse("2020-06-04T11:36:54.502951Z")

      val jsonLd =
        s"""{
           |  "@id" : "$resourceIri",
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

      for {
        responseJsonDoc <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri        <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        valueUUID       <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(KA.ValueHasUUID))
        savedCreationDate = responseJsonDoc.body.requireDatatypeValueInObject(
                              key = KA.ValueCreationDate,
                              expectedDatatype = Xsd.DateTimeStamp.toSmartIri,
                              validationFun =
                                (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
                            )
      } yield assertTrue(
        valueUUID == customValueUUID,
        valueIri == customValueIri,
        savedCreationDate == customCreationDate,
      )
    },
    test("update an integer value") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue: Int         = 5

      val jsonLd =
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
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = intValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        newIntegerValueUUID = responseJsonDoc.body.requireStringWithValidation(
                                KA.ValueHasUUID,
                                (key, errorFun) => UuidUtil.base64Decode(key).getOrElse(errorFun),
                              )
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = intValueIri.get,
                      )
        intValueAsInt <- ZIO.fromEither(savedValue.getRequiredInt(KA.IntValueAsInt))
      } yield assertTrue(
        newIntegerValueUUID == integerValueUUID, // The new version should have the same UUID.
        intValueAsInt == intValue,
        valueType == KA.IntValue,
      )
    },
    test("update an integer value with a custom creation date") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue: Int         = 6
      val valueCreationDate     = Instant.now

      val jsonLd =
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
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = intValueForRsyncIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = intValueForRsyncIri.get,
                      )
        intValueAsInt <- ZIO.fromEither(savedValue.getRequiredInt(KA.IntValueAsInt))
        savedCreationDate: Instant = savedValue.requireDatatypeValueInObject(
                                       key = KA.ValueCreationDate,
                                       expectedDatatype = Xsd.DateTimeStamp.toSmartIri,
                                       validationFun = (s, errorFun) =>
                                         ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
                                     )
      } yield assertTrue(valueType == KA.IntValue, savedCreationDate == valueCreationDate, intValueAsInt == intValue)
    },
    test("update an integer value with a custom new value version IRI") {
      val resourceIri: IRI        = AThing.iri
      val propertyIri: SmartIri   = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue: Int           = 7
      val newValueVersionIri: IRI = s"http://rdfh.ch/0001/a-thing/values/W8COP_RXRpqVsjW9NL2JYg"

      val jsonLd = updateIntValueWithCustomNewValueVersionIriRequest(
        resourceIri = resourceIri,
        valueIri = intValueForRsyncIri.get,
        intValue = intValue,
        newValueVersionIri = newValueVersionIri,
      )

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = intValueForRsyncIri.set(valueIri)
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = intValueForRsyncIri.get,
                      )
        intValueAsInt <- ZIO.fromEither(savedValue.getRequiredInt(KA.IntValueAsInt))
      } yield assertTrue(valueIri == newValueVersionIri, intValueAsInt == intValue)
    },
    test("not update an integer value with a custom new value version IRI that is the same as the current IRI") {
      val resourceIri: IRI        = AThing.iri
      val intValue: Int           = 8
      val newValueVersionIri: IRI = s"http://rdfh.ch/0001/a-thing/values/W8COP_RXRpqVsjW9NL2JYg"

      val jsonLd = updateIntValueWithCustomNewValueVersionIriRequest(
        resourceIri = resourceIri,
        valueIri = intValueForRsyncIri.get,
        intValue = intValue,
        newValueVersionIri = newValueVersionIri,
      )
      TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert400).as(assertCompletes)
    },
    test("not update an integer value with an invalid custom new value version IRI") {
      val resourceIri: IRI        = AThing.iri
      val intValue: Int           = 8
      val newValueVersionIri: IRI = "http://example.com/foo"

      val jsonLd = updateIntValueWithCustomNewValueVersionIriRequest(
        resourceIri = resourceIri,
        valueIri = intValueForRsyncIri.get,
        intValue = intValue,
        newValueVersionIri = newValueVersionIri,
      )
      TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert400).as(assertCompletes)
    },
    test("not update an integer value with a custom new value version IRI that refers to the wrong project code") {
      val resourceIri: IRI        = AThing.iri
      val intValue: Int           = 8
      val newValueVersionIri: IRI = "http://rdfh.ch/0002/a-thing/values/foo"

      val jsonLd = updateIntValueWithCustomNewValueVersionIriRequest(
        resourceIri = resourceIri,
        valueIri = intValueForRsyncIri.get,
        intValue = intValue,
        newValueVersionIri = newValueVersionIri,
      )
      TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert400).as(assertCompletes)
    },
    test("not update an integer value with a custom new value version IRI that refers to the wrong resource") {
      val resourceIri: IRI        = AThing.iri
      val intValue: Int           = 8
      val newValueVersionIri: IRI = "http://rdfh.ch/0001/nResNuvARcWYUdWyo0GWGw/values/iEYi6E7Ntjvj2syzJZiXlg"

      val jsonLd = updateIntValueWithCustomNewValueVersionIriRequest(
        resourceIri = resourceIri,
        valueIri = intValueForRsyncIri.get,
        intValue = intValue,
        newValueVersionIri = newValueVersionIri,
      )
      TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert400).as(assertCompletes)
    },
    test("not update an integer value if the simple schema is submitted") {
      val resourceIri: IRI = AThing.iri
      val intValue: Int    = 10

      val jsonLd =
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
      TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert400).as(assertCompletes)
    },
    test("update an integer value with custom permissions") {
      val resourceIri: IRI          = AThing.iri
      val propertyIri: SmartIri     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue: Int             = 3879
      val customPermissions: String = "CR http://rdfh.ch/groups/0001/thing-searcher"

      val jsonLd =
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

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = intValueWithCustomPermissionsIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = intValueWithCustomPermissionsIri.get,
                      )
        intValueAsInt  <- ZIO.fromEither(savedValue.getRequiredInt(KA.IntValueAsInt))
        hasPermissions <- ZIO.fromEither(savedValue.getRequiredString(KA.HasPermissions))
      } yield assertTrue(valueType == KA.IntValue, hasPermissions == customPermissions, intValueAsInt == intValue)
    },
    test("update an integer value, changing only the permissions") {
      val resourceIri: IRI          = AThing.iri
      val propertyIri: SmartIri     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val customPermissions: String = "CR http://rdfh.ch/groups/0001/thing-searcher|V knora-admin:KnownUser"

      val jsonLd =
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

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = intValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = intValueIri.get,
                      )
        hasPermissions <- ZIO.fromEither(savedValue.getRequiredString(KA.HasPermissions))
      } yield assertTrue(hasPermissions == customPermissions, valueType == KA.IntValue)
    },
    test("update a decimal value") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
      val decimalValue          = BigDecimal(5.6)

      val jsonLd =
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

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = decimalValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = decimalValueIri.get,
                      )
        savedDecimalValue = savedValue.requireDatatypeValueInObject(
                              key = KA.DecimalValueAsDecimal,
                              expectedDatatype = Xsd.Decimal.toSmartIri,
                              validationFun = (s, errorFun) => ValuesValidator.validateBigDecimal(s).getOrElse(errorFun),
                            )
      } yield assertTrue(valueType == KA.DecimalValue, savedDecimalValue == decimalValue)
    },
    test("update a text value with standoff") {
      val resourceIri: IRI = AThing.iri

      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri

      val jsonLd =
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasText" : {
           |    "@id" : "${textValueWithStandoffIri.get}",
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:textValueAsXml" : ${textValue2AsXmlWithStandardMapping.toJson},
           |    "knora-api:textValueHasMapping" : {
           |      "@id": "$standardMappingIri"
           |    }
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = textValueWithStandoffIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = textValueWithStandoffIri.get,
                      )
        savedTextValueAsXml <- ZIO.fromEither(savedValue.getRequiredString(KA.TextValueAsXml))
      } yield assertTrue(
        savedTextValueAsXml.contains("updated text"),
        savedTextValueAsXml.contains("salsah-link"),
        valueType == KA.TextValue,
      )
    },
    test("update a text value with standoff containing escaped text") {
      val resourceIri = AThing.iri
      val jsonLd =
        FileUtil.readTextFile(Paths.get("test_data/generated_test_data/valuesE2EV2/UpdateValueWithEscape.jsonld"))
      val jsonLdWithResourceValueIri = jsonLd.replace("VALUE_IRI", textValueWithEscapeIri.get)
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc <- TestApiClient
                             .putJsonLdDocument(uri"/v2/values", jsonLdWithResourceValueIri, anythingUser1)
                             .flatMap(_.assert200)
        valueIri   <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _           = textValueWithEscapeIri.set(valueIri)
        propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = valueIri,
                      )
        savedTextValueAsXml <- ZIO.fromEither(savedValue.getRequiredString(KA.TextValueAsXml))
        expectedText = """<p>
                         | test update</p>""".stripMargin
      } yield assertTrue(savedTextValueAsXml.contains(expectedText))
    },
    test("update a text value with standoff containing horrible input") {
      val resourceIri = AThing.iri
      val jsonLd =
        FileUtil.readTextFile(Paths.get("test_data/generated_test_data/valuesE2EV2/UpdateValueWithTripleQuote.jsonld"))
      val jsonLdWithResourceValueIri = jsonLd.replace("VALUE_IRI", textValueWithEscapeIri.get)
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc <- TestApiClient
                             .putJsonLdDocument(uri"/v2/values", jsonLdWithResourceValueIri, anythingUser1)
                             .flatMap(_.assert200)
        valueIri   <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _           = textValueWithEscapeIri.set(valueIri)
        propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = valueIri,
                      )
        savedTextValueAsXml <- ZIO.fromEither(savedValue.getRequiredString(KA.TextValueAsXml))
        expectedText         = """<p>&quot;&quot;&quot;</p>""".stripMargin
      } yield assertTrue(savedTextValueAsXml.contains(expectedText))
    },
    test("update a text value with a comment") {
      val resourceIri: IRI        = AThing.iri
      val valueAsString: String   = "this is a text value that has a 'thoroughly' updated comment"
      val valueHasComment: String = "this is an updated comment"
      val propertyIri: SmartIri   = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri

      val jsonLd = updateTextValueWithCommentRequest(
        resourceIri = resourceIri,
        valueIri = textValueWithoutStandoffIri.get,
        valueAsString = valueAsString,
        valueHasComment = valueHasComment,
      )

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = textValueWithoutStandoffIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = textValueWithoutStandoffIri.get,
                      )
        savedValueAsString   <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueAsString))
        savedValueHasComment <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueHasComment))
      } yield assertTrue(
        valueType == KA.TextValue,
        savedValueHasComment == valueHasComment,
        savedValueAsString == valueAsString,
      )
    },
    test("update a date value representing a range with day precision") {
      val resourceIri: IRI       = AThing.iri
      val propertyIri            = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar   = "GREGORIAN"
      val dateValueHasStartYear  = 2018
      val dateValueHasStartMonth = 10
      val dateValueHasStartDay   = 5
      val dateValueHasStartEra   = "CE"
      val dateValueHasEndYear    = 2018
      val dateValueHasEndMonth   = 12
      val dateValueHasEndDay     = 6
      val dateValueHasEndEra     = "CE"

      val jsonLd = updateDateValueWithDayPrecisionRequest(
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
        dateValueHasEndEra = dateValueHasEndEra,
      )

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = dateValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = dateValueIri.get,
                      )
        valueAsString             <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueAsString))
        dateValueHasCalendarStr   <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasCalendar))
        dateValueHasStartYearInt  <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartYear))
        dateValueHasStartMonthInt <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartMonth))
        dateValueHasStartDayInt   <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartDay))
        dateValueHasStartEraStr   <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasStartEra))
        dateValueHasEndYearInt    <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndYear))
        dateValueHasEndMonthInt   <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndMonth))
        dateValueHasEndDayInt     <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndDay))
        dateValueHasEndEraStr     <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasEndEra))
      } yield assertTrue(
        valueAsString == "GREGORIAN:2018-10-05 CE:2018-12-06 CE",
        dateValueHasCalendarStr == dateValueHasCalendar,
        dateValueHasStartYearInt == dateValueHasStartYear,
        dateValueHasStartMonthInt == dateValueHasStartMonth,
        dateValueHasStartDayInt == dateValueHasStartDay,
        dateValueHasStartEraStr == dateValueHasStartEra,
        dateValueHasEndYearInt == dateValueHasEndYear,
        dateValueHasEndMonthInt == dateValueHasEndMonth,
        dateValueHasEndDayInt == dateValueHasEndDay,
        dateValueHasEndEraStr == dateValueHasEndEra,
        valueType == KA.DateValue,
      )
    },
    test("update a date value representing a range with month precision") {
      val resourceIri: IRI       = AThing.iri
      val propertyIri            = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar   = "GREGORIAN"
      val dateValueHasStartYear  = 2018
      val dateValueHasStartMonth = 9
      val dateValueHasStartEra   = "CE"
      val dateValueHasEndYear    = 2018
      val dateValueHasEndMonth   = 12
      val dateValueHasEndEra     = "CE"

      val jsonLd = updateDateValueWithMonthPrecisionRequest(
        resourceIri = resourceIri,
        valueIri = dateValueIri.get,
        dateValueHasCalendar = dateValueHasCalendar,
        dateValueHasStartYear = dateValueHasStartYear,
        dateValueHasStartMonth = dateValueHasStartMonth,
        dateValueHasStartEra = dateValueHasStartEra,
        dateValueHasEndYear = dateValueHasEndYear,
        dateValueHasEndMonth = dateValueHasEndMonth,
        dateValueHasEndEra = dateValueHasEndEra,
      )

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = dateValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = dateValueIri.get,
                      )
        valueAsString             <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueAsString))
        dateValueHasCalendarStr   <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasCalendar))
        dateValueHasStartYearInt  <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartYear))
        dateValueHasStartMonthInt <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartMonth))
        dateValueHasStartDayOpt   <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasStartDay))
        dateValueHasStartEraStr   <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasStartEra))
        dateValueHasEndYearInt    <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndYear))
        dateValueHasEndMonthInt   <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndMonth))
        dateValueHasEndDayOpt     <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasEndDay))
        dateValueHasEndEraStr     <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasEndEra))
      } yield assertTrue(
        valueType == KA.DateValue,
        valueAsString == "GREGORIAN:2018-09 CE:2018-12 CE",
        dateValueHasCalendarStr == dateValueHasCalendar,
        dateValueHasStartYearInt == dateValueHasStartYear,
        dateValueHasStartMonthInt == dateValueHasStartMonth,
        dateValueHasStartDayOpt.isEmpty,
        dateValueHasStartEraStr == dateValueHasStartEra,
        dateValueHasEndYearInt == dateValueHasEndYear,
        dateValueHasEndMonthInt == dateValueHasEndMonth,
        dateValueHasEndDayOpt.isEmpty,
        dateValueHasEndEraStr == dateValueHasEndEra,
      )
    },
    test("update a date value representing a range with year precision") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri           = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar  = "GREGORIAN"
      val dateValueHasStartYear = 2018
      val dateValueHasStartEra  = "CE"
      val dateValueHasEndYear   = 2020
      val dateValueHasEndEra    = "CE"

      val jsonLd = updateDateValueWithYearPrecisionRequest(
        resourceIri = resourceIri,
        valueIri = dateValueIri.get,
        dateValueHasCalendar = dateValueHasCalendar,
        dateValueHasStartYear = dateValueHasStartYear,
        dateValueHasStartEra = dateValueHasStartEra,
        dateValueHasEndYear = dateValueHasEndYear,
        dateValueHasEndEra = dateValueHasEndEra,
      )

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = dateValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = dateValueIri.get,
                      )
        valueAsString             <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueAsString))
        dateValueHasCalendarStr   <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasCalendar))
        dateValueHasStartYearInt  <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartYear))
        dateValueHasStartMonthOpt <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasStartMonth))
        dateValueHasStartDayOpt   <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasStartDay))
        dateValueHasStartEraStr   <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasStartEra))
        dateValueHasEndYearInt    <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndYear))
        dateValueHasEndMonthOpt   <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasEndMonth))
        dateValueHasEndDayOpt     <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasEndDay))
        dateValueHasEndEraStr     <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasEndEra))
      } yield assertTrue(
        valueType == KA.DateValue,
        valueAsString == "GREGORIAN:2018 CE:2020 CE",
        dateValueHasCalendarStr == dateValueHasCalendar,
        dateValueHasStartYearInt == dateValueHasStartYear,
        dateValueHasStartMonthOpt.isEmpty,
        dateValueHasStartDayOpt.isEmpty,
        dateValueHasStartEraStr == dateValueHasStartEra,
        dateValueHasEndYearInt == dateValueHasEndYear,
        dateValueHasEndMonthOpt.isEmpty,
        dateValueHasEndDayOpt.isEmpty,
        dateValueHasEndEraStr == dateValueHasEndEra,
      )
    },
    test("update a date value representing a single date with day precision") {
      val resourceIri: IRI       = AThing.iri
      val propertyIri            = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar   = "GREGORIAN"
      val dateValueHasStartYear  = 2018
      val dateValueHasStartMonth = 10
      val dateValueHasStartDay   = 6
      val dateValueHasStartEra   = "CE"

      val jsonLd = updateDateValueWithDayPrecisionRequest(
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
        dateValueHasEndEra = dateValueHasStartEra,
      )

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = dateValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = dateValueIri.get,
                      )
        valueAsString             <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueAsString))
        dateValueHasCalendarStr   <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasCalendar))
        dateValueHasStartYearInt  <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartYear))
        dateValueHasStartMonthInt <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartMonth))
        dateValueHasStartDayInt   <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartDay))
        dateValueHasStartEraStr   <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasStartEra))
        dateValueHasEndYearInt    <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndYear))
        dateValueHasEndMonthInt   <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndMonth))
        dateValueHasEndDayInt     <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndDay))
        dateValueHasEndEraStr     <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasEndEra))
      } yield assertTrue(
        valueType == KA.DateValue,
        valueAsString == "GREGORIAN:2018-10-06 CE",
        dateValueHasCalendarStr == dateValueHasCalendar,
        dateValueHasStartYearInt == dateValueHasStartYear,
        dateValueHasStartMonthInt == dateValueHasStartMonth,
        dateValueHasStartDayInt == dateValueHasStartDay,
        dateValueHasStartEraStr == dateValueHasStartEra,
        dateValueHasEndYearInt == dateValueHasStartYear,
        dateValueHasEndMonthInt == dateValueHasStartMonth,
        dateValueHasEndDayInt == dateValueHasStartDay,
        dateValueHasEndEraStr == dateValueHasStartEra,
      )
    },
    test("update a date value representing a single date with month precision") {
      val resourceIri: IRI       = AThing.iri
      val propertyIri            = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar   = "GREGORIAN"
      val dateValueHasStartYear  = 2018
      val dateValueHasStartMonth = 7
      val dateValueHasStartEra   = "CE"

      val jsonLd = updateDateValueWithMonthPrecisionRequest(
        resourceIri = resourceIri,
        valueIri = dateValueIri.get,
        dateValueHasCalendar = dateValueHasCalendar,
        dateValueHasStartYear = dateValueHasStartYear,
        dateValueHasStartMonth = dateValueHasStartMonth,
        dateValueHasStartEra = dateValueHasStartEra,
        dateValueHasEndYear = dateValueHasStartYear,
        dateValueHasEndMonth = dateValueHasStartMonth,
        dateValueHasEndEra = dateValueHasStartEra,
      )

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = dateValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = dateValueIri.get,
                      )
        valueAsString             <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueAsString))
        dateValueHasCalendarStr   <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasCalendar))
        dateValueHasStartYearInt  <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartYear))
        dateValueHasStartMonthInt <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartMonth))
        dateValueHasStartDayOpt   <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasStartDay))
        dateValueHasStartEraStr   <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasStartEra))
        dateValueHasEndYearInt    <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndYear))
        dateValueHasEndMonthInt   <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndMonth))
        dateValueHasEndDayOpt     <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasEndDay))
        dateValueHasEndEraStr     <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasEndEra))
      } yield assertTrue(
        valueType == KA.DateValue,
        valueAsString == "GREGORIAN:2018-07 CE",
        dateValueHasCalendarStr == dateValueHasCalendar,
        dateValueHasStartYearInt == dateValueHasStartYear,
        dateValueHasStartMonthInt == dateValueHasStartMonth,
        dateValueHasStartDayOpt.isEmpty,
        dateValueHasStartEraStr == dateValueHasStartEra,
        dateValueHasEndYearInt == dateValueHasStartYear,
        dateValueHasEndMonthInt == dateValueHasStartMonth,
        dateValueHasEndDayOpt.isEmpty,
        dateValueHasEndEraStr == dateValueHasStartEra,
      )
    },
    test("update a date value representing a single date with year precision") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri           = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val dateValueHasCalendar  = "GREGORIAN"
      val dateValueHasStartYear = 2019
      val dateValueHasStartEra  = "CE"

      val jsonLd = updateDateValueWithYearPrecisionRequest(
        resourceIri = resourceIri,
        valueIri = dateValueIri.get,
        dateValueHasCalendar = dateValueHasCalendar,
        dateValueHasStartYear = dateValueHasStartYear,
        dateValueHasStartEra = dateValueHasStartEra,
        dateValueHasEndYear = dateValueHasStartYear,
        dateValueHasEndEra = dateValueHasStartEra,
      )

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = dateValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = dateValueIri.get,
                      )
        valueAsString             <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueAsString))
        dateValueHasCalendarStr   <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasCalendar))
        dateValueHasStartYearInt  <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasStartYear))
        dateValueHasStartMonthOpt <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasStartMonth))
        dateValueHasStartDayOpt   <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasStartDay))
        dateValueHasStartEraStr   <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasStartEra))
        dateValueHasEndYearInt    <- ZIO.fromEither(savedValue.getRequiredInt(KA.DateValueHasEndYear))
        dateValueHasEndMonthOpt   <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasEndMonth))
        dateValueHasEndDayOpt     <- ZIO.fromEither(savedValue.getInt(KA.DateValueHasEndDay))
        dateValueHasEndEraStr     <- ZIO.fromEither(savedValue.getRequiredString(KA.DateValueHasEndEra))
      } yield assertTrue(
        valueType == KA.DateValue,
        valueAsString == "GREGORIAN:2019 CE",
        dateValueHasCalendarStr == dateValueHasCalendar,
        dateValueHasStartYearInt == dateValueHasStartYear,
        dateValueHasStartMonthOpt.isEmpty,
        dateValueHasStartDayOpt.isEmpty,
        dateValueHasStartEraStr == dateValueHasStartEra,
        dateValueHasEndYearInt == dateValueHasStartYear,
        dateValueHasEndMonthOpt.isEmpty,
        dateValueHasEndDayOpt.isEmpty,
        dateValueHasEndEraStr == dateValueHasStartEra,
      )
    },
    test("update a boolean value") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri
      val booleanValue: Boolean = false

      val jsonLd =
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

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = booleanValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = booleanValueIri.get,
                      )
        booleanValueAsBoolean <- ZIO.fromEither(savedValue.getRequiredBoolean(KA.BooleanValueAsBoolean))
      } yield assertTrue(valueType == KA.BooleanValue, booleanValueAsBoolean == booleanValue)
    },
    test("update a geometry value") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri

      val jsonLd =
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasGeometry" : {
           |    "@id" : "${geometryValueIri.get}",
           |    "@type" : "knora-api:GeomValue",
           |    "knora-api:geometryValueAsGeometry" : ${geometryValue2.toJson}
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = geometryValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = geometryValueIri.get,
                      )
        geometryValueAsGeometry <- ZIO.fromEither(savedValue.getRequiredString(KA.GeometryValueAsGeometry))
      } yield assertTrue(valueType == KA.GeomValue, geometryValueAsGeometry == geometryValue2)
    },
    test("update an interval value") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
      val intervalStart         = BigDecimal("5.6")
      val intervalEnd           = BigDecimal("7.8")

      val jsonLd =
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

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = intervalValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = intervalValueIri.get,
                      )
        savedIntervalValueHasStart: BigDecimal = savedValue.requireDatatypeValueInObject(
                                                   key = KA.IntervalValueHasStart,
                                                   expectedDatatype = Xsd.Decimal.toSmartIri,
                                                   validationFun = (s, errorFun) =>
                                                     ValuesValidator.validateBigDecimal(s).getOrElse(errorFun),
                                                 )
        savedIntervalValueHasEnd: BigDecimal = savedValue.requireDatatypeValueInObject(
                                                 key = KA.IntervalValueHasEnd,
                                                 expectedDatatype = Xsd.Decimal.toSmartIri,
                                                 validationFun = (s, errorFun) =>
                                                   ValuesValidator.validateBigDecimal(s).getOrElse(errorFun),
                                               )
      } yield assertTrue(
        valueType == KA.IntervalValue,
        savedIntervalValueHasStart == intervalStart,
        savedIntervalValueHasEnd == intervalEnd,
      )
    },
    test("update a time value") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasTimeStamp".toSmartIri
      val timeStamp             = Instant.parse("2019-12-16T09:14:56.409249Z")

      val jsonLd =
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

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = timeValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = timeValueIri.get,
                      )
        savedTimeStamp: Instant = savedValue.requireDatatypeValueInObject(
                                    key = KA.TimeValueAsTimeStamp,
                                    expectedDatatype = Xsd.DateTimeStamp.toSmartIri,
                                    validationFun =
                                      (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
                                  )
      } yield assertTrue(valueType == KA.TimeValue, savedTimeStamp == timeStamp)
    },
    test("update a list value") {
      val resourceIri = AThing.iri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val listNode    = "http://rdfh.ch/lists/0001/treeList02"

      val jsonLd =
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

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = listValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = listValueIri.get,
                      )
        savedListValueHasListNode <-
          ZIO.fromEither(savedValue.getRequiredObject(KA.ListValueAsListNode).flatMap(_.getIri))
      } yield assertTrue(valueType == KA.ListValue, savedListValueHasListNode == listNode)
    },
    test("update a color value") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
      val color                 = "#ff3344"

      val jsonLd =
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

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = colorValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = colorValueIri.get,
                      )
        savedColor <- ZIO.fromEither(savedValue.getRequiredString(KA.ColorValueAsColor))
      } yield assertTrue(valueType == KA.ColorValue, savedColor == color)
    },
    test("update a URI value") {
      val resourceIri = AThing.iri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
      val uri         = URI.create("https://docs.knora.org")

      val jsonLd =
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

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = uriValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = uriValueIri.get,
                      )
        savedUri <- ZIO.fromEither(savedValue.getRequiredUri(KA.UriValueAsUri))
      } yield assertTrue(valueType == KA.UriValue, savedUri == uri)
    },
    test("update a geoname value") {
      val resourceIri: IRI      = AThing.iri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
      val geonameCode           = "2988507"

      val jsonLd =
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

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = geonameValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = propertyIri,
                        propertyIriInResult = propertyIri,
                        expectedValueIri = geonameValueIri.get,
                      )
        savedGeonameCode <- ZIO.fromEither(savedValue.getRequiredString(KA.GeonameValueAsGeonameCode))
      } yield assertTrue(valueType == KA.GeonameValue, savedGeonameCode == geonameCode)
    },
    test("update a link between two resources") {
      val resourceIri: IRI               = AThing.iri
      val linkPropertyIri: SmartIri      = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThing".toSmartIri
      val linkValuePropertyIri: SmartIri = linkPropertyIri.fromLinkPropToLinkValueProp
      val linkTargetIri: IRI             = "http://rdfh.ch/0001/5IEswyQFQp2bxXDrOyEfEA"

      val jsonLd = updateLinkValueRequest(
        resourceIri = resourceIri,
        valueIri = linkValueIri.get,
        targetResourceIri = linkTargetIri,
      )

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = linkValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        _ <- ZIO // When you change a link value's target, it gets a new UUID.
               .fromEither(responseJsonDoc.body.getRequiredString(KA.ValueHasUUID))
               .map(UuidUtil.decode)
               .filterOrFail(_ != linkValueUUID)(AssertionException(s"Expected different UUID"))
               .tap(uuid => ZIO.succeed(self.linkValueUUID = uuid))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = linkPropertyIri,
                        propertyIriInResult = linkValuePropertyIri,
                        expectedValueIri = linkValueIri.get,
                      )
        savedTarget    <- ZIO.fromEither(savedValue.getRequiredObject(KA.LinkValueHasTarget))
        savedTargetIri <- ZIO.fromEither(savedTarget.getRequiredString(JsonLDKeywords.ID))
      } yield assertTrue(valueType == KA.LinkValue, savedTargetIri == linkTargetIri)
    },
    test("update a link between two resources, adding a comment") {
      val resourceIri: IRI               = AThing.iri
      val linkPropertyIri: SmartIri      = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThing".toSmartIri
      val linkValuePropertyIri: SmartIri = linkPropertyIri.fromLinkPropToLinkValueProp
      val linkTargetIri: IRI             = "http://rdfh.ch/0001/5IEswyQFQp2bxXDrOyEfEA"
      val comment                        = "adding a comment"

      val jsonLd = updateLinkValueRequest(
        resourceIri = resourceIri,
        valueIri = linkValueIri.get,
        targetResourceIri = linkTargetIri,
        comment = Some(comment),
      )

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = linkValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        // Since we only changed metadata, the UUID should be the same.
        _ <- ZIO
               .fromEither(responseJsonDoc.body.getRequiredString(KA.ValueHasUUID))
               .filterOrFail(uuid => self.linkValueUUID == UuidUtil.decode(uuid))(
                 AssertionException(s"Expected same UUID"),
               )
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = linkPropertyIri,
                        propertyIriInResult = linkValuePropertyIri,
                        expectedValueIri = linkValueIri.get,
                      )
        savedComment <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueHasComment))
      } yield assertTrue(valueType == KA.LinkValue, savedComment == comment)
    },
    test("update a link between two resources, changing only the comment") {
      val resourceIri: IRI               = AThing.iri
      val linkPropertyIri: SmartIri      = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThing".toSmartIri
      val linkValuePropertyIri: SmartIri = linkPropertyIri.fromLinkPropToLinkValueProp
      val linkTargetIri: IRI             = "http://rdfh.ch/0001/5IEswyQFQp2bxXDrOyEfEA"
      val comment                        = "changing only the comment"

      val jsonLd = updateLinkValueRequest(
        resourceIri = resourceIri,
        valueIri = linkValueIri.get,
        targetResourceIri = linkTargetIri,
        comment = Some(comment),
      )

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.putJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = linkValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        // Since we only changed metadata, the UUID should be the same.
        newLinkValueUUID = responseJsonDoc.body.requireStringWithValidation(
                             KA.ValueHasUUID,
                             (key, errorFun) => UuidUtil.base64Decode(key).getOrElse(errorFun),
                           )
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = linkPropertyIri,
                        propertyIriInResult = linkValuePropertyIri,
                        expectedValueIri = linkValueIri.get,
                      )
        savedComment <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueHasComment))
      } yield assertTrue(newLinkValueUUID == linkValueUUID, valueType == KA.LinkValue, savedComment == comment)
    },
    test("create a link between two resources, with a comment") {
      val resourceIri: IRI               = AThing.iri
      val linkPropertyIri: SmartIri      = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThing".toSmartIri
      val linkValuePropertyIri: SmartIri = linkPropertyIri.fromLinkPropToLinkValueProp
      val comment                        = "Initial comment"

      val jsonLd =
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

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri)
        responseJsonDoc          <- TestApiClient.postJsonLdDocument(uri"/v2/values", jsonLd, anythingUser1).flatMap(_.assert200)
        valueIri                 <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID))
        _                         = linkValueIri.set(valueIri)
        valueType                <- ZIO.fromEither(responseJsonDoc.body.getRequiredString(JsonLDKeywords.TYPE))
        savedValue <- getValue(
                        resourceIri = resourceIri,
                        maybePreviousLastModDate = maybeResourceLastModDate,
                        propertyIriForGravsearch = linkPropertyIri,
                        propertyIriInResult = linkValuePropertyIri,
                        expectedValueIri = linkValueIri.get,
                      )
        savedTarget    <- ZIO.fromEither(savedValue.getRequiredObject(KA.LinkValueHasTarget))
        savedTargetIri <- ZIO.fromEither(savedTarget.getRequiredString(JsonLDKeywords.ID))
        savedComment   <- ZIO.fromEither(savedValue.getRequiredString(KA.ValueHasComment))
      } yield assertTrue(savedTargetIri == TestDing.iri, valueType == KA.LinkValue, savedComment == comment)
    },
    test("delete an integer value") {
      val jsonLd = deleteIntValueRequest(AThing.iri, intValueIri.get, "this value was incorrect")
      TestApiClient.postJsonLd(uri"/v2/values/delete", jsonLd, anythingUser1).flatMap(_.assert200).as(assertCompletes)
    },
    test("delete an integer value, supplying a custom delete date") {
      val deleteDate = Instant.now
      val jsonLd =
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
      TestApiClient.postJsonLd(uri"/v2/values/delete", jsonLd, anythingUser1).flatMap(_.assert200).as(assertCompletes)
    },
    test("not delete an integer value if the simple schema is submitted") {
      val jsonLd =
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
      TestApiClient.postJsonLd(uri"/v2/values/delete", jsonLd, anythingUser1).flatMap(_.assert400).as(assertCompletes)
    },
    test("delete an integer value without supplying a delete comment") {
      val resourceIri: IRI = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw"
      val valueIri: IRI    = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/dJ1ES8QTQNepFKF5-EAqdg"
      val timestamp        = "2018-05-28T15:52:03.897Z"

      val jsonLd = deleteIntValueRequest(resourceIri, valueIri)
      TestApiClient.postJsonLd(uri"/v2/values/delete", jsonLd, anythingUser2).flatMap(_.assert200) *>
        // Request the resource as it was before the value was deleted.
        TestApiClient
          .getJsonLd(uri"/v2/resources/$resourceIri", anythingUser2, addVersionQueryParam(timestamp))
          .flatMap(_.assert200) *>
        assertCompletes
    },
    test("delete a link between two resources") {
      val jsonLd =
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
      TestApiClient.postJsonLd(uri"/v2/values/delete", jsonLd, anythingUser1).flatMap(_.assert200) *>
        assertCompletes
    },
    test("update a TextValue comment containing linebreaks should store linebreaks as Unicode") {
      val resourceIri     = ResourceIri.unsafeFrom(AThing.iri.toSmartIri)
      val anythingHasText = anythingOntologyIri.makeProperty("hasText").toComplexSchema.toString
      val anythingThing   = anythingOntologyIri.makeClass("Thing").toComplexSchema.toString

      val commentWithLinebreaks = "This is line one\nThis is line two\nThis is line three"

      def valueJsonLd(props: (String, Json.Str)*) =
        Json.Obj(
          "@id"   -> Json.Str(resourceIri.toString),
          "@type" -> Json.Str(anythingThing),
          anythingHasText -> Json.Obj(
            Seq("@type" -> Json.Str(KA.TextValue), KA.ValueAsString -> Json.Str("Not important")) ++ props: _*,
          ),
        )

      for {
        responseJsonDoc <- TestApiClient
                             .postJsonLdDocument(uri"/v2/values", valueJsonLd().toJson, anythingUser1)
                             .flatMap(_.assert200)
        newValueIri <- responseJsonDoc.body.getRequiredIdValueAsKnoraDataIri
        updateValueJsonLd = valueJsonLd(
                              "@id"              -> Json.Str(newValueIri.toString),
                              KA.ValueHasComment -> Json.Str(commentWithLinebreaks),
                            )
        updatedValueResponse <- TestApiClient
                                  .putJsonLdDocument(uri"/v2/values", updateValueJsonLd.toJson, anythingUser1)
                                  .flatMap(_.assert200)
        updatedValueIri <- updatedValueResponse.body.getRequiredIdValueAsKnoraDataIri
        resource        <- TestResourcesApiClient.getResource(resourceIri, anythingUser1).flatMap(_.assert200)
        savedComment <- ZIO.fromEither(resource.body.getRequiredArray(anythingHasText).map {
                          _.value.collect { case obj: JsonLDObject => obj }
                            .filter(_.getRequiredString(JsonLDKeywords.ID).toOption.contains(updatedValueIri.toString))
                            .flatMap(_.getRequiredString(KA.ValueHasComment).toOption)
                            .head
                        })
      } yield assertTrue(savedComment == commentWithLinebreaks)
    },
  )
}
