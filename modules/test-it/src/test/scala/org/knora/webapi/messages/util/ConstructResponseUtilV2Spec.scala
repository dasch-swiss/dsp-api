/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import zio.*
import zio.test.*

import java.nio.file.Paths

import org.knora.webapi.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse
import org.knora.webapi.responders.v2.ResourcesResponderV2SpecFullData
import org.knora.webapi.responders.v2.ResourcesResponseCheckerV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.util.FileUtil

object ConstructResponseUtilV2Spec extends E2EZSpec {
  private implicit val sf: StringFormatter = StringFormatter.getGeneralInstance

  private val constructResponseUtilV2SpecFullData = new ConstructResponseUtilV2SpecFullData

  private val constructResponseUtilV2    = ZIO.serviceWith[ConstructResponseUtilV2]
  private val constructResponseUtilV2ZIO = ZIO.serviceWithZIO[ConstructResponseUtilV2]

  override val e2eSpec = suite("ConstructResponseUtilV2")(
    test("convert a resource Turtle response into a resource") {
      val resourceIri: IRI = "http://rdfh.ch/0803/c5058f3a"
      val turtleStr: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/constructResponseUtilV2/Zeitglocklein.ttl"),
        )
      for {
        resourceRequestResponse <- SparqlExtendedConstructResponse.make(turtleStr)
        mainResourcesAndValueRdfData <-
          constructResponseUtilV2(
            _.splitMainResourcesAndValueRdfData(resourceRequestResponse, incunabulaProjectAdminUser),
          )
        resourceSequence <- constructResponseUtilV2ZIO(
                              _.createApiResponse(
                                mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
                                orderByResourceIri = Seq(resourceIri),
                                pageSizeBeforeFiltering = 1,
                                mappings = Map.empty,
                                queryStandoff = false,
                                versionDate = None,
                                calculateMayHaveMoreResults = false,
                                targetSchema = ApiV2Complex,
                                requestingUser = incunabulaProjectAdminUser,
                              ),
                            )

        check <- ResourcesResponseCheckerV2.compareReadResourcesSequenceV2ResponseZIO(
                   ResourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein,
                   resourceSequence,
                 )
      } yield check
    },
    test("convert a resource Turtle response with hidden values into a resource with the anything admin user") {
      val resourceIri: IRI = "http://rdfh.ch/0001/F8L7zPp7TI-4MGJQlCO4Zg"
      val turtleStr: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/constructResponseUtilV2/visibleThingWithHiddenIntValues.ttl"),
        )
      for {
        resourceRequestResponse <- SparqlExtendedConstructResponse.make(turtleStr)
        mainResourcesAndValueRdfData <-
          constructResponseUtilV2(_.splitMainResourcesAndValueRdfData(resourceRequestResponse, anythingAdminUser))
        resourceSequence <- constructResponseUtilV2ZIO(
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
                            )
        check <-
          ResourcesResponseCheckerV2.compareReadResourcesSequenceV2ResponseZIO(
            constructResponseUtilV2SpecFullData.expectedReadResourceForAnythingVisibleThingWithHiddenIntValuesAnythingAdmin,
            resourceSequence,
          )
      } yield check
    },
    test("convert a resource Turtle response with hidden values into a resource with the incunabula user") {
      val resourceIri: IRI = "http://rdfh.ch/0001/F8L7zPp7TI-4MGJQlCO4Zg"
      val turtleStr: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/constructResponseUtilV2/visibleThingWithHiddenIntValues.ttl"),
        )
      for {
        resourceRequestResponse <- SparqlExtendedConstructResponse.make(turtleStr)
        mainResourcesAndValueRdfData <-
          constructResponseUtilV2(
            _.splitMainResourcesAndValueRdfData(resourceRequestResponse, incunabulaProjectAdminUser),
          )
        resourceSequence <- constructResponseUtilV2ZIO(
                              _.createApiResponse(
                                mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
                                orderByResourceIri = Seq(resourceIri),
                                pageSizeBeforeFiltering = 1,
                                mappings = Map.empty,
                                queryStandoff = false,
                                versionDate = None,
                                calculateMayHaveMoreResults = false,
                                targetSchema = ApiV2Complex,
                                requestingUser = incunabulaProjectAdminUser,
                              ),
                            )
        check <-
          ResourcesResponseCheckerV2.compareReadResourcesSequenceV2ResponseZIO(
            constructResponseUtilV2SpecFullData.expectedReadResourceForAnythingVisibleThingWithHiddenIntValuesIncunabulaUser,
            resourceSequence,
          )
      } yield check
    },
    test("convert a resource Turtle response with a hidden thing into a resource with the anything admin user") {
      val resourceIri: IRI = "http://rdfh.ch/0001/0JhgKcqoRIeRRG6ownArSw"
      val turtleStr: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/constructResponseUtilV2/thingWithOneHiddenThing.ttl"),
        )
      for {
        resourceRequestResponse <- SparqlExtendedConstructResponse.make(turtleStr)
        mainResourcesAndValueRdfData <-
          constructResponseUtilV2(_.splitMainResourcesAndValueRdfData(resourceRequestResponse, anythingAdminUser))
        resourceSequence <- constructResponseUtilV2ZIO(
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
                            )
        check <-
          ResourcesResponseCheckerV2.compareReadResourcesSequenceV2ResponseZIO(
            constructResponseUtilV2SpecFullData.expectedReadResourceForAnythingThingWithOneHiddenThingAnythingAdmin,
            resourceSequence,
          )
      } yield check
    },
    test("convert a resource Turtle response with a hidden thing into a resource with an unknown user") {
      val resourceIri: IRI = "http://rdfh.ch/0001/0JhgKcqoRIeRRG6ownArSw"
      val turtleStr: String = FileUtil.readTextFile(
        Paths.get("test_data/generated_test_data/constructResponseUtilV2/thingWithOneHiddenThing.ttl"),
      )
      for {
        resourceRequestResponse <- SparqlExtendedConstructResponse.make(turtleStr)
        mainResourcesAndValueRdfData <-
          constructResponseUtilV2(_.splitMainResourcesAndValueRdfData(resourceRequestResponse, anonymousUser))
        resourceSequence <- constructResponseUtilV2ZIO(
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
                            )
        check <-
          ResourcesResponseCheckerV2.compareReadResourcesSequenceV2ResponseZIO(
            constructResponseUtilV2SpecFullData.expectedReadResourceForAnythingThingWithOneHiddenThingAnonymousUser,
            resourceSequence,
          )
      } yield check
    },
    test("convert a resource Turtle response with standoff into a resource with anything admin user") {
      val resourceIri: IRI = "http://rdfh.ch/0001/a-thing-with-text-values"
      val turtleStr: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/constructResponseUtilV2/thingWithStandoff.ttl"),
        )
      for {
        resourceRequestResponse <- SparqlExtendedConstructResponse.make(turtleStr)
        mainResourcesAndValueRdfData <-
          constructResponseUtilV2(_.splitMainResourcesAndValueRdfData(resourceRequestResponse, anythingAdminUser))
        resourceSequence <- constructResponseUtilV2ZIO(
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
                            )
        check <- ResourcesResponseCheckerV2.compareReadResourcesSequenceV2ResponseZIO(
                   constructResponseUtilV2SpecFullData.expectedReadResourceSequenceV2WithStandoffAnythingAdminUser,
                   resourceSequence,
                 )
      } yield check
    },
    test("convert a Gravsearch Turtle response into a resource sequence") {

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
        FileUtil.readTextFile(Paths.get("test_data/generated_test_data/constructResponseUtilV2/mainQuery1.ttl"))
      for {
        resourceRequestResponse <- SparqlExtendedConstructResponse.make(turtleStr)
        mainResourcesAndValueRdfData <-
          constructResponseUtilV2(
            _.splitMainResourcesAndValueRdfData(resourceRequestResponse, incunabulaProjectAdminUser),
          )
        resourceSequence <- constructResponseUtilV2ZIO(
                              _.createApiResponse(
                                mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
                                orderByResourceIri = resourceIris,
                                pageSizeBeforeFiltering = 1,
                                mappings = Map.empty,
                                queryStandoff = false,
                                versionDate = None,
                                calculateMayHaveMoreResults = false,
                                targetSchema = ApiV2Complex,
                                requestingUser = incunabulaProjectAdminUser,
                              ),
                            )
        check <- ResourcesResponseCheckerV2.compareReadResourcesSequenceV2ResponseZIO(
                   constructResponseUtilV2SpecFullData.expectedReadResourceSequenceV2ForMainQuery1,
                   resourceSequence,
                 )
      } yield check
    },
    test("convert a Gravsearch Turtle response with virtual incoming links into a resource sequence") {

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
        FileUtil.readTextFile(Paths.get("test_data/generated_test_data/constructResponseUtilV2/mainQuery2.ttl"))
      for {
        mainResourcesAndValueRdfData <-
          SparqlExtendedConstructResponse.make(turtleStr).flatMap { r =>
            constructResponseUtilV2(_.splitMainResourcesAndValueRdfData(r, incunabulaProjectAdminUser))
          }
        resourceSequence <- constructResponseUtilV2ZIO(
                              _.createApiResponse(
                                mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
                                orderByResourceIri = resourceIris,
                                pageSizeBeforeFiltering = 1,
                                mappings = Map.empty,
                                queryStandoff = false,
                                versionDate = None,
                                calculateMayHaveMoreResults = false,
                                targetSchema = ApiV2Complex,
                                requestingUser = incunabulaProjectAdminUser,
                              ),
                            )
        check <- ResourcesResponseCheckerV2.compareReadResourcesSequenceV2ResponseZIO(
                   constructResponseUtilV2SpecFullData.expectedReadResourceSequenceV2ForMainQuery2,
                   resourceSequence,
                 )
      } yield check
    },
  )
}
