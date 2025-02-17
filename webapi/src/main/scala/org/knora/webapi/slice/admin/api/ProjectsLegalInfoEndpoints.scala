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
import org.knora.webapi.slice.admin.api.model.PagedResponse
import org.knora.webapi.slice.admin.api.model.pageAndSizeQuery
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.License
import org.knora.webapi.slice.common.api.BaseEndpoints

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
    .in(pageAndSizeQuery())
    .out(
      jsonBody[PagedResponse[LicenseDto]].example(
        Examples.PageResponse.from(
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
    .description("Get the allowed licenses for use within this project. The user must be a system or project admin.")

  val getProjectCopyrightHolders = baseEndpoints.securedEndpoint.get
    .in(base / "copyright-holders")
    .in(pageAndSizeQuery())
    .out(
      jsonBody[PagedResponse[CopyrightHolder]].example(
        Examples.PageResponse.from(Chunk("DaSch", "University of Zurich").map(CopyrightHolder.unsafeFrom)),
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
