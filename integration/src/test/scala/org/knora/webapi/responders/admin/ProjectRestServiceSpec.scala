/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * To be able to test UsersResponder, we need to be able to start UsersResponder isolated. Now the UsersResponder
 * extend ResponderADM which messes up testing, as we cannot inject the TestActor system.
 */
package org.knora.webapi.responders.admin

import org.apache.pekko.testkit.ImplicitSender
import zio.ZIO

import dsp.errors.DuplicateValueException
import dsp.errors.NotFoundException
import dsp.valueobjects.Iri
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectCreateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectUpdateRequest
import org.knora.webapi.slice.admin.api.service.ProjectRestService
import org.knora.webapi.slice.admin.domain.model.KnoraProject._
import org.knora.webapi.slice.admin.domain.model.ObjectAccessPermission
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.util.MutableTestIri
import org.knora.webapi.util.ZioScalaTestUtil.assertFailsWithA

/**
 * This spec is used to test the messages received by the [[ProjectsResponderADM]] actor.
 */
class ProjectRestServiceSpec extends CoreSpec with ImplicitSender {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getInitializedTestInstance

  private val notExistingProjectButValidProjectIri = "http://rdfh.ch/projects/notexisting"

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
          ProjectRestService(_.findById(SharedTestDataADM.incunabulaProject.projectIri)),
        )
        assert(actual == ProjectGetResponse(toExternal(SharedTestDataADM.incunabulaProject)))
      }

      "return information about a project identified by shortname" in {
        val actual = UnsafeZioRun.runOrThrow(
          ProjectRestService(_.findByShortname(SharedTestDataADM.incunabulaProject.getShortname)),
        )
        assert(actual == ProjectGetResponse(toExternal(SharedTestDataADM.incunabulaProject)))
      }

      "return 'NotFoundException' when the project IRI is unknown" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(_.findById(ProjectIri.unsafeFrom(notExistingProjectButValidProjectIri))),
        )
        assertFailsWithA[NotFoundException](exit, s"Project '$notExistingProjectButValidProjectIri' not found.")
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
            _.getProjectRestrictedViewSettings(IriIdentifier.unsafeFrom(SharedTestDataADM.imagesProject.id)),
          ),
        )
        actual shouldEqual expectedResult
      }

      "return restricted view settings using project SHORTNAME" in {
        val actual = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.getProjectRestrictedViewSettings(
              ShortnameIdentifier.unsafeFrom(SharedTestDataADM.imagesProject.shortname),
            ),
          ),
        )
        actual shouldEqual expectedResult
      }

      "return restricted view settings using project SHORTCODE" in {
        val actual = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.getProjectRestrictedViewSettings(
              ShortcodeIdentifier.unsafeFrom(SharedTestDataADM.imagesProject.shortcode),
            ),
          ),
        )
        actual shouldEqual expectedResult
      }

      "return 'NotFoundException' when the project IRI is unknown" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(
            _.getProjectRestrictedViewSettings(
              IriIdentifier.unsafeFrom(notExistingProjectButValidProjectIri),
            ),
          ),
        )
        assertFailsWithA[NotFoundException](exit, s"Project '$notExistingProjectButValidProjectIri' not found.")
      }

      "return 'NotFoundException' when the project SHORTCODE is unknown" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(_.getProjectRestrictedViewSettings(ShortcodeIdentifier.unsafeFrom("9999"))),
        )
        assertFailsWithA[NotFoundException](exit, s"Project '9999' not found.")
      }

      "return 'NotFoundException' when the project SHORTNAME is unknown" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(
            _.getProjectRestrictedViewSettings(
              ShortnameIdentifier.unsafeFrom("wrongshortname"),
            ),
          ),
        )
        assertFailsWithA[NotFoundException](exit, s"Project 'wrongshortname' not found.")
      }
    }

    "used to modify project information" should {
      val newProjectIri = new MutableTestIri

      "CREATE a project and return the project info if the supplied shortname is unique" in {
        val shortcode = "111c"
        val received = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.createProject(
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
              SharedTestDataADM.rootUser,
            ),
          ),
        )

        received.project.shortname should be("newproject")
        received.project.shortcode should be(shortcode.toUpperCase) // upper case
        received.project.longname should contain("project longname")
        received.project.description should be(
          Seq(StringLiteralV2.from(value = "project description", language = Some("en"))),
        )

        newProjectIri.set(received.project.id)

        // Check Administrative Permissions
        val receivedApAdmin =
          UnsafeZioRun.runOrThrow(
            ZIO.serviceWithZIO[PermissionsResponderADM](_.getPermissionsApByProjectIri(received.project.id)),
          )

        val hasAPForProjectAdmin = receivedApAdmin.administrativePermissions.filter {
          (ap: AdministrativePermissionADM) =>
            ap.forProject == received.project.id && ap.forGroup == OntologyConstants.KnoraAdmin.ProjectAdmin &&
            ap.hasPermissions.equals(
              Set(PermissionADM.ProjectAdminAllPermission, PermissionADM.ProjectResourceCreateAllPermission),
            )
        }

        hasAPForProjectAdmin.size shouldBe 1

        // Check Administrative Permission of ProjectMember
        val hasAPForProjectMember = receivedApAdmin.administrativePermissions.filter {
          (ap: AdministrativePermissionADM) =>
            ap.forProject == received.project.id && ap.forGroup == OntologyConstants.KnoraAdmin.ProjectMember &&
            ap.hasPermissions.equals(Set(PermissionADM.ProjectResourceCreateAllPermission))
        }
        hasAPForProjectMember.size shouldBe 1

        // Check Default Object Access permissions
        val receivedDoaps = UnsafeZioRun.runOrThrow(
          ZIO.serviceWithZIO[PermissionsResponderADM](
            _.getPermissionsDaopByProjectIri(ProjectIri.unsafeFrom(received.project.id)),
          ),
        )

        // Check Default Object Access permission of ProjectAdmin
        val hasDOAPForProjectAdmin = receivedDoaps.defaultObjectAccessPermissions.filter {
          (doap: DefaultObjectAccessPermissionADM) =>
            doap.forProject == received.project.id && doap.forGroup.contains(
              OntologyConstants.KnoraAdmin.ProjectAdmin,
            ) &&
            doap.hasPermissions.equals(
              Set(
                PermissionADM.from(ObjectAccessPermission.ChangeRights, OntologyConstants.KnoraAdmin.ProjectAdmin),
                PermissionADM.from(ObjectAccessPermission.Modify, OntologyConstants.KnoraAdmin.ProjectMember),
              ),
            )
        }
        hasDOAPForProjectAdmin.size shouldBe 1

        // Check Default Object Access permission of ProjectMember
        val hasDOAPForProjectMember = receivedDoaps.defaultObjectAccessPermissions.filter {
          (doap: DefaultObjectAccessPermissionADM) =>
            doap.forProject == received.project.id && doap.forGroup.contains(
              OntologyConstants.KnoraAdmin.ProjectMember,
            ) &&
            doap.hasPermissions.equals(
              Set(
                PermissionADM.from(ObjectAccessPermission.ChangeRights, OntologyConstants.KnoraAdmin.ProjectAdmin),
                PermissionADM.from(ObjectAccessPermission.Modify, OntologyConstants.KnoraAdmin.ProjectMember),
              ),
            )
        }
        hasDOAPForProjectMember.size shouldBe 1
      }

      "CREATE a project and return the project info if the supplied shortname and shortcode is unique" in {
        val received = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.createProject(
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
              SharedTestDataADM.rootUser,
            ),
          ),
        )

        received.project.shortname should be("newproject2")
        received.project.shortcode should be("1112")
        received.project.longname should contain("project longname")
        received.project.description should be(
          Seq(StringLiteralV2.from(value = "project description", language = Some("en"))),
        )

      }

      "CREATE a project that its info has special characters" in {

        val longnameWithSpecialCharacter    = "New \\\"Longname\\\""
        val descriptionWithSpecialCharacter = "project \\\"description\\\""
        val keywordWithSpecialCharacter     = "new \\\"keyword\\\""
        val received = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.createProject(
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
              SharedTestDataADM.rootUser,
            ),
          ),
        )

        received.project.longname should contain(Iri.fromSparqlEncodedString(longnameWithSpecialCharacter))
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
            _.createProject(
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
              SharedTestDataADM.rootUser,
            ),
          ),
        )
        assertFailsWithA[DuplicateValueException](exit, s"Project with the shortname: 'newproject' already exists")
      }

      "return a 'DuplicateValueException' during creation if the supplied project shortname is unique but the shortcode is not" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(
            _.createProject(
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
              SharedTestDataADM.rootUser,
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
                shortname = None,
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
        received.project.shortname should be("newproject")
        received.project.shortcode should be("111C")
        received.project.longname should be(Some("updated project longname"))
        received.project.description should be(
          Seq(
            StringLiteralV2.from(
              value = """updated project description with "quotes" and <html tags>""",
              language = Some("en"),
            ),
          ),
        )
        received.project.keywords.sorted should be(Seq("updated", "keywords").sorted)
        received.project.logo should be(Some("/fu/bar/baz-updated.jpg"))
        received.project.status should be(true)
        received.project.selfjoin should be(true)
      }

      "return 'NotFound' if a not existing project IRI is submitted during update" in {
        val longname = Longname.unsafeFrom("longname")
        val iri      = ProjectIri.unsafeFrom(notExistingProjectButValidProjectIri)
        val exit = UnsafeZioRun.run(
          ProjectRestService(
            _.updateProject(
              iri,
              ProjectUpdateRequest(longname = Some(longname)),
              SharedTestDataADM.rootUser,
            ),
          ),
        )

        assertFailsWithA[NotFoundException](exit, s"Project '$notExistingProjectButValidProjectIri' not found.")
      }
    }

    "used to query members" should {
      "return all members of a project identified by IRI" in {
        val actual = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.getProjectMembers(
              SharedTestDataADM.rootUser,
              IriIdentifier.unsafeFrom(SharedTestDataADM.imagesProject.id),
            ),
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
            _.getProjectMembers(
              SharedTestDataADM.rootUser,
              ShortnameIdentifier.unsafeFrom(SharedTestDataADM.imagesProject.shortname),
            ),
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
            _.getProjectMembers(
              SharedTestDataADM.rootUser,
              ShortcodeIdentifier.unsafeFrom(SharedTestDataADM.imagesProject.shortcode),
            ),
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

      "return 'NotFound' when the project IRI is unknown (project membership)" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(
            _.getProjectMembers(
              SharedTestDataADM.rootUser,
              IriIdentifier.unsafeFrom(notExistingProjectButValidProjectIri),
            ),
          ),
        )
        assertFailsWithA[NotFoundException](exit, s"Project '$notExistingProjectButValidProjectIri' not found.")
      }

      "return 'NotFound' when the project shortname is unknown (project membership)" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(
            _.getProjectMembers(
              SharedTestDataADM.rootUser,
              ShortnameIdentifier.unsafeFrom("wrongshortname"),
            ),
          ),
        )
        assertFailsWithA[NotFoundException](exit, s"Project 'wrongshortname' not found.")
      }

      "return 'NotFound' when the project shortcode is unknown (project membership)" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(
            _.getProjectMembers(
              SharedTestDataADM.rootUser,
              ShortcodeIdentifier.unsafeFrom("9999"),
            ),
          ),
        )
        assertFailsWithA[NotFoundException](exit, s"Project '9999' not found.")
      }

      "return all project admin members of a project identified by IRI" in {
        val received = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.getProjectAdminMembers(
              SharedTestDataADM.rootUser,
              IriIdentifier.unsafeFrom(SharedTestDataADM.imagesProject.id),
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

      "return all project admin members of a project identified by shortname" in {
        val received = UnsafeZioRun.runOrThrow(
          ProjectRestService(
            _.getProjectAdminMembers(
              SharedTestDataADM.rootUser,
              ShortnameIdentifier.unsafeFrom(SharedTestDataADM.imagesProject.shortname),
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
            _.getProjectAdminMembers(
              SharedTestDataADM.rootUser,
              ShortcodeIdentifier.unsafeFrom(SharedTestDataADM.imagesProject.shortcode),
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

      "return 'NotFound' when the project IRI is unknown (project admin membership)" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(
            _.getProjectAdminMembers(
              SharedTestDataADM.rootUser,
              IriIdentifier.unsafeFrom(notExistingProjectButValidProjectIri),
            ),
          ),
        )

        assertFailsWithA[NotFoundException](exit, s"Project '$notExistingProjectButValidProjectIri' not found.")
      }

      "return 'NotFound' when the project shortname is unknown (project admin membership)" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(
            _.getProjectAdminMembers(
              SharedTestDataADM.rootUser,
              ShortnameIdentifier.unsafeFrom("wrongshortname"),
            ),
          ),
        )
        assertFailsWithA[NotFoundException](exit, s"Project 'wrongshortname' not found.")
      }

      "return 'NotFound' when the project shortcode is unknown (project admin membership)" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(
            _.getProjectMembers(
              SharedTestDataADM.rootUser,
              ShortcodeIdentifier.unsafeFrom("9999"),
            ),
          ),
        )
        assertFailsWithA[NotFoundException](exit, s"Project '9999' not found.")
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
            _.getKeywordsByProjectIri(SharedTestDataADM.incunabulaProject.projectIri),
          ),
        )
        received.keywords should be(SharedTestDataADM.incunabulaProject.keywords)
      }

      "return empty list for a project without keywords" in {
        val received = UnsafeZioRun.runOrThrow(
          ProjectRestService(_.getKeywordsByProjectIri(SharedTestDataADM.dokubibProject.projectIri)),
        )
        received.keywords should be(Seq.empty[String])
      }

      "return 'NotFound' when the project IRI is unknown" in {
        val exit = UnsafeZioRun.run(
          ProjectRestService(
            _.getKeywordsByProjectIri(ProjectIri.unsafeFrom(notExistingProjectButValidProjectIri)),
          ),
        )
        assertFailsWithA[NotFoundException](exit, s"Project '$notExistingProjectButValidProjectIri' not found.")
      }
    }
  }
}
