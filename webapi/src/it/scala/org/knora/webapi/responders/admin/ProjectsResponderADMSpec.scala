/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * To be able to test UsersResponder, we need to be able to start UsersResponder isolated. Now the UsersResponder
 * extend ResponderADM which messes up testing, as we cannot inject the TestActor system.
 */
package org.knora.webapi.responders.admin

import akka.actor.Status.Failure
import akka.testkit.ImplicitSender

import java.util.UUID
import scala.concurrent.duration._

import dsp.errors.BadRequestException
import dsp.errors.DuplicateValueException
import dsp.errors.NotFoundException
import dsp.valueobjects.Project._
import dsp.valueobjects.V2
import org.knora.webapi._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.UserInformationTypeADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.MutableTestIri

/**
 * This spec is used to test the messages received by the [[ProjectsResponderADM]] actor.
 */
class ProjectsResponderADMSpec extends CoreSpec with ImplicitSender {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  private val timeout                                   = 5.seconds

  private val rootUser = SharedTestDataADM.rootUser

  private val notExistingProjectButValidProjectIri = "http://rdfh.ch/projects/cXxbOh4xRce3uLUyXHujfg"

  "The ProjectsResponderADM" when {
    "used to query for project information" should {
      "return information for every project" in {
        appActor ! ProjectsGetRequestADM(
          requestingUser = rootUser
        )
        val received = expectMsgType[ProjectsGetResponseADM](timeout)

        assert(received.projects.contains(SharedTestDataADM.imagesProject))
        assert(received.projects.contains(SharedTestDataADM.incunabulaProject))
      }

      "return information about a project identified by IRI" in {
        appActor ! ProjectGetRequestADM(
          identifier = IriIdentifier
            .fromString(SharedTestDataADM.incunabulaProject.id)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = SharedTestDataADM.rootUser
        )
        expectMsg(ProjectGetResponseADM(SharedTestDataADM.incunabulaProject))

      }

      "return information about a project identified by shortname" in {
        appActor ! ProjectGetRequestADM(
          identifier = ShortnameIdentifier
            .fromString(SharedTestDataADM.incunabulaProject.shortname)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = SharedTestDataADM.rootUser
        )
        expectMsg(ProjectGetResponseADM(SharedTestDataADM.incunabulaProject))
      }

      "return 'NotFoundException' when the project IRI is unknown" in {
        appActor ! ProjectGetRequestADM(
          identifier = IriIdentifier
            .fromString(notExistingProjectButValidProjectIri)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = SharedTestDataADM.rootUser
        )
        expectMsg(Failure(NotFoundException(s"Project '$notExistingProjectButValidProjectIri' not found")))

      }

      "return 'NotFoundException' when the project shortname is unknown " in {
        appActor ! ProjectGetRequestADM(
          identifier = ShortnameIdentifier
            .fromString("wrongshortname")
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = SharedTestDataADM.rootUser
        )
        expectMsg(Failure(NotFoundException(s"Project 'wrongshortname' not found")))
      }

      "return 'NotFoundException' when the project shortcode is unknown " in {
        appActor ! ProjectGetRequestADM(
          identifier = ShortcodeIdentifier
            .fromString("9999")
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = SharedTestDataADM.rootUser
        )
        expectMsg(Failure(NotFoundException(s"Project '9999' not found")))
      }
    }

    "used to query project's restricted view settings" should {
      val expectedResult = ProjectRestrictedViewSettingsADM(size = Some("!512,512"), watermark = Some("path_to_image"))

      "return restricted view settings using project IRI" in {
        appActor ! ProjectRestrictedViewSettingsGetADM(
          identifier = IriIdentifier
            .fromString(SharedTestDataADM.imagesProject.id)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = SharedTestDataADM.rootUser
        )
        expectMsg(Some(expectedResult))
      }

      "return restricted view settings using project SHORTNAME" in {
        appActor ! ProjectRestrictedViewSettingsGetADM(
          identifier = ShortnameIdentifier
            .fromString(SharedTestDataADM.imagesProject.shortname)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = SharedTestDataADM.rootUser
        )
        expectMsg(Some(expectedResult))
      }

      "return restricted view settings using project SHORTCODE" in {
        appActor ! ProjectRestrictedViewSettingsGetADM(
          identifier = ShortcodeIdentifier
            .fromString(SharedTestDataADM.imagesProject.shortcode)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = SharedTestDataADM.rootUser
        )
        expectMsg(Some(expectedResult))
      }

      "return 'NotFoundException' when the project IRI is unknown" in {
        appActor ! ProjectRestrictedViewSettingsGetRequestADM(
          identifier = IriIdentifier
            .fromString(notExistingProjectButValidProjectIri)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = SharedTestDataADM.rootUser
        )
        expectMsg(Failure(NotFoundException(s"Project '$notExistingProjectButValidProjectIri' not found.")))
      }

      "return 'NotFoundException' when the project SHORTCODE is unknown" in {
        appActor ! ProjectRestrictedViewSettingsGetRequestADM(
          identifier = ShortcodeIdentifier
            .fromString("9999")
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = SharedTestDataADM.rootUser
        )
        expectMsg(Failure(NotFoundException(s"Project '9999' not found.")))
      }

      "return 'NotFoundException' when the project SHORTNAME is unknown" in {
        appActor ! ProjectRestrictedViewSettingsGetRequestADM(
          identifier = ShortnameIdentifier
            .fromString("wrongshortname")
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = SharedTestDataADM.rootUser
        )
        expectMsg(Failure(NotFoundException(s"Project 'wrongshortname' not found.")))
      }

    }

    "used to modify project information" should {
      val newProjectIri = new MutableTestIri

      "CREATE a project and return the project info if the supplied shortname is unique" in {
        val shortCode = "111c"
        appActor ! ProjectCreateRequestADM(
          createRequest = ProjectCreatePayloadADM(
            shortname = ShortName.make("newproject").fold(error => throw error.head, value => value),
            shortcode = ShortCode.make(shortCode).fold(error => throw error.head, value => value), // lower case
            longname = Name.make(Some("project longname")).fold(error => throw error.head, value => value),
            description = ProjectDescription
              .make(Seq(V2.StringLiteralV2(value = "project description", language = Some("en"))))
              .fold(error => throw error.head, value => value),
            keywords = Keywords.make(Seq("keywords")).fold(error => throw error.head, value => value),
            logo = Logo.make(Some("/fu/bar/baz.jpg")).fold(error => throw error.head, value => value),
            status = ProjectStatus.make(true).fold(error => throw error.head, value => value),
            selfjoin = ProjectSelfJoin.make(false).fold(error => throw error.head, value => value)
          ),
          SharedTestDataADM.rootUser,
          UUID.randomUUID()
        )
        val received: ProjectOperationResponseADM = expectMsgType[ProjectOperationResponseADM](timeout)

        received.project.shortname should be("newproject")
        received.project.shortcode should be(shortCode.toUpperCase) // upper case
        received.project.longname should contain("project longname")
        received.project.description should be(
          Seq(StringLiteralV2(value = "project description", language = Some("en")))
        )

        newProjectIri.set(received.project.id)

        // Check Administrative Permissions
        appActor ! AdministrativePermissionsForProjectGetRequestADM(
          projectIri = received.project.id,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        // Check Administrative Permission of ProjectAdmin
        val receivedApAdmin: AdministrativePermissionsForProjectGetResponseADM =
          expectMsgType[AdministrativePermissionsForProjectGetResponseADM]
        val hasAPForProjectAdmin = receivedApAdmin.administrativePermissions.filter { ap: AdministrativePermissionADM =>
          ap.forProject == received.project.id && ap.forGroup == OntologyConstants.KnoraAdmin.ProjectAdmin &&
          ap.hasPermissions
            .equals(Set(PermissionADM.ProjectAdminAllPermission, PermissionADM.ProjectResourceCreateAllPermission))
        }

        hasAPForProjectAdmin.size shouldBe 1

        // Check Administrative Permission of ProjectMember
        val hasAPForProjectMember = receivedApAdmin.administrativePermissions.filter {
          ap: AdministrativePermissionADM =>
            ap.forProject == received.project.id && ap.forGroup == OntologyConstants.KnoraAdmin.ProjectMember &&
            ap.hasPermissions.equals(Set(PermissionADM.ProjectResourceCreateAllPermission))
        }
        hasAPForProjectMember.size shouldBe 1

        // Check Default Object Access permissions
        appActor ! DefaultObjectAccessPermissionsForProjectGetRequestADM(
          projectIri = received.project.id,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        val receivedDoaps: DefaultObjectAccessPermissionsForProjectGetResponseADM =
          expectMsgType[DefaultObjectAccessPermissionsForProjectGetResponseADM]

        // Check Default Object Access permission of ProjectAdmin
        val hasDOAPForProjectAdmin = receivedDoaps.defaultObjectAccessPermissions.filter {
          doap: DefaultObjectAccessPermissionADM =>
            doap.forProject == received.project.id && doap.forGroup.contains(
              OntologyConstants.KnoraAdmin.ProjectAdmin
            ) &&
            doap.hasPermissions.equals(
              Set(
                PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectAdmin),
                PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember)
              )
            )
        }
        hasDOAPForProjectAdmin.size shouldBe 1

        // Check Default Object Access permission of ProjectMember
        val hasDOAPForProjectMember = receivedDoaps.defaultObjectAccessPermissions.filter {
          doap: DefaultObjectAccessPermissionADM =>
            doap.forProject == received.project.id && doap.forGroup.contains(
              OntologyConstants.KnoraAdmin.ProjectMember
            ) &&
            doap.hasPermissions.equals(
              Set(
                PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectAdmin),
                PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember)
              )
            )
        }
        hasDOAPForProjectMember.size shouldBe 1
      }

