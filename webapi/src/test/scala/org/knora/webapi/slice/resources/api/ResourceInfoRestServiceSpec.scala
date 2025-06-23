/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

import zio.Exit
import zio.ZIO
import zio.test.*

import java.time.Instant.now
import java.time.temporal.ChronoUnit.DAYS
import java.util.UUID.randomUUID

import dsp.errors.BadRequestException
import org.knora.webapi.IRI
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.resources.api.model.ListResponseDto
import org.knora.webapi.slice.resources.api.model.QueryParams.Asc
import org.knora.webapi.slice.resources.api.model.QueryParams.CreationDate
import org.knora.webapi.slice.resources.api.model.QueryParams.Desc
import org.knora.webapi.slice.resources.api.model.QueryParams.LastModificationDate
import org.knora.webapi.slice.resources.api.model.QueryParams.Order
import org.knora.webapi.slice.resources.api.model.QueryParams.OrderBy
import org.knora.webapi.slice.resources.api.model.ResourceInfoDto
import org.knora.webapi.slice.resources.api.service.ResourceInfoRestService
import org.knora.webapi.slice.resources.domain.ResourceInfo
import org.knora.webapi.slice.resources.repo.ResourceInfoRepoFake
import org.knora.webapi.slice.resources.repo.ResourceInfoRepoFake.knownProjectIRI
import org.knora.webapi.slice.resources.repo.ResourceInfoRepoFake.knownResourceClass
import org.knora.webapi.slice.resources.repo.ResourceInfoRepoFake.unknownProjectIRI

object ResourceInfoRestServiceSpec extends ZIOSpecDefault {

  private def findByProjectAndResourceClass(
    projectIri: ProjectIri,
    resourceClass: IRI,
    order: Order,
    orderBy: OrderBy,
  ): ZIO[ResourceInfoRestService, Throwable, ListResponseDto] =
    ZIO.serviceWithZIO[ResourceInfoRestService](
      _.findByProjectAndResourceClass(projectIri, resourceClass, order, orderBy),
    )

  override def spec: Spec[Any, Any] =
    suite("LiveResourceInfoRestServiceSpec")(
      test("should fail with bad request given an invalid resourceClass") {
        for {
          actual <-
            findByProjectAndResourceClass(knownProjectIRI, "invalid-resource-class", Asc, LastModificationDate).exit
        } yield assertTrue(
          actual == Exit.fail(BadRequestException("Invalid resourceClass: Couldn't parse IRI: invalid-resource-class")),
        )
      },
      test("should return empty list if no resources found // unknown project and resourceClass") {
        for {
          actual <-
            findByProjectAndResourceClass(unknownProjectIRI, "http://unknown-resource-class", Asc, LastModificationDate)
        } yield assertTrue(actual == ListResponseDto.empty)
      },
      test("should return empty list if no resources found // unknown resourceClass") {
        for {
          actual <-
            findByProjectAndResourceClass(knownProjectIRI, "http://unknown-resource-class", Asc, LastModificationDate)
        } yield assertTrue(actual == ListResponseDto.empty)
      },
      test("should return empty list if no resources found // unknown project") {
        for {
          actual <-
            findByProjectAndResourceClass(unknownProjectIRI, knownResourceClass.value, Asc, LastModificationDate)
        } yield assertTrue(actual == ListResponseDto.empty)
      },
      test(
        """given two ResourceInfo exist
          | when findByProjectAndResourceClass
          | then it should return all info sorted by (lastModificationDate, ASC)
          |""".stripMargin.linesIterator.mkString(""),
      ) {
        val given1 = ResourceInfo("http://resourceIri/" + randomUUID, now.minus(10, DAYS), Some(now.minus(9, DAYS)))
        val given2 =
          ResourceInfo("http://resourceIri/" + randomUUID, now.minus(20, DAYS), Some(now.minus(8, DAYS)), now)
        for {
          _      <- ResourceInfoRepoFake.addAll(List(given1, given2), knownProjectIRI, knownResourceClass)
          actual <- findByProjectAndResourceClass(knownProjectIRI, knownResourceClass.value, Asc, LastModificationDate)
        } yield {
          val items = List(given1, given2).map(ResourceInfoDto.from).sortBy(_.lastModificationDate)
          assertTrue(actual == ListResponseDto(items))
        }
      },
      test(
        """given two ResourceInfo exist
          | when findByProjectAndResourceClass ordered by (creationDate, DESC)
          | then it should return all info sorted correctly
          |""".stripMargin.linesIterator.mkString(""),
      ) {
        val given1 = ResourceInfo("http://resourceIri/" + randomUUID, now.minus(10, DAYS), Some(now.minus(9, DAYS)))
        val given2 =
          ResourceInfo("http://resourceIri/" + randomUUID, now.minus(20, DAYS), Some(now.minus(8, DAYS)), now)
        for {
          _      <- ResourceInfoRepoFake.addAll(List(given1, given2), knownProjectIRI, knownResourceClass)
          actual <- findByProjectAndResourceClass(knownProjectIRI, knownResourceClass.value, Desc, CreationDate)
        } yield {
          val items = List(given1, given2).map(ResourceInfoDto.from).sortBy(_.creationDate).reverse
          assertTrue(actual == ListResponseDto(items))
        }
      },
    ).provide(
      IriConverter.layer,
      StringFormatter.test,
      ResourceInfoRestService.layer,
      ResourceInfoRepoFake.layer,
    )
}
