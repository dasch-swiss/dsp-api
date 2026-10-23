/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.resources.info

import org.junit.runner.RunWith
import zio.Exit
import zio.ZIO
import zio.test.*

import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS

import dsp.errors.BadRequestException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.api.admin.model.Order
import org.knora.webapi.slice.api.admin.model.Order.*
import org.knora.webapi.slice.api.admin.model.OrderBy
import org.knora.webapi.slice.api.admin.model.OrderBy.*
import org.knora.webapi.slice.api.v2.IriDto
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.resources.domain.ResourceInfo
import org.knora.webapi.slice.resources.repo.ResourceInfoRepoFake
import org.knora.webapi.slice.resources.repo.ResourceInfoRepoFake.knownProjectIRI
import org.knora.webapi.slice.resources.repo.ResourceInfoRepoFake.unknownProjectIRI

@RunWith(classOf[DspZTestJUnitRunner])
class ResourceInfoRestServiceSpec extends ZIOSpecDefault {

  private val thing                 = InternalIri("http://www.knora.org/ontology/0001/anything#Thing")
  private val thingPicture          = InternalIri("http://www.knora.org/ontology/0001/anything#ThingPicture")
  private val thingExternal         = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing"
  private val thingPictureExternal  = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture"
  private val thingExternalIriDto   = IriDto(thingExternal)
  private val unknownClassIriDto    = IriDto("http://0.0.0.0:3333/ontology/0001/anything/v2#Unknown")
  private val t0                    = Instant.parse("2026-01-01T00:00:00Z")
  private def day(n: Long): Instant = t0.plus(n, DAYS)
  private def iri(id: String)       = ResourceIri.unsafeFrom(s"http://rdfh.ch/0001/$id")

  // The IRI order (aaa, bbb, ccc) differs from every date order, so a sort cannot pass by insertion or IRI order.
  // effective date: day 5
  private val ccc = ResourceInfo(iri("ccc"), thing, day(1), Some(day(5)))
  // effective date: day 3, from the creation date
  private val aaa = ResourceInfo(iri("aaa"), thing, day(3), None)
  // effective date: day 4
  private val bbb = ResourceInfo(iri("bbb"), thingPicture, day(2), Some(day(4)), deleteDate = day(4))

  private val seed = ResourceInfoRepoFake.addAll(List(ccc, aaa, bbb), knownProjectIRI)

  private def find(
    projectIri: ProjectIri = knownProjectIRI,
    resourceClass: Option[IriDto] = None,
    cutoff: Option[Instant] = None,
    order: Order = Asc,
    orderBy: OrderBy = LastModificationDate,
  ): ZIO[ResourceInfoRestService, Throwable, ListResponseDto] =
    ZIO.serviceWithZIO[ResourceInfoRestService](_.find(projectIri, resourceClass, cutoff, order, orderBy))

  private def iris(response: ListResponseDto): List[String] = response.resources.map(_.resourceIri)