      "CREATE a project and return the project info if the supplied shortname and shortcode is unique" in {
        appActor ! ProjectCreateRequestADM(
          createRequest = ProjectCreatePayloadADM(
            shortname = ShortName.make("newproject2").fold(error => throw error.head, value => value),
            shortcode = ShortCode.make("1112").fold(error => throw error.head, value => value), // lower case
            longname = Some(Name.make("project longname").fold(error => throw error.head, value => value)),
            description = ProjectDescription
              .make(Seq(V2.StringLiteralV2(value = "project description", language = Some("en"))))
              .fold(error => throw error.head, value => value),
            keywords = Keywords.make(Seq("keywords")).fold(error => throw error.head, value => value),
            logo = Logo.make(Some("/fu/bar/baz.jpg")).fold(error => throw error.head, value => value),
            status = ProjectStatus.make(true).fold(error => throw error.head, value => value),
            selfjoin = ProjectSelfJoin.make(false).fold(error => throw error.head, value => value)
          ),
          SharedTestDataADM.rootUser,
          UUID.randomUUID()
        )
        val received: ProjectOperationResponseADM = expectMsgType[ProjectOperationResponseADM](timeout)

        received.project.shortname should be("newproject2")
        received.project.shortcode should be("1112")
        received.project.longname should contain("project longname")
        received.project.description should be(
          Seq(StringLiteralV2(value = "project description", language = Some("en")))
        )

      }

