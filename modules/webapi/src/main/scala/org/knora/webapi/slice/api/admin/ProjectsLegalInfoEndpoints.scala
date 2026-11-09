/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.Chunk
import zio.ZLayer
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.PagedResponse
import org.knora.webapi.slice.api.admin.AdminPathVariables.projectShortcode
import org.knora.webapi.slice.api.admin.Codecs.TapirCodec
import org.knora.webapi.slice.api.admin.model.FilterAndOrder
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.Policy

final case class ProjectLicenseDto(id: String, uri: String, labelEn: String, isRecommended: Boolean, isEnabled: Boolean)
object ProjectLicenseDto {
  given JsonCodec[ProjectLicenseDto] = DeriveJsonCodec.gen[ProjectLicenseDto]
  given Ordering[ProjectLicenseDto]  = Ordering.by(_.labelEn)

  def from(license: License, project: KnoraProject): ProjectLicenseDto =
    from(license, project.enabledLicenses.contains(license.id))

  def from(license: License, isEnabled: Boolean): ProjectLicenseDto =
    ProjectLicenseDto(
      license.id.value,
      license.uri.toString,
      license.labelEn,
      license.isRecommended.toBoolean,
      isEnabled,
    )
}

final case class CopyrightHolderAddRequest(data: Set[CopyrightHolder])
object CopyrightHolderAddRequest {
  given JsonCodec[CopyrightHolderAddRequest] = DeriveJsonCodec.gen[CopyrightHolderAddRequest]
}

final case class CopyrightHolderReplaceRequest(`old-value`: CopyrightHolder, `new-value`: CopyrightHolder)
object CopyrightHolderReplaceRequest {
  given JsonCodec[CopyrightHolderReplaceRequest] = DeriveJsonCodec.gen[CopyrightHolderReplaceRequest]
}

final case class ResourceSideLegalInfo(
  dataLicense: Option[LicenseIri],
  dataCopyrightHolder: Option[CopyrightHolder],
  defaultDataAuthorship: List[Authorship],
)
object ResourceSideLegalInfo {
  given JsonCodec[ResourceSideLegalInfo] = DeriveJsonCodec.gen[ResourceSideLegalInfo]

  val example: ResourceSideLegalInfo = ResourceSideLegalInfo(
    Some(LicenseIri.CC_BY_4_0),
    Some(CopyrightHolder.unsafeFrom("University of Basel")),
    List("Lotte Reiniger", "Hilma af Klint").map(Authorship.unsafeFrom),
  )
}

final class ProjectsLegalInfoEndpoints(baseEndpoints: BaseEndpoints) {

  private val publicReason      = "The licenses a project allows are shown on its public data."
  private val publicRequirement = Policy.Public(publicReason).requirement

  private val pathProjectShortcode = "path projectShortcode"
  private val adminRequirement     = Policy.ProjectAdmin(pathProjectShortcode).requirement
  private val memberRequirement    = Policy.ProjectMember(pathProjectShortcode).requirement

  private final val base = "admin" / "projects" / "shortcode" / projectShortcode / "legal-info"

  private final val licenseIriPath = path[LicenseIri](using TapirCodec.stringCodec(LicenseIri.from))
    .name("licenseIri")
    .description("The IRI of the license. Must be URL-encoded.")
    .example(LicenseIri.CC_BY_NC_4_0)

  val getProjectAuthorships = baseEndpoints
    .projectMemberPolicyEndpoint(pathProjectShortcode)
    .get
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
    .description(s"Get the allowed authorships for use within this project. $memberRequirement")

  val getProjectLicenses = baseEndpoints
    .publicPolicyEndpoint(publicReason)
    .get
    .in(base / "licenses")
    .in(PageAndSize.queryParams())
    .in(FilterAndOrder.queryParams)
    .in(query[Boolean]("showOnlyEnabled").description("Show only enabled licenses if true.").default(false))
    .out(
      jsonBody[PagedResponse[ProjectLicenseDto]]
        .example(Examples.PagedResponse.fromTotal(License.BUILT_IN.map(l => ProjectLicenseDto.from(l, true)))),
    )
    .description(s"Get the available licenses for use within this project. $publicRequirement")

  val getProjectLicensesIri = baseEndpoints
    .publicPolicyEndpoint(publicReason)
    .get
    .in(base / "licenses" / licenseIriPath)
    .out(jsonBody[ProjectLicenseDto])
    .description(s"Get a specific license by its IRI for use within this project. $publicRequirement")

  val putProjectLicensesEnable = baseEndpoints
    .projectAdminPolicyEndpoint(pathProjectShortcode)
    .put
    .in(base / "licenses" / licenseIriPath / "enable")
    .description(s"Enable a license for use within this project. $adminRequirement")

  val putProjectLicensesDisable = baseEndpoints
    .projectAdminPolicyEndpoint(pathProjectShortcode)
    .put
    .in(base / "licenses" / licenseIriPath / "disable")
    .description(s"Disable a license for use within this project. $adminRequirement")

  val getProjectCopyrightHolders = baseEndpoints
    .projectMemberPolicyEndpoint(pathProjectShortcode)
    .get
    .in(base / "copyright-holders")
    .in(PageAndSize.queryParams())
    .in(FilterAndOrder.queryParams)
    .out(
      jsonBody[PagedResponse[CopyrightHolder]].example(
        Examples.PagedResponse.fromSlice(Chunk("DaSCH", "University of Zurich").map(CopyrightHolder.unsafeFrom)),
      ),
    )
    .description(s"Get the allowed copyright holders for use within this project. $memberRequirement")

  val postProjectCopyrightHolders = baseEndpoints
    .projectAdminPolicyEndpoint(pathProjectShortcode)
    .post
    .in(base / "copyright-holders")
    .in(
      jsonBody[CopyrightHolderAddRequest]
        .example(CopyrightHolderAddRequest(Set("DaSCH", "University of Zurich").map(CopyrightHolder.unsafeFrom))),
    )
    .description(s"Add new allowed copyright holders for use within this project. $adminRequirement")

  val putProjectCopyrightHolders = baseEndpoints.systemAdminPolicyEndpoint.put
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
        Policy.SystemAdmin.requirement,
    )

  val putProjectLegalInfoResourceSide = baseEndpoints
    .projectAdminPolicyEndpoint(pathProjectShortcode)
    .put
    .in(base / "resource")
    .in(jsonBody[ResourceSideLegalInfo].example(ResourceSideLegalInfo.example))
    .out(jsonBody[ResourceSideLegalInfo].example(ResourceSideLegalInfo.example))
    .description(
      "Set the resource-side (data) legal info for this project: the data license (Creative Commons only), " +
        "the copyright holder and the default authorship, each applied to every resource record in the project. " +
        s"Returns the saved values. $adminRequirement",
    )
}

object ProjectsLegalInfoEndpoints {
  val layer = ZLayer.derive[ProjectsLegalInfoEndpoints]
}
