/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain

import org.apache.jena.query.Dataset
import zio.Random
import zio.Ref
import zio.ZLayer
import zio.test.ZIOSpecDefault
import zio.test._

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologyMetadataV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.responders.v2.ontology.Cache
import org.knora.webapi.responders.v2.ontology.Cache.OntologyCacheData
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.ontology.domain.model.Cardinality.AtLeastOne
import org.knora.webapi.slice.ontology.domain.model.Cardinality.ExactlyOne
import org.knora.webapi.slice.ontology.domain.model.Cardinality.Unbounded
import org.knora.webapi.slice.ontology.domain.model.Cardinality.ZeroOrOne
import org.knora.webapi.slice.ontology.domain.model.Cardinality.allCardinalities
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanSetCardinalityCheckResult.BaseClassCheckFailure
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanSetCardinalityCheckResult.CanSetCheckSuccess
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanSetCardinalityCheckResult.KnoraOntologyCheckFailure
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheFake
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resourceinfo.domain.IriTestConstants._
import org.knora.webapi.store.triplestore.TestDatasetBuilder._
import org.knora.webapi.store.triplestore.api.TriplestoreServiceFake

object CardinalityServiceLiveSpec extends ZIOSpecDefault {

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

  private object IsPropertyUsedInResourcesTestData {

    private val ontologyAnything = "http://www.knora.org/ontology/0001/anything"
    private val ontologyBooks    = "http://www.knora.org/ontology/0001/books"

    val classThing: InternalIri    = InternalIri(s"$ontologyAnything#Thing")
    val classBook: InternalIri     = InternalIri(s"$ontologyBooks#Book")
    val classTextBook: InternalIri = InternalIri(s"$ontologyBooks#Textbook")
    val turtle: String =
      s"""
         |@prefix rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
         |@prefix rdfs:        <http://www.w3.org/2000/01/rdf-schema#> .
         |
         |<http://aThing>
         |  a <${classThing.value}> ;
         |  <${KnoraBase.Property.isDeleted.value}> false .
         |
         |<http://aBook> a <${classBook.value}> .
         |
         |<${classTextBook.value}> rdfs:subClassOf <${classBook.value}> .
         |
         |<http://aTextBook>
         |  a <${classTextBook.value}> ;
         |  <${KnoraBase.Property.isEditable.value}> true .
         |""".stripMargin
  }

  private object CanWidenCardinalityTestData {
    object Gens {
      case class TestIris(
        ontologyIri: InternalIri,
        classIri: InternalIri,
        subClassIri: InternalIri,
        propertyIri: InternalIri
      )

      val knoraOntologiesGen: Gen[Any, TestIris] = Gen.fromZIO {
        val values = Array(
          (KnoraBase.Ontology, KnoraBase.Class.Resource, KnoraBase.Class.Annotation, KnoraBase.Property.isDeleted),
          (
            KnoraAdmin.Ontology,
            KnoraAdmin.Class.Permission,
            KnoraAdmin.Class.AdministrativePermission,
            KnoraAdmin.Property.belongsToProject
          )
        )
        Random
          .nextIntBounded(values.length)
          .map(values(_))
          .map(iris =>
            TestIris(ontologyIri = iris._1, classIri = iris._2, subClassIri = iris._3, propertyIri = iris._4)
          )
      }
    }

    private val sf: StringFormatter = { StringFormatter.initForTest(); StringFormatter.getGeneralInstance }

    case class DataCreated(subclass: InternalIri, property: InternalIri, data: OntologyCacheData)

    def makeOntologyTestData(cardinality: Cardinality): DataCreated = {
      val ontologyIri: InternalIri = InternalIri("http://www.knora.org/ontology/0001/anything")
      makeOntologyTestData(
        cardinality = cardinality,
        propertyIri = InternalIri(s"${ontologyIri.value}#aProperty"),
        subClassIri = InternalIri(s"${ontologyIri.value}#aSubClass"),
        classIri = InternalIri(s"${ontologyIri.value}#aClass"),
        ontologyIri = ontologyIri
      )
    }

