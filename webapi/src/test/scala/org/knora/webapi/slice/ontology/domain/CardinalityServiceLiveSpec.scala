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
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologyMetadataV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.responders.ActorDepsTest
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.ontology.domain.model.Cardinality.allCardinalities
import org.knora.webapi.slice.ontology.domain.model.Cardinality.AtLeastOne
import org.knora.webapi.slice.ontology.domain.model.Cardinality.ExactlyOne
import org.knora.webapi.slice.ontology.domain.model.Cardinality.Unbounded
import org.knora.webapi.slice.ontology.domain.model.Cardinality.ZeroOrOne
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.CardinalityServiceLiveSpec.CanWidenCardinality.anOntologySmartIri
import org.knora.webapi.slice.ontology.repo.OntologyCacheFake
import org.knora.webapi.slice.ontology.repo.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.InternalIri.Property.KnoraBase
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.triplestore.TestDatasetBuilder._
import org.knora.webapi.store.triplestore.api.TriplestoreServiceFake
import org.knora.webapi.ApiV2Complex

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

  private val commonLayers = ZLayer.makeSome[Ref[Dataset], CardinalityService with OntologyCacheFake](
    ActorDepsTest.stub,
    CardinalityService.layer,
    IriConverter.layer,
    OntologyCacheFake.emptyCache,
    OntologyRepo.layer,
    StringFormatter.test,
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
            val ontologyData = OntologyCacheFake.empty.copy(
              ontologies = Map(
                anOntologySmartIri -> ReadOntologyV2(
                  OntologyMetadataV2(anOntologySmartIri),
                  classes = Map(
                    anOntologySmartIri -> ReadClassInfoV2(
                      ClassInfoContentV2(anOntologySmartIri, ontologySchema = ApiV2Complex),
                      List.empty
                    )
                  )
                )
              )
            )
            for {
              _      <- OntologyCacheFake.set(ontologyData)
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
      ).provide(commonLayers, emptyDataSet)
    )

  object CanWidenCardinality {
    implicit val sf: StringFormatter = { StringFormatter.initForTest(); StringFormatter.getGeneralInstance }

    val anOntologySmartIri: SmartIri = sf.toSmartIri("http://0.0.0.0:3333/ontology/0001/anything/v2")
    val anOntology: InternalIri      = anOntologySmartIri.toInternalIri

    val aClassSmartIri: SmartIri = anOntologySmartIri.makeEntityIri("aClass")
    val aClass: InternalIri      = aClassSmartIri.toInternalIri

    val aSubClassSmartIri: SmartIri = anOntologySmartIri.makeEntityIri("aSubClass")
    val aSubClass: InternalIri      = aSubClassSmartIri.toInternalIri

    val mayHaveManyProperty: InternalIri  = InternalIri(s"${anOntology.value}#mayHaveManyValue")
    val mayHaveOneProperty: InternalIri   = InternalIri(s"${anOntology.value}#mayHaveOneValue")
    val mustHaveOneProperty: InternalIri  = InternalIri(s"${anOntology.value}#mustHaveOneValue")
    val mustHaveSomeProperty: InternalIri = InternalIri(s"${anOntology.value}#mustHaveSomeValue")
  }
}
