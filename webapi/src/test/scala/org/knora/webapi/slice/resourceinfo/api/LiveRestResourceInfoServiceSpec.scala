/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.api

import zhttp.http.HttpError.BadRequest
import zio.test.Assertion.equalTo
import zio.test.Assertion.fails
import zio.test._

import java.time.Instant.now
import java.time.temporal.ChronoUnit.DAYS
import java.util.UUID.randomUUID

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.resourceinfo.api.LiveRestResourceInfoService.ASC
import org.knora.webapi.slice.resourceinfo.api.LiveRestResourceInfoService.DESC
import org.knora.webapi.slice.resourceinfo.api.LiveRestResourceInfoService.creationDate
import org.knora.webapi.slice.resourceinfo.api.LiveRestResourceInfoService.lastModificationDate
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resourceinfo.domain.ResourceInfo
import org.knora.webapi.slice.resourceinfo.repo.TestResourceInfoRepo
import org.knora.webapi.slice.resourceinfo.repo.TestResourceInfoRepo.knownProjectIRI
import org.knora.webapi.slice.resourceinfo.repo.TestResourceInfoRepo.knownResourceClass

object LiveRestResourceInfoServiceSpec extends ZIOSpecDefault {
  override def spec =
    suite("LiveRestResourceInfoServiceSpec")(
      test("should fail with bad request given an invalid projectIri") {
        for {
          result <- RestResourceInfoService
                      .findByProjectAndResourceClass(
                        "invalid-project",
                        knownResourceClass.value,
                        (lastModificationDate, ASC)
                      )
                      .exit
        } yield assert(result)(fails(equalTo(BadRequest("Invalid projectIri: Couldn't parse IRI: invalid-project"))))
      },
      test("should fail with bad request given an invalid resourceClass") {
        for {
          result <- RestResourceInfoService
                      .findByProjectAndResourceClass(
                        knownProjectIRI.value,
                        "invalid-resource-class",
                        (lastModificationDate, ASC)
                      )
                      .exit
        } yield assert(result)(
          fails(equalTo(BadRequest("Invalid resourceClass: Couldn't parse IRI: invalid-resource-class")))
        )
      },
      test("should return empty list if no resources found // unknown project and resourceClass") {
        for {
          actual <-
            RestResourceInfoService.findByProjectAndResourceClass(
              "http://unknown-project",
              "http://unknown-resource-class",
              (lastModificationDate, ASC)
            )
        } yield assertTrue(actual == ListResponseDto.empty)
      },
      test("should return empty list if no resources found // unknown resourceClass") {
        for {
          actual <- RestResourceInfoService.findByProjectAndResourceClass(
                      knownProjectIRI.value,
                      "http://unknown-resource-class",
                      (lastModificationDate, ASC)
                    )
        } yield assertTrue(actual == ListResponseDto.empty)
      },
      test("should return empty list if no resources found // unknown project") {
        for {
          actual <-
            RestResourceInfoService.findByProjectAndResourceClass(
              "http://unknown-project",
              knownResourceClass.value,
              (lastModificationDate, ASC)
            )
        } yield assertTrue(actual == ListResponseDto.empty)
      },
      test(
        """given two ResourceInfo exist
          | when findByProjectAndResourceClass
          | then it should return all info sorted by (lastModificationDate, ASC)
          |""".stripMargin.linesIterator.mkString("")
      ) {
        val given1 = ResourceInfo("http://resourceIri/" + randomUUID, now.minus(10, DAYS), Some(now.minus(9, DAYS)))
        val given2 =
          ResourceInfo("http://resourceIri/" + randomUUID, now.minus(20, DAYS), Some(now.minus(8, DAYS)), now)
        for {
          _ <- TestResourceInfoRepo.addAll(List(given1, given2), knownProjectIRI, knownResourceClass)
          actual <-
            RestResourceInfoService.findByProjectAndResourceClass(
              knownProjectIRI.value,
              knownResourceClass.value,
              (lastModificationDate, ASC)
            )
        } yield {
          val items = List(given1, given2).map(ResourceInfoDto(_)).sortBy(_.lastModificationDate)
          assertTrue(actual == ListResponseDto(items))
        }
      },
      test(
        """given two ResourceInfo exist
          | when findByProjectAndResourceClass ordered by (creationDate, DESC)
          | then it should return all info sorted correctly
          |""".stripMargin.linesIterator.mkString("")
      ) {
        val given1 = ResourceInfo("http://resourceIri/" + randomUUID, now.minus(10, DAYS), Some(now.minus(9, DAYS)))
        val given2 =
          ResourceInfo("http://resourceIri/" + randomUUID, now.minus(20, DAYS), Some(now.minus(8, DAYS)), now)
        for {
          _ <- TestResourceInfoRepo.addAll(List(given1, given2), knownProjectIRI, knownResourceClass)
          actual <- RestResourceInfoService.findByProjectAndResourceClass(
                      knownProjectIRI.value,
                      knownResourceClass.value,
                      ordering = (creationDate, DESC)
                    )
        } yield {
          val items = List(given1, given2).map(ResourceInfoDto(_)).sortBy(_.creationDate).reverse
          assertTrue(actual == ListResponseDto(items))
        }
      }
    ).provide(
      IriConverter.layer,
      StringFormatter.test,
      LiveRestResourceInfoService.layer,
      TestResourceInfoRepo.layer
    )
}
