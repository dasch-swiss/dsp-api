/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain

import org.apache.jena.query.Dataset
import zio.Ref
import zio.ZLayer
import zio.test.ZIOSpecDefault
import zio.test._
import zio.Random

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.responders.ActorDepsTest
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.ontology.domain.model.Cardinality.allCardinalities
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.InternalIri.Property.KnoraBase
import org.knora.webapi.store.triplestore.TestDatasetBuilder._
import org.knora.webapi.store.triplestore.api.TriplestoreServiceFake

object CardinalityServiceLiveSpec extends ZIOSpecDefault {

  // test data
  private val ontologyAnything = InternalIri("http://www.knora.org/ontology/0001/anything#")
  private val ontologyBooks    = InternalIri("http://www.knora.org/ontology/0001/books#")

  private val classThing    = InternalIri(s"${ontologyAnything.value}Thing")
  private val classBook     = InternalIri(s"${ontologyBooks.value}Book")
  private val classTextBook = InternalIri(s"${ontologyBooks.value}Textbook")

  val cardinalitiesGen: Gen[Any, Cardinality] = Gen.fromZIO(
    for {
      i <- Random.nextIntBetween(0, allCardinalities.length)
    } yield allCardinalities(i)
  )

  private object IsPropertyUsedInResources {
    val testData: String =
      s"""
         |@prefix rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
         |@prefix rdfs:        <http://www.w3.org/2000/01/rdf-schema#> .
         |
         |<http://aThing>
         |  a <${classThing.value}> ;
         |  <${KnoraBase.isDeleted.value}> false .
         |
         |<http://aBook> a <${classBook.value}> .
         |
         |<${classTextBook.value}> rdfs:subClassOf <${classBook.value}> .
         |
         |<http://aTextBook>
         |  a <${classTextBook.value}> ;
         |  <${KnoraBase.isEditable.value}> true .
         |""".stripMargin
  }

  private val commonLayers = ZLayer.makeSome[Ref[Dataset], CardinalityService](
    CardinalityService.layer,
    StringFormatter.test,
    ActorDepsTest.stub,
    TriplestoreServiceFake.layer
  )

  override def spec: Spec[Any, Throwable] =
    suite("CardinalityServiceLive")(
      suite("isPropertyUsedInResources should")(
        test("given a property is in use by the class => return true") {
          val classIri    = classThing
          val propertyIri = KnoraBase.isDeleted
          for {
            result <- CardinalityService.isPropertyUsedInResources(classIri, propertyIri)
          } yield assertTrue(result)
        },
        test("given a property is not used by the class but in a different class => return false") {
          val classIri    = classBook
          val propertyIri = KnoraBase.isDeleted
          for {
            result <- CardinalityService.isPropertyUsedInResources(classIri, propertyIri)
          } yield assertTrue(!result)
        },
        test("given a property is never used => return false") {
          val classIri    = classThing
          val propertyIri = KnoraBase.isMainResource
          for {
            result <- CardinalityService.isPropertyUsedInResources(classIri, propertyIri)
          } yield assertTrue(!result)
        },
        test("given a property is in use in a subclass => return true") {
          val classIri    = classTextBook
          val propertyIri = KnoraBase.isEditable
          for {
            result <- CardinalityService.isPropertyUsedInResources(classIri, propertyIri)
          } yield assertTrue(result)
        }
      ).provide(commonLayers, datasetLayerFromTurtle(IsPropertyUsedInResources.testData)),
      suite("canWidenCardinality")(
        test("incomplete") {
          check(cardinalitiesGen) { c =>
            val classIri: InternalIri    = InternalIri("http://aResource/WithUnboundedCardinalityValue")
            val propertyIri: InternalIri = InternalIri("http://example.com/ontology#hasMayHaveSomeValue")
            for {
              result <- CardinalityService.canWidenCardinality(classIri, propertyIri, c)
            } yield assertTrue(!result)
          }
        }
      ).provide(commonLayers, datasetLayerFromTurtle(CanWidenCardinality.testData))
    )
  object CanWidenCardinality {
    val testData: String =
      s"""
         |@prefix owl:         <http://www.w3.org/2002/07/owl#> .
         |@prefix rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
         |@prefix rdfs:        <http://www.w3.org/2000/01/rdf-schema#> .
         |@prefix xsd:         <http://www.w3.org/2001/XMLSchema#> .
         |
         |<http://aResource/WithUnboundedCardinalityValue>
         |    rdf:type        owl:Class ;
         |    rdfs:subClassOf <${InternalIri.Class.KnoraBase.Resource.value}> ;
         |    rdfs:subClassOf [ rdf:type            owl:Restriction ;
         |                      owl:onProperty      <http://example/ontology#hasMayHaveSomeValue> ;
         |                      owl:minCardinality  "0"^^xsd:nonNegativeInteger ] .
         |
         | <http://aSubResource/WithUnboundedCardinalityValue>
         |    rdf:type        owl:Class ;
         |    rdfs:subClassOf <http://aResource/WithUnboundedCardinalityValue> ;
         |    rdfs:subClassOf [ rdf:type            owl:Restriction ;
         |                      owl:onProperty      <http://example/ontology#hasMayHaveSomeValue> ;
         |                      owl:minCardinality  "0"^^xsd:nonNegativeInteger ] .
         |
         |<http://aSubSubResource/WithUnboundedCardinalityValue>
         |    rdf:type        owl:Class ;
         |    rdfs:subClassOf <http://aSubResource/WithUnboundedCardinalityValue> ;
         |    rdfs:subClassOf [ rdf:type            owl:Restriction ;
         |                      owl:onProperty      <http://example/ontology#hasMayHaveSomeValue> ;
         |                      owl:minCardinality  "0"^^xsd:nonNegativeInteger ] .
         |""".stripMargin
  }
}