      "CREATE a project that its info has special characters" in {

        val longnameWithSpecialCharacter    = "New \\\"Longname\\\""
        val descriptionWithSpecialCharacter = "project \\\"description\\\""
        val keywordWithSpecialCharacter     = "new \\\"keyword\\\""
        appActor ! ProjectCreateRequestADM(
          createRequest = ProjectCreatePayloadADM(
            shortname = ShortName.make("project_with_character").fold(error => throw error.head, value => value),
            shortcode = ShortCode.make("1312").fold(error => throw error.head, value => value), // lower case
            longname = Name.make(Some(longnameWithSpecialCharacter)).fold(error => throw error.head, value => value),
            description = ProjectDescription
              .make(Seq(V2.StringLiteralV2(value = descriptionWithSpecialCharacter, language = Some("en"))))
              .fold(error => throw error.head, value => value),
            keywords = Keywords.make(Seq(keywordWithSpecialCharacter)).fold(error => throw error.head, value => value),
            logo = Logo.make(Some("/fu/bar/baz.jpg")).fold(error => throw error.head, value => value),
            status = ProjectStatus.make(true).fold(error => throw error.head, value => value),
            selfjoin = ProjectSelfJoin.make(false).fold(error => throw error.head, value => value)
          ),
          SharedTestDataADM.rootUser,
          UUID.randomUUID()
        )
        val received: ProjectOperationResponseADM = expectMsgType[ProjectOperationResponseADM](timeout)

        received.project.longname should contain(stringFormatter.fromSparqlEncodedString(longnameWithSpecialCharacter))
        received.project.description should be(
          Seq(
            StringLiteralV2(
              value = stringFormatter.fromSparqlEncodedString(descriptionWithSpecialCharacter),
              language = Some("en")
            )
          )
        )
        received.project.keywords should contain(stringFormatter.fromSparqlEncodedString(keywordWithSpecialCharacter))

      }

