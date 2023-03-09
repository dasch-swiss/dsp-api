/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain

import org.apache.jena.query.Dataset
import zio.Random
import zio.Ref
import zio.ZLayer
import zio.test.ZIOSpecDefault
import zio.test._

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.ontology.domain.model.Cardinality._
import org.knora.webapi.slice.ontology.domain.model.CardinalitySpec.Generator.cardinalitiesGen
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanSetCardinalityCheckResult._
import org.knora.webapi.slice.ontology.repo.model.OntologyCacheData
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheFake
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.ontology.repo.service.PredicateRepositoryLive
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resourceinfo.domain.IriTestConstants._
import org.knora.webapi.store.triplestore.TestDatasetBuilder._
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

object CardinalityServiceLiveSpec extends ZIOSpecDefault {

  private object CanSetCardinalityTestData {
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

    private val sf: StringFormatter = StringFormatter.getInitializedTestInstance

    val anythingOntologySmartIri: SmartIri = sf.toSmartIri("http://www.knora.org/ontology/0001/anything")
    val thingSmartIri: SmartIri            = anythingOntologySmartIri.makeEntityIri("Thing")
    val hasValueSmartIri: SmartIri         = anythingOntologySmartIri.makeEntityIri("hasValue")

    case class DataCreated(
      classIri: InternalIri,
      subclassIri: InternalIri,
      propertyIri: InternalIri,
      data: OntologyCacheData
    )

    def createOntologyWithSuperClassCardinality(cardinality: Cardinality): DataCreated = {
      val ontologySmartIri = anythingOntologySmartIri
      val classIri         = ontologySmartIri.makeEntityIri("aClass")
      val subClassIri      = ontologySmartIri.makeEntityIri("aSubClass")
      val propertyIri      = ontologySmartIri.makeEntityIri("aProperty")
      createOntologyWithSuperClassCardinality(cardinality, ontologySmartIri, classIri, subClassIri, propertyIri)
    }

    def createOntologyWithSuperClassCardinality(
      cardinality: Cardinality,
      ontologyIntIri: InternalIri,
      classIntIri: InternalIri,
      subClassIntIri: InternalIri,
      propertyIntIri: InternalIri
    ): DataCreated = {
      val ontologyIri = sf.toSmartIri(ontologyIntIri.value, requireInternal = true)
      val classIri    = sf.toSmartIri(classIntIri.value, requireInternal = true)
      val subClassIri = sf.toSmartIri(subClassIntIri.value, requireInternal = true)
      val propertyIri = sf.toSmartIri(propertyIntIri.value, requireInternal = true)
      createOntologyWithSuperClassCardinality(cardinality, ontologyIri, classIri, subClassIri, propertyIri)
    }

    private def createOntologyWithSuperClassCardinality(
      cardinality: Cardinality,
      ontologyIri: SmartIri,
      classIri: SmartIri,
      subClassIri: SmartIri,
      propertyIri: SmartIri
    ): DataCreated = {
      val cardinalities = OntologyCacheDataBuilder.cardinalitiesMap(propertyIri, cardinality)
      val data =
        OntologyCacheDataBuilder.builder
          .addOntology(
            ReadOntologyV2Builder
              .builder(ontologyIri)
              .addClassInfo(
                ReadClassInfoV2Builder
                  .builder(classIri)
                  .setDirectCardinalities(cardinalities)
              )
              .addClassInfo(
                ReadClassInfoV2Builder
                  .builder(subClassIri)
                  .setSuperClassIri(classIri)
                  .setInheritedCardinalities(cardinalities)
              )
          )
          .build

      DataCreated(classIri.toInternalIri, subClassIri.toInternalIri, propertyIri.toInternalIri, data)
    }

    def createOntologyWithSubClassCardinality(cardinality: Cardinality): DataCreated = {
      val ontologySmartIri = anythingOntologySmartIri
      val classIri         = ontologySmartIri.makeEntityIri("aClass")
      val subClassIri      = ontologySmartIri.makeEntityIri("aSubClass")
      val propertyIri      = ontologySmartIri.makeEntityIri("aProperty")
      createOntologyWithSubClassCardinality(cardinality, ontologySmartIri, classIri, subClassIri, propertyIri)
    }

