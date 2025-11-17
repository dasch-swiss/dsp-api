/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio.*
import zio.test.*
import zio.test.Assertion.*

import dsp.errors.DuplicateValueException
import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import dsp.valueobjects.Iri
import org.knora.webapi.*
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages.*
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.api.model.*
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectCreateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectUpdateRequest
import org.knora.webapi.slice.admin.api.service.ProjectRestService
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.common.domain.LanguageCode.EN
import org.knora.webapi.util.MutableTestIri

object ProjectRestServiceSpec extends E2EZSpec {

  private val projectRestService = ZIO.serviceWithZIO[ProjectRestService]

  private implicit val stringFormatter: StringFormatter = StringFormatter.getInitializedTestInstance

  private val newProjectIri         = new MutableTestIri
  private val notExistingProjectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/notexisting")

  private def toExternal(project: Project) =
    project.copy(ontologies =
      project.ontologies.map((iri: String) => iri.toSmartIri.toOntologySchema(ApiV2Complex).toString),
    )

  override val e2eSpec = suite("The ProjectRestService")(
    suite("used to query for project information")(
      test("return information for every project excluding system projects") {
        projectRestService(_.listAllProjects(())).map { received =>
          val projectIris = received.projects.map(_.id)
          assertTrue(
            projectIris.contains(imagesProject.id),
            projectIris.contains(incunabulaProject.id),
            !projectIris.contains(systemProjectIri),
            !projectIris.contains(defaultSharedOntologiesProject.id),
          )
        }
      },
      test("return information about a project identified by IRI") {
        projectRestService(_.findById(incunabulaProject.id)).map(actual =>
          assertTrue(actual == ProjectGetResponse(toExternal(incunabulaProject))),
        )
      },
      test("return information about a project identified by shortname") {
        projectRestService(_.findByShortname(incunabulaProject.shortname)).map(actual =>
          assertTrue(actual == ProjectGetResponse(toExternal(incunabulaProject))),
        )
      },
      test("return 'NotFoundException' when the project IRI is unknown") {
        projectRestService(_.findById(notExistingProjectIri)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[NotFoundException](s"Project '$notExistingProjectIri' not found."),
          ),
        )
      },
      test("return 'NotFoundException' when the project shortname is unknown") {
        projectRestService(_.findByShortname(Shortname.unsafeFrom("wrongshortname"))).exit.map(
          assert(_)(E2EZSpec.failsWithMessageEqualTo[NotFoundException](s"Project 'wrongshortname' not found.")),
        )
      },
      test("return 'NotFoundException' when the project shortcode is unknown") {
        projectRestService(_.findByShortcode(Shortcode.unsafeFrom("9999"))).exit.map(
          assert(_)(E2EZSpec.failsWithMessageEqualTo[NotFoundException](s"Project '9999' not found.")),
        )
      },
    ),
    suite("used to query project's restricted view settings")(
      test("return restricted view settings using project IRI") {
        projectRestService(_.getProjectRestrictedViewSettingsById(imagesProject.id)).map(actual =>
          assertTrue(
            actual == ProjectRestrictedViewSettingsGetResponseADM.from(RestrictedView.Size.unsafeFrom("!512,512")),
          ),
        )
      },
      test("return restricted view settings using project SHORTNAME") {
        projectRestService(_.getProjectRestrictedViewSettingsByShortname(imagesProject.shortname)).map(actual =>
          assertTrue(
            actual == ProjectRestrictedViewSettingsGetResponseADM.from(RestrictedView.Size.unsafeFrom("!512,512")),
          ),
        )
      },
      test("return restricted view settings using project SHORTCODE") {
        projectRestService(_.getProjectRestrictedViewSettingsByShortcode(imagesProject.shortcode)).map(actual =>
          assertTrue(
            actual == ProjectRestrictedViewSettingsGetResponseADM.from(RestrictedView.Size.unsafeFrom("!512,512")),
          ),
        )
      },
      test("return 'NotFoundException' when the project IRI is unknown") {
        projectRestService(_.getProjectRestrictedViewSettingsById(notExistingProjectIri)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[NotFoundException](s"Project with id $notExistingProjectIri not found."),
          ),
        )
      },
      test("return 'NotFoundException' when the project SHORTCODE is unknown") {
        projectRestService(_.getProjectRestrictedViewSettingsByShortcode(Shortcode.unsafeFrom("9999"))).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[NotFoundException](s"Project with shortcode 9999 not found."),
          ),
        )
      },
      test("return 'NotFoundException' when the project SHORTNAME is unknown") {
        projectRestService(
          _.getProjectRestrictedViewSettingsByShortname(Shortname.unsafeFrom("wrongshortname")),
        ).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[NotFoundException](s"Project with shortname wrongshortname not found."),
          ),
        )
      },
    ),
    suite("used to modify project information")(
      test("CREATE a project and return the project info if the supplied shortname is unique") {
        val shortcode = "111c"
        val createReq = ProjectCreateRequest(
          None,
          Shortname.unsafeFrom("newproject"),
          Shortcode.unsafeFrom(shortcode),
          Some(Longname.unsafeFrom("project longname")),
          List(Description.unsafeFrom(StringLiteralV2.from("project description", EN))),
          List(Keyword.unsafeFrom("keywords")),
          Some(Logo.unsafeFrom("/fu/bar/baz.jpg")),
          Status.Active,
          SelfJoin.CannotJoin,
        )
        for {
          project <- projectRestService(_.createProject(rootUser)(createReq)).map(_.project)
          _        = newProjectIri.set(project.id.value)
          // Check Administrative Permissions
          receivedApAdmin <- ZIO.serviceWithZIO[PermissionsResponder](_.getPermissionsApByProjectIri(project.id.value))
          // Check Default Object Access permissions
          receivedDoaps <- ZIO.serviceWithZIO[PermissionsResponder](_.getPermissionsDaopByProjectIri(project.id))
        } yield {
          val hasAPForProjectAdmin =
            receivedApAdmin.administrativePermissions.filter { (ap: AdministrativePermissionADM) =>
              ap.forProject == project.id.value && ap.forGroup == KnoraGroupRepo.builtIn.ProjectAdmin.id.value &&
              ap.hasPermissions.equals(
                Set(
                  PermissionADM.from(Permission.Administrative.ProjectAdminAll),
                  PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
                ),
              )
            }
          // Check Administrative Permission of ProjectMember
          val hasAPForProjectMember =
            receivedApAdmin.administrativePermissions.filter { (ap: AdministrativePermissionADM) =>
              ap.forProject == project.id.value && ap.forGroup == KnoraGroupRepo.builtIn.ProjectMember.id.value &&
              ap.hasPermissions.equals(Set(PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll)))
            }

          // Check Default Object Access permission of ProjectAdmin
          val hasDOAPForProjectAdmin =
            receivedDoaps.defaultObjectAccessPermissions.filter { (doap: DefaultObjectAccessPermissionADM) =>
              doap.forProject == project.id.value && doap.forGroup.contains(
                KnoraGroupRepo.builtIn.ProjectAdmin.id.value,
              ) &&
              doap.hasPermissions.equals(
                Set(
                  PermissionADM
                    .from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
                  PermissionADM.from(Permission.ObjectAccess.Delete, KnoraGroupRepo.builtIn.ProjectMember.id.value),
                ),
              )
            }

          // Check Default Object Access permission of ProjectMember
          val hasDOAPForProjectMember =
            receivedDoaps.defaultObjectAccessPermissions.filter { (doap: DefaultObjectAccessPermissionADM) =>
              doap.forProject == project.id.value && doap.forGroup.contains(
                KnoraGroupRepo.builtIn.ProjectMember.id.value,
              ) &&
              doap.hasPermissions.equals(
                Set(
                  PermissionADM
                    .from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
                  PermissionADM.from(Permission.ObjectAccess.Delete, KnoraGroupRepo.builtIn.ProjectMember.id.value),
                ),
              )
            }
          assertTrue(
            project.shortname.value == "newproject",
            project.shortcode.value == shortcode.toUpperCase,
            project.longname.map(_.value).contains("project longname"),
            project.description == Seq(StringLiteralV2.from(value = "project description", EN)),
            hasAPForProjectAdmin.size == 1,
            hasAPForProjectMember.size == 1,
            hasDOAPForProjectAdmin.size == 1,
            hasDOAPForProjectMember.size == 1,
          )
        }
      },
      test("CREATE a project and return the project info if the supplied shortname and shortcode is unique") {
        val createRequest = ProjectCreateRequest(
          None,
          Shortname.unsafeFrom("newproject2"),
          Shortcode.unsafeFrom("1112"),
          Some(Longname.unsafeFrom("project longname")),
          List(Description.unsafeFrom(StringLiteralV2.from("project description", EN))),
          List(Keyword.unsafeFrom("keywords")),
          Some(Logo.unsafeFrom("/fu/bar/baz.jpg")),
          Status.Active,
          SelfJoin.CannotJoin,
        )
        projectRestService(_.createProject(rootUser)(createRequest))
          .map(_.project)
          .map(project =>
            assertTrue(
              project.shortname.value == "newproject2",
              project.shortcode.value == "1112",
              project.longname.map(_.value).contains("project longname"),
              project.description == Seq(StringLiteralV2.from("project description", EN)),
            ),
          )
      },
      test("CREATE a project that its info has special characters") {
        val longnameWithSpecialCharacter    = """New "Longname""""
        val descriptionWithSpecialCharacter = """project "description""""
        val keywordWithSpecialCharacter     = """new "keyword""""
        val createRequest = ProjectCreateRequest(
          None,
          Shortname.unsafeFrom("project_with_char"),
          Shortcode.unsafeFrom("1312"),
          Some(Longname.unsafeFrom(longnameWithSpecialCharacter)),
          List(
            Description.unsafeFrom(
              StringLiteralV2.from(value = descriptionWithSpecialCharacter, EN),
            ),
          ),
          List(keywordWithSpecialCharacter).map(Keyword.unsafeFrom),
          Some(Logo.unsafeFrom("/fu/bar/baz.jpg")),
          Status.Active,
          SelfJoin.CannotJoin,
        )
        projectRestService(_.createProject(rootUser)(createRequest))
          .map(received =>
            assertTrue(
              received.project.longname
                .map(_.value)
                .contains(Iri.fromSparqlEncodedString(longnameWithSpecialCharacter)),
              received.project.description ==
                Seq(
                  StringLiteralV2.from(
                    Iri.fromSparqlEncodedString(descriptionWithSpecialCharacter),
                    EN,
                  ),
                ),
              received.project.keywords.contains(Iri.fromSparqlEncodedString(keywordWithSpecialCharacter)),
            ),
          )
      },
      test("return a 'DuplicateValueException' during creation if the supplied project shortname is not unique") {
        val createRequest = ProjectCreateRequest(
          None,
          Shortname.unsafeFrom("newproject"),
          Shortcode.unsafeFrom("111D"),
          Some(Longname.unsafeFrom("project longname")),
          List(Description.unsafeFrom(StringLiteralV2.from(value = "description", EN))),
          List("keywords").map(Keyword.unsafeFrom),
          Some(Logo.unsafeFrom("/fu/bar/baz.jpg")),
          Status.Active,
          SelfJoin.CannotJoin,
        )
        projectRestService(_.createProject(rootUser)(createRequest)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageContaining[DuplicateValueException](
              "Project with the shortname: 'newproject' already exists",
            ),
          ),
        )
      },
      test(
        "return a 'DuplicateValueException' during creation if the supplied project shortname is unique but the shortcode is not",
      ) {
        val createRequest = ProjectCreateRequest(
          None,
          Shortname.unsafeFrom("newproject3"),
          Shortcode.unsafeFrom("111C"),
          Some(Longname.unsafeFrom("project longname")),
          List(Description.unsafeFrom(StringLiteralV2.from(value = "description", EN))),
          List("keywords").map(Keyword.unsafeFrom),
          Some(Logo.unsafeFrom("/fu/bar/baz.jpg")),
          Status.Active,
          SelfJoin.CannotJoin,
        )
        projectRestService(_.createProject(rootUser)(createRequest)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageContaining[DuplicateValueException](
              "Project with the shortcode: '111C' already exists",
            ),
          ),
        )
      },
      test("UPDATE a project") {
        val updateRequest = ProjectUpdateRequest(
          Some(Longname.unsafeFrom("updated project longname")),
          Some(
            List(
              Description.unsafeFrom(
                StringLiteralV2.from("""updated project description with "quotes" and <html tags>""", EN),
              ),
            ),
          ),
          Some(List("updated", "keywords").map(Keyword.unsafeFrom)),
          Some(Logo.unsafeFrom("/fu/bar/baz-updated.jpg")),
          Some(Status.Active),
          Some(SelfJoin.CanJoin),
        )
        projectRestService(_.updateProject(rootUser)(newProjectIri.asProjectIri, updateRequest)).map(received =>
          assertTrue(
            received.project.shortname.value == "newproject",
            received.project.shortcode.value == "111C",
            received.project.longname.map(_.value).contains("updated project longname"),
            received.project.description ==
              Seq(
                StringLiteralV2.from(
                  value = """updated project description with "quotes" and <html tags>""",
                  EN,
                ),
              ),
            received.project.keywords.sorted == Seq("updated", "keywords").sorted,
            received.project.logo.map(_.value).contains("/fu/bar/baz-updated.jpg"),
            received.project.status == Status.Active,
            received.project.selfjoin == SelfJoin.CanJoin,
          ),
        )
      },
      test("return 'NotFound' if a not existing project IRI is submitted during update") {
        val updateRequest = ProjectUpdateRequest(longname = Some(Longname.unsafeFrom("longname")))
        projectRestService(_.updateProject(rootUser)(notExistingProjectIri, updateRequest)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[NotFoundException](s"Project '$notExistingProjectIri' not found."),
          ),
        )
      },
    ),
    suite("used to query members")(
      test("return all members of a project identified by IRI") {
        projectRestService(_.getProjectMembersById(rootUser)(imagesProject.id)).map { actual =>
          assert(actual.members.map(_.id))(
            hasSameElements(Seq(imagesUser01.id, imagesUser02.id, multiuserUser.id, imagesReviewerUser.id)),
          )
        }
      },
      test("return all members of a project identified by shortname") {
        projectRestService(_.getProjectMembersByShortname(rootUser)(imagesProject.shortname)).map { actual =>
          assert(actual.members.map(_.id))(
            hasSameElements(Seq(imagesUser01.id, imagesUser02.id, multiuserUser.id, imagesReviewerUser.id)),
          )
        }
      },
      test("return all members of a project identified by shortcode") {
        projectRestService(_.getProjectMembersByShortcode(rootUser)(imagesProject.shortcode)).map { actual =>
          assert(actual.members.map(_.id))(
            hasSameElements(Seq(imagesUser01.id, imagesUser02.id, multiuserUser.id, imagesReviewerUser.id)),
          )
        }
      },
      test("return 'Forbidden' when the project IRI is unknown (project membership)") {
        projectRestService(_.getProjectMembersById(rootUser)(notExistingProjectIri)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[ForbiddenException](
              s"Project with id $notExistingProjectIri not found.",
            ),
          ),
        )
      },
      test("return 'Forbidden' when the project shortname is unknown (project membership)") {
        projectRestService(_.getProjectMembersByShortname(rootUser)(Shortname.unsafeFrom("wrongshortname"))).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[ForbiddenException](
              s"Project with shortname wrongshortname not found.",
            ),
          ),
        )
      },
      test("return 'Forbidden' when the project shortcode is unknown (project membership)") {
        projectRestService(_.getProjectMembersByShortcode(rootUser)(Shortcode.unsafeFrom("9999"))).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[ForbiddenException](
              s"Project with shortcode 9999 not found.",
            ),
          ),
        )
      },
      test("return all project admin members of a project identified by IRI") {
        projectRestService(_.getProjectAdminMembersById(rootUser)(imagesProject.id)).map { actual =>
          assert(actual.members.map(_.id))(hasSameElements(Seq(imagesUser01.id, multiuserUser.id)))
        }
      },
      test("return all project admin members of a project identified by shortname") {
        projectRestService(_.getProjectAdminMembersByShortname(rootUser)(imagesProject.shortname)).map { actual =>
          assert(actual.members.map(_.id))(hasSameElements(Seq(imagesUser01.id, multiuserUser.id)))
        }
      },
      test("return all project admin members of a project identified by shortcode") {
        projectRestService(_.getProjectAdminMembersByShortcode(rootUser)(imagesProject.shortcode)).map { actual =>
          assert(actual.members.map(_.id))(hasSameElements(Seq(imagesUser01.id, multiuserUser.id)))
        }
      },
      test("return 'Forbidden' when the project IRI is unknown (project admin membership)") {
        projectRestService(_.getProjectAdminMembersById(rootUser)(notExistingProjectIri)).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[ForbiddenException](
              s"Project with id $notExistingProjectIri not found.",
            ),
          ),
        )
      },
      test("return 'Forbidden' when the project shortname is unknown (project admin membership)") {
        projectRestService(_.getProjectAdminMembersByShortname(rootUser)(Shortname.unsafeFrom("wrongshortname"))).exit
          .map(
            assert(_)(
              E2EZSpec.failsWithMessageEqualTo[ForbiddenException](
                s"Project with shortname wrongshortname not found.",
              ),
            ),
          )
      },
      test("return 'Forbidden' when the project shortcode is unknown (project admin membership)") {
        projectRestService(_.getProjectMembersByShortcode(rootUser)(Shortcode.unsafeFrom("9999"))).exit.map(
          assert(_)(
            E2EZSpec.failsWithMessageEqualTo[ForbiddenException](
              s"Project with shortcode 9999 not found.",
            ),
          ),
        )
      },
    ),
    suite("used to query keywords")(
      test("return all unique keywords for all projects") {
        projectRestService(_.listAllKeywords(()))
          .map(received => assertTrue(received.keywords.size == 21))
      },
      test("return all keywords for a single project") {
        projectRestService(_.getKeywordsByProjectIri(incunabulaProject.id))
          .map(received => assertTrue(received.keywords == incunabulaProject.keywords))
      },
      test("return empty list for a project without keywords") {
        projectRestService(_.getKeywordsByProjectIri(dokubibProject.id))
          .map(received => assertTrue(received.keywords == Seq.empty[String]))
      },
      test("return 'NotFound' when the project IRI is unknown") {
        projectRestService(_.getKeywordsByProjectIri(notExistingProjectIri)).exit.map(exit =>
          assert(exit)(
            E2EZSpec.failsWithMessageEqualTo[NotFoundException](
              s"Project '$notExistingProjectIri' not found.",
            ),
          ),
        )
      },
    ),
  )
}
