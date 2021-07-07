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

package org.knora.webapi.util

import java.nio.file.Paths

import akka.testkit.ImplicitSender
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.rdf.RdfFeatureFactory
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.responders.v2.{ResourcesResponderV2SpecFullData, ResourcesResponseCheckerV2}
import org.knora.webapi.sharedtestdata.SharedTestDataADM

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Tests [[ConstructResponseUtilV2]].
  */
class ConstructResponseUtilV2Spec extends CoreSpec() with ImplicitSender {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  private implicit val timeout: Timeout = 10.seconds
  private val incunabulaUser = SharedTestDataADM.incunabulaProjectAdminUser
  private val anythingAdminUser = SharedTestDataADM.anythingAdminUser
  private val anonymousUser = SharedTestDataADM.anonymousUser
  private val resourcesResponderV2SpecFullData = new ResourcesResponderV2SpecFullData
  private val constructResponseUtilV2SpecFullData = new ConstructResponseUtilV2SpecFullData
  private val rdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(defaultFeatureFactoryConfig)

  "ConstructResponseUtilV2" should {

    "convert a resource Turtle response into a resource" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw"
      val turtleStr: String = FileUtil.readTextFile(Paths.get("test_data/constructResponseUtilV2/Zeitglocklein.ttl"))
      val resourceRequestResponse: SparqlExtendedConstructResponse =
        SparqlExtendedConstructResponse.parseTurtleResponse(turtleStr, rdfFormatUtil, log).get
      val mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData =
        ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(
          constructQueryResults = resourceRequestResponse,
          requestingUser = incunabulaUser
        )

      val apiResponseFuture: Future[ReadResourcesSequenceV2] = ConstructResponseUtilV2.createApiResponse(
        mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
        orderByResourceIri = Seq(resourceIri),
        pageSizeBeforeFiltering = 1,
        mappings = Map.empty,
        queryStandoff = false,
        versionDate = None,
        calculateMayHaveMoreResults = false,
        responderManager = responderManager,
        targetSchema = ApiV2Complex,
        settings = settings,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser
      )

      val resourceSequence: ReadResourcesSequenceV2 = Await.result(apiResponseFuture, 10.seconds)

      ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response(
        expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein,
        received = resourceSequence
      )
    }

    "convert a resource Turtle response with hidden values into a resource with the anything admin user" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/F8L7zPp7TI-4MGJQlCO4Zg"
      val turtleStr: String =
        FileUtil.readTextFile(Paths.get("test_data/constructResponseUtilV2/visibleThingWithHiddenIntValues.ttl"))
      val resourceRequestResponse: SparqlExtendedConstructResponse =
        SparqlExtendedConstructResponse.parseTurtleResponse(turtleStr, rdfFormatUtil, log).get
      val mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData =
        ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(
          constructQueryResults = resourceRequestResponse,
          requestingUser = anythingAdminUser
        )

      val apiResponseFuture: Future[ReadResourcesSequenceV2] = ConstructResponseUtilV2.createApiResponse(
        mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
        orderByResourceIri = Seq(resourceIri),
        pageSizeBeforeFiltering = 1,
        mappings = Map.empty,
        queryStandoff = false,
        versionDate = None,
        calculateMayHaveMoreResults = false,
        responderManager = responderManager,
        targetSchema = ApiV2Complex,
        settings = settings,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingAdminUser
      )