    private def createOntologyWithSubClassCardinality(
      cardinality: Cardinality,
      ontologyIri: SmartIri,
      classIri: SmartIri,
      subClassIri: SmartIri,
      propertyIri: SmartIri
    ): DataCreated = {
      val cardinalities = OntologyCacheDataBuilder.cardinalitiesMap(propertyIri, cardinality)
      val data =
        OntologyCacheDataBuilder.builder
          .addOntology(
            ReadOntologyV2Builder
              .builder(ontologyIri)
              .addClassInfo(
                ReadClassInfoV2Builder
                  .builder(classIri)
              )
              .addClassInfo(
                ReadClassInfoV2Builder
                  .builder(subClassIri)
                  .addSuperClass(classIri)
                  .setDirectCardinalities(cardinalities)
              )
          )
          .build

      DataCreated(classIri.toInternalIri, subClassIri.toInternalIri, propertyIri.toInternalIri, data)
    }
  }

  private val commonLayers = ZLayer.makeSome[Ref[Dataset], CardinalityService with OntologyCacheFake](
    CardinalityService.layer,
    IriConverter.layer,
    OntologyCacheFake.emptyCache,
    OntologyRepoLive.layer,
    PredicateRepositoryLive.layer,
    StringFormatter.test,
    TriplestoreServiceInMemory.layer
  )

