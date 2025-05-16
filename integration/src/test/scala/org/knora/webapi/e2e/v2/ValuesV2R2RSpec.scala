/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials

import scala.concurrent.ExecutionContextExecutor

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import org.knora.webapi.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as OntConsts
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.MutableTestIri

/**
 * Tests creating a still image file value using a mock Sipi.
 */
class ValuesV2R2RSpec extends ITKnoraLiveSpec {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  private val aThingPictureIri = "http://rdfh.ch/0001/a-thing-picture"

  private val anythingUserEmail = SharedTestDataADM.anythingUser1.email
  private val password          = SharedTestDataADM.testPass

  private val stillImageFileValueIri = new MutableTestIri

  private val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)

  override lazy val rdfDataObjects = List(
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything",
    ),
  )

  private def getResourceWithValues(
    resourceIri: IRI,
    propertyIrisForGravsearch: Seq[SmartIri],
    userEmail: String,
  ): JsonLDDocument = {
    // Make a Gravsearch query from a template.
    val gravsearchQuery: String = org.knora.webapi.messages.twirl.queries.gravsearch.txt
      .getResourceWithSpecifiedProperties(resourceIri, propertyIrisForGravsearch)
      .toString()

    val request = Post(
      baseApiUrl + "/v2/searchextended",
      HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
    ) ~> addCredentials(BasicHttpCredentials(userEmail, password))

    val response: HttpResponse = singleAwaitingRequest(request)
    assert(response.status == StatusCodes.OK)
    responseToJsonLDDocument(response)
  }

  private def getValuesFromResource(resource: JsonLDDocument, propertyIriInResult: SmartIri): JsonLDArray =
    resource.body.getRequiredArray(propertyIriInResult.toString).fold(e => throw BadRequestException(e), identity)

  private def getValueFromResource(
    resource: JsonLDDocument,
    propertyIriInResult: SmartIri,
    expectedValueIri: IRI,
  ): JsonLDObject = {
    val matchingValues: Seq[JsonLDObject] =
      getValuesFromResource(resource = resource, propertyIriInResult = propertyIriInResult).value.collect {
        case jsonLDObject: JsonLDObject
            if jsonLDObject.requireStringWithValidation(JsonLDKeywords.ID, validationFun) == expectedValueIri =>
          jsonLDObject
      }

    assert(matchingValues.size == 1)
    matchingValues.head
  }

  private def getValue(
    resourceIri: IRI,
    propertyIriForGravsearch: SmartIri,
    propertyIriInResult: SmartIri,
    expectedValueIri: IRI,
    userEmail: String,
  ): JsonLDObject = {
    val resource: JsonLDDocument = getResourceWithValues(
      resourceIri = resourceIri,
      propertyIrisForGravsearch = Seq(propertyIriForGravsearch),
      userEmail = userEmail,
    )

    getValueFromResource(
      resource = resource,
      propertyIriInResult = propertyIriInResult,
      expectedValueIri = expectedValueIri,
    )
  }

  "The values v2 endpoint" should {
    "update a still image file value using a mock Sipi" in {
      val resourceIri: IRI = aThingPictureIri
      val internalFilename = "De6XyNL4H71-D9QxghOuOPJ.jp2"
      val jsonLDEntity =
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:ThingPicture",
           |  "knora-api:hasStillImageFileValue" : {
           |    "@id" : "http://rdfh.ch/0001/a-thing-picture/values/goZ7JFRNSeqF-dNxsqAS7Q",
           |    "@type" : "knora-api:StillImageFileValue",
           |    "knora-api:fileValueHasFilename" : "$internalFilename"
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      val request =
        Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity))
          ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))

      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK)
      val responseJsonDoc = responseToJsonLDDocument(response)
      val valueIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      stillImageFileValueIri.set(valueIri)
      val valueType: SmartIri =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
      valueType should ===(OntConsts.StillImageFileValue.toSmartIri)

      val savedValue: JsonLDObject = getValue(
        resourceIri = resourceIri,
        propertyIriForGravsearch = OntConsts.HasStillImageFileValue.toSmartIri,
        propertyIriInResult = OntConsts.HasStillImageFileValue.toSmartIri,
        expectedValueIri = stillImageFileValueIri.get,
        userEmail = anythingUserEmail,
      )

      savedValue.getRequiredString(OntConsts.FileValueHasFilename).toOption should contain(internalFilename)
      savedValue.getRequiredInt(OntConsts.StillImageFileValueHasDimX).toOption should contain(72)
      savedValue.getRequiredInt(OntConsts.StillImageFileValueHasDimY).toOption should contain(72)
    }
  }
}