      val resourceSequence: ReadResourcesSequenceV2 = Await.result(apiResponseFuture, 10.seconds)

      ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response(
        expected =
          constructResponseUtilV2SpecFullData.expectedReadResourceForAnythingVisibleThingWithHiddenIntValuesAnythingAdmin,
        received = resourceSequence
      )
    }

    "convert a resource Turtle response with hidden values into a resource with the incunabula user" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/F8L7zPp7TI-4MGJQlCO4Zg"
      val turtleStr: String =
        FileUtil.readTextFile(Paths.get("test_data/constructResponseUtilV2/visibleThingWithHiddenIntValues.ttl"))
      val resourceRequestResponse: SparqlExtendedConstructResponse =
        SparqlExtendedConstructResponse.parseTurtleResponse(turtleStr, rdfFormatUtil, log).get
      val mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData =
        ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(
          constructQueryResults = resourceRequestResponse,
          requestingUser = incunabulaUser
        )

      val apiResponseFuture: Future[ReadResourcesSequenceV2] = ConstructResponseUtilV2.createApiResponse(
        mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
        orderByResourceIri = Seq(resourceIri),
        pageSizeBeforeFiltering = 1,
        mappings = Map.empty,
        queryStandoff = false,
        versionDate = None,
        calculateMayHaveMoreResults = false,
        responderManager = responderManager,
        targetSchema = ApiV2Complex,
        settings = settings,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser
      )

      val resourceSequence: ReadResourcesSequenceV2 = Await.result(apiResponseFuture, 10.seconds)

      ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response(
        expected =
          constructResponseUtilV2SpecFullData.expectedReadResourceForAnythingVisibleThingWithHiddenIntValuesIncunabulaUser,
        received = resourceSequence
      )
    }

    "convert a resource Turtle response with a hidden thing into a resource with the anything admin user" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/0JhgKcqoRIeRRG6ownArSw"
      val turtleStr: String =
        FileUtil.readTextFile(Paths.get("test_data/constructResponseUtilV2/thingWithOneHiddenThing.ttl"))
      val resourceRequestResponse: SparqlExtendedConstructResponse =
        SparqlExtendedConstructResponse.parseTurtleResponse(turtleStr, rdfFormatUtil, log).get
      val mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData =
        ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(
          constructQueryResults = resourceRequestResponse,
          requestingUser = anythingAdminUser
        )

      val apiResponseFuture: Future[ReadResourcesSequenceV2] = ConstructResponseUtilV2.createApiResponse(
        mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
        orderByResourceIri = Seq(resourceIri),
        pageSizeBeforeFiltering = 1,
        mappings = Map.empty,
        queryStandoff = false,
        versionDate = None,
        calculateMayHaveMoreResults = false,
        responderManager = responderManager,
        targetSchema = ApiV2Complex,
        settings = settings,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingAdminUser
      )

      val resourceSequence: ReadResourcesSequenceV2 = Await.result(apiResponseFuture, 10.seconds)

      ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response(
        expected =
          constructResponseUtilV2SpecFullData.expectedReadResourceForAnythingThingWithOneHiddenThingAnythingAdmin,
        received = resourceSequence
      )
    }

    "convert a resource Turtle response with a hidden thing into a resource with an unknown user" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/0JhgKcqoRIeRRG6ownArSw"
      val turtleStr: String =
        FileUtil.readTextFile(Paths.get("test_data/constructResponseUtilV2/thingWithOneHiddenThing.ttl"))
      val resourceRequestResponse: SparqlExtendedConstructResponse =
        SparqlExtendedConstructResponse.parseTurtleResponse(turtleStr, rdfFormatUtil, log).get
      val mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData =
        ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(
          constructQueryResults = resourceRequestResponse,
          requestingUser = anonymousUser
        )

      val apiResponseFuture: Future[ReadResourcesSequenceV2] = ConstructResponseUtilV2.createApiResponse(
        mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
        orderByResourceIri = Seq(resourceIri),
        pageSizeBeforeFiltering = 1,
        mappings = Map.empty,
        queryStandoff = false,
        versionDate = None,
        calculateMayHaveMoreResults = false,
        responderManager = responderManager,
        targetSchema = ApiV2Complex,
        settings = settings,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anonymousUser
      )

      val resourceSequence: ReadResourcesSequenceV2 = Await.result(apiResponseFuture, 10.seconds)

      ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response(
        expected =
          constructResponseUtilV2SpecFullData.expectedReadResourceForAnythingThingWithOneHiddenThingAnonymousUser,
        received = resourceSequence
      )
    }

    "convert a resource Turtle response with standoff into a resource with anything admin user" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/jT0UHG9_wtaX23VoYydmGg"
      val turtleStr: String =
        FileUtil.readTextFile(Paths.get("test_data/constructResponseUtilV2/thingWithStandoff.ttl"))
      val resourceRequestResponse: SparqlExtendedConstructResponse =
        SparqlExtendedConstructResponse.parseTurtleResponse(turtleStr, rdfFormatUtil, log).get
      val mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData =
        ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(
          constructQueryResults = resourceRequestResponse,
          requestingUser = anythingAdminUser
        )

      val apiResponseFuture: Future[ReadResourcesSequenceV2] = ConstructResponseUtilV2.createApiResponse(
        mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
        orderByResourceIri = Seq(resourceIri),
        pageSizeBeforeFiltering = 1,
        mappings = Map.empty,
        queryStandoff = false,
        versionDate = None,
        calculateMayHaveMoreResults = false,
        responderManager = responderManager,
        targetSchema = ApiV2Complex,
        settings = settings,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingAdminUser
      )

      val resourceSequence: ReadResourcesSequenceV2 = Await.result(apiResponseFuture, 10.seconds)

      ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response(
        expected = constructResponseUtilV2SpecFullData.expectedReadResourceSequenceV2WithStandoffAnythingAdminUser,
        received = resourceSequence
      )
    }

    "convert a Gravsearch Turtle response into a resource sequence" in {

      /*

            PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
            PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>

            CONSTRUCT {
                ?page knora-api:isMainResource true .

                ?page knora-api:isPartOf ?book .

                ?page incunabula:seqnum ?seqnum .

                ?book incunabula:title ?title .
            } WHERE {

                ?page a incunabula:page .

                ?page knora-api:isPartOf ?book .

                ?page incunabula:seqnum ?seqnum .

                FILTER(?seqnum = 10)

                ?book incunabula:title ?title .

                FILTER(?title = 'Zeitglöcklein des Lebens und Leidens Christi')

            }

       */

      val resourceIris: Seq[IRI] =
        Seq("http://rdfh.ch/resources/I5qjztyxx63BGvfAjr5ZKQ", "http://rdfh.ch/resources/aW-UJ8Hd_gEXDoWD1Da5wQ")
      val turtleStr: String = FileUtil.readTextFile(Paths.get("test_data/constructResponseUtilV2/mainQuery1.ttl"))
      val resourceRequestResponse: SparqlExtendedConstructResponse =
        SparqlExtendedConstructResponse.parseTurtleResponse(turtleStr, rdfFormatUtil, log).get
      val mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData =
        ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(
          constructQueryResults = resourceRequestResponse,
          requestingUser = incunabulaUser
        )

      val apiResponseFuture: Future[ReadResourcesSequenceV2] = ConstructResponseUtilV2.createApiResponse(
        mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
        orderByResourceIri = resourceIris,
        pageSizeBeforeFiltering = 1,
        mappings = Map.empty,
        queryStandoff = false,
        versionDate = None,
        calculateMayHaveMoreResults = false,
        responderManager = responderManager,
        targetSchema = ApiV2Complex,
        settings = settings,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser
      )

      val resourceSequence: ReadResourcesSequenceV2 = Await.result(apiResponseFuture, 10.seconds)

      ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response(
        expected = constructResponseUtilV2SpecFullData.expectedReadResourceSequenceV2ForMainQuery1,
        received = resourceSequence
      )
    }

    "convert a Gravsearch Turtle response with virtual incoming links into a resource sequence" in {

      // the same query as above, but with a different main resource.
      /*

            PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
            PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>

            CONSTRUCT {
                ?book knora-api:isMainResource true .

                ?page knora-api:isPartOf ?book .

                ?page incunabula:seqnum ?seqnum .

                ?book incunabula:title ?title .
            } WHERE {

                ?page a incunabula:page .

                ?page knora-api:isPartOf ?book .

                ?page incunabula:seqnum ?seqnum .

                FILTER(?seqnum = 10)

                ?book incunabula:title ?title .

                FILTER(?title = 'Zeitglöcklein des Lebens und Leidens Christi')

            }

       */

      val resourceIris: Seq[IRI] =
        Seq("http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw", "http://rdfh.ch/resources/i4egXDOr2dZR3JRcdlapSQ")
      val turtleStr: String = FileUtil.readTextFile(Paths.get("test_data/constructResponseUtilV2/mainQuery2.ttl"))
      val resourceRequestResponse: SparqlExtendedConstructResponse =
        SparqlExtendedConstructResponse.parseTurtleResponse(turtleStr, rdfFormatUtil, log).get
      val mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData =
        ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(
          constructQueryResults = resourceRequestResponse,
          requestingUser = incunabulaUser
        )

      val apiResponseFuture: Future[ReadResourcesSequenceV2] = ConstructResponseUtilV2.createApiResponse(
        mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
        orderByResourceIri = resourceIris,
        pageSizeBeforeFiltering = 1,
        mappings = Map.empty,
        queryStandoff = false,
        versionDate = None,
        calculateMayHaveMoreResults = false,
        responderManager = responderManager,
        targetSchema = ApiV2Complex,
        settings = settings,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser
      )

      val resourceSequence: ReadResourcesSequenceV2 = Await.result(apiResponseFuture, 10.seconds)

      ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response(
        expected = constructResponseUtilV2SpecFullData.expectedReadResourceSequenceV2ForMainQuery2,
        received = resourceSequence
      )
    }
  }
}
