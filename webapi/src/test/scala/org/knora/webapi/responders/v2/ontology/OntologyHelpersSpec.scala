/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.ontology

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

import dsp.schema.domain.Cardinality
import dsp.schema.domain.Cardinality._
import org.knora.webapi.CoreSpec
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.responders.v2.ontology.Cache._

/**
 * This spec is used to test [[org.knora.webapi.responders.v2.ontology.OntologyHelpers]].
 */
class OntologyHelpersSpec extends CoreSpec {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  private implicit val timeout                          = 10.seconds

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

      val cacheDataFuture: Future[OntologyCacheData] = Cache.getCacheData
      val cacheData: OntologyCacheData               = Await.result(cacheDataFuture, 2.seconds)

      // define the classes
      val mayHaveMany  = freetestOntologyIri.makeEntityIri("PubMayHaveMany").toOntologySchema(InternalSchema)  // 0-n
      val mustHaveSome = freetestOntologyIri.makeEntityIri("PubMustHaveSome").toOntologySchema(InternalSchema) // 1-n
      val mayHaveOne   = freetestOntologyIri.makeEntityIri("PubMayHaveOne").toOntologySchema(InternalSchema)   // 0-1
      val mustHaveOne  = freetestOntologyIri.makeEntityIri("PubMustHaveOne").toOntologySchema(InternalSchema)  // 1

      val hasPublicationDateProperty = freetestOntologyIri.makeEntityIri("hasPublicationDate")

      // define all test cases and the expected results
      val testCases: List[(Set[SmartIri], Option[Cardinality])] = List(
        (Set(mayHaveMany), Some(MayHaveMany)),
        (Set(mustHaveSome), Some(MustHaveSome)),
        (Set(mayHaveOne), Some(MayHaveOne)),
        (Set(mustHaveOne), Some(MustHaveOne)),
        (Set(mayHaveMany, mustHaveSome), Some(MustHaveSome)),
        (Set(mayHaveMany, mayHaveOne), Some(MayHaveOne)),
        (Set(mayHaveMany, mustHaveOne), Some(MustHaveOne)),
        (Set(mustHaveSome, mayHaveOne), Some(MustHaveOne)),
        (Set(mustHaveSome, mustHaveOne), Some(MustHaveOne)),
        (Set(mayHaveOne, mustHaveOne), Some(MustHaveOne)),
        (Set(mayHaveMany, mustHaveSome, mayHaveOne), Some(MustHaveOne)),
        (Set(mayHaveMany, mustHaveSome, mustHaveOne), Some(MustHaveOne)),
        (Set(mayHaveMany, mayHaveOne, mustHaveOne), Some(MustHaveOne)),
        (Set(mustHaveSome, mayHaveOne, mustHaveOne), Some(MustHaveOne)),
        (Set(mayHaveMany, mustHaveSome, mayHaveOne, mustHaveOne), Some(MustHaveOne))
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
