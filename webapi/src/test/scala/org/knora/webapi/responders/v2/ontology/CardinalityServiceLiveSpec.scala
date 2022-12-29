/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.ontology
import org.apache.jena.query.Dataset
import zio.test.ZIOSpecDefault
import zio.test._
import zio.Ref
import zio.ZLayer

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.responders.ActorDepsTest
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreServiceFake
import org.knora.webapi.store.triplestore.TestDatasetBuilder._

object CardinalityServiceLiveSpec extends ZIOSpecDefault {

  // test data
  private val testDataForIsPropertyUsedInResources =
    """
      |@prefix anything:    <http://www.knora.org/ontology/0001/anything#> .
      |@prefix books:       <http://www.knora.org/ontology/0001/books#> .
      |@prefix knora-base:  <http://www.knora.org/ontology/knora-base#> .
      |@prefix rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
      |@prefix rdfs:        <http://www.w3.org/2000/01/rdf-schema#>
      |
      |<http://rdfh.ch/0001/aThing>
      |  a anything:Thing ;
      |  knora-base:isDeleted false .
      |
      |<http://rdfh.ch/0001/aBook>
      |  a books:Book .
      |
      |<http://www.knora.org/ontology/0001/books#Textbook> 
      |  rdfs:subClassOf books:Book .
      |
      |<http://rdfh.ch/0001/anotherBook>
      |  a <http://www.knora.org/ontology/0001/books#Textbook> ;
      |  knora-base:isEditable true .
      |
      |""".stripMargin

  private val classThing    = InternalIri("http://www.knora.org/ontology/0001/anything#Thing")
  private val classBook     = InternalIri("http://www.knora.org/ontology/0001/books#Book")
  private val classTextBook = InternalIri("http://www.knora.org/ontology/0001/books#Textbook")

  // layers
  private val commonLayers = ZLayer.makeSome[Ref[Dataset], CardinalityService](
    CardinalityService.layer,
    StringFormatter.test,
    ActorDepsTest.stub,
    TriplestoreServiceFake.layer
  )

  override def spec = suite("CardinalityServiceLive")(
    suite("isPropertyUsedInResources should")(
      test("given a property is in use by the class => return true") {
        val classIri    = classThing
        val propertyIri = InternalIri.Property.KnoraBase.isDeleted
        for {
          result <- CardinalityService.isPropertyUsedInResources(classIri, propertyIri)
        } yield assertTrue(result)
      },
      test("given a property is not used by the class but in a different class => return false") {
        val classIri    = classBook
        val propertyIri = InternalIri.Property.KnoraBase.isDeleted
        for {
          result <- CardinalityService.isPropertyUsedInResources(classIri, propertyIri)
        } yield assertTrue(!result)
      },
      test("given a property is never used => return false") {
        val classIri    = classThing
        val propertyIri = InternalIri.Property.KnoraBase.isMainResource
        for {
          result <- CardinalityService.isPropertyUsedInResources(classIri, propertyIri)
        } yield assertTrue(!result)
      },
      test("given a property is in use in a subclass => return true") {
        val classIri    = classTextBook
        val propertyIri = InternalIri.Property.KnoraBase.isEditable
        for {
          result <- CardinalityService.isPropertyUsedInResources(classIri, propertyIri)
        } yield assertTrue(result)
      }
    ).provide(commonLayers, datasetLayerFromTurtle(testDataForIsPropertyUsedInResources)),
    suite("canDeleteCardinalitiesFromClass")(test("incomplete") {
      for {
        result <- CardinalityService.canWidenCardinality()
      } yield assertTrue(result)
    }).provide(commonLayers, datasetLayerFromTurtle(""))
  )
}
