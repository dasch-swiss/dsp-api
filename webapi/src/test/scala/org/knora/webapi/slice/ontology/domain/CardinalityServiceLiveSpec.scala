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
import org.knora.webapi.slice.ontology.domain.model.Cardinality.AtLeastOne
import org.knora.webapi.slice.ontology.domain.model.Cardinality.ExactlyOne
import org.knora.webapi.slice.ontology.domain.model.Cardinality.Unbounded
import org.knora.webapi.slice.ontology.domain.model.Cardinality.ZeroOrOne
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

  def cardinalitiesGen(cardinalities: Cardinality*): Gen[Any, Cardinality] = Gen.fromZIO {
    val candidates: Array[Cardinality] = if (cardinalities != Nil) { cardinalities.toArray }
    else { allCardinalities }
    val length = candidates.length
    if (length == 0) {
      throw new IllegalArgumentException()
    } else {
      Random.nextIntBetween(0, length).map(i => candidates(i))
    }
  }

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
        test("Unbounded Cardinality (MayHaveMany) can NOT be widened any further on super class") {
          check(cardinalitiesGen()) { c =>
            val propertyIri: InternalIri = CanWidenCardinality.mayHaveManyProperty
            for {
              result <- CardinalityService.canWidenCardinality(CanWidenCardinality.aClass, propertyIri, c)
            } yield assertTrue(!result)
          }
        },
        test("ZeroOrOne Cardinality (MayHaveOne) can be widened on a super class, by Unbounded") {
          val propertyIri: InternalIri = CanWidenCardinality.mayHaveOneProperty
          for {
            result <- CardinalityService.canWidenCardinality(CanWidenCardinality.aClass, propertyIri, Unbounded)
          } yield assertTrue(result)
        },
        test(
          "ZeroOrOne Cardinality (MayHaveOne) can NOT be widened on a super class, by AtLeastOne,  ExactlyOne, ZeroOrOne"
        ) {
          check(cardinalitiesGen(AtLeastOne, Unbounded, ZeroOrOne)) { c =>
            val propertyIri: InternalIri = CanWidenCardinality.mayHaveOneProperty
            for {
              result <- CardinalityService.canWidenCardinality(CanWidenCardinality.aClass, propertyIri, c)
            } yield assertTrue(!result)
          }
        },
        test(
          "ExactlyOne Cardinality (MustHaveOne) can be widened on a super class, by AtLeastOne, Unbounded, ZeroOrOne"
        ) {
          check(cardinalitiesGen(AtLeastOne, Unbounded, ZeroOrOne)) { c =>
            val propertyIri: InternalIri = CanWidenCardinality.mustHaveOneProperty
            for {
              result <- CardinalityService.canWidenCardinality(CanWidenCardinality.aClass, propertyIri, c)
            } yield assertTrue(result)
          }
        },
        test(
          "ExactlyOne Cardinality (MustHaveOne) can NOT be widened on a super class, by ExactlyOne"
        ) {
          val propertyIri: InternalIri = CanWidenCardinality.mustHaveOneProperty
          for {
            result <- CardinalityService.canWidenCardinality(CanWidenCardinality.aClass, propertyIri, ExactlyOne)
          } yield assertTrue(!result)
        },
        test("AtLeastOne Cardinality (MustHaveSome) can NOT be widened any further on super class") {
          check(cardinalitiesGen()) { c =>
            val propertyIri: InternalIri = CanWidenCardinality.mustHaveSomeProperty
            for {
              result <- CardinalityService.canWidenCardinality(CanWidenCardinality.aClass, propertyIri, c)
            } yield assertTrue(!result)
          }
        }
      ).provide(commonLayers, datasetLayerFromTurtle(CanWidenCardinality.testData))
    )

  object CanWidenCardinality {
    val aClass: InternalIri    = InternalIri("http://aClass")
    val aSubClass: InternalIri = InternalIri("http://aSubClass")

    val mayHaveManyProperty: InternalIri  = InternalIri("http://example/ontology#mayHaveManyValue")
    val mayHaveOneProperty: InternalIri   = InternalIri("http://example/ontology#mayHaveOneValue")
    val mustHaveOneProperty: InternalIri  = InternalIri("http://example/ontology#mustHaveOneValue")
    val mustHaveSomeProperty: InternalIri = InternalIri("http://example/ontology#mustHaveSomeValue")

    val testData: String =
      s"""
         |@prefix owl:         <http://www.w3.org/2002/07/owl#> .
         |@prefix rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
         |@prefix rdfs:        <http://www.w3.org/2000/01/rdf-schema#> .
         |@prefix xsd:         <http://www.w3.org/2001/XMLSchema#> .
         |
         |<http://aClass>
         |    rdf:type        owl:Class ;
         |    rdfs:subClassOf <${aClass.value}> ;
         |    rdfs:subClassOf [ rdf:type            owl:Restriction ;
         |                      owl:onProperty      <${mayHaveManyProperty.value}> ;
         |                      owl:minCardinality  "0"^^xsd:nonNegativeInteger ] ;
         |    rdfs:subClassOf [ rdf:type            owl:Restriction ;
         |                      owl:onProperty      <${mayHaveOneProperty.value}> ;
         |                      owl:maxCardinality  "1"^^xsd:nonNegativeInteger ] ;
         |    rdfs:subClassOf [ rdf:type            owl:Restriction ;
         |                      owl:onProperty      <${mustHaveOneProperty.value}> ;
         |                      owl:cardinality     "1"^^xsd:nonNegativeInteger ] ;
         |    rdfs:subClassOf [ rdf:type            owl:Restriction ;
         |                      owl:onProperty      <${mustHaveSomeProperty.value}> ;
         |                      owl:minCardinality  "1"^^xsd:nonNegativeInteger ] .
         |""".stripMargin
  }
}