  override def spec: Spec[TestEnvironment, Any] =
    suite("ResourceInfoRestService")(
      test("should fail with bad request given an invalid resourceClass") {
        for {
          actual <- find(resourceClass = Some(IriDto("invalid-resource-class"))).exit
        } yield assertTrue(
          actual == Exit.fail(BadRequestException("Invalid resourceClass: Couldn't parse IRI: invalid-resource-class")),
        )
      },
      test("should return an empty list for an unknown project") {
        for {
          _      <- seed
          actual <- find(projectIri = unknownProjectIRI)
        } yield assertTrue(actual.resources.isEmpty, actual.count == 0)
      },
      test("should return an empty list for an unknown resourceClass") {
        for {
          _      <- seed
          actual <- find(resourceClass = Some(unknownClassIriDto))
        } yield assertTrue(actual.resources.isEmpty, actual.count == 0)
      },
      test("with a resourceClass, only that class is returned, each entry with its external class IRI") {
        for {
          _      <- seed
          actual <- find(resourceClass = Some(thingExternalIriDto))
        } yield assertTrue(
          iris(actual) == List(aaa, ccc).map(_.iri.value),
          actual.resources.map(_.resourceClass) == List(thingExternal, thingExternal),
          actual.count == 2,
        )
      },
      test("without a resourceClass, all classes are returned, each entry with its external class IRI") {
        for {
          _      <- seed
          actual <- find()
        } yield assertTrue(
          actual.resources.map(r => (r.resourceIri, r.resourceClass)) == List(
            aaa.iri.value -> thingExternal,
            bbb.iri.value -> thingPictureExternal,
            ccc.iri.value -> thingExternal,
          ),
          actual.count == 3,
        )
      },
      test("an entry carries the creation date as lastModificationDate when it has none, and its delete state") {
        for {
          _      <- seed
          actual <- find()
        } yield assertTrue(
          actual.resources.map(r => (r.resourceIri, r.lastModificationDate, r.isDeleted, r.deleteDate)) == List(
            (aaa.iri.value, day(3), false, None),
            (bbb.iri.value, day(4), true, Some(day(4))),
            (ccc.iri.value, day(5), false, None),
          ),
        )
      },
      test("a cutoff excludes resources whose effective date is older, judging by lastModificationDate first") {
        // ccc was created long before the cutoff but modified after it; bbb was both created and modified before it.
        for {
          _      <- seed
          actual <- find(cutoff = Some(day(4).plusMillis(1)))
        } yield assertTrue(iris(actual) == List(ccc.iri.value))
      },
      test("a cutoff exactly equal to a resource's effective date includes it") {
        for {
          _      <- seed
          actual <- find(cutoff = Some(day(4)))
        } yield assertTrue(iris(actual) == List(bbb, ccc).map(_.iri.value))
      },
      test("a resource without lastModificationDate is judged by its creation date") {
        for {
          _         <- seed
          atCreated <- find(resourceClass = Some(thingExternalIriDto), cutoff = Some(day(3)))
          after     <- find(resourceClass = Some(thingExternalIriDto), cutoff = Some(day(3).plusMillis(1)))
        } yield assertTrue(
          iris(atCreated) == List(aaa, ccc).map(_.iri.value),
          iris(after) == List(ccc.iri.value),
        )
      },
      test("sorted by lastModificationDate descending") {
        for {
          _      <- seed
          actual <- find(order = Desc)
        } yield assertTrue(iris(actual) == List(ccc, bbb, aaa).map(_.iri.value))
      },
      test("sorted by creationDate ascending and descending") {
        for {
          _    <- seed
          asc  <- find(orderBy = CreationDate)
          desc <- find(order = Desc, orderBy = CreationDate)
        } yield assertTrue(
          iris(asc) == List(ccc, bbb, aaa).map(_.iri.value),
          iris(desc) == List(aaa, bbb, ccc).map(_.iri.value),
        )
      },
      test("equal dates come back ordered by resource IRI, in both directions") {
        val zzz = ResourceInfo(iri("zzz"), thing, day(7), None)
        val mmm = ResourceInfo(iri("mmm"), thing, day(7), None)
        for {
          _    <- ResourceInfoRepoFake.addAll(List(zzz, mmm), knownProjectIRI)
          asc  <- find()
          desc <- find(order = Desc)
        } yield assertTrue(
          iris(asc) == List(mmm, zzz).map(_.iri.value),
          iris(desc) == List(mmm, zzz).map(_.iri.value),
        )
      },
      test("asOf is the server clock time") {
        val now = Instant.parse("2026-10-23T08:15:30.123Z")
        for {
          _      <- TestClock.setTime(now)
          _      <- seed
          actual <- find()
          empty  <- find(projectIri = unknownProjectIRI)
        } yield assertTrue(actual.asOf == now, empty.asOf == now)
      },
    ).provide(
      IriConverter.layer,
      StringFormatter.test,
      ResourceInfoRestService.layer,
      ResourceInfoRepoFake.layer,
    )
}
