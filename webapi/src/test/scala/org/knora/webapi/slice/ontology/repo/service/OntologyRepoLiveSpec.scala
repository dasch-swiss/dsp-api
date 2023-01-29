/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo.service

import zio.Scope
import zio.test.ZIOSpecDefault
import zio.test._

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.ontology.domain.OntologyCacheDataBuilder
import org.knora.webapi.slice.ontology.domain.ReadClassInfoV2Builder
import org.knora.webapi.slice.ontology.domain.ReadOntologyV2Builder
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resourceinfo.domain.IriTestConstants.Anything
import org.knora.webapi.slice.resourceinfo.domain.IriTestConstants.Biblio

object OntologyRepoLiveSpec extends ZIOSpecDefault {

  private val sf = {
    StringFormatter.initForTest()
    StringFormatter.getGeneralInstance
  }
  private val anUnknownInternalOntologyIri = InternalIri("http://www.knora.org/ontology/0001/anything")
  private val anUnknownClassIri            = InternalIri("http://www.knora.org/ontology/0001/anything#Thing")

  private val aKnownClassIri: InternalIri   = InternalIri("http://www.knora.org/ontology/0001/gizmo#Gizmo")
  private val aKnownClassSmartIri: SmartIri = sf.toSmartIri(aKnownClassIri.value)
  private val ontologySmartIri: SmartIri    = aKnownClassSmartIri.getOntologyFromEntity

  val spec: Spec[TestEnvironment with Scope, Any] =
    suite("OntologyRepoLive")(
      suite("findOntologyBy(InternalIri)")(
        test("when searching for unknown iri => return None") {
          for {
            actual <- OntologyRepo.findById(anUnknownInternalOntologyIri)
          } yield assertTrue(actual.isEmpty)
        },
        test("when searching for known iri => return Some(ReadOntology)") {
          val cacheData = OntologyCacheDataBuilder.builder(ontologySmartIri).build
          for {
            _      <- OntologyCacheFake.set(cacheData)
            actual <- OntologyRepo.findById(ontologySmartIri.toInternalIri)
          } yield assertTrue(actual == cacheData.ontologies.get(ontologySmartIri))
        }
      ),
      suite("findClassBy(InternalIri)")(
        test("when searching for a known iri => return Some(ReadClassInfoV2])") {
          val knownClass = ReadClassInfoV2Builder.builder(aKnownClassIri).build
          val data = OntologyCacheDataBuilder.builder
            .addOntology(
              ReadOntologyV2Builder
                .builder(ontologySmartIri)
                .addClassInfo(knownClass)
            )
            .build
          for {
            _      <- OntologyCacheFake.set(data)
            actual <- OntologyRepo.findClassBy(aKnownClassIri)
          } yield assertTrue(actual.contains(knownClass))
        },
        test("when searching for unknown iri => return None") {
          for {
            _      <- OntologyCacheFake.set(OntologyCacheDataBuilder.empty)
            actual <- OntologyRepo.findClassBy(anUnknownClassIri)
          } yield assertTrue(actual.isEmpty)
        }
      ),
      suite("findAll()")(
        test("given cache is Empty => return empty List") {
          for {
            _      <- OntologyCacheFake.set(OntologyCacheDataBuilder.empty)
            actual <- OntologyRepo.findAll()
          } yield assertTrue(actual.isEmpty)
        },
        test("given cache has an ontology => return List of ontologies") {
          val ontology  = ReadOntologyV2Builder.builder(ontologySmartIri).build
          val cacheData = OntologyCacheDataBuilder.builder.addOntology(ontology).build
          for {
            _      <- OntologyCacheFake.set(cacheData)
            actual <- OntologyRepo.findAll()
          } yield assertTrue(actual == List(ontology))
        }
      ),
      suite("findAllSuperClassesBy") {
        test("findAllSuperClassesBy multiple levels up across ontologies") {
          val anythingOntologyDefinition = ReadOntologyV2Builder
            .builder(Anything.Ontology)
            .addClassInfo(ReadClassInfoV2Builder.builder(Anything.Class.Thing))
          val biblioOntologyDefinition = ReadOntologyV2Builder
            .builder(Biblio.Ontology)
            .addClassInfo(ReadClassInfoV2Builder.builder(Biblio.Class.Publication))
            .addClassInfo(
              ReadClassInfoV2Builder
                .builder(Biblio.Class.Article)
                .addSuperClass(Biblio.Class.Publication) // super-class Publication should be found
                .addSuperClass(Anything.Class.Thing)     // super-class Thing should be found, defined in another ontology
            )
            .addClassInfo(
              ReadClassInfoV2Builder
                .builder(Biblio.Class.JournalArticle)
                .addSuperClass(Biblio.Class.Article) // super-class Article should be found
            )
          val data = OntologyCacheDataBuilder.builder
            .addOntology(anythingOntologyDefinition)
            .addOntology(biblioOntologyDefinition)
          for {
            _         <- OntologyCacheFake.set(data.build).debug
            actual    <- OntologyRepo.findAllSuperClassesBy(Biblio.Class.JournalArticle)
            actualIris = actual.map(_.entityInfoContent.classIri.toInternalIri)
          } yield assertTrue(actualIris == List(Biblio.Class.Article, Anything.Class.Thing, Biblio.Class.Publication))
        }
      }
    ).provide(OntologyRepoLive.layer, OntologyCacheFake.emptyCache, IriConverter.layer, StringFormatter.test)
}