      "return a 'DuplicateValueException' during creation if the supplied project shortname is not unique" in {
        appActor ! ProjectCreateRequestADM(
          createRequest = ProjectCreatePayloadADM(
            shortname = ShortName.make("newproject").fold(error => throw error.head, value => value),
            shortcode = ShortCode.make("111C").fold(error => throw error.head, value => value), // lower case
            longname = Name.make(Some("project longname")).fold(error => throw error.head, value => value),
            description = ProjectDescription
              .make(Seq(V2.StringLiteralV2(value = "project description", language = Some("en"))))
              .fold(error => throw error.head, value => value),
            keywords = Keywords.make(Seq("keywords")).fold(error => throw error.head, value => value),
            logo = Logo.make(Some("/fu/bar/baz.jpg")).fold(error => throw error.head, value => value),
            status = ProjectStatus.make(true).fold(error => throw error.head, value => value),
            selfjoin = ProjectSelfJoin.make(false).fold(error => throw error.head, value => value)
          ),
          SharedTestDataADM.rootUser,
          UUID.randomUUID()
        )
        expectMsg(Failure(DuplicateValueException(s"Project with the shortname: 'newproject' already exists")))
      }

      "return a 'DuplicateValueException' during creation if the supplied project shortname is unique but the shortcode is not" in {
        appActor ! ProjectCreateRequestADM(
          createRequest = ProjectCreatePayloadADM(
            shortname = ShortName.make("newproject3").fold(error => throw error.head, value => value),
            shortcode = ShortCode.make("111C").fold(error => throw error.head, value => value), // lower case
            longname = Name.make(Some("project longname")).fold(error => throw error.head, value => value),
            description = ProjectDescription
              .make(Seq(V2.StringLiteralV2(value = "project description", language = Some("en"))))
              .fold(error => throw error.head, value => value),
            keywords = Keywords.make(Seq("keywords")).fold(error => throw error.head, value => value),
            logo = Logo.make(Some("/fu/bar/baz.jpg")).fold(error => throw error.head, value => value),
            status = ProjectStatus.make(true).fold(error => throw error.head, value => value),
            selfjoin = ProjectSelfJoin.make(false).fold(error => throw error.head, value => value)
          ),
          SharedTestDataADM.rootUser,
          UUID.randomUUID()
        )
        expectMsg(Failure(DuplicateValueException(s"Project with the shortcode: '111C' already exists")))
      }

