/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.ontology

import dsp.schema.domain.Cardinality
import dsp.schema.domain.Cardinality._
import org.knora.webapi.CoreSpec
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.responders.v2.ontology.Cache._

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * This spec is used to test [[org.knora.webapi.responders.v2.ontology.OntologyHelpers]].
 */
class OntologyHelpersSpec extends CoreSpec {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  private implicit val timeout                          = 10.seconds
  private val freeTestOntology = RdfDataObject(
    path = "test_data/ontologies/freetest-onto.ttl",
    name = "http://www.knora.org/ontology/0001/freetest"
  )
  override lazy val rdfDataObjects: List[RdfDataObject] = List(freeTestOntology)

  val freetestOntologyIri: SmartIri =
    "http://0.0.0.0:3333/ontology/0001/freetest/v2".toSmartIri.toOntologySchema(InternalSchema)

  "The ontology helper" should {

    "determine the least strict cardinality allowed for a (inherited) property from a list of classes" in {

      val cacheDataFuture: Future[OntologyCacheData] = Cache.getCacheData
      val cacheData: OntologyCacheData               = Await.result(cacheDataFuture, 2.seconds)

      // define the classes
      val mayHaveMany  = freetestOntologyIri.makeEntityIri("PubMayHaveMany").toOntologySchema(InternalSchema)  // 0-n
      val mustHaveSome = freetestOntologyIri.makeEntityIri("PubMustHaveSome").toOntologySchema(InternalSchema) // 1-n
      val mayHaveOne   = freetestOntologyIri.makeEntityIri("PubMayHaveOne").toOntologySchema(InternalSchema)   // 0-1
      val mustHaveOne  = freetestOntologyIri.makeEntityIri("PubMustHaveOne").toOntologySchema(InternalSchema)  // 1

      val hasPublicationDateProperty = freetestOntologyIri.makeEntityIri("hasPublicationDate")

      // define all test cases and the expected results (see comments)
      val testCases: List[Set[SmartIri]] = List(
        Set(mayHaveMany),                                       // given: 0-n               => expected result: 0-n
        Set(mustHaveSome),                                      // given: 1-n               => expected result: 1-n
        Set(mayHaveOne),                                        // given: 0-1               => expected result: 0-1
        Set(mustHaveOne),                                       // given: 1                 => expected result: 1
        Set(mayHaveMany, mustHaveSome),                         // given: 0-n, 1-n          => expected result: 1-n
        Set(mayHaveMany, mayHaveOne),                           // given: 0-n, 0-1          => expected result: 0-1
        Set(mayHaveMany, mustHaveOne),                          // given: 0-n, 1            => expected result: 1
        Set(mustHaveSome, mayHaveOne),                          // given: 1-n, 0-1          => expected result: 1
        Set(mustHaveSome, mustHaveOne),                         // given: 1-n, 1            => expected result: 1
        Set(mayHaveOne, mustHaveOne),                           // given: 0-1, 1            => expected result: 1
        Set(mayHaveMany, mustHaveSome, mayHaveOne),             // given: 0-n, 1-n, 0-1     => expected result: 1
        Set(mayHaveMany, mustHaveSome, mustHaveOne),            // given: 0-n, 1-n, 1       => expected result: 1
        Set(mayHaveMany, mayHaveOne, mustHaveOne),              // given: 0-n, 0-1, 1       => expected result: 1
        Set(mustHaveSome, mayHaveOne, mustHaveOne),             // given: 1-n, 0-1, 1       => expected result: 1
        Set(mayHaveMany, mustHaveSome, mayHaveOne, mustHaveOne) // given: 0-n, 1-n, 0-1, 1  => expected result: 1
      )

      // Expected results: 0-n, 1-n, 0-1, 1, 1-n, 0-1, 1, 1, 1, 1, 1, 1, 1, 1, 1
      val expectedResults: List[Option[Cardinality]] = List(
        Some(MayHaveMany),
        Some(MustHaveSome),
        Some(MayHaveOne),
        Some(MustHaveOne),
        Some(MustHaveSome),
        Some(MayHaveOne),
        Some(MustHaveOne),
        Some(MustHaveOne),
        Some(MustHaveOne),
        Some(MustHaveOne),
        Some(MustHaveOne),
        Some(MustHaveOne),
        Some(MustHaveOne),
        Some(MustHaveOne),
        Some(MustHaveOne)
      )

      // call method to all test cases
      val strictestCardinalitiesForAllCases: List[Map[SmartIri, KnoraCardinalityInfo]] = testCases.map(testCase =>
        OntologyHelpers.getStrictestCardinalitiesFromClasses(
          testCase,
          cacheData
        )
      )

      val results: List[Option[Cardinality]] =
        strictestCardinalitiesForAllCases.map((strictestCards: Map[SmartIri, KnoraCardinalityInfo]) =>
          strictestCards.get(hasPublicationDateProperty) match {
            case None       => None
            case Some(card) => Some(card.cardinality)
          }
        )

      assert(results == expectedResults)
    }
  }
}