  override def spec: Spec[Any, Throwable] =
    suite("CardinalityServiceLive")(
      suite("canSetCardinality")(
        test("Any class/property of the Knora admin or base ontologies may never be changed") {
          check(CanSetCardinalityTestData.Gens.knoraOntologiesGen) { iris =>
            check(cardinalitiesGen()) { cardinality =>
              val d = CanSetCardinalityTestData.createOntologyWithSuperClassCardinality(
                cardinality,
                iris.ontologyIri,
                iris.classIri,
                iris.subClassIri,
                iris.propertyIri
              )
              for {
                _      <- OntologyCacheFake.set(d.data)
                actual <- CardinalityService.canSetCardinality(iris.classIri, iris.propertyIri, cardinality)
              } yield assertTrue(actual == Left(List(KnoraOntologyCheckFailure)))
            }
          }
        },
        test("Given no super-class or subclass exists then setting a cardinality is possible") {
          val classIri    = CanSetCardinalityTestData.thingSmartIri
          val propertyIri = CanSetCardinalityTestData.hasValueSmartIri
          val data = OntologyCacheDataBuilder.builder
            .addOntology(
              ReadOntologyV2Builder
                .builder(CanSetCardinalityTestData.anythingOntologySmartIri)
                .addClassInfo(
                  ReadClassInfoV2Builder
                    .builder(classIri)
                    .setDirectCardinalities(OntologyCacheDataBuilder.cardinalitiesMap(propertyIri, ExactlyOne))
                )
            )
            .build

          check(cardinalitiesGen()) { cardinality =>
            for {
              _ <- OntologyCacheFake.set(data)
              actual <-
                CardinalityService.canSetCardinality(classIri.toInternalIri, propertyIri.toInternalIri, cardinality)
            } yield assertTrue(actual.isRight)
          }
        },
        suite("Check super-class")(
          suite(s"Given 'ExactlyOne $ExactlyOne' Cardinality on super-class property")(
            test(
              s"""
                 |when checking new cardinalities 'AtLeastOne $AtLeastOne', 'Unbounded $Unbounded', 'ZeroOrOne $ZeroOrOne'
                 |then this is NOT possible""".stripMargin
            ) {
              check(cardinalitiesGen(AtLeastOne, Unbounded, ZeroOrOne)) { newCardinality =>
                val d = CanSetCardinalityTestData.createOntologyWithSuperClassCardinality(ExactlyOne)
                for {
                  _      <- OntologyCacheFake.set(d.data)
                  actual <- CardinalityService.canSetCardinality(d.subclassIri, d.propertyIri, newCardinality)
                } yield assertTrue(actual == Left(List(SuperClassCheckFailure(List(d.classIri)))))
              }
            },
            test(
              s"""
                 |when checking new cardinality 'ExactlyOne $ExactlyOne'
                 |then this is possible""".stripMargin
            ) {
              val d = CanSetCardinalityTestData.createOntologyWithSuperClassCardinality(ExactlyOne)
              for {
                _      <- OntologyCacheFake.set(d.data)
                actual <- CardinalityService.canSetCardinality(d.subclassIri, d.propertyIri, ExactlyOne)
              } yield assertTrue(actual.isRight)
            }
          ),
          suite(s"Given 'AtLeastOne $AtLeastOne' Cardinality on super-class property")(
            test(
              s"""
                 |when checking new cardinalities 'Unbounded $Unbounded', 'ZeroOrOne $ZeroOrOne'
                 |then this is NOT possible""".stripMargin
            ) {
              check(cardinalitiesGen(Unbounded, ZeroOrOne)) { newCardinality =>
                val d = CanSetCardinalityTestData.createOntologyWithSuperClassCardinality(AtLeastOne)
                for {
                  _ <- OntologyCacheFake.set(d.data)
                  actual <-
                    CardinalityService.canSetCardinality(d.subclassIri, d.propertyIri, newCardinality)
                } yield assertTrue(actual == Left(List(SuperClassCheckFailure(superClasses = List(d.classIri)))))
              }
            },
            test(
              s"""
                 |when checking new cardinalities 'AtLeastOne $AtLeastOne', 'ExactlyOne $ExactlyOne'
                 |then this is possible""".stripMargin
            ) {
              check(cardinalitiesGen(AtLeastOne, ExactlyOne)) { newCardinality =>
                val d = CanSetCardinalityTestData.createOntologyWithSuperClassCardinality(AtLeastOne)
                for {
                  _      <- OntologyCacheFake.set(d.data)
                  actual <- CardinalityService.canSetCardinality(d.subclassIri, d.propertyIri, newCardinality)
                } yield assertTrue(actual.isRight)
              }
            }
          ),
          suite(s"Given 'ZeroOrOne $ZeroOrOne' Cardinality on super-class property")(
            test(
              s"""
                 |when checking new cardinalities 'AtLeastOne $AtLeastOne', 'Unbounded $Unbounded'
                 |then this is NOT possible""".stripMargin
            ) {
              check(cardinalitiesGen(AtLeastOne, Unbounded)) { newCardinality =>
                val d = CanSetCardinalityTestData.createOntologyWithSuperClassCardinality(ZeroOrOne)
                for {
                  _      <- OntologyCacheFake.set(d.data)
                  actual <- CardinalityService.canSetCardinality(d.subclassIri, d.propertyIri, newCardinality)
                } yield assertTrue(actual == Left(List(SuperClassCheckFailure(List(d.classIri)))))
              }
            },
            test(
              s"""
                 |when checking new cardinalities 'ExactlyOne $ExactlyOne', 'ZeroOrOne $ZeroOrOne'
                 |then this is possible""".stripMargin
            ) {
              check(cardinalitiesGen(ExactlyOne, ZeroOrOne)) { newCardinality =>
                val d = CanSetCardinalityTestData.createOntologyWithSuperClassCardinality(ZeroOrOne)
                for {
                  _      <- OntologyCacheFake.set(d.data)
                  actual <- CardinalityService.canSetCardinality(d.subclassIri, d.propertyIri, newCardinality)
                } yield assertTrue(actual.isRight)
              }
            }
          ),
          test(
            s"""
               |Given 'Unbounded $Unbounded' Cardinality on super-class property'
               |when checking all cardinalities
               |then this is always possible""".stripMargin
          ) {
            check(cardinalitiesGen()) { newCardinality =>
              val d = CanSetCardinalityTestData.createOntologyWithSuperClassCardinality(Unbounded)
              for {
                _      <- OntologyCacheFake.set(d.data)
                actual <- CardinalityService.canSetCardinality(d.subclassIri, d.propertyIri, newCardinality)
              } yield assertTrue(actual.isRight)
            }
          }
        ),
        suite("Check subclass")(
          test(
            s"""
               |Given 'Unbounded $Unbounded' Cardinality on subclass property'
               |when checking $Unbounded cardinality
               |then this is possible""".stripMargin
          ) {
            val d = CanSetCardinalityTestData.createOntologyWithSubClassCardinality(Unbounded)
            for {
              _      <- OntologyCacheFake.set(d.data)
              actual <- CardinalityService.canSetCardinality(d.classIri, d.propertyIri, Unbounded)
            } yield assertTrue(actual.isRight)
          },
          test(
            s"""
               |Given 'Unbounded $Unbounded' Cardinality on subclass property'
               |when checking cardinalities $AtLeastOne, $ExactlyOne, $ZeroOrOne
               |then this is NOT possible""".stripMargin
          ) {
            val d = CanSetCardinalityTestData.createOntologyWithSubClassCardinality(Unbounded)
            check(cardinalitiesGen(AtLeastOne, ExactlyOne, ZeroOrOne)) { newCardinality =>
              for {
                _      <- OntologyCacheFake.set(d.data)
                actual <- CardinalityService.canSetCardinality(d.classIri, d.propertyIri, newCardinality)
              } yield assertTrue(actual == Left(List(SubclassCheckFailure(List(d.subclassIri)))))
            }
          },
          test(
            s"""
               |Given 'ExactlyOne $ExactlyOne' Cardinality on subclass property'
               |when checking cardinalities
               |then this is always possible""".stripMargin
          ) {
            val d = CanSetCardinalityTestData.createOntologyWithSubClassCardinality(ExactlyOne)
            check(cardinalitiesGen()) { newCardinality =>
              for {
                _      <- OntologyCacheFake.set(d.data)
                actual <- CardinalityService.canSetCardinality(d.classIri, d.propertyIri, newCardinality)
              } yield assertTrue(actual.isRight)
            }
          },
          test(
            s"""
               |Given 'AtLeastOne $AtLeastOne' Cardinality on subclass property'
               |when checking cardinalities $Unbounded, $AtLeastOne
               |then this is possible""".stripMargin
          ) {
            val d = CanSetCardinalityTestData.createOntologyWithSubClassCardinality(AtLeastOne)
            check(cardinalitiesGen(Unbounded, AtLeastOne)) { newCardinality =>
              for {
                _      <- OntologyCacheFake.set(d.data)
                actual <- CardinalityService.canSetCardinality(d.classIri, d.propertyIri, newCardinality)
              } yield assertTrue(actual.isRight)
            }
          },
          test(
            s"""
               |Given 'AtLeastOne $AtLeastOne' Cardinality on subclass property'
               |when checking cardinalities $ExactlyOne, $ZeroOrOne
               |then this is NOT possible""".stripMargin
          ) {
            val d = CanSetCardinalityTestData.createOntologyWithSubClassCardinality(AtLeastOne)
            check(cardinalitiesGen(ExactlyOne, ZeroOrOne)) { newCardinality =>
              for {
                _      <- OntologyCacheFake.set(d.data)
                actual <- CardinalityService.canSetCardinality(d.classIri, d.propertyIri, newCardinality)
              } yield assertTrue(actual == Left(List(SubclassCheckFailure(List(d.subclassIri)))))
            }
          },
          test(
            s"""
               |Given 'AtLeastOne $ZeroOrOne' Cardinality on subclass property'
               |when checking cardinalities $ZeroOrOne, $Unbounded
               |then this is possible""".stripMargin
          ) {
            val d = CanSetCardinalityTestData.createOntologyWithSubClassCardinality(ZeroOrOne)
            check(cardinalitiesGen(ZeroOrOne, Unbounded)) { newCardinality =>
              for {
                _      <- OntologyCacheFake.set(d.data)
                actual <- CardinalityService.canSetCardinality(d.classIri, d.propertyIri, newCardinality)
              } yield assertTrue(actual.isRight)
            }
          },
          test(
            s"""
               |Given 'AtLeastOne $ZeroOrOne' Cardinality on subclass property'
               |when checking cardinalities $ExactlyOne, $AtLeastOne
               |then this is NOT possible""".stripMargin
          ) {
            val d = CanSetCardinalityTestData.createOntologyWithSubClassCardinality(ZeroOrOne)
            check(cardinalitiesGen(ExactlyOne, AtLeastOne)) { newCardinality =>
              for {
                _      <- OntologyCacheFake.set(d.data)
                actual <- CardinalityService.canSetCardinality(d.classIri, d.propertyIri, newCardinality)
              } yield assertTrue(actual == Left(List(SubclassCheckFailure(List(d.subclassIri)))))
            }
          }
        )
      ).provide(commonLayers, emptyDataset),
      suite("canSetCardinality with property in use twice")(
        test(s"""
                |Given the previous cardinality on the class/property
                |does not include the new cardinality to be set
                |and given the property is in use
                |then this is NOT possible because the new cardinality does violate the
                |number of times the property is used on each instance
          """.stripMargin) {
          val propertyCardinality =
            OntologyCacheDataBuilder.cardinalitiesMap(Anything.Property.hasOtherThing, Unbounded)
          val data = OntologyCacheDataBuilder.builder
            .addOntology(
              ReadOntologyV2Builder
                .builder(Anything.Ontology)
                .addClassInfo(
                  ReadClassInfoV2Builder
                    .builder(Anything.Class.Thing)
                    .setDirectCardinalities(propertyCardinality)
                )
            )
          check(cardinalitiesGen(ZeroOrOne, ExactlyOne)) { newCardinality =>
            for {
              _ <- OntologyCacheFake.set(data.build)
              actual <-
                CardinalityService.canSetCardinality(
                  Anything.Class.Thing,
                  Anything.Property.hasOtherThing,
                  newCardinality
                )
            } yield assertTrue(actual == Left(List(CurrentClassFailure(Anything.Class.Thing))))
          }
        }
      ).provide(
        commonLayers,
        datasetLayerFromTurtle(s"""
                                  |@prefix rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                                  |@prefix rdfs:        <http://www.w3.org/2000/01/rdf-schema#> .
                                  |
                                  |<http://aThing>
                                  |  a <${Anything.Class.Thing.value}> ;
                                  |  <${Anything.Property.hasOtherThing.value}> "theOther" ;
                                  |  <${Anything.Property.hasOtherThing.value}> "theOtherOther" .
                                  |
                                  | <http://anotherThing>
                                  |  a <${Anything.Class.Thing.value}> ;
                                  |  <${Anything.Property.hasOtherThing.value}> "theOther" ;
                                  |  <${Anything.Property.hasOtherThing.value}> "theOtherOther" .
                                  |
                                  |""".stripMargin)
      ),
      suite("canSetCardinality with property in use once")(
        test(s"""
                |Given the previous cardinality on the class/property
                |does not include the new cardinality to be set
                |and given the property is in use
                |then this is possible because the new cardinality does not violate the
                |number of times the property is used on the instance
          """.stripMargin) {
          val propertyCardinality =
            OntologyCacheDataBuilder.cardinalitiesMap(Anything.Property.hasOtherThing, Unbounded)
          val data = OntologyCacheDataBuilder.builder
            .addOntology(
              ReadOntologyV2Builder
                .builder(Anything.Ontology)
                .addClassInfo(
                  ReadClassInfoV2Builder
                    .builder(Anything.Class.Thing)
                    .setDirectCardinalities(propertyCardinality)
                )
            )
          check(cardinalitiesGen(AtLeastOne, ZeroOrOne, ExactlyOne)) { newCardinality =>
            for {
              _ <- OntologyCacheFake.set(data.build)
              actual <-
                CardinalityService.canSetCardinality(
                  Anything.Class.Thing,
                  Anything.Property.hasOtherThing,
                  newCardinality
                )
            } yield assertTrue(actual.isRight)
          }
        }
      ).provide(
        commonLayers,
        datasetLayerFromTurtle(s"""
                                  |@prefix rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                                  |@prefix rdfs:        <http://www.w3.org/2000/01/rdf-schema#> .
                                  |
                                  |<http://aThing>
                                  |  a <${Anything.Class.Thing.value}> ;
                                  |  <${Anything.Property.hasOtherThing.value}> false .
                                  |
                                  |""".stripMargin)
      )
    )
}