      "UPDATE a project" in {
        appActor ! ProjectChangeRequestADM(
          projectIri = newProjectIri.get,
          changeProjectRequest = ChangeProjectApiRequestADM(
            shortname = None,
            longname = Some("updated project longname"),
            description = Some(
              Seq(
                StringLiteralV2(
                  value = """updated project description with "quotes" and <html tags>""",
                  language = Some("en")
                )
              )
            ),
            keywords = Some(Seq("updated", "keywords")),
            logo = Some("/fu/bar/baz-updated.jpg"),
            status = Some(false),
            selfjoin = Some(true)
          ).validateAndEscape,
          SharedTestDataADM.rootUser,
          UUID.randomUUID()
        )
        val received: ProjectOperationResponseADM = expectMsgType[ProjectOperationResponseADM](timeout)
        received.project.shortname should be("newproject")
        received.project.shortcode should be("111C")
        received.project.longname should be(Some("updated project longname"))
        received.project.description should be(
          Seq(
            StringLiteralV2(
              value = """updated project description with "quotes" and <html tags>""",
              language = Some("en")
            )
          )
        )
        received.project.keywords.sorted should be(Seq("updated", "keywords").sorted)
        received.project.logo should be(Some("/fu/bar/baz-updated.jpg"))
        received.project.status should be(false)
        received.project.selfjoin should be(true)
      }

      "return 'NotFound' if a not existing project IRI is submitted during update" in {
        appActor ! ProjectChangeRequestADM(
          projectIri = notExistingProjectButValidProjectIri,
          changeProjectRequest = ChangeProjectApiRequestADM(longname = Some("new long name")).validateAndEscape,
          SharedTestDataADM.rootUser,
          UUID.randomUUID()
        )
        expectMsg(
          Failure(
            NotFoundException(s"Project '$notExistingProjectButValidProjectIri' not found. Aborting update request.")
          )
        )
      }

