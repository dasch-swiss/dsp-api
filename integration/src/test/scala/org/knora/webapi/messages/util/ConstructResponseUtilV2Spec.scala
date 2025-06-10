/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import org.apache.pekko
import zio.FiberFailure
import zio.Unsafe
import zio.ZIO

import java.nio.file.Paths
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.*

import org.knora.webapi.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.responders.v2.ResourcesResponderV2SpecFullData
import org.knora.webapi.responders.v2.ResourcesResponseCheckerV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.FileUtil

import pekko.testkit.ImplicitSender

/**
 * Tests [[ConstructResponseUtilV2]].
 */
class ConstructResponseUtilV2Spec extends E2ESpec with ImplicitSender {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  private val incunabulaUser                            = SharedTestDataADM.incunabulaProjectAdminUser
  private val anythingAdminUser                         = SharedTestDataADM.anythingAdminUser
  private val anonymousUser                             = SharedTestDataADM.anonymousUser
  private val resourcesResponderV2SpecFullData          = new ResourcesResponderV2SpecFullData
  private val constructResponseUtilV2SpecFullData       = new ConstructResponseUtilV2SpecFullData

  "ConstructResponseUtilV2" should {

    "convert a resource Turtle response into a resource" in {
      val resourceIri: IRI = "http://rdfh.ch/0803/c5058f3a"
      val turtleStr: String =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/constructResponseUtilV2/Zeitglocklein.ttl"),
        )
      val resourceRequestResponse: SparqlExtendedConstructResponse =
        Unsafe.unsafe { implicit u =>
          runtime.unsafe
            .run(
              SparqlExtendedConstructResponse.make(turtleStr),
            )
            .getOrElse(c => throw FiberFailure(c))
        }

      val mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData =
        UnsafeZioRun.runOrThrow(
          ZIO
            .service[ConstructResponseUtilV2]
            .map(
              _.splitMainResourcesAndValueRdfData(
                constructQueryResults = resourceRequestResponse,
                requestingUser = incunabulaUser,
              ),
            ),
        )

      val apiResponseFuture: Future[ReadResourcesSequenceV2] = UnsafeZioRun.runToFuture(
        ZIO
          .serviceWithZIO[ConstructResponseUtilV2](
            _.createApiResponse(
              mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
              orderByResourceIri = Seq(resourceIri),
              pageSizeBeforeFiltering = 1,
              mappings = Map.empty,
              queryStandoff = false,
              versionDate = None,
              calculateMayHaveMoreResults = false,
              targetSchema = ApiV2Complex,
              requestingUser = incunabulaUser,
            ),
          ),
      )

      val resourceSequence: ReadResourcesSequenceV2 = Await.result(apiResponseFuture, 10.seconds)

      ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response(
        expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein,
        received = resourceSequence,
      )
    }

    "convert a resource Turtle response with hidden values into a resource with the anything admin user" in {
      val resourceIri: IRI = "http://rdfh.ch/0001/F8L7zPp7TI-4MGJQlCO4Zg"
      val turtleStr: String =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/constructResponseUtilV2/visibleThingWithHiddenIntValues.ttl"),
        )
      val resourceRequestResponse: SparqlExtendedConstructResponse =
        Unsafe.unsafe { implicit u =>
          runtime.unsafe
            .run(
              SparqlExtendedConstructResponse.make(turtleStr),
            )
            .getOrElse(c => throw FiberFailure(c))
        }

      val mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData =
        UnsafeZioRun.runOrThrow(
          ZIO
            .service[ConstructResponseUtilV2]
            .map(
              _.splitMainResourcesAndValueRdfData(
                constructQueryResults = resourceRequestResponse,
                requestingUser = anythingAdminUser,
              ),
            ),
        )

      val apiResponseFuture: Future[ReadResourcesSequenceV2] = UnsafeZioRun.runToFuture(
        ZIO
          .serviceWithZIO[ConstructResponseUtilV2](
            _.createApiResponse(
              mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
              orderByResourceIri = Seq(resourceIri),
              pageSizeBeforeFiltering = 1,
              mappings = Map.empty,
              queryStandoff = false,
              versionDate = None,
              calculateMayHaveMoreResults = false,
              targetSchema = ApiV2Complex,
              requestingUser = anythingAdminUser,
            ),
          ),
      )

