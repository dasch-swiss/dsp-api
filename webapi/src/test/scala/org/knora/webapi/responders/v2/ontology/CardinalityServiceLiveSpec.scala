/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.ontology
import zio.test.ZIOSpecDefault
import zio.test._

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.responders.ActorDepsTest
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreServiceFake
import org.knora.webapi.store.triplestore.TestDatasetBuilder._

object CardinalityServiceLiveSpec extends ZIOSpecDefault {

  // test data
  private val testDataSet =
    dataSetFromTurtle("""
                        |@prefix rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                        |@prefix anything:    <http://www.knora.org/ontology/0001/anything#> .
                        |@prefix knora-base:  <http://www.knora.org/ontology/knora-base#> .
                        |
                        |<http://rdfh.ch/0001/55UrkgTKR2SEQgnsLWI9mg>
                        |  a anything:Thing ;
                        |  knora-base:isDeleted         false .
                        |""".stripMargin)

  override def spec = suite("CardinalityServiceLive")(
    suite("isPropertyUsedInResources should")(
      test("detect that property is in use, when used in a resource\"") {
        val classIri    = InternalIri("http://www.knora.org/ontology/0001/anything#Thing")
        val propertyIri = InternalIri("http://www.knora.org/ontology/knora-base#isDeleted")
        for {
          result <- CardinalityService.isPropertyUsedInResources(classIri, propertyIri)
        } yield assertTrue(result)
      },
      test("detect that property is not in use, when used in a resource\"") {
        val classIri    = InternalIri("http://www.knora.org/ontology/0001/anything#Thing")
        val propertyIri = InternalIri("http://www.knora.org/ontology/knora-base#isMainResource")
        for {
          result <- CardinalityService.isPropertyUsedInResources(classIri, propertyIri)
        } yield assertTrue(!result)
      }
    )
  ).provide(
    CardinalityService.layer,
    StringFormatter.test,
    ActorDepsTest.stub,
    TriplestoreServiceFake.layer,
    asLayer(testDataSet)
  )
}
