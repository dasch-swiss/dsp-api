package org.knora.webapi.slice.resourceinfo.api

import org.knora.webapi.slice.resourceinfo.repo.TestResourceInfoRepo.{knownProjectIRI, knownResourceClass}
import org.knora.webapi.slice.resourceinfo.repo.{ResourceInfo, TestResourceInfoRepo}
import zio.test._

import java.time.Instant.now
import java.time.temporal.ChronoUnit.DAYS
import java.util.UUID.randomUUID

object LiveRestResourceInfoServiceSpec extends ZIOSpecDefault {
  override def spec =
    suite("LiveRestResourceInfoServiceSpec")(
      test("should return empty list if no resources found // unknown project and resourceClass") {
        for {
          actual <- RestResourceInfoService.findByProjectAndResourceClass("unknown", "unknown")
        } yield assertTrue(actual == ListResponseDto.empty)
      },
      test("should return empty list if no resources found // unknown resourceClass") {
        for {
          actual <- RestResourceInfoService.findByProjectAndResourceClass(knownProjectIRI, "unknown")
        } yield assertTrue(actual == ListResponseDto.empty)
      },
      test("should return empty list if no resources found // unknown project") {
        for {
          actual <- RestResourceInfoService.findByProjectAndResourceClass("unknown", knownResourceClass)
        } yield assertTrue(actual == ListResponseDto.empty)
      },
      test(
        """given two ResourceInfo exist
          | when findByProjectAndResourceClass
          | then it should return all info sorted by lastModificationDate
          |""".stripMargin.linesIterator.mkString("")
      ) {
        val given1 = ResourceInfo("http://resourceIri/" + randomUUID, now.minus(1, DAYS), now)
        val given2 = ResourceInfo("http://resourceIri/" + randomUUID, now.minus(2, DAYS), now, now.plusSeconds(5))
        for {
          _      <- TestResourceInfoRepo.addAll(List(given1, given2), knownProjectIRI, knownResourceClass)
          actual <- RestResourceInfoService.findByProjectAndResourceClass(knownProjectIRI, knownResourceClass)
        } yield {
          val items = List(given1, given2).map(ResourceInfoDto(_)).sortBy(_.lastModificationDate)
          assertTrue(actual == ListResponseDto(items))
        }
      }
    ).provide(
      LiveRestResourceInfoService.layer,
      TestResourceInfoRepo.layer
    )
}
