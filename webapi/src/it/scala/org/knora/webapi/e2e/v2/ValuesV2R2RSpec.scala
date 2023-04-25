/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.RouteTestTimeout
import scala.concurrent.ExecutionContextExecutor
import dsp.errors.AssertionException

import org.knora.webapi._
import org.knora.webapi.e2e.ClientTestDataCollector
import org.knora.webapi.e2e.TestDataFileContent
import org.knora.webapi.e2e.TestDataFilePath
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.messages.util.search.SparqlQueryConstants
import org.knora.webapi.routing.v2.SearchRouteV2
import org.knora.webapi.routing.v2.ValuesRouteV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.MutableTestIri

/**
 * Tests creating a still image file value using a mock Sipi.
 */
class ValuesV2R2RSpec extends R2RSpec {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private val valuesPath = ValuesRouteV2().makeRoute
  private val searchPath = SearchRouteV2(routeData.appConfig.v2.fulltextSearch.searchValueMinLength).makeRoute

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(
    appConfig.defaultTimeoutAsDuration
  )

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  /* we need to run our app with the mocked sipi implementation */
  override type Environment = core.LayersTest.DefaultTestEnvironmentWithoutSipi
  override lazy val effectLayers = core.LayersTest.integrationTestsWithFusekiTestcontainers(Some(system))

  private val aThingPictureIri = "http://rdfh.ch/0001/a-thing-picture"

  private val anythingUserEmail = SharedTestDataADM.anythingUser1.email
  private val password          = SharedTestDataADM.testPass

  private val stillImageFileValueIri = new MutableTestIri

  // Directory path for generated client test data
  private val clientTestDataPath: Seq[String] = Seq("v2", "values")

  // Collects client test data
  private val clientTestDataCollector = new ClientTestDataCollector(appConfig)

  override lazy val rdfDataObjects = List(
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  private def getResourceWithValues(
    resourceIri: IRI,
    propertyIrisForGravsearch: Seq[SmartIri],
    userEmail: String
  ): JsonLDDocument = {
    // Make a Gravsearch query from a template.
    val gravsearchQuery: String = org.knora.webapi.messages.twirl.queries.gravsearch.txt
      .getResourceWithSpecifiedProperties(
        resourceIri = resourceIri,
        propertyIris = propertyIrisForGravsearch
      )
      .toString()

    // Run the query.

    Post(
      "/v2/searchextended",
      HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)
    ) ~> addCredentials(BasicHttpCredentials(userEmail, password)) ~> searchPath ~> check {
      assert(status == StatusCodes.OK, response.toString)
      responseToJsonLDDocument(response)
    }
  }

  private def getValuesFromResource(resource: JsonLDDocument, propertyIriInResult: SmartIri): JsonLDArray =
    resource.body.requireArray(propertyIriInResult.toString)

  private def getValueFromResource(
    resource: JsonLDDocument,
    propertyIriInResult: SmartIri,
    expectedValueIri: IRI
  ): JsonLDObject = {
    val resourceIri: IRI =
      resource.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
    val propertyValues: JsonLDArray =
      getValuesFromResource(resource = resource, propertyIriInResult = propertyIriInResult)

    val matchingValues: Seq[JsonLDObject] = propertyValues.value.collect {
      case jsonLDObject: JsonLDObject
          if jsonLDObject.requireStringWithValidation(
            JsonLDKeywords.ID,
            stringFormatter.validateAndEscapeIri
          ) == expectedValueIri =>
        jsonLDObject
    }

    if (matchingValues.isEmpty) {
      throw AssertionException(
        s"Property <$propertyIriInResult> of resource <$resourceIri> does not have value <$expectedValueIri>"
      )
    }

    if (matchingValues.size > 1) {
      throw AssertionException(
        s"Property <$propertyIriInResult> of resource <$resourceIri> has more than one value with the IRI <$expectedValueIri>"
      )
    }

    matchingValues.head
  }

  private def getValue(
    resourceIri: IRI,
    propertyIriForGravsearch: SmartIri,
    propertyIriInResult: SmartIri,
    expectedValueIri: IRI,
    userEmail: String
  ): JsonLDObject = {
    val resource: JsonLDDocument = getResourceWithValues(
      resourceIri = resourceIri,
      propertyIrisForGravsearch = Seq(propertyIriForGravsearch),
      userEmail = userEmail
    )

    getValueFromResource(
      resource = resource,
      propertyIriInResult = propertyIriInResult,
      expectedValueIri = expectedValueIri
    )
  }

  "The values v2 endpoint" should {
    "update a still image file value using a mock Sipi" in {
      val resourceIri: IRI = aThingPictureIri
      val internalFilename = "IQUO3t1AABm-FSLC0vNvVpr.jp2"

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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-still-image-file-value-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      Put("/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)
      ) ~> valuesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)
        val valueIri: IRI =
          responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
        stillImageFileValueIri.set(valueIri)
        val valueType: SmartIri =
          responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
        valueType should ===(OntologyConstants.KnoraApiV2Complex.StillImageFileValue.toSmartIri)

        val savedValue: JsonLDObject = getValue(
          resourceIri = resourceIri,
          propertyIriForGravsearch = OntologyConstants.KnoraApiV2Complex.HasStillImageFileValue.toSmartIri,
          propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasStillImageFileValue.toSmartIri,
          expectedValueIri = stillImageFileValueIri.get,
          userEmail = anythingUserEmail
        )

        savedValue.requireString(OntologyConstants.KnoraApiV2Complex.FileValueHasFilename) should ===(internalFilename)
        savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasDimX) should ===(512)
        savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasDimY) should ===(256)
      }
    }
  }
}
