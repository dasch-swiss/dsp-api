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
    dataSetFromTurtle(
      """
        |@prefix anything:    <http://www.knora.org/ontology/0001/anything#> .
        |@prefix books: <http://www.knora.org/ontology/0001/books#> .
        |@prefix knora-base:  <http://www.knora.org/ontology/knora-base#> .
        |@prefix rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
        |@prefix rdfs:        <http://www.w3.org/2000/01/rdf-schema#> .
        |@prefix xsd:         <http://www.w3.org/2001/XMLSchema#> 
        |
        |<http://rdfh.ch/0001/aThing>
        |  a anything:Thing ;
        |  knora-base:isDeleted false .
        |
        |<http://rdfh.ch/0001/aBook>
        |  a books:Book ;
        |  knora-base:creationDate "2016-03-02T15:05:10Z"^^xsd:dateTime .
        |
        |<http://www.knora.org/ontology/0001/books#Textbook> 
        |  rdfs:subClassOf books:Book .
        |
        |<http://rdfh.ch/0001/anotherBook>
        |  a <http://www.knora.org/ontology/0001/books#Textbook> ;
        |  knora-base:isEditable true .
        |
        |""".stripMargin
    )

  private val classThing    = InternalIri("http://www.knora.org/ontology/0001/anything#Thing")
  private val classBook     = InternalIri("http://www.knora.org/ontology/0001/books#Book")
  private val classTextBook = InternalIri("http://www.knora.org/ontology/0001/books#Textbook")

  override def spec = suite("CardinalityServiceLive")(
    suite("isPropertyUsedInResources should")(
      test("detect that property is in use, when used in a resource") {
        val classIri    = classThing
        val propertyIri = InternalIri.Property.KnoraBase.isDeleted
        for {
          result <- CardinalityService.isPropertyUsedInResources(classIri, propertyIri)
        } yield assertTrue(result)
      },
      test("detect that property is not in use, when used in a resource of a different class") {
        val classIri    = classBook
        val propertyIri = InternalIri.Property.KnoraBase.isDeleted
        for {
          result <- CardinalityService.isPropertyUsedInResources(classIri, propertyIri)
        } yield assertTrue(!result)
      },
      test("detect that property is not in use, when not used") {
        val classIri    = classThing
        val propertyIri = InternalIri.Property.KnoraBase.isMainResource
        for {
          result <- CardinalityService.isPropertyUsedInResources(classIri, propertyIri)
        } yield assertTrue(!result)
      },
      test("detect that property is in use in a subclass") {
        val classIri    = classTextBook
        val propertyIri = InternalIri.Property.KnoraBase.isEditable
        for {
          result <- CardinalityService.isPropertyUsedInResources(classIri, propertyIri)
        } yield assertTrue(result)
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
