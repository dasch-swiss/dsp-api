package org.knora.webapi.slice.admin.domain.service

import zio.Chunk
import zio.ZIO
import zio.prelude.ForEachOps

import dsp.valueobjects.LanguageCode
import org.knora.webapi.CoreSpec
import org.knora.webapi.IRI
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.imagesProjectIri
import org.knora.webapi.sharedtestdata.SharedTestDataADM2
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.FamilyName
import org.knora.webapi.slice.admin.domain.model.GivenName
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.PasswordHash
import org.knora.webapi.slice.admin.domain.model.SystemAdmin
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.UserStatus
import org.knora.webapi.slice.admin.domain.model.Username

class KnoraUserToUserConverterSpec extends CoreSpec {

  private def createDummyUser(
    isInProject: Iterable[IRI],
    isInGroup: Iterable[IRI],
    systemAdmin: SystemAdmin,
    isInProjectAdminGroup: Iterable[IRI],
  ) = KnoraUser(
    UserIri.unsafeFrom("http://rdfh.ch/users/dummy"),
    Username.unsafeFrom("dummy"),
    Email.unsafeFrom("dummy@example.com"),
    FamilyName.unsafeFrom("dummy"),
    GivenName.unsafeFrom("dummy"),
    PasswordHash.unsafeFrom("dummy"),
    LanguageCode.en,
    UserStatus.Active,
    isInProject.map(ProjectIri.unsafeFrom).toChunk,
    isInGroup.map(GroupIri.unsafeFrom).toChunk,
    systemAdmin,
    isInProjectAdminGroup.map(ProjectIri.unsafeFrom).toChunk,
  )

  val KnoraUserToUserConverter = ZIO.serviceWithZIO[KnoraUserToUserConverter]

  "The PermissionsResponderADM" when {
    "given a KnoraUser" should {

      "return the permissions profile (root user)" in {
        val actual = UnsafeZioRun.runOrThrow(
          KnoraUserToUserConverter(
            _.toUser(
              createDummyUser(
                SharedTestDataADM2.rootUser.projects_info.keys,
                SharedTestDataADM2.rootUser.groups,
                SystemAdmin.IsSystemAdmin,
                Chunk.empty,
              ),
            ),
          ),
        )

        assert(actual.permissions == SharedTestDataADM2.rootUser.permissionData)
      }

      "return the permissions profile (multi group user)" in {
        val actual = UnsafeZioRun.runOrThrow(
          KnoraUserToUserConverter(
            _.toUser(
              createDummyUser(
                SharedTestDataADM2.multiuserUser.projects_info.keys,
                SharedTestDataADM2.multiuserUser.groups,
                SystemAdmin.IsNotSystemAdmin,
                Chunk(SharedTestDataADM.incunabulaProjectIri, imagesProjectIri),
              ),
            ),
          ),
        )

        assert(actual.permissions == SharedTestDataADM2.multiuserUser.permissionData)
      }

      "return the permissions profile (incunabula project admin user)" in {
        val actual = UnsafeZioRun.runOrThrow(
          KnoraUserToUserConverter(
            _.toUser(
              createDummyUser(
                SharedTestDataADM2.incunabulaProjectAdminUser.projects_info.keys,
                SharedTestDataADM2.incunabulaProjectAdminUser.groups,
                SystemAdmin.IsNotSystemAdmin,
                Chunk(SharedTestDataADM.incunabulaProjectIri),
              ),
            ),
          ),
        )

        assert(actual.permissions == SharedTestDataADM2.incunabulaProjectAdminUser.permissionData)
      }

      "return the permissions profile (incunabula creator user)" in {
        val actual = UnsafeZioRun.runOrThrow(
          KnoraUserToUserConverter(
            _.toUser(
              createDummyUser(
                SharedTestDataADM2.incunabulaProjectAdminUser.projects_info.keys,
                SharedTestDataADM2.incunabulaCreatorUser.groups,
                SystemAdmin.IsNotSystemAdmin,
                Chunk.empty,
              ),
            ),
          ),
        )

        assert(actual.permissions == SharedTestDataADM2.incunabulaCreatorUser.permissionData)
      }

      "return the permissions profile (incunabula normal project member user)" in {
        val actual = UnsafeZioRun.runOrThrow(
          KnoraUserToUserConverter(
            _.toUser(
              createDummyUser(
                SharedTestDataADM2.incunabulaProjectAdminUser.projects_info.keys,
                SharedTestDataADM2.incunabulaMemberUser.groups,
                SystemAdmin.IsNotSystemAdmin,
                Chunk.empty,
              ),
            ),
          ),
        )

        assert(actual.permissions == SharedTestDataADM2.incunabulaMemberUser.permissionData)
      }

      "return the permissions profile (images user 01)" in {
        val actual = UnsafeZioRun.runOrThrow(
          KnoraUserToUserConverter(
            _.toUser(
              createDummyUser(
                SharedTestDataADM2.imagesUser01.projects_info.keys,
                SharedTestDataADM2.imagesUser01.groups,
                SystemAdmin.IsNotSystemAdmin,
                Chunk(imagesProjectIri),
              ),
            ),
          ),
        )

        assert(actual.permissions == SharedTestDataADM2.imagesUser01.permissionData)
      }

      "return the permissions profile (images-reviewer-user)" in {
        val actual = UnsafeZioRun.runOrThrow(
          KnoraUserToUserConverter(
            _.toUser(
              createDummyUser(
                SharedTestDataADM2.imagesReviewerUser.projects_info.keys,
                SharedTestDataADM2.imagesReviewerUser.groups,
                SystemAdmin.IsNotSystemAdmin,
                Chunk.empty,
              ),
            ),
          ),
        )

        assert(actual.permissions == SharedTestDataADM2.imagesReviewerUser.permissionData)
      }

      "return the permissions profile (anything user 01)" in {
        val actual = UnsafeZioRun.runOrThrow(
          KnoraUserToUserConverter(
            _.toUser(
              createDummyUser(
                SharedTestDataADM2.anythingUser1.projects_info.keys,
                SharedTestDataADM2.anythingUser1.groups,
                SystemAdmin.IsNotSystemAdmin,
                Chunk.empty,
              ),
            ),
          ),
        )

        assert(actual.permissions == SharedTestDataADM2.anythingUser1.permissionData)
      }
    }
  }
}
