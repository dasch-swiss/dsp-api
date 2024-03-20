/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import eu.timepit.refined.auto.*
import zio.Chunk
import zio.ZIO
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertCompletes
import zio.test.assertTrue

import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.*
import org.knora.webapi.slice.admin.domain.repo.KnoraProjectRepoInMemory
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.MaintenanceService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.triplestore.TestDatasetBuilder
import org.knora.webapi.store.triplestore.api.TestTripleStore
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory.emptyDatasetRefLayer

object MaintenanceServiceSpec extends ZIOSpecDefault {

  private val testProject              = TestDataFactory.someProject
  private val createProject            = ZIO.serviceWithZIO[KnoraProjectRepoInMemory](_.save(testProject))
  private val projectDataNamedGraphIri = ProjectService.projectDataNamedGraphV2(testProject).value
  private val testAssetId              = AssetId.unsafeFrom("some-asset-id")
  private val expectedDimension        = Dimensions(5202, 3602)
  private val testReport = ProjectsWithBakfilesReport(
    Chunk(ProjectWithBakFiles(testProject.shortcode, Chunk(ReportAsset(testAssetId, expectedDimension)))),
  )
  private val testValueIri = "http://rdfh.ch/some-value-iri"

  private def saveStillImageFileValueWithDimensions(width: Int, height: Int) = TestDatasetBuilder
    .datasetFromTriG(s"""
                        | @prefix knora-base: <http://www.knora.org/ontology/knora-base#> .
                        | @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> . 
                        | @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                        | 
                        | <$projectDataNamedGraphIri> {
                        |   <$testValueIri> a knora-base:StillImageFileValue;
                        |     knora-base:dimX "${width}"^^xsd:integer;
                        |     knora-base:dimY "${height}"^^xsd:integer;
                        |     knora-base:internalFilename "$testAssetId.jp2"^^xsd:string;
                        |  }
                        |""".stripMargin)
    .flatMap(ds => ZIO.serviceWithZIO[TestTripleStore](_.setDataset(ds)))

  def queryForDim() = for {
    rowMap <- TriplestoreService
                .query(Select(s"""
                                 |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |
                                 |SELECT ?width ?height
                                 |FROM <$projectDataNamedGraphIri>
                                 |WHERE {
                                 |   <$testValueIri> a knora-base:StillImageFileValue ;
                                 |                   knora-base:dimX ?width ;
                                 |                   knora-base:dimY ?height .
                                 |}
                                 |""".stripMargin))
                .map(_.results.bindings.head.rowMap)
    width  <- ZIO.fromOption(rowMap.get("width").map(_.toInt))
    height <- ZIO.fromOption(rowMap.get("height").map(_.toInt))
    dim    <- ZIO.fromEither(Dimensions.from(width, height))
  } yield dim

  val spec: Spec[Any, Any] = suite("MaintenanceService")(
    test("fixTopLeftDimensions should not fail for an empty report") {
      createProject *>
        saveStillImageFileValueWithDimensions(width = expectedDimension.height, height = expectedDimension.width) *>
        ZIO
          .serviceWithZIO[MaintenanceService](_.fixTopLeftDimensions(ProjectsWithBakfilesReport(Chunk.empty)))
          .as(assertCompletes)
    },
    test("fixTopLeftDimensions should not fail if no StillImageFileValue is found") {
      createProject *>
        ZIO.serviceWithZIO[MaintenanceService](_.fixTopLeftDimensions(testReport)).as(assertCompletes)
    },
    test("fixTopLeftDimensions should not fail if project is not found") {
      ZIO.serviceWithZIO[MaintenanceService](_.fixTopLeftDimensions(testReport)).as(assertCompletes)
    },
    test("fixTopLeftDimensions should transpose dimension for an existing StillImageFileValue") {
      for {
        // given
        _ <- createProject
        _ <- saveStillImageFileValueWithDimensions(width = expectedDimension.height, height = expectedDimension.width)
        // when
        _ <- ZIO.serviceWithZIO[MaintenanceService](_.fixTopLeftDimensions(testReport))
        // then
        actualDimension <- queryForDim()
      } yield assertTrue(actualDimension == expectedDimension)
    },
    test(
      "fixTopLeftDimensions should not transpose dimension for an existing StillImageFileValue if the dimensions are correct",
    ) {
      for {
        // given
        _ <- createProject
        _ <- saveStillImageFileValueWithDimensions(width = expectedDimension.width, height = expectedDimension.height)
        // when
        _ <- ZIO.serviceWithZIO[MaintenanceService](_.fixTopLeftDimensions(testReport))
        // then
        actualDimension <- queryForDim()
      } yield assertTrue(actualDimension == expectedDimension)
    },
  ).provide(
    MaintenanceService.layer,
    KnoraProjectService.layer,
    KnoraProjectRepoInMemory.layer,
    emptyDatasetRefLayer >>> TriplestoreServiceInMemory.layer,
    PredicateObjectMapper.layer,
    IriConverter.layer,
    StringFormatter.test,
  )
}
