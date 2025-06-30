/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import org.apache.pekko.testkit.ImplicitSender
import zio.ZIO

import dsp.errors.DuplicateValueException
import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import dsp.valueobjects.Iri
import org.knora.webapi.*
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages.*
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.api.model.*
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectCreateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectUpdateRequest
import org.knora.webapi.slice.admin.api.service.ProjectRestService
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.util.MutableTestIri
import org.knora.webapi.util.ZioScalaTestUtil.assertFailsWithA

/**
 * This spec is used to test the messages received by the [[ProjectsResponderADM]] actor.
 */
class ProjectRestServiceSpec extends E2ESpec with ImplicitSender {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getInitializedTestInstance

  private val notExistingProjectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/notexisting")

  private val ProjectRestService = ZIO.serviceWithZIO[ProjectRestService]

  private def toExternal(project: Project) =
    project.copy(ontologies =
      project.ontologies.map((iri: String) => iri.toSmartIri.toOntologySchema(ApiV2Complex).toString),
    )

  "The ProjectRestService" when {
    "used to query for project information" should {
      "return information for every project excluding system projects" in {
        val received    = UnsafeZioRun.runOrThrow(ProjectRestService(_.listAllProjects()))
        val projectIris = received.projects.map(_.id)
        assert(projectIris.contains(SharedTestDataADM.imagesProject.id))
        assert(projectIris.contains(SharedTestDataADM.incunabulaProject.id))
        assert(!projectIris.contains(SharedTestDataADM.systemProjectIri))
        assert(!projectIris.contains(SharedTestDataADM.defaultSharedOntologiesProject.id))
      }

      "return information about a project identified by IRI" in {
        val actual = UnsafeZioRun.runOrThrow(
          ProjectRestService(_.findById(SharedTestDataADM.incunabulaProject.id)),
        )
        assert(actual == ProjectGetResponse(toExternal(SharedTestDataADM.incunabulaProject)))
      }

      "return information about a project identified by shortname" in {
        val actual = UnsafeZioRun.runOrThrow(
          ProjectRestService(_.findByShortname(SharedTestDataADM.incunabulaProject.shortname)),
        )
        assert(actual == ProjectGetResponse(toExternal(SharedTestDataADM.incunabulaProject)))
      }

      "return 'NotFoundException' when the project IRI is unknown" in {
        val exit = UnsafeZioRun.run(ProjectRestService(_.findById(notExistingProjectIri)))
        assertFailsWithA[NotFoundException](exit, s"Project '${notExistingProjectIri.value}' not found.")
      }

      "return 'NotFoundException' when the project shortname is unknown" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(_.findByShortname(Shortname.unsafeFrom("wrongshortname"))),
        )
        assertFailsWithA[NotFoundException](exit, s"Project 'wrongshortname' not found.")
      }