      "return 'BadRequest' if nothing would be changed during the update" in {

        an[BadRequestException] should be thrownBy ChangeProjectApiRequestADM(None, None, None, None, None, None, None)

        /*
                actorUnderTest ! ProjectChangeRequestADM(
                    projectIri = "http://rdfh.ch/projects/notexisting",
                    changeProjectRequest = ChangeProjectApiRequestADM(None, None, None, None, None, None, None, None, None, None),
                    SharedAdminTestData.rootUser,
                    UUID.randomUUID()
                )
                expectMsg(Failure(BadRequestException("No data would be changed. Aborting update request.")))
         */
      }
    }

    /*
        "used to query named graphs" should {
            "return all named graphs" in {
                actorUnderTest ! ProjectsNamedGraphGetADM(SharedTestDataADM.rootUser)

                val received: Seq[NamedGraphADM] = expectMsgType[Seq[NamedGraphADM]]
                received.size should be (7)
            }

            "return all named graphs after adding a new ontology" in {
                actorUnderTest ! ProjectOntologyAddADM(
                    projectIri = IMAGES_PROJECT_IRI,
                    ontologyIri = "http://wwww.knora.org/ontology/00FF/blabla1",

                    requestingUser = KnoraSystemInstances.Users.SystemUser,
                    apiRequestID = UUID.randomUUID()
                )
                val received01: ProjectADM = expectMsgType[ProjectADM](timeout)
                received01.ontologies.size should be (2)

                actorUnderTest ! ProjectsNamedGraphGetADM(SharedTestDataADM.rootUser)

                val received02: Seq[NamedGraphADM] = expectMsgType[Seq[NamedGraphADM]]
                received02.size should be (8)
            }
        }
     */

    "used to query members" should {
      "return all members of a project identified by IRI" in {
        appActor ! ProjectMembersGetRequestADM(
          IriIdentifier
            .fromString(SharedTestDataADM.imagesProject.id)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          SharedTestDataADM.rootUser
        )
        val received: ProjectMembersGetResponseADM = expectMsgType[ProjectMembersGetResponseADM](timeout)
        val members                                = received.members

        members.size should be(4)

        members.map(_.id) should contain allElementsOf Seq(
          SharedTestDataADM.imagesUser01.ofType(UserInformationTypeADM.Restricted),
          SharedTestDataADM.imagesUser02.ofType(UserInformationTypeADM.Restricted),
          SharedTestDataADM.multiuserUser.ofType(UserInformationTypeADM.Restricted),
          SharedTestDataADM.imagesReviewerUser.ofType(UserInformationTypeADM.Restricted)
        ).map(_.id)
      }

      "return all members of a project identified by shortname" in {
        appActor ! ProjectMembersGetRequestADM(
          ShortnameIdentifier
            .fromString(SharedTestDataADM.imagesProject.shortname)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = SharedTestDataADM.rootUser
        )
        val received: ProjectMembersGetResponseADM = expectMsgType[ProjectMembersGetResponseADM](timeout)
        val members                                = received.members

        members.size should be(4)

        members.map(_.id) should contain allElementsOf Seq(
          SharedTestDataADM.imagesUser01.ofType(UserInformationTypeADM.Short),
          SharedTestDataADM.imagesUser02.ofType(UserInformationTypeADM.Short),
          SharedTestDataADM.multiuserUser.ofType(UserInformationTypeADM.Short),
          SharedTestDataADM.imagesReviewerUser.ofType(UserInformationTypeADM.Short)
        ).map(_.id)
      }

      "return all members of a project identified by shortcode" in {
        appActor ! ProjectMembersGetRequestADM(
          ShortcodeIdentifier
            .fromString(SharedTestDataADM.imagesProject.shortcode)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = SharedTestDataADM.rootUser
        )
        val received: ProjectMembersGetResponseADM = expectMsgType[ProjectMembersGetResponseADM](timeout)
        val members                                = received.members

        members.size should be(4)

        members.map(_.id) should contain allElementsOf Seq(
          SharedTestDataADM.imagesUser01.ofType(UserInformationTypeADM.Short),
          SharedTestDataADM.imagesUser02.ofType(UserInformationTypeADM.Short),
          SharedTestDataADM.multiuserUser.ofType(UserInformationTypeADM.Short),
          SharedTestDataADM.imagesReviewerUser.ofType(UserInformationTypeADM.Short)
        ).map(_.id)
      }

      "return 'NotFound' when the project IRI is unknown (project membership)" in {
        appActor ! ProjectMembersGetRequestADM(
          IriIdentifier
            .fromString(notExistingProjectButValidProjectIri)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          SharedTestDataADM.rootUser
        )
        expectMsg(Failure(NotFoundException(s"Project '$notExistingProjectButValidProjectIri' not found.")))
      }

      "return 'NotFound' when the project shortname is unknown (project membership)" in {
        appActor ! ProjectMembersGetRequestADM(
          ShortnameIdentifier
            .fromString("wrongshortname")
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = SharedTestDataADM.rootUser
        )
        expectMsg(Failure(NotFoundException(s"Project 'wrongshortname' not found.")))
      }

      "return 'NotFound' when the project shortcode is unknown (project membership)" in {
        appActor ! ProjectMembersGetRequestADM(
          ShortcodeIdentifier
            .fromString("9999")
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = SharedTestDataADM.rootUser
        )
        expectMsg(Failure(NotFoundException(s"Project '9999' not found.")))
      }

      "return all project admin members of a project identified by IRI" in {
        appActor ! ProjectAdminMembersGetRequestADM(
          IriIdentifier
            .fromString(SharedTestDataADM.imagesProject.id)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          SharedTestDataADM.rootUser
        )
        val received: ProjectAdminMembersGetResponseADM = expectMsgType[ProjectAdminMembersGetResponseADM](timeout)
        val members                                     = received.members

        members.size should be(2)

        members.map(_.id) should contain allElementsOf Seq(
          SharedTestDataADM.imagesUser01.ofType(UserInformationTypeADM.Short),
          SharedTestDataADM.multiuserUser.ofType(UserInformationTypeADM.Short)
        ).map(_.id)
      }

      "return all project admin members of a project identified by shortname" in {
        appActor ! ProjectAdminMembersGetRequestADM(
          ShortnameIdentifier
            .fromString(SharedTestDataADM.imagesProject.shortname)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = SharedTestDataADM.rootUser
        )
        val received: ProjectAdminMembersGetResponseADM = expectMsgType[ProjectAdminMembersGetResponseADM](timeout)
        val members                                     = received.members

        members.size should be(2)

        members.map(_.id) should contain allElementsOf Seq(
          SharedTestDataADM.imagesUser01.ofType(UserInformationTypeADM.Short),
          SharedTestDataADM.multiuserUser.ofType(UserInformationTypeADM.Short)
        ).map(_.id)
      }

      "return all project admin members of a project identified by shortcode" in {
        appActor ! ProjectAdminMembersGetRequestADM(
          ShortcodeIdentifier
            .fromString(SharedTestDataADM.imagesProject.shortcode)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = SharedTestDataADM.rootUser
        )
        val received: ProjectAdminMembersGetResponseADM = expectMsgType[ProjectAdminMembersGetResponseADM](timeout)
        val members                                     = received.members

        members.size should be(2)

        members.map(_.id) should contain allElementsOf Seq(
          SharedTestDataADM.imagesUser01.ofType(UserInformationTypeADM.Short),
          SharedTestDataADM.multiuserUser.ofType(UserInformationTypeADM.Short)
        ).map(_.id)
      }

      "return 'NotFound' when the project IRI is unknown (project admin membership)" in {
        appActor ! ProjectAdminMembersGetRequestADM(
          IriIdentifier
            .fromString(notExistingProjectButValidProjectIri)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          SharedTestDataADM.rootUser
        )
        expectMsg(Failure(NotFoundException(s"Project '$notExistingProjectButValidProjectIri' not found.")))
      }

      "return 'NotFound' when the project shortname is unknown (project admin membership)" in {
        appActor ! ProjectAdminMembersGetRequestADM(
          ShortnameIdentifier
            .fromString("wrongshortname")
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = SharedTestDataADM.rootUser
        )
        expectMsg(Failure(NotFoundException(s"Project 'wrongshortname' not found.")))
      }

      "return 'NotFound' when the project shortcode is unknown (project admin membership)" in {
        appActor ! ProjectAdminMembersGetRequestADM(
          ShortcodeIdentifier
            .fromString("9999")
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = SharedTestDataADM.rootUser
        )
        expectMsg(Failure(NotFoundException(s"Project '9999' not found.")))
      }
    }

    "used to query keywords" should {

      "return all unique keywords for all projects" in {
        appActor ! ProjectsKeywordsGetRequestADM(
          SharedTestDataADM.rootUser
        )
        val received: ProjectsKeywordsGetResponseADM = expectMsgType[ProjectsKeywordsGetResponseADM](timeout)
        received.keywords.size should be(21)
      }

      "return all keywords for a single project" in {
        appActor ! ProjectKeywordsGetRequestADM(
          projectIri = SharedTestDataADM.incunabulaProject.id,
          requestingUser = SharedTestDataADM.rootUser
        )
        val received: ProjectKeywordsGetResponseADM = expectMsgType[ProjectKeywordsGetResponseADM](timeout)
        received.keywords should be(SharedTestDataADM.incunabulaProject.keywords)
      }

      "return empty list for a project without keywords" in {
        appActor ! ProjectKeywordsGetRequestADM(
          projectIri = SharedTestDataADM.dokubibProject.id,
          requestingUser = SharedTestDataADM.rootUser
        )
        val received: ProjectKeywordsGetResponseADM = expectMsgType[ProjectKeywordsGetResponseADM](timeout)
        received.keywords should be(Seq.empty[String])
      }

      "return 'NotFound' when the project IRI is unknown" in {
        appActor ! ProjectKeywordsGetRequestADM(
          projectIri = notExistingProjectButValidProjectIri,
          SharedTestDataADM.rootUser
        )

        expectMsg(Failure(NotFoundException(s"Project '$notExistingProjectButValidProjectIri' not found.")))
      }
    }
  }
}