    def makeOntologyTestData(
      cardinality: Cardinality,
      ontologyIri: InternalIri,
      classIri: InternalIri,
      subClassIri: InternalIri,
      propertyIri: InternalIri
    ): DataCreated = {
      val anOntologySmartIri: SmartIri = sf.toSmartIri(ontologyIri.value).toOntologySchema(InternalSchema)
      val aClassSmartIri: SmartIri     = sf.toSmartIri(classIri.value).toOntologySchema(InternalSchema)
      val aSubClassSmartIri: SmartIri  = sf.toSmartIri(subClassIri.value).toOntologySchema(InternalSchema)
      val aPropertySmartIri: SmartIri  = sf.toSmartIri(propertyIri.value).toOntologySchema(InternalSchema)

      val propertyCardinality = makePropertyCardinality(aPropertySmartIri, cardinality)
      val classInfo           = makeClassInfoContent(aClassSmartIri, directCardinalities = propertyCardinality)
      val subClassInfo = makeClassInfoContent(
        aSubClassSmartIri,
        superClassIris = List(aClassSmartIri),
        inheritedCardinalities = propertyCardinality
      )
      val ontologyData = makeOntologyData(anOntologySmartIri, classes = classInfo, subClassInfo)
      DataCreated(aSubClassSmartIri.toInternalIri, aPropertySmartIri.toInternalIri, ontologyData)
    }

    private def makePropertyCardinality(
      propertyIri: SmartIri,
      cardinality: Cardinality
    ): Map[SmartIri, KnoraCardinalityInfo] = Map(propertyIri -> KnoraCardinalityInfo(cardinality))

    private def makeClassInfoContent(
      classIri: SmartIri,
      superClassIris: List[SmartIri] = List.empty,
      directCardinalities: Map[SmartIri, KnoraCardinalityInfo] = Map.empty,
      inheritedCardinalities: Map[SmartIri, KnoraCardinalityInfo] = Map.empty
    ): (SmartIri, ReadClassInfoV2) =
      (
        classIri,
        ReadClassInfoV2(
          ClassInfoContentV2(
            classIri = classIri,
            ontologySchema = ApiV2Complex,
            directCardinalities = directCardinalities
          ),
          allBaseClasses = superClassIris,
          inheritedCardinalities = inheritedCardinalities
        )
      )

    private def makeOntologyData(
      ontologyIri: SmartIri,
      classes: (SmartIri, ReadClassInfoV2)*
    ): Cache.OntologyCacheData =
      OntologyCacheFake.emptyData.copy(ontologies =
        Map(ontologyIri -> ReadOntologyV2(OntologyMetadataV2(ontologyIri), classes = classes.toMap))
      )
  }

  private val commonLayers = ZLayer.makeSome[Ref[Dataset], CardinalityService with OntologyCacheFake](
    CardinalityService.layer,
    IriConverter.layer,
    OntologyCacheFake.emptyCache,
    OntologyRepoLive.layer,
    StringFormatter.test,
    TriplestoreServiceFake.layer
  )