      "return 'NotFoundException' when the project shortcode is unknown" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(_.findByShortcode(Shortcode.unsafeFrom("9999"))),
        )
        assertFailsWithA[NotFoundException](exit, s"Project '9999' not found.")
      }
    }

    "used to query project's restricted view settings" should {
      val expectedResult = ProjectRestrictedViewSettingsGetResponseADM.from(RestrictedView.Size.unsafeFrom("!512,512"))

      "return restricted view settings using project IRI" in {
        val actual = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.getProjectRestrictedViewSettingsById(SharedTestDataADM.imagesProject.id),
          ),
        )
        actual shouldEqual expectedResult
      }

      "return restricted view settings using project SHORTNAME" in {
        val actual = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.getProjectRestrictedViewSettingsByShortname(SharedTestDataADM.imagesProject.shortname),
          ),
        )
        actual shouldEqual expectedResult
      }

      "return restricted view settings using project SHORTCODE" in {
        val actual = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.getProjectRestrictedViewSettingsByShortcode(SharedTestDataADM.imagesProject.shortcode),
          ),
        )
        actual shouldEqual expectedResult
      }

      "return 'NotFoundException' when the project IRI is unknown" in {
        val exit = UnsafeZioRun.run(ProjectRestService(_.getProjectRestrictedViewSettingsById(notExistingProjectIri)))
        assertFailsWithA[NotFoundException](exit, s"Project with id ${notExistingProjectIri.value} not found.")
      }

      "return 'NotFoundException' when the project SHORTCODE is unknown" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(_.getProjectRestrictedViewSettingsByShortcode(Shortcode.unsafeFrom("9999"))),
        )
        assertFailsWithA[NotFoundException](exit, s"Project with shortcode 9999 not found.")
      }

      "return 'NotFoundException' when the project SHORTNAME is unknown" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(
            _.getProjectRestrictedViewSettingsByShortname(Shortname.unsafeFrom("wrongshortname")),
          ),
        )
        assertFailsWithA[NotFoundException](exit, s"Project with shortname wrongshortname not found.")
      }
    }

    "used to modify project information" should {
      val newProjectIri = new MutableTestIri

      "CREATE a project and return the project info if the supplied shortname is unique" in {
        val shortcode = "111c"
        val received = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.createProject(SharedTestDataADM.rootUser)(
              ProjectCreateRequest(
                shortname = Shortname.unsafeFrom("newproject"),
                shortcode = Shortcode.unsafeFrom(shortcode),
                longname = Some(Longname.unsafeFrom("project longname")),
                description = List(
                  Description.unsafeFrom(StringLiteralV2.from(value = "project description", language = Some("en"))),
                ),
                keywords = List("keywords").map(Keyword.unsafeFrom),
                logo = Some(Logo.unsafeFrom("/fu/bar/baz.jpg")),
                status = Status.Active,
                selfjoin = SelfJoin.CannotJoin,
              ),
            ),
          ),
        )

        received.project.shortname.value should be("newproject")
        received.project.shortcode.value should be(shortcode.toUpperCase) // upper case
        received.project.longname.map(_.value) should contain("project longname")
        received.project.description should be(
          Seq(StringLiteralV2.from(value = "project description", language = Some("en"))),
        )
        newProjectIri.set(received.project.id.value)

        // Check Administrative Permissions
        val receivedApAdmin =
          UnsafeZioRun.runOrThrow(
            ZIO.serviceWithZIO[PermissionsResponder](_.getPermissionsApByProjectIri(received.project.id.value)),
          )

        val hasAPForProjectAdmin = receivedApAdmin.administrativePermissions.filter {
          (ap: AdministrativePermissionADM) =>
            ap.forProject == received.project.id.value && ap.forGroup == KnoraGroupRepo.builtIn.ProjectAdmin.id.value &&
            ap.hasPermissions.equals(
              Set(
                PermissionADM.from(Permission.Administrative.ProjectAdminAll),
                PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
              ),
            )
        }

        hasAPForProjectAdmin.size shouldBe 1

        // Check Administrative Permission of ProjectMember
        val hasAPForProjectMember = receivedApAdmin.administrativePermissions.filter {
          (ap: AdministrativePermissionADM) =>
            ap.forProject == received.project.id.value && ap.forGroup == KnoraGroupRepo.builtIn.ProjectMember.id.value &&
            ap.hasPermissions.equals(Set(PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll)))
        }
        hasAPForProjectMember.size shouldBe 1

        // Check Default Object Access permissions
        val receivedDoaps = UnsafeZioRun.runOrThrow(
          ZIO.serviceWithZIO[PermissionsResponder](
            _.getPermissionsDaopByProjectIri(received.project.id),
          ),
        )

        // Check Default Object Access permission of ProjectAdmin
        val hasDOAPForProjectAdmin = receivedDoaps.defaultObjectAccessPermissions.filter {
          (doap: DefaultObjectAccessPermissionADM) =>
            doap.forProject == received.project.id.value && doap.forGroup.contains(
              KnoraGroupRepo.builtIn.ProjectAdmin.id.value,
            ) &&
            doap.hasPermissions.equals(
              Set(
                PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
                PermissionADM.from(Permission.ObjectAccess.Delete, KnoraGroupRepo.builtIn.ProjectMember.id.value),
              ),
            )
        }
        hasDOAPForProjectAdmin.size shouldBe 1

        // Check Default Object Access permission of ProjectMember
        val hasDOAPForProjectMember = receivedDoaps.defaultObjectAccessPermissions.filter {
          (doap: DefaultObjectAccessPermissionADM) =>
            doap.forProject == received.project.id.value && doap.forGroup.contains(
              KnoraGroupRepo.builtIn.ProjectMember.id.value,
            ) &&
            doap.hasPermissions.equals(
              Set(
                PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
                PermissionADM.from(Permission.ObjectAccess.Delete, KnoraGroupRepo.builtIn.ProjectMember.id.value),
              ),
            )
        }
        hasDOAPForProjectMember.size shouldBe 1
      }

      "CREATE a project and return the project info if the supplied shortname and shortcode is unique" in {
        val received = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.createProject(SharedTestDataADM.rootUser)(
              ProjectCreateRequest(
                shortname = Shortname.unsafeFrom("newproject2"),
                shortcode = Shortcode.unsafeFrom("1112"),
                longname = Some(Longname.unsafeFrom("project longname")),
                description = List(
                  Description.unsafeFrom(StringLiteralV2.from(value = "project description", language = Some("en"))),
                ),
                keywords = List("keywords").map(Keyword.unsafeFrom),
                logo = Some(Logo.unsafeFrom("/fu/bar/baz.jpg")),
                status = Status.Active,
                selfjoin = SelfJoin.CannotJoin,
              ),
            ),
          ),
        )

        received.project.shortname.value should be("newproject2")
        received.project.shortcode.value should be("1112")
        received.project.longname.map(_.value) should contain("project longname")
        received.project.description should be(
          Seq(StringLiteralV2.from(value = "project description", language = Some("en"))),
        )
      }

      "CREATE a project that its info has special characters" in {
        val longnameWithSpecialCharacter    = """New "Longname""""
        val descriptionWithSpecialCharacter = """project "description""""
        val keywordWithSpecialCharacter     = """new "keyword""""
        val received = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.createProject(SharedTestDataADM.rootUser)(
              ProjectCreateRequest(
                shortname = Shortname.unsafeFrom("project_with_char"),
                shortcode = Shortcode.unsafeFrom("1312"),
                longname = Some(Longname.unsafeFrom(longnameWithSpecialCharacter)),
                description = List(
                  Description.unsafeFrom(
                    StringLiteralV2.from(value = descriptionWithSpecialCharacter, language = Some("en")),
                  ),
                ),
                keywords = List(keywordWithSpecialCharacter).map(Keyword.unsafeFrom),
                logo = Some(Logo.unsafeFrom("/fu/bar/baz.jpg")),
                status = Status.Active,
                selfjoin = SelfJoin.CannotJoin,
              ),
            ),
          ),
        )

        received.project.longname.map(_.value) should contain(Iri.fromSparqlEncodedString(longnameWithSpecialCharacter))
        received.project.description should be(
          Seq(
            StringLiteralV2.from(
              value = Iri.fromSparqlEncodedString(descriptionWithSpecialCharacter),
              language = Some("en"),
            ),
          ),
        )
        received.project.keywords should contain(Iri.fromSparqlEncodedString(keywordWithSpecialCharacter))
      }

      "return a 'DuplicateValueException' during creation if the supplied project shortname is not unique" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(
            _.createProject(SharedTestDataADM.rootUser)(
              ProjectCreateRequest(
                shortname = Shortname.unsafeFrom("newproject"),
                shortcode = Shortcode.unsafeFrom("111D"),
                longname = Some(Longname.unsafeFrom("project longname")),
                description =
                  List(Description.unsafeFrom(StringLiteralV2.from(value = "description", language = Some("en")))),
                keywords = List("keywords").map(Keyword.unsafeFrom),
                logo = Some(Logo.unsafeFrom("/fu/bar/baz.jpg")),
                status = Status.Active,
                selfjoin = SelfJoin.CannotJoin,
              ),
            ),
          ),
        )
        assertFailsWithA[DuplicateValueException](exit, s"Project with the shortname: 'newproject' already exists")
      }

      "return a 'DuplicateValueException' during creation if the supplied project shortname is unique but the shortcode is not" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(
            _.createProject(SharedTestDataADM.rootUser)(
              ProjectCreateRequest(
                shortname = Shortname.unsafeFrom("newproject3"),
                shortcode = Shortcode.unsafeFrom("111C"),
                longname = Some(Longname.unsafeFrom("project longname")),
                description =
                  List(Description.unsafeFrom(StringLiteralV2.from(value = "description", language = Some("en")))),
                keywords = List("keywords").map(Keyword.unsafeFrom),
                logo = Some(Logo.unsafeFrom("/fu/bar/baz.jpg")),
                status = Status.Active,
                selfjoin = SelfJoin.CannotJoin,
              ),
            ),
          ),
        )
        assertFailsWithA[DuplicateValueException](exit, s"Project with the shortcode: '111C' already exists")
      }

      "UPDATE a project" in {
        val iri             = ProjectIri.unsafeFrom(newProjectIri.get)
        val updatedLongname = Longname.unsafeFrom("updated project longname")
        val updatedDescription = List(
          Description.unsafeFrom(
            StringLiteralV2.from("""updated project description with "quotes" and <html tags>""", Some("en")),
          ),
        )
        val updatedKeywords = List("updated", "keywords").map(Keyword.unsafeFrom)
        val updatedLogo     = Logo.unsafeFrom("/fu/bar/baz-updated.jpg")
        val projectStatus   = Status.Active
        val selfJoin        = SelfJoin.CanJoin

        val received = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.updateProject(
              iri,
              ProjectUpdateRequest(
                longname = Some(updatedLongname),
                description = Some(updatedDescription),
                keywords = Some(updatedKeywords),
                logo = Some(updatedLogo),
                status = Some(projectStatus),
                selfjoin = Some(selfJoin),
              ),
              SharedTestDataADM.rootUser,
            ),
          ),
        )
        received.project.shortname.value should be("newproject")
        received.project.shortcode.value should be("111C")
        received.project.longname.map(_.value) should be(Some("updated project longname"))
        received.project.description should be(
          Seq(
            StringLiteralV2.from(
              value = """updated project description with "quotes" and <html tags>""",
              language = Some("en"),
            ),
          ),
        )
        received.project.keywords.sorted should be(Seq("updated", "keywords").sorted)
        received.project.logo.map(_.value) should be(Some("/fu/bar/baz-updated.jpg"))
        received.project.status should be(Status.Active)
        received.project.selfjoin should be(SelfJoin.CanJoin)
      }

      "return 'NotFound' if a not existing project IRI is submitted during update" in {
        val longname = Longname.unsafeFrom("longname")
        val exit = UnsafeZioRun.run(
          ProjectRestService(
            _.updateProject(
              notExistingProjectIri,
              ProjectUpdateRequest(longname = Some(longname)),
              SharedTestDataADM.rootUser,
            ),
          ),
        )

        assertFailsWithA[NotFoundException](exit, s"Project '${notExistingProjectIri.value}' not found.")
      }
    }

    "used to query members" should {
      "return all members of a project identified by IRI" in {
        val actual = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.getProjectMembersById(SharedTestDataADM.rootUser, SharedTestDataADM.imagesProject.id),
          ),
        )

        val members = actual.members
        members.size should be(4)
        members.map(_.id) should contain allElementsOf Seq(
          SharedTestDataADM.imagesUser01.id,
          SharedTestDataADM.imagesUser02.id,
          SharedTestDataADM.multiuserUser.id,
          SharedTestDataADM.imagesReviewerUser.id,
        )
      }

      "return all members of a project identified by shortname" in {
        val actual = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.getProjectMembersByShortname(SharedTestDataADM.rootUser, SharedTestDataADM.imagesProject.shortname),
          ),
        )

        val members = actual.members
        members.size should be(4)
        members.map(_.id) should contain allElementsOf Seq(
          SharedTestDataADM.imagesUser01.id,
          SharedTestDataADM.imagesUser02.id,
          SharedTestDataADM.multiuserUser.id,
          SharedTestDataADM.imagesReviewerUser.id,
        )
      }

      "return all members of a project identified by shortcode" in {
        val actual = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.getProjectMembersByShortcode(SharedTestDataADM.rootUser, SharedTestDataADM.imagesProject.shortcode),
          ),
        )

        val members = actual.members
        members.size should be(4)
        members.map(_.id) should contain allElementsOf Seq(
          SharedTestDataADM.imagesUser01.id,
          SharedTestDataADM.imagesUser02.id,
          SharedTestDataADM.multiuserUser.id,
          SharedTestDataADM.imagesReviewerUser.id,
        )
      }

      "return 'Forbidden' when the project IRI is unknown (project membership)" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(_.getProjectMembersById(SharedTestDataADM.rootUser, notExistingProjectIri)),
        )
        assertFailsWithA[ForbiddenException](exit, s"Project with id ${notExistingProjectIri.value} not found.")
      }

      "return 'Forbidden' when the project shortname is unknown (project membership)" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(
            _.getProjectMembersByShortname(SharedTestDataADM.rootUser, Shortname.unsafeFrom("wrongshortname")),
          ),
        )
        assertFailsWithA[ForbiddenException](exit, s"Project with shortname wrongshortname not found.")
      }

      "return 'Forbidden' when the project shortcode is unknown (project membership)" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(
            _.getProjectMembersByShortcode(SharedTestDataADM.rootUser, Shortcode.unsafeFrom("9999")),
          ),
        )
        assertFailsWithA[ForbiddenException](exit, s"Project with shortcode 9999 not found.")
      }

      "return all project admin members of a project identified by IRI" in {
        val received = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.getProjectAdminMembersById(SharedTestDataADM.rootUser, SharedTestDataADM.imagesProject.id),
          ),
        )

        val members = received.members
        members.size should be(2)
        members.map(_.id) should contain allElementsOf Seq(
          SharedTestDataADM.imagesUser01.id,
          SharedTestDataADM.multiuserUser.id,
        )
      }

      "return all project admin members of a project identified by shortname" in {
        val received = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.getProjectAdminMembersByShortname(
              SharedTestDataADM.rootUser,
              SharedTestDataADM.imagesProject.shortname,
            ),
          ),
        )

        val members = received.members
        members.size should be(2)
        members.map(_.id) should contain allElementsOf Seq(
          SharedTestDataADM.imagesUser01.id,
          SharedTestDataADM.multiuserUser.id,
        )
      }

      "return all project admin members of a project identified by shortcode" in {
        val received = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.getProjectAdminMembersByShortcode(
              SharedTestDataADM.rootUser,
              SharedTestDataADM.imagesProject.shortcode,
            ),
          ),
        )

        val members = received.members
        members.size should be(2)
        members.map(_.id) should contain allElementsOf Seq(
          SharedTestDataADM.imagesUser01.id,
          SharedTestDataADM.multiuserUser.id,
        )
      }

      "return 'Forbidden' when the project IRI is unknown (project admin membership)" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(_.getProjectAdminMembersById(SharedTestDataADM.rootUser, notExistingProjectIri)),
        )
        assertFailsWithA[ForbiddenException](exit, s"Project with id ${notExistingProjectIri.value} not found.")
      }

      "return 'Forbidden' when the project shortname is unknown (project admin membership)" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(
            _.getProjectAdminMembersByShortname(SharedTestDataADM.rootUser, Shortname.unsafeFrom("wrongshortname")),
          ),
        )
        assertFailsWithA[ForbiddenException](exit, s"Project with shortname wrongshortname not found.")
      }

      "return 'Forbidden' when the project shortcode is unknown (project admin membership)" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(
            _.getProjectMembersByShortcode(SharedTestDataADM.rootUser, Shortcode.unsafeFrom("9999")),
          ),
        )
        assertFailsWithA[ForbiddenException](exit, s"Project with shortcode 9999 not found.")
      }
    }

    "used to query keywords" should {
      "return all unique keywords for all projects" in {
        val received = UnsafeZioRun.runOrThrow(ProjectRestService(_.listAllKeywords()))
        received.keywords.size should be(21)
      }

      "return all keywords for a single project" in {
        val received = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.getKeywordsByProjectIri(SharedTestDataADM.incunabulaProject.id),
          ),
        )
        received.keywords should be(SharedTestDataADM.incunabulaProject.keywords)
      }

      "return empty list for a project without keywords" in {
        val received = UnsafeZioRun.runOrThrow(
          ProjectRestService(_.getKeywordsByProjectIri(SharedTestDataADM.dokubibProject.id)),
        )
        received.keywords should be(Seq.empty[String])
      }

      "return 'NotFound' when the project IRI is unknown" in {
        val exit = UnsafeZioRun.run(ProjectRestService(_.getKeywordsByProjectIri(notExistingProjectIri)))
        assertFailsWithA[NotFoundException](exit, s"Project '${notExistingProjectIri.value}' not found.")
      }
    }
  }
}