      val resourceSequence: ReadResourcesSequenceV2 = Await.result(apiResponseFuture, 10.seconds)

      ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response(
        expected =
          constructResponseUtilV2SpecFullData.expectedReadResourceForAnythingVisibleThingWithHiddenIntValuesAnythingAdmin,
        received = resourceSequence,
      )
    }

    "convert a resource Turtle response with hidden values into a resource with the incunabula user" in {
      val resourceIri: IRI = "http://rdfh.ch/0001/F8L7zPp7TI-4MGJQlCO4Zg"
      val turtleStr: String =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/constructResponseUtilV2/visibleThingWithHiddenIntValues.ttl"),
        )
      val resourceRequestResponse: SparqlExtendedConstructResponse =
        Unsafe.unsafe { implicit u =>
          runtime.unsafe
            .run(
              SparqlExtendedConstructResponse.make(turtleStr),
            )
            .getOrElse(c => throw FiberFailure(c))
        }

      val mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData =
        UnsafeZioRun.runOrThrow(
          ZIO
            .service[ConstructResponseUtilV2]
            .map(
              _.splitMainResourcesAndValueRdfData(
                constructQueryResults = resourceRequestResponse,
                requestingUser = incunabulaUser,
              ),
            ),
        )

      val apiResponseFuture: Future[ReadResourcesSequenceV2] = UnsafeZioRun.runToFuture(
        ZIO
          .serviceWithZIO[ConstructResponseUtilV2](
            _.createApiResponse(
              mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
              orderByResourceIri = Seq(resourceIri),
              pageSizeBeforeFiltering = 1,
              mappings = Map.empty,
              queryStandoff = false,
              versionDate = None,
              calculateMayHaveMoreResults = false,
              targetSchema = ApiV2Complex,
              requestingUser = incunabulaUser,
            ),
          ),
      )

      val resourceSequence: ReadResourcesSequenceV2 = Await.result(apiResponseFuture, 10.seconds)

      ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response(
        expected =
          constructResponseUtilV2SpecFullData.expectedReadResourceForAnythingVisibleThingWithHiddenIntValuesIncunabulaUser,
        received = resourceSequence,
      )
    }

    "convert a resource Turtle response with a hidden thing into a resource with the anything admin user" in {
      val resourceIri: IRI = "http://rdfh.ch/0001/0JhgKcqoRIeRRG6ownArSw"
      val turtleStr: String =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/constructResponseUtilV2/thingWithOneHiddenThing.ttl"),
        )
      val resourceRequestResponse: SparqlExtendedConstructResponse =
        Unsafe.unsafe { implicit u =>
          runtime.unsafe
            .run(
              SparqlExtendedConstructResponse.make(turtleStr),
            )
            .getOrElse(c => throw FiberFailure(c))
        }

      val mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData =
        UnsafeZioRun.runOrThrow(
          ZIO
            .service[ConstructResponseUtilV2]
            .map(
              _.splitMainResourcesAndValueRdfData(
                constructQueryResults = resourceRequestResponse,
                requestingUser = anythingAdminUser,
              ),
            ),
        )

      val apiResponseFuture: Future[ReadResourcesSequenceV2] = UnsafeZioRun.runToFuture(
        ZIO
          .serviceWithZIO[ConstructResponseUtilV2](
            _.createApiResponse(
              mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
              orderByResourceIri = Seq(resourceIri),
              pageSizeBeforeFiltering = 1,
              mappings = Map.empty,
              queryStandoff = false,
              versionDate = None,
              calculateMayHaveMoreResults = false,
              targetSchema = ApiV2Complex,
              requestingUser = anythingAdminUser,
            ),
          ),
      )

      val resourceSequence: ReadResourcesSequenceV2 = Await.result(apiResponseFuture, 10.seconds)

      ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response(
        expected =
          constructResponseUtilV2SpecFullData.expectedReadResourceForAnythingThingWithOneHiddenThingAnythingAdmin,
        received = resourceSequence,
      )
    }

    "convert a resource Turtle response with a hidden thing into a resource with an unknown user" in {
      val resourceIri: IRI = "http://rdfh.ch/0001/0JhgKcqoRIeRRG6ownArSw"
      val turtleStr: String =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/constructResponseUtilV2/thingWithOneHiddenThing.ttl"),
        )
      val resourceRequestResponse: SparqlExtendedConstructResponse =
        Unsafe.unsafe { implicit u =>
          runtime.unsafe
            .run(
              SparqlExtendedConstructResponse.make(turtleStr),
            )
            .getOrElse(c => throw FiberFailure(c))
        }

      val mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData =
        UnsafeZioRun.runOrThrow(
          ZIO
            .service[ConstructResponseUtilV2]
            .map(
              _.splitMainResourcesAndValueRdfData(
                constructQueryResults = resourceRequestResponse,
                requestingUser = anonymousUser,
              ),
            ),
        )

      val apiResponseFuture: Future[ReadResourcesSequenceV2] = UnsafeZioRun.runToFuture(
        ZIO
          .serviceWithZIO[ConstructResponseUtilV2](
            _.createApiResponse(
              mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
              orderByResourceIri = Seq(resourceIri),
              pageSizeBeforeFiltering = 1,
              mappings = Map.empty,
              queryStandoff = false,
              versionDate = None,
              calculateMayHaveMoreResults = false,
              targetSchema = ApiV2Complex,
              requestingUser = anonymousUser,
            ),
          ),
      )

      val resourceSequence: ReadResourcesSequenceV2 = Await.result(apiResponseFuture, 10.seconds)

      ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response(
        expected =
          constructResponseUtilV2SpecFullData.expectedReadResourceForAnythingThingWithOneHiddenThingAnonymousUser,
        received = resourceSequence,
      )
    }

    "convert a resource Turtle response with standoff into a resource with anything admin user" in {
      val resourceIri: IRI = "http://rdfh.ch/0001/a-thing-with-text-values"
      val turtleStr: String =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/constructResponseUtilV2/thingWithStandoff.ttl"),
        )
      val resourceRequestResponse: SparqlExtendedConstructResponse =
        Unsafe.unsafe { implicit u =>
          runtime.unsafe
            .run(
              SparqlExtendedConstructResponse.make(turtleStr),
            )
            .getOrElse(c => throw FiberFailure(c))
        }

      val mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData =
        UnsafeZioRun.runOrThrow(
          ZIO
            .service[ConstructResponseUtilV2]
            .map(
              _.splitMainResourcesAndValueRdfData(
                constructQueryResults = resourceRequestResponse,
                requestingUser = anythingAdminUser,
              ),
            ),
        )

      val apiResponseFuture: Future[ReadResourcesSequenceV2] = UnsafeZioRun.runToFuture(
        ZIO
          .serviceWithZIO[ConstructResponseUtilV2](
            _.createApiResponse(
              mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
              orderByResourceIri = Seq(resourceIri),
              pageSizeBeforeFiltering = 1,
              mappings = Map.empty,
              queryStandoff = false,
              versionDate = None,
              calculateMayHaveMoreResults = false,
              targetSchema = ApiV2Complex,
              requestingUser = anythingAdminUser,
            ),
          ),
      )

      val resourceSequence: ReadResourcesSequenceV2 = Await.result(apiResponseFuture, 10.seconds)

      ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response(
        expected = constructResponseUtilV2SpecFullData.expectedReadResourceSequenceV2WithStandoffAnythingAdminUser,
        received = resourceSequence,
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

      val resourceIris: Seq[IRI] = Seq("http://rdfh.ch/0803/76570a749901", "http://rdfh.ch/0803/773f258402")
      val turtleStr: String =
        FileUtil.readTextFile(Paths.get("..", "test_data/generated_test_data/constructResponseUtilV2/mainQuery1.ttl"))
      val resourceRequestResponse: SparqlExtendedConstructResponse =
        Unsafe.unsafe { implicit u =>
          runtime.unsafe
            .run(
              SparqlExtendedConstructResponse.make(turtleStr),
            )
            .getOrElse(c => throw FiberFailure(c))
        }

      val mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData =
        UnsafeZioRun.runOrThrow(
          ZIO
            .service[ConstructResponseUtilV2]
            .map(
              _.splitMainResourcesAndValueRdfData(
                constructQueryResults = resourceRequestResponse,
                requestingUser = incunabulaUser,
              ),
            ),
        )

      val apiResponseFuture: Future[ReadResourcesSequenceV2] = UnsafeZioRun.runToFuture(
        ZIO
          .serviceWithZIO[ConstructResponseUtilV2](
            _.createApiResponse(
              mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
              orderByResourceIri = resourceIris,
              pageSizeBeforeFiltering = 1,
              mappings = Map.empty,
              queryStandoff = false,
              versionDate = None,
              calculateMayHaveMoreResults = false,
              targetSchema = ApiV2Complex,
              requestingUser = incunabulaUser,
            ),
          ),
      )

      val resourceSequence: ReadResourcesSequenceV2 = Await.result(apiResponseFuture, 10.seconds)

      ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response(
        expected = constructResponseUtilV2SpecFullData.expectedReadResourceSequenceV2ForMainQuery1,
        received = resourceSequence,
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

      val resourceIris: Seq[IRI] = Seq("http://rdfh.ch/0803/c5058f3a", "http://rdfh.ch/0803/ff17e5ef9601")
      val turtleStr: String =
        FileUtil.readTextFile(Paths.get("..", "test_data/generated_test_data/constructResponseUtilV2/mainQuery2.ttl"))
      val mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData =
        UnsafeZioRun.runOrThrow(
          SparqlExtendedConstructResponse.make(turtleStr).flatMap { resp =>
            ZIO.serviceWith[ConstructResponseUtilV2](_.splitMainResourcesAndValueRdfData(resp, incunabulaUser))
          },
        )

      val apiResponseFuture: Future[ReadResourcesSequenceV2] = UnsafeZioRun.runToFuture(
        ZIO
          .serviceWithZIO[ConstructResponseUtilV2](
            _.createApiResponse(
              mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
              orderByResourceIri = resourceIris,
              pageSizeBeforeFiltering = 1,
              mappings = Map.empty,
              queryStandoff = false,
              versionDate = None,
              calculateMayHaveMoreResults = false,
              targetSchema = ApiV2Complex,
              requestingUser = incunabulaUser,
            ),
          ),
      )

      val resourceSequence: ReadResourcesSequenceV2 = Await.result(apiResponseFuture, 10.seconds)

      ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response(
        expected = constructResponseUtilV2SpecFullData.expectedReadResourceSequenceV2ForMainQuery2,
        received = resourceSequence,
      )
    }
  }
}