  override def spec: Spec[Any, Throwable] =
    suite("CardinalityServiceLive")(
      suite("isPropertyUsedInResources should")(
        test("given a property is in use by the class => return true") {
          val classIri    = IsPropertyUsedInResourcesTestData.classThing
          val propertyIri = KnoraBase.Property.isDeleted
          for {
            result <- CardinalityService.isPropertyUsedInResources(classIri, propertyIri)
          } yield assertTrue(result)
        },
        test("given a property is not used by the class but in a different class => return false") {
          val classIri    = IsPropertyUsedInResourcesTestData.classBook
          val propertyIri = KnoraBase.Property.isDeleted
          for {
            result <- CardinalityService.isPropertyUsedInResources(classIri, propertyIri)
          } yield assertTrue(!result)
        },
        test("given a property is never used => return false") {
          val classIri    = IsPropertyUsedInResourcesTestData.classThing
          val propertyIri = KnoraBase.Property.isMainResource
          for {
            result <- CardinalityService.isPropertyUsedInResources(classIri, propertyIri)
          } yield assertTrue(!result)
        },
        test("given a property is in use in a subclass => return true") {
          val classIri    = IsPropertyUsedInResourcesTestData.classTextBook
          val propertyIri = KnoraBase.Property.isEditable
          for {
            result <- CardinalityService.isPropertyUsedInResources(classIri, propertyIri)
          } yield assertTrue(result)
        }
      ).provide(commonLayers, datasetLayerFromTurtle(IsPropertyUsedInResourcesTestData.turtle)),
      suite("canSetCardinality")(
        test("Any class/property of the Knora admin or base ontologies may never be changed") {
          check(CanWidenCardinalityTestData.Gens.knoraOntologiesGen) { iris =>
            check(cardinalitiesGen()) { cardinality =>
              val d = CanWidenCardinalityTestData.makeOntologyTestData(
                cardinality,
                iris.ontologyIri,
                iris.classIri,
                iris.subClassIri,
                iris.propertyIri
              )
              for {
                _      <- OntologyCacheFake.set(d.data)
                actual <- CardinalityService.canSetCardinality(iris.classIri, iris.propertyIri, cardinality)
              } yield assertTrue(actual == KnoraOntologyCheckFailure)
            }
          }
        },
        suite(s"Given 'ExactlyOne $ExactlyOne' Cardinality on super class property")(
          test(
            s"""
               |when checking new cardinalities 'AtLeastOne $AtLeastOne', 'Unbounded $Unbounded', 'ZeroOrOne $ZeroOrOne' 
               |then this is NOT possible""".stripMargin
          ) {
            check(cardinalitiesGen(AtLeastOne, Unbounded, ZeroOrOne)) { newCardinality =>
              val d = CanWidenCardinalityTestData.makeOntologyTestData(ExactlyOne)
              for {
                _      <- OntologyCacheFake.set(d.data)
                actual <- CardinalityService.canSetCardinality(d.subclass, d.property, newCardinality)
              } yield assertTrue(actual == BaseClassCheckFailure)
            }
          },
          test(
            s"""
               |when checking new cardinality 'ExactlyOne $ExactlyOne' 
               |then this is possible""".stripMargin
          ) {
            val d = CanWidenCardinalityTestData.makeOntologyTestData(ExactlyOne)
            for {
              _      <- OntologyCacheFake.set(d.data)
              actual <- CardinalityService.canSetCardinality(d.subclass, d.property, ExactlyOne)
            } yield assertTrue(actual == CanSetCheckSuccess)
          }
        ),
        suite(s"Given 'AtLeastOne $AtLeastOne' Cardinality on super class property")(
          test(
            s"""
               |when checking new cardinalities 'Unbounded $Unbounded', 'ZeroOrOne $ZeroOrOne' 
               |then this is NOT possible""".stripMargin
          ) {
            check(cardinalitiesGen(Unbounded, ZeroOrOne)) { newCardinality =>
              val d = CanWidenCardinalityTestData.makeOntologyTestData(AtLeastOne)
              for {
                _ <- OntologyCacheFake.set(d.data)
                actual <-
                  CardinalityService.canSetCardinality(d.subclass, d.property, newCardinality)
              } yield assertTrue(actual == BaseClassCheckFailure)
            }
          },
          test(
            s"""
               |when checking new cardinalities 'AtLeastOne $AtLeastOne', 'ExactlyOne $ExactlyOne'
               |then this is possible""".stripMargin
          ) {
            check(cardinalitiesGen(AtLeastOne, ExactlyOne)) { newCardinality =>
              val d = CanWidenCardinalityTestData.makeOntologyTestData(AtLeastOne)
              for {
                _      <- OntologyCacheFake.set(d.data)
                actual <- CardinalityService.canSetCardinality(d.subclass, d.property, newCardinality)
              } yield assertTrue(actual == CanSetCheckSuccess)
            }
          }
        ),
        suite(s"Given 'ZeroOrOne $ZeroOrOne' Cardinality on super class property")(
          test(
            s"""
               |when checking new cardinalities 'AtLeastOne $AtLeastOne', 'Unbounded $Unbounded' 
               |then this is NOT possible""".stripMargin
          ) {
            check(cardinalitiesGen(AtLeastOne, Unbounded)) { newCardinality =>
              val d = CanWidenCardinalityTestData.makeOntologyTestData(ZeroOrOne)
              for {
                _      <- OntologyCacheFake.set(d.data)
                actual <- CardinalityService.canSetCardinality(d.subclass, d.property, newCardinality)
              } yield assertTrue(actual == BaseClassCheckFailure)
            }
          },
          test(
            s"""
               |when checking new cardinalities 'ExactlyOne $ExactlyOne', 'ZeroOrOne $ZeroOrOne'
               |then this is possible""".stripMargin
          ) {
            check(cardinalitiesGen(ExactlyOne, ZeroOrOne)) { newCardinality =>
              val d = CanWidenCardinalityTestData.makeOntologyTestData(ZeroOrOne)
              for {
                _      <- OntologyCacheFake.set(d.data)
                actual <- CardinalityService.canSetCardinality(d.subclass, d.property, newCardinality)
              } yield assertTrue(actual == CanSetCheckSuccess)
            }
          }
        ),
        test(
          s"""
             |Given 'Unbounded $Unbounded' Cardinality on super class property'
             |when checking all cardinalities
             |then this is always possible""".stripMargin
        ) {
          check(cardinalitiesGen()) { newCardinality =>
            val d = CanWidenCardinalityTestData.makeOntologyTestData(Unbounded)
            for {
              _      <- OntologyCacheFake.set(d.data)
              actual <- CardinalityService.canSetCardinality(d.subclass, d.property, newCardinality)
            } yield assertTrue(actual == CanSetCheckSuccess)
          }
        }
      ).provide(commonLayers, emptyDataSet)
    )

}
