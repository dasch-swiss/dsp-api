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
import org.knora.webapi.slice.admin.api.Codecs.TapirCodec
import org.knora.webapi.slice.admin.api.model.FilterAndOrder
import org.knora.webapi.slice.admin.api.model.PageAndSize
import org.knora.webapi.slice.admin.api.model.PagedResponse
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.common.api.BaseEndpoints

final case class ProjectLicenseDto(id: String, uri: String, labelEn: String, isRecommended: Boolean, isEnabled: Boolean)
object ProjectLicenseDto {
  given JsonCodec[ProjectLicenseDto] = DeriveJsonCodec.gen[ProjectLicenseDto]
  given Ordering[ProjectLicenseDto]  = Ordering.by(_.labelEn)
  given Schema[PagedResponse[ProjectLicenseDto]] = Schema
    .derived[PagedResponse[ProjectLicenseDto]]
    .modify(_.data)(_.copy(isOptional = false))

  def from(license: License, isEnabled: Boolean): ProjectLicenseDto = {
    val isRecommended = license.isRecommended match {
      case IsDaschRecommended.Yes => true
      case IsDaschRecommended.No  => false
    }
    ProjectLicenseDto(license.id.value, license.uri.toString, license.labelEn, isRecommended, isEnabled)
  }
}

final case class CopyrightHolderAddRequest(data: Set[CopyrightHolder])
object CopyrightHolderAddRequest {
  given JsonCodec[CopyrightHolderAddRequest] = DeriveJsonCodec.gen[CopyrightHolderAddRequest]
}

final case class CopyrightHolderReplaceRequest(`old-value`: CopyrightHolder, `new-value`: CopyrightHolder)
object CopyrightHolderReplaceRequest {
  given JsonCodec[CopyrightHolderReplaceRequest] = DeriveJsonCodec.gen[CopyrightHolderReplaceRequest]
}

final case class ProjectsLegalInfoEndpoints(baseEndpoints: BaseEndpoints) {

  private final val base = "admin" / "projects" / "shortcode" / projectShortcode / "legal-info"

  private final val licenseIriPath = path[LicenseIri](TapirCodec.stringCodec(LicenseIri.from))
    .name("licenseIri")
    .description("The IRI of the license. Must be URL-encoded.")
    .example(LicenseIri.CC_BY_NC_4_0)

  val getProjectAuthorships = baseEndpoints.securedEndpoint.get
    .in(base / "authorships")
    .in(PageAndSize.queryParams())
    .in(FilterAndOrder.queryParams)
    .out(
      jsonBody[PagedResponse[Authorship]].example(
        Examples.PagedResponse.fromSlice(
          Chunk(
            Authorship.unsafeFrom("Lotte Reiniger"),
            Authorship.unsafeFrom("Margaret J. Winkler"),
            Authorship.unsafeFrom("Hilma af Klint"),
          ),
        ),
      ),
    )
    .description(
      "Get the allowed authorships for use within this project. " +
        "The user must be project member, project admin or system admin.",
    )

  val getProjectLicenses = baseEndpoints.securedEndpoint.get
    .in(base / "licenses")
    .in(PageAndSize.queryParams())
    .in(FilterAndOrder.queryParams)
    .in(query[Boolean]("showEnabledOnly").description("Filter by enabled licenses").default(false))
    .out(
      jsonBody[PagedResponse[ProjectLicenseDto]]
        .example(Examples.PagedResponse.fromTotal(License.BUILT_IN.map(l => ProjectLicenseDto.from(l, true)))),
    )
    .description(
      "Get the available (enabled and disabled) licenses for use within this project. " +
        "The user must be project member, project admin or system admin.",
    )

  val putProjectLicensesEnable = baseEndpoints.securedEndpoint.put
    .in(base / "licenses" / licenseIriPath / "enable")
    .description(
      "Enable a license for use within this project. " +
        "The user must be a system admin.",
    )

  val putProjectLicensesDisable = baseEndpoints.securedEndpoint.put
    .in(base / "licenses" / licenseIriPath / "disable")
    .description(
      "Disable a license for use within this project. " +
        "The user must be a system admin.",
    )

  val getProjectCopyrightHolders = baseEndpoints.securedEndpoint.get
    .in(base / "copyright-holders")
    .in(PageAndSize.queryParams())
    .in(FilterAndOrder.queryParams)
    .out(
      jsonBody[PagedResponse[CopyrightHolder]].example(
        Examples.PagedResponse.fromSlice(Chunk("DaSCH", "University of Zurich").map(CopyrightHolder.unsafeFrom)),
      ),
    )
    .description(
      "Get the allowed copyright holders for use within this project. " +
        "The user must be project member, project admin or system admin.",
    )

  val postProjectCopyrightHolders = baseEndpoints.securedEndpoint.post
    .in(base / "copyright-holders")
    .in(
      jsonBody[CopyrightHolderAddRequest]
        .example(CopyrightHolderAddRequest(Set("DaSCH", "University of Zurich").map(CopyrightHolder.unsafeFrom))),
    )
    .description(
      "Add new allowed copyright holders for use within this project. " +
        "The user must be a system or project admin.",
    )

  val putProjectCopyrightHolders = baseEndpoints.securedEndpoint.put
    .in(base / "copyright-holders")
    .in(
      jsonBody[CopyrightHolderReplaceRequest]
        .example(
          CopyrightHolderReplaceRequest(
            CopyrightHolder.unsafeFrom("DaSch"),
            CopyrightHolder.unsafeFrom("DaSCH"),
          ),
        ),
    )
    .description(
      "Update a particular allowed copyright holder for use within this project, does not update existing values on assets. " +
        "The user must be a system admin.",
    )

  val endpoints: Seq[AnyEndpoint] = Seq(
    getProjectAuthorships,
    getProjectLicenses,
    putProjectLicensesEnable,
    putProjectLicensesDisable,
    getProjectCopyrightHolders,
    postProjectCopyrightHolders,
    putProjectCopyrightHolders,
  ).map(_.endpoint).map(_.tag("Admin Projects (Legal Info)"))
}

object ProjectsLegalInfoEndpoints {
  val layer = ZLayer.derive[ProjectsLegalInfoEndpoints]
}
