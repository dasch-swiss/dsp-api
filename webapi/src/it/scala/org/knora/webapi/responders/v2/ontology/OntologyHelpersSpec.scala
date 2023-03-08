/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.ontology

import zio.ZIO

import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.responders.v2.ontology.Cache._
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.ontology.domain.model.Cardinality._
import org.knora.webapi.CoreSpec
import org.knora.webapi.InternalSchema
import org.knora.webapi.routing.UnsafeZioRun

/**
 * This spec is used to test [[org.knora.webapi.responders.v2.ontology.OntologyHelpers]].
 */
class OntologyHelpersSpec extends CoreSpec {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/ontologies/freetest-onto.ttl",
      name = "http://www.knora.org/ontology/0001/freetest"
    ),
    RdfDataObject(
      path = "test_data/ontologies/anything-onto.ttl",
      name = "http://www.knora.org/ontology/0001/anything"
    )
  )

  val freetestOntologyIri: SmartIri =
    "http://0.0.0.0:3333/ontology/0001/freetest/v2".toSmartIri.toOntologySchema(InternalSchema)

  "The ontology helper" should {

    "determine the least strict cardinality allowed for a (inherited) property from a list of classes" in {

      val cacheData: OntologyCacheData = UnsafeZioRun.runOrThrow(ZIO.serviceWithZIO[Cache](_.getCacheData))

      // define the classes
      val unbounded  = freetestOntologyIri.makeEntityIri("PubMayHaveMany").toOntologySchema(InternalSchema)  // 0-n
      val atLeastOne = freetestOntologyIri.makeEntityIri("PubMustHaveSome").toOntologySchema(InternalSchema) // 1-n
      val zeroOrOne  = freetestOntologyIri.makeEntityIri("PubMayHaveOne").toOntologySchema(InternalSchema)   // 0-1
      val exactlyOne = freetestOntologyIri.makeEntityIri("PubMustHaveOne").toOntologySchema(InternalSchema)  // 1

      val hasPublicationDateProperty = freetestOntologyIri.makeEntityIri("hasPublicationDate")

      // define all test cases and the expected results
      val testCases: List[(Set[SmartIri], Option[Cardinality])] = List(
        (Set(unbounded), Some(Unbounded)),
        (Set(atLeastOne), Some(AtLeastOne)),
        (Set(zeroOrOne), Some(ZeroOrOne)),
        (Set(exactlyOne), Some(ExactlyOne)),
        (Set(unbounded, atLeastOne), Some(AtLeastOne)),
        (Set(unbounded, zeroOrOne), Some(ZeroOrOne)),
        (Set(unbounded, exactlyOne), Some(ExactlyOne)),
        (Set(atLeastOne, zeroOrOne), Some(ExactlyOne)),
        (Set(atLeastOne, exactlyOne), Some(ExactlyOne)),
        (Set(zeroOrOne, exactlyOne), Some(ExactlyOne)),
        (Set(unbounded, atLeastOne, zeroOrOne), Some(ExactlyOne)),
        (Set(unbounded, atLeastOne, exactlyOne), Some(ExactlyOne)),
        (Set(unbounded, zeroOrOne, exactlyOne), Some(ExactlyOne)),
        (Set(atLeastOne, zeroOrOne, exactlyOne), Some(ExactlyOne)),
        (Set(unbounded, atLeastOne, zeroOrOne, exactlyOne), Some(ExactlyOne))
      )

      def getStrictest(classes: Set[SmartIri]): Option[Cardinality] =
        OntologyHelpers
          .getStrictestCardinalitiesFromClasses(classes, cacheData)
          .get(hasPublicationDateProperty)
          .map(card => card.cardinality)

      testCases.map { case (testCase: Set[SmartIri], expectedResult: Option[Cardinality]) =>
        assert(getStrictest(testCase) == expectedResult)
      }
    }
  }
}
