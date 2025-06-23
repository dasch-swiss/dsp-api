/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.service
import org.apache.jena.query.Dataset
import zio.Ref
import zio.ZIO
import zio.ZLayer
import zio.test.*
import zio.test.Assertion.*

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.ontology.repo.service.PredicateRepositoryLive
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriTestConstants.Biblio
import org.knora.webapi.slice.resourceinfo.domain.IriTestConstants.KnoraBase
import org.knora.webapi.store.triplestore.TestDatasetBuilder.datasetLayerFromTurtle
import org.knora.webapi.store.triplestore.TestDatasetBuilder.emptyDataset
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

object PredicateRepositoryLiveSpec extends ZIOSpecDefault {
  private val usedOnce: String =
    s"""
       |@prefix rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs:        <http://www.w3.org/2000/01/rdf-schema#> .
       |
       |<http://aPublication>
       |  a <${Biblio.Class.Publication.value}> ;
       |  <${Biblio.Property.hasTitle.value}> "A Single Publication Title" .
       |
       |""".stripMargin
  private val usedTwice: String =
    s"""
       |@prefix rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs:        <http://www.w3.org/2000/01/rdf-schema#> .
       |
       |<http://aPublicationWithOne>
       |  a <${Biblio.Class.Publication.value}> ;
       |  <${Biblio.Property.hasTitle.value}> "A Single Publication Title" .
       |
       |<http://aPublicationWithTwo>
       |  a <${Biblio.Class.Publication.value}> ;
       |  <${Biblio.Property.hasTitle.value}> "The first Another Publication Title" ;
       |  <${Biblio.Property.hasTitle.value}> "The second Another Publication Title" .
       |
       |<http://aPublicationWithZero>
       |  a <${Biblio.Class.Publication.value}> .
       |
       |<http://aDeletedPublication>
       |  a <${Biblio.Class.Publication.value}> ;
       |  <${Biblio.Property.hasTitle.value}>     "The Title of a deleted Publication" ;
       |  <${KnoraBase.Property.isDeleted.value}> true .
       |""".stripMargin

  private val commonLayers = ZLayer.makeSome[Ref[Dataset], PredicateRepositoryLive](
    PredicateRepositoryLive.layer,
    StringFormatter.test,
    TriplestoreServiceInMemory.layer,
  )

  val spec: Spec[Any, Throwable] = suite("PredicateRepositoryLive")(
    suite("getCountForPropertyUseNumberOfTimesWithClass given not used")(
      test("given a property is not used by any instance of the class then return empty List") {
        for {
          result <-
            ZIO.serviceWithZIO[PredicateRepository](
              _.getCountForPropertyUsedNumberOfTimesWithClass(
                Biblio.Property.hasTitle,
                Biblio.Class.Publication,
              ),
            )
        } yield assertTrue(result == List.empty)
      },
    ).provide(commonLayers, emptyDataset),
    suite("getCountForPropertyUseNumberOfTimesWithClass given used once")(
      test("given a property is in use by a single instance of the class return this instance with a count of one") {
        for {
          result <-
            ZIO.serviceWithZIO[PredicateRepository](
              _.getCountForPropertyUsedNumberOfTimesWithClass(
                Biblio.Property.hasTitle,
                Biblio.Class.Publication,
              ),
            )
        } yield assertTrue(result == List((InternalIri("http://aPublication"), 1)))
      },
    ).provide(commonLayers, datasetLayerFromTurtle(usedOnce)),
    suite("getCountForPropertyUseNumberOfTimesWithClass given used twice")(
      test("given a property is in use by multiple instances of the class return each instance and the times used") {
        for {
          result <-
            ZIO.serviceWithZIO[PredicateRepository](
              _.getCountForPropertyUsedNumberOfTimesWithClass(
                Biblio.Property.hasTitle,
                Biblio.Class.Publication,
              ),
            )
        } yield assert(result)(
          hasSameElements(
            List(
              (InternalIri("http://aPublicationWithZero"), 0),
              (InternalIri("http://aPublicationWithOne"), 1),
              (InternalIri("http://aPublicationWithTwo"), 2),
            ),
          ),
        )
      },
    ).provide(commonLayers, datasetLayerFromTurtle(usedTwice)),
  )
}
