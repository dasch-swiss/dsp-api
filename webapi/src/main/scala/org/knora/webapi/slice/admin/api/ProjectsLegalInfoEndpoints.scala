/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.Chunk
import zio.ZLayer
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.slice.admin.api.AdminPathVariables.projectShortcode
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.License
import org.knora.webapi.slice.common.api.BaseEndpoints

final case class PageInfo(size: Int, `total-elements`: Int, pages: Int, number: Int)
object PageInfo {
  given JsonCodec[PageInfo]         = DeriveJsonCodec.gen[PageInfo]
  def single(seq: Seq[_]): PageInfo = PageInfo(seq.size, seq.size, 1, 1)
  def from(total: Int, pageAndSize: PageAndSize) =
    val pages = Math.ceil(total.toDouble / pageAndSize.size).toInt
    PageInfo(pageAndSize.size, total, pages, pageAndSize.page)
}

case class PageAndSize(page: Int, size: Int)
val pageQuery = query[Int]("page")
  .description("The page number to retrieve.")
  .default(1)
  .validate(Validator.min(1))
val sizeQuery = query[Int]("size")
  .description("The number of items to retrieve.")
  .default(100)
  .validate(Validator.min(1))
val pageRequestAndSize = pageQuery.and(sizeQuery).mapTo[PageAndSize]

final case class PagedResponse[A](data: Seq[A], page: PageInfo)
object PagedResponse {
  given [A: JsonCodec]: JsonCodec[PagedResponse[A]] = DeriveJsonCodec.gen[PagedResponse[A]]
  def allInOnePage[A](as: Seq[A]): PagedResponse[A] = PagedResponse[A](as, PageInfo.single(as))
}

final case class LicenseDto(id: String, uri: String, `label-en`: String)
object LicenseDto {
  given JsonCodec[LicenseDto]            = DeriveJsonCodec.gen[LicenseDto]
  def from(license: License): LicenseDto = LicenseDto(license.id.value, license.uri.toString, license.labelEn)
}

final case class CopyrightHolderAddRequest(data: Set[CopyrightHolder])
object CopyrightHolderAddRequest {
  given JsonCodec[CopyrightHolderAddRequest] = DeriveJsonCodec.gen[CopyrightHolderAddRequest]
}

final case class CopyrighHolderReplaceRequest(`old-value`: CopyrightHolder, `new-value`: CopyrightHolder)
object CopyrighHolderReplaceRequest {
  given JsonCodec[CopyrighHolderReplaceRequest] = DeriveJsonCodec.gen[CopyrighHolderReplaceRequest]
}

final case class ProjectsLegalInfoEndpoints(baseEndpoints: BaseEndpoints) {

  private final val base = "admin" / "projects" / "shortcode" / projectShortcode / "legal-info"

  val getProjectLicenses = baseEndpoints.securedEndpoint.get
    .in(base / "licenses")
    .in(pageRequestAndSize)
    .out(
      jsonBody[PagedResponse[LicenseDto]].example(
        PagedResponse.allInOnePage(
          Chunk(
            LicenseDto(
              "http://rdfh.ch/licenses/cc-by-4.0",
              "https://creativecommons.org/licenses/by/4.0/",
              "CC BY 4.0",
            ),
            LicenseDto(
              "http://rdfh.ch/licenses/cc-by-sa-4.0",
              "https://creativecommons.org/licenses/by-sa/4.0/",
              "CC BY-SA 4.0",
            ),
          ),
        ),
      ),
    )
    .description("Get the allowed licenses of a project. The user must be a system or project admin.")

  val getProjectCopyrightHolders = baseEndpoints.securedEndpoint.get
    .in(base / "copyright-holders")
    .in(pageRequestAndSize)
    .out(
      jsonBody[PagedResponse[CopyrightHolder]].example(
        PagedResponse.allInOnePage(Chunk("DaSch", "University of Zurich").map(CopyrightHolder.unsafeFrom)),
      ),
    )

  val postProjectCopyrightHolders = baseEndpoints.securedEndpoint.post
    .in(base / "copyright-holders")
    .in(
      jsonBody[CopyrightHolderAddRequest]
        .example(CopyrightHolderAddRequest(Set("DaSCH", "University of Zurich").map(CopyrightHolder.unsafeFrom))),
    )
    .description("Add a new predefined authorships to a project. The user must be a system or project admin.")

  val putProjectCopyrightHolders = baseEndpoints.securedEndpoint.put
    .in(base / "copyright-holders")
    .in(
      jsonBody[CopyrighHolderReplaceRequest]
        .example(
          CopyrighHolderReplaceRequest(
            CopyrightHolder.unsafeFrom("DaSch"),
            CopyrightHolder.unsafeFrom("DaSCH"),
          ),
        ),
    )
    .description(
      "Update a particular predefined authorships of a project, does not update existing authorships on assets. The user must be a system admin.",
    )

  val endpoints: Seq[AnyEndpoint] = Seq(
    getProjectLicenses,
    getProjectCopyrightHolders,
    postProjectCopyrightHolders,
    putProjectCopyrightHolders,
  ).map(_.endpoint).map(_.tag("Admin Projects (Legal Info)"))
}

object ProjectsLegalInfoEndpoints {
  val layer = ZLayer.derive[ProjectsLegalInfoEndpoints]
}
