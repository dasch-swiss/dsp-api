/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import akka.actor.Status.Failure
import akka.testkit.ImplicitSender

import java.util.UUID
import scala.concurrent.duration._

import dsp.errors.BadRequestException
import dsp.errors.DuplicateValueException
import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import dsp.valueobjects.Iri.ProjectIri
import dsp.valueobjects.LanguageCode
import dsp.valueobjects.User._
import org.knora.webapi._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupMembersGetRequestADM
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupMembersGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectAdminMembersGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectAdminMembersGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectMembersGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectMembersGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.sharedtestdata.SharedTestDataADM

/**
 * This spec is used to test the messages received by the [[UsersResponderADM]] actor.
 */
class UsersResponderADMSpec extends CoreSpec with ImplicitSender with Authenticator {

  private val timeout: FiniteDuration = 8.seconds

  private val rootUser          = SharedTestDataADM.rootUser
  private val anythingAdminUser = SharedTestDataADM.anythingAdminUser
  private val normalUser        = SharedTestDataADM.normalUser

  private val incunabulaProjectAdminUser = SharedTestDataADM.incunabulaProjectAdminUser

  private val imagesProject       = SharedTestDataADM.imagesProject
  private val incunabulaProject   = SharedTestDataADM.incunabulaProject
  private val imagesReviewerGroup = SharedTestDataADM.imagesReviewerGroup

  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  val imagesProjectIri     = ProjectIri.make(imagesProject.id).fold(e => throw e.head, v => v)
  val incunabulaProjectIri = ProjectIri.make(incunabulaProject.id).fold(e => throw e.head, v => v)

  "The UsersResponder " when {
    "asked about all users" should {
      "return a list if asked by SystemAdmin" in {
        appActor ! UsersGetRequestADM(
          requestingUser = rootUser
        )
        val response = expectMsgType[UsersGetResponseADM](timeout)
        response.users.nonEmpty should be(true)
        response.users.size should be(18)
      }

      "return a list if asked by ProjectAdmin" in {
        appActor ! UsersGetRequestADM(
          requestingUser = anythingAdminUser
        )
        val response = expectMsgType[UsersGetResponseADM](timeout)
        response.users.nonEmpty should be(true)
        response.users.size should be(18)
      }

      "return 'ForbiddenException' if asked by normal user'" in {
        appActor ! UsersGetRequestADM(
          requestingUser = normalUser
        )
        expectMsg(timeout, Failure(ForbiddenException("ProjectAdmin or SystemAdmin permissions are required.")))
      }

      "not return the system and anonymous users" in {
        appActor ! UsersGetRequestADM(
          requestingUser = rootUser
        )
        val response = expectMsgType[UsersGetResponseADM](timeout)
        response.users.nonEmpty should be(true)
        response.users.size should be(18)
        response.users.count(_.id == KnoraSystemInstances.Users.AnonymousUser.id) should be(0)
        response.users.count(_.id == KnoraSystemInstances.Users.SystemUser.id) should be(0)
      }
    }

    "asked about an user identified by 'iri' " should {
      "return a profile if the user (root user) is known" in {
        appActor ! UserGetADM(
          identifier = UserIdentifierADM(maybeIri = Some(rootUser.id)),
          userInformationTypeADM = UserInformationTypeADM.Full,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Some(rootUser.ofType(UserInformationTypeADM.Full)))
      }

      "return a profile if the user (incunabula user) is known" in {
        appActor ! UserGetADM(
          identifier = UserIdentifierADM(maybeIri = Some(incunabulaProjectAdminUser.id)),
          userInformationTypeADM = UserInformationTypeADM.Full,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Some(incunabulaProjectAdminUser.ofType(UserInformationTypeADM.Full)))
      }

      "return 'NotFoundException' when the user is unknown" in {
        appActor ! UserGetRequestADM(
          identifier = UserIdentifierADM(maybeIri = Some("http://rdfh.ch/users/notexisting")),
          userInformationTypeADM = UserInformationTypeADM.Full,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Failure(NotFoundException(s"User 'http://rdfh.ch/users/notexisting' not found")))
      }

      "return 'None' when the user is unknown" in {
        appActor ! UserGetADM(
          identifier = UserIdentifierADM(maybeIri = Some("http://rdfh.ch/users/notexisting")),
          userInformationTypeADM = UserInformationTypeADM.Full,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(None)
      }
    }

    "asked about an user identified by 'email'" should {
      "return a profile if the user (root user) is known" in {
        appActor ! UserGetADM(
          identifier = UserIdentifierADM(maybeEmail = Some(rootUser.email)),
          userInformationTypeADM = UserInformationTypeADM.Full,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Some(rootUser.ofType(UserInformationTypeADM.Full)))
      }

      "return a profile if the user (incunabula user) is known" in {
        appActor ! UserGetADM(
          identifier = UserIdentifierADM(maybeEmail = Some(incunabulaProjectAdminUser.email)),
          userInformationTypeADM = UserInformationTypeADM.Full,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Some(incunabulaProjectAdminUser.ofType(UserInformationTypeADM.Full)))
      }

      "return 'NotFoundException' when the user is unknown" in {
        appActor ! UserGetRequestADM(
          identifier = UserIdentifierADM(maybeEmail = Some("userwrong@example.com")),
          userInformationTypeADM = UserInformationTypeADM.Full,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Failure(NotFoundException(s"User 'userwrong@example.com' not found")))
      }

      "return 'None' when the user is unknown" in {
        appActor ! UserGetADM(
          identifier = UserIdentifierADM(maybeEmail = Some("userwrong@example.com")),
          userInformationTypeADM = UserInformationTypeADM.Full,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(None)
      }
    }

    "asked about an user identified by 'username'" should {
      "return a profile if the user (root user) is known" in {
        appActor ! UserGetADM(
          identifier = UserIdentifierADM(maybeUsername = Some(rootUser.username)),
          userInformationTypeADM = UserInformationTypeADM.Full,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Some(rootUser.ofType(UserInformationTypeADM.Full)))
      }

      "return a profile if the user (incunabula user) is known" in {
        appActor ! UserGetADM(
          identifier = UserIdentifierADM(maybeUsername = Some(incunabulaProjectAdminUser.username)),
          userInformationTypeADM = UserInformationTypeADM.Full,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Some(incunabulaProjectAdminUser.ofType(UserInformationTypeADM.Full)))
      }

      "return 'NotFoundException' when the user is unknown" in {
        appActor ! UserGetRequestADM(
          identifier = UserIdentifierADM(maybeUsername = Some("userwrong")),
          userInformationTypeADM = UserInformationTypeADM.Full,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Failure(NotFoundException(s"User 'userwrong' not found")))
      }

      "return 'None' when the user is unknown" in {
        appActor ! UserGetADM(
          identifier = UserIdentifierADM(maybeUsername = Some("userwrong")),
          userInformationTypeADM = UserInformationTypeADM.Full,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(None)
      }
    }

    "asked to create a new user" should {
      "CREATE the user and return it's profile if the supplied email is unique " in {
        appActor ! UserCreateRequestADM(
          userCreatePayloadADM = UserCreatePayloadADM(
            username = Username.make("donald.duck").fold(e => throw e.head, v => v),
            email = Email.make("donald.duck@example.com").fold(e => throw e.head, v => v),
            givenName = GivenName.make("Donald").fold(e => throw e.head, v => v),
            familyName = FamilyName.make("Duck").fold(e => throw e.head, v => v),
            password = Password.make("test").fold(e => throw e.head, v => v),
            status = UserStatus.make(true).fold(error => throw error.head, value => value),
            lang = LanguageCode.en,
            systemAdmin = SystemAdmin.make(false).fold(e => throw e.head, v => v)
          ),
          requestingUser = SharedTestDataADM.anonymousUser,
          apiRequestID = UUID.randomUUID
        )
        val u = expectMsgType[UserOperationResponseADM](timeout).user
        u.username shouldBe "donald.duck"
        u.givenName shouldBe "Donald"
        u.familyName shouldBe "Duck"
        u.email shouldBe "donald.duck@example.com"
        u.lang shouldBe "en"

      }

      "return a 'DuplicateValueException' if the supplied 'username' is not unique" in {
        appActor ! UserCreateRequestADM(
          userCreatePayloadADM = UserCreatePayloadADM(
            username = Username.make("root").fold(e => throw e.head, v => v),
            email = Email.make("root2@example.com").fold(e => throw e.head, v => v),
            givenName = GivenName.make("Donald").fold(e => throw e.head, v => v),
            familyName = FamilyName.make("Duck").fold(e => throw e.head, v => v),
            password = Password.make("test").fold(e => throw e.head, v => v),
            status = UserStatus.make(true).fold(error => throw error.head, value => value),
            lang = LanguageCode.en,
            systemAdmin = SystemAdmin.make(false).fold(e => throw e.head, v => v)
          ),
          SharedTestDataADM.anonymousUser,
          UUID.randomUUID
        )
        expectMsg(Failure(DuplicateValueException(s"User with the username 'root' already exists")))
      }

      "return a 'DuplicateValueException' if the supplied 'email' is not unique" in {
        appActor ! UserCreateRequestADM(
          userCreatePayloadADM = UserCreatePayloadADM(
            username = Username.make("root2").fold(e => throw e.head, v => v),
            email = Email.make("root@example.com").fold(e => throw e.head, v => v),
            givenName = GivenName.make("Donald").fold(e => throw e.head, v => v),
            familyName = FamilyName.make("Duck").fold(e => throw e.head, v => v),
            password = Password.make("test").fold(e => throw e.head, v => v),
            status = UserStatus.make(true).fold(error => throw error.head, value => value),
            lang = LanguageCode.en,
            systemAdmin = SystemAdmin.make(false).fold(e => throw e.head, v => v)
          ),
          SharedTestDataADM.anonymousUser,
          UUID.randomUUID
        )
        expectMsg(Failure(DuplicateValueException(s"User with the email 'root@example.com' already exists")))
      }
    }

    "asked to update a user" should {
      "UPDATE the user's basic information" in {
        /* User information is updated by the user */
        appActor ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            givenName = Some(GivenName.make("Donald").fold(e => throw e.head, v => v))
          ),
          requestingUser = SharedTestDataADM.normalUser,
          apiRequestID = UUID.randomUUID
        )

        val response1 = expectMsgType[UserOperationResponseADM](timeout)
        response1.user.givenName should equal("Donald")

        /* User information is updated by a system admin */
        appActor ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            familyName = Some(FamilyName.make("Duck").fold(e => throw e.head, v => v))
          ),
          requestingUser = SharedTestDataADM.superUser,
          apiRequestID = UUID.randomUUID
        )

        val response2 = expectMsgType[UserOperationResponseADM](timeout)
        response2.user.familyName should equal("Duck")

        /* User information is updated by a system admin */
        appActor ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            givenName = Some(GivenName.make(SharedTestDataADM.normalUser.givenName).fold(e => throw e.head, v => v)),
            familyName = Some(FamilyName.make(SharedTestDataADM.normalUser.familyName).fold(e => throw e.head, v => v))
          ),
          requestingUser = SharedTestDataADM.superUser,
          apiRequestID = UUID.randomUUID
        )

        val response3 = expectMsgType[UserOperationResponseADM](timeout)
        response3.user.givenName should equal(SharedTestDataADM.normalUser.givenName)
        response3.user.familyName should equal(SharedTestDataADM.normalUser.familyName)
      }

      "return a 'DuplicateValueException' if the supplied 'username' is not unique" in {
        val duplicateUsername =
          Some(Username.make(SharedTestDataADM.anythingUser1.username).fold(e => throw e.head, v => v))
        appActor ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            username = duplicateUsername
          ),
          SharedTestDataADM.superUser,
          UUID.randomUUID
        )
        expectMsg(
          Failure(
            DuplicateValueException(
              s"User with the username '${SharedTestDataADM.anythingUser1.username}' already exists"
            )
          )
        )
      }

      "return a 'DuplicateValueException' if the supplied 'email' is not unique" in {
        val duplicateEmail = Some(Email.make(SharedTestDataADM.anythingUser1.email).fold(e => throw e.head, v => v))
        appActor ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            email = duplicateEmail
          ),
          SharedTestDataADM.superUser,
          UUID.randomUUID
        )
        expectMsg(
          Failure(
            DuplicateValueException(s"User with the email '${SharedTestDataADM.anythingUser1.email}' already exists")
          )
        )
      }

      "UPDATE the user's password (by himself)" in {
        val requesterPassword = Password.make("test").fold(e => throw e.head, v => v)
        val newPassword       = Password.make("test123456").fold(e => throw e.head, v => v)
        appActor ! UserChangePasswordRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdatePasswordPayload = UserUpdatePasswordPayloadADM(
            requesterPassword = requesterPassword,
            newPassword = newPassword
          ),
          requestingUser = SharedTestDataADM.normalUser,
          apiRequestID = UUID.randomUUID()
        )

        expectMsgType[UserOperationResponseADM](timeout)

        // need to be able to authenticate credentials with new password
        val resF = Authenticator.authenticateCredentialsV2(
          credentials = Some(
            KnoraCredentialsV2
              .KnoraPasswordCredentialsV2(UserIdentifierADM(maybeEmail = Some(normalUser.email)), "test123456")
          ),
          appConfig
        )(system, appActor, executionContext)

        resF map { res =>
          assert(res)
        }
      }

      "UPDATE the user's password (by a system admin)" in {
        val requesterPassword = Password.make("test").fold(e => throw e.head, v => v)
        val newPassword       = Password.make("test654321").fold(e => throw e.head, v => v)

        appActor ! UserChangePasswordRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdatePasswordPayload = UserUpdatePasswordPayloadADM(
            requesterPassword = requesterPassword,
            newPassword = newPassword
          ),
          requestingUser = SharedTestDataADM.rootUser,
          apiRequestID = UUID.randomUUID()
        )

        expectMsgType[UserOperationResponseADM](timeout)

        // need to be able to authenticate credentials with new password
        val resF = Authenticator.authenticateCredentialsV2(
          credentials = Some(
            KnoraCredentialsV2
              .KnoraPasswordCredentialsV2(UserIdentifierADM(maybeEmail = Some(normalUser.email)), "test654321")
          ),
          appConfig
        )(system, appActor, executionContext)

        resF map { res =>
          assert(res)
        }
      }

      "UPDATE the user's status, (deleting) making him inactive " in {
        appActor ! UserChangeStatusRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          status = UserStatus.make(false).fold(error => throw error.head, value => value),
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        val response1 = expectMsgType[UserOperationResponseADM](timeout)
        response1.user.status should equal(false)

        appActor ! UserChangeStatusRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          status = UserStatus.make(true).fold(error => throw error.head, value => value),
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        val response2 = expectMsgType[UserOperationResponseADM](timeout)
        response2.user.status should equal(true)
      }

      "UPDATE the user's system admin membership" in {
        appActor ! UserChangeSystemAdminMembershipStatusRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          systemAdmin = SystemAdmin.make(true).fold(e => throw e.head, v => v),
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        val response1 = expectMsgType[UserOperationResponseADM](timeout)
        response1.user.isSystemAdmin should equal(true)

        appActor ! UserChangeSystemAdminMembershipStatusRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          systemAdmin = SystemAdmin.make(false).fold(e => throw e.head, v => v),
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        val response2 = expectMsgType[UserOperationResponseADM](timeout)
        response2.user.permissions.isSystemAdmin should equal(false)
      }

      "return a 'ForbiddenException' if the user requesting update is not the user itself or system admin" in {
        /* User information is updated by other normal user */
        appActor ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.superUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            email = None,
            givenName = Some(GivenName.make("Donald").fold(e => throw e.head, v => v)),
            familyName = None,
            lang = None
          ),
          requestingUser = SharedTestDataADM.normalUser,
          UUID.randomUUID
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException("User information can only be changed by the user itself or a system administrator")
          )
        )

        /* Password is updated by other normal user */
        appActor ! UserChangePasswordRequestADM(
          userIri = SharedTestDataADM.superUser.id,
          userUpdatePasswordPayload = UserUpdatePasswordPayloadADM(
            requesterPassword = Password.make("test").fold(e => throw e.head, v => v),
            newPassword = Password.make("test123456").fold(e => throw e.head, v => v)
          ),
          requestingUser = SharedTestDataADM.normalUser,
          UUID.randomUUID
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException("User's password can only be changed by the user itself or a system administrator")
          )
        )

        /* Status is updated by other normal user */
        appActor ! UserChangeStatusRequestADM(
          userIri = SharedTestDataADM.superUser.id,
          status = UserStatus.make(false).fold(error => throw error.head, value => value),
          requestingUser = SharedTestDataADM.normalUser,
          UUID.randomUUID
        )
        expectMsg(
          timeout,
          Failure(ForbiddenException("User's status can only be changed by the user itself or a system administrator"))
        )

        /* System admin group membership */
        appActor ! UserChangeSystemAdminMembershipStatusRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          systemAdmin = SystemAdmin.make(true).fold(e => throw e.head, v => v),
          requestingUser = SharedTestDataADM.normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(ForbiddenException("User's system admin membership can only be changed by a system administrator"))
        )
      }

      "return 'BadRequest' if system user is requested to change" in {
        appActor ! UserChangeStatusRequestADM(
          userIri = KnoraSystemInstances.Users.SystemUser.id,
          status = UserStatus.make(false).fold(error => throw error.head, value => value),
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        expectMsg(timeout, Failure(BadRequestException("Changes to built-in users are not allowed.")))
      }

      "return 'BadRequest' if anonymous user is requested to change" in {
        appActor ! UserChangeStatusRequestADM(
          userIri = KnoraSystemInstances.Users.AnonymousUser.id,
          status = UserStatus.make(false).fold(error => throw error.head, value => value),
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        expectMsg(timeout, Failure(BadRequestException("Changes to built-in users are not allowed.")))
      }
    }

    "asked to update the user's project membership" should {
      "ADD user to project" in {

        // get current project memberships
        appActor ! UserProjectMembershipsGetRequestADM(normalUser.id, rootUser)
        val membershipsBeforeUpdate = expectMsgType[UserProjectMembershipsGetResponseADM](timeout)
        membershipsBeforeUpdate.projects should equal(Seq())

        // add user to images project (00FF)
        appActor ! UserProjectMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          rootUser,
          UUID.randomUUID()
        )
        val membershipUpdateResponse = expectMsgType[UserOperationResponseADM](timeout)

        appActor ! UserProjectMembershipsGetRequestADM(normalUser.id, rootUser)
        val membershipsAfterUpdate = expectMsgType[UserProjectMembershipsGetResponseADM](timeout)
        membershipsAfterUpdate.projects should equal(Seq(imagesProject))

        appActor ! ProjectMembersGetRequestADM(
          ProjectIdentifierADM.Iri(imagesProjectIri),
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )

        val received: ProjectMembersGetResponseADM = expectMsgType[ProjectMembersGetResponseADM](timeout)

        received.members.map(_.id) should contain(normalUser.id)
      }

      "not ADD user to project as project admin of another project" in {
        // get current project memberships
        appActor ! UserProjectMembershipsGetRequestADM(normalUser.id, rootUser)
        val membershipsBeforeUpdate = expectMsgType[UserProjectMembershipsGetResponseADM](timeout)
        membershipsBeforeUpdate.projects.map(_.id).sorted should equal(Seq(imagesProject.id).sorted)

        // try to add user to incunabula project but as project admin of another project
        appActor ! UserProjectMembershipAddRequestADM(
          normalUser.id,
          incunabulaProject.id,
          anythingAdminUser,
          UUID.randomUUID()
        )

        expectMsg(
          timeout,
          Failure(
            ForbiddenException("User's project membership can only be changed by a project or system administrator")
          )
        )

        // check that the user is still only member of one project
        appActor ! UserProjectMembershipsGetRequestADM(normalUser.id, rootUser)

        val membershipsAfterUpdate = expectMsgType[UserProjectMembershipsGetResponseADM](timeout)
        membershipsAfterUpdate.projects.map(_.id).sorted should equal(Seq(imagesProject.id).sorted)

        // check that the user was not added to the project
        appActor ! ProjectMembersGetRequestADM(
          ProjectIdentifierADM.Iri(incunabulaProjectIri),
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        val received = expectMsgType[ProjectMembersGetResponseADM](timeout)

        received.members.map(_.id) should not contain (normalUser.id)
      }

      "ADD user to project as project admin" in {
        // get current project memberships
        appActor ! UserProjectMembershipsGetRequestADM(normalUser.id, rootUser)
        val membershipsBeforeUpdate = expectMsgType[UserProjectMembershipsGetResponseADM](timeout)
        membershipsBeforeUpdate.projects.map(_.id).sorted should equal(Seq(imagesProject.id).sorted)

        // add user to images project (00FF)
        appActor ! UserProjectMembershipAddRequestADM(
          normalUser.id,
          incunabulaProject.id,
          incunabulaProjectAdminUser,
          UUID.randomUUID()
        )

        val membershipUpdateResponse = expectMsgType[UserOperationResponseADM](timeout)

        appActor ! UserProjectMembershipsGetRequestADM(normalUser.id, rootUser)
        val membershipsAfterUpdate = expectMsgType[UserProjectMembershipsGetResponseADM](timeout)
        membershipsAfterUpdate.projects.map(_.id).sorted should equal(
          Seq(imagesProject.id, incunabulaProject.id).sorted
        )

        appActor ! ProjectMembersGetRequestADM(
          ProjectIdentifierADM.Iri(incunabulaProjectIri),
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        val received = expectMsgType[ProjectMembersGetResponseADM](timeout)

        received.members.map(_.id) should contain(normalUser.id)
      }

      "DELETE user from project and also as project admin" in {
        // check project memberships (user should be member of images and incunabula projects)
        appActor ! UserProjectMembershipsGetRequestADM(normalUser.id, rootUser)
        val membershipsBeforeUpdate = expectMsgType[UserProjectMembershipsGetResponseADM](timeout)
        membershipsBeforeUpdate.projects.map(_.id).sorted should equal(
          Seq(imagesProject.id, incunabulaProject.id).sorted
        )

        // add user as project admin to images project
        appActor ! UserProjectAdminMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          rootUser,
          UUID.randomUUID()
        )

        expectMsgType[UserOperationResponseADM](timeout)

        // verify that the user has been added as project admin to the images project
        appActor ! UserProjectAdminMembershipsGetRequestADM(
          normalUser.id,
          rootUser,
          UUID.randomUUID()
        )
        val projectAdminMembershipsBeforeUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseADM](timeout)
        projectAdminMembershipsBeforeUpdate.projects.map(_.id).sorted should equal(
          Seq(imagesProject.id).sorted
        )

        // remove the user as member of the images project
        appActor ! UserProjectMembershipRemoveRequestADM(
          normalUser.id,
          imagesProject.id,
          rootUser,
          UUID.randomUUID()
        )
        expectMsgType[UserOperationResponseADM](timeout)

        // verify that the user has been removed as project member of the images project
        appActor ! UserProjectMembershipsGetRequestADM(normalUser.id, rootUser)
        val membershipsAfterUpdate = expectMsgType[UserProjectMembershipsGetResponseADM](timeout)
        membershipsAfterUpdate.projects should equal(Seq(incunabulaProject))

        // this should also have removed him as project admin from images project
        appActor ! UserProjectAdminMembershipsGetRequestADM(
          normalUser.id,
          rootUser,
          UUID.randomUUID()
        )
        val projectAdminMembershipsAfterUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseADM](timeout)
        projectAdminMembershipsAfterUpdate.projects should equal(Seq())

        // also check that the user has been removed from the project's list of users
        appActor ! ProjectMembersGetRequestADM(
          ProjectIdentifierADM.Iri(imagesProjectIri),
          requestingUser = rootUser
        )
        val received: ProjectMembersGetResponseADM = expectMsgType[ProjectMembersGetResponseADM](timeout)

        received.members should not contain normalUser.ofType(UserInformationTypeADM.Restricted)

      }

      "return a 'ForbiddenException' if the user requesting update is not the project or system admin" in {
        /* User is added to a project by a normal user */
        appActor ! UserProjectMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException("User's project membership can only be changed by a project or system administrator")
          )
        )

        /* User is removed from a project by a normal user */
        appActor ! UserProjectMembershipRemoveRequestADM(
          normalUser.id,
          imagesProject.id,
          normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException("User's project membership can only be changed by a project or system administrator")
          )
        )
      }

    }

    "asked to update the user's project admin group membership" should {
      "Not ADD user to project admin group if he is not a member of that project" in {
        // get the current project admin memberships (should be empty)
        appActor ! UserProjectAdminMembershipsGetRequestADM(
          normalUser.id,
          rootUser,
          UUID.randomUUID()
        )
        val membershipsBeforeUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseADM](timeout)
        membershipsBeforeUpdate.projects should equal(Seq())

        // try to add user as project admin to images project (expected to fail because he is not a member of the project)
        appActor ! UserProjectAdminMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          rootUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(
            BadRequestException(
              "User http://rdfh.ch/users/normaluser is not a member of project http://rdfh.ch/projects/MTvoB0EJRrqovzRkWXqfkA. A user needs to be a member of the project to be added as project admin."
            )
          )
        )
      }

      "ADD user to project admin group" in {
        // get the current project admin memberships (should be empty)
        appActor ! UserProjectAdminMembershipsGetRequestADM(
          normalUser.id,
          rootUser,
          UUID.randomUUID()
        )
        val membershipsBeforeUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseADM](timeout)
        membershipsBeforeUpdate.projects should equal(Seq())

        // add user as project member to images project
        appActor ! UserProjectMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          rootUser,
          UUID.randomUUID()
        )
        expectMsgType[UserOperationResponseADM](timeout)

        // add user as project admin to images project
        appActor ! UserProjectAdminMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          rootUser,
          UUID.randomUUID()
        )

        expectMsgType[UserOperationResponseADM](timeout)

        // get the updated project admin memberships (should contain images project)
        appActor ! UserProjectAdminMembershipsGetRequestADM(
          normalUser.id,
          rootUser,
          UUID.randomUUID()
        )
        val membershipsAfterUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseADM](timeout)
        membershipsAfterUpdate.projects should equal(Seq(imagesProject))

        // get project admins for images project (should contain normal user)
        appActor ! ProjectAdminMembersGetRequestADM(
          ProjectIdentifierADM.Iri(imagesProjectIri),
          requestingUser = rootUser
        )
        val received: ProjectAdminMembersGetResponseADM = expectMsgType[ProjectAdminMembersGetResponseADM](timeout)

        received.members.map(_.id) should contain(normalUser.id)
      }

      "DELETE user from project admin group" in {
        appActor ! UserProjectAdminMembershipsGetRequestADM(
          normalUser.id,
          rootUser,
          UUID.randomUUID()
        )
        val membershipsBeforeUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseADM](timeout)
        membershipsBeforeUpdate.projects should equal(Seq(imagesProject))

        appActor ! UserProjectAdminMembershipRemoveRequestADM(
          normalUser.id,
          imagesProject.id,
          rootUser,
          UUID.randomUUID()
        )
        expectMsgType[UserOperationResponseADM](timeout)

        appActor ! UserProjectAdminMembershipsGetRequestADM(
          normalUser.id,
          rootUser,
          UUID.randomUUID()
        )
        val membershipsAfterUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseADM](timeout)
        membershipsAfterUpdate.projects should equal(Seq())

        appActor ! ProjectAdminMembersGetRequestADM(
          ProjectIdentifierADM.Iri(imagesProjectIri),
          requestingUser = rootUser
        )
        val received: ProjectAdminMembersGetResponseADM = expectMsgType[ProjectAdminMembersGetResponseADM](timeout)

        received.members should not contain normalUser.ofType(UserInformationTypeADM.Restricted)
      }

      "return a 'ForbiddenException' if the user requesting update is not the project or system admin" in {
        /* User is added to a project by a normal user */
        appActor ! UserProjectAdminMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException(
              "User's project admin membership can only be changed by a project or system administrator"
            )
          )
        )

        /* User is removed from a project by a normal user */
        appActor ! UserProjectAdminMembershipRemoveRequestADM(
          normalUser.id,
          imagesProject.id,
          normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException(
              "User's project admin membership can only be changed by a project or system administrator"
            )
          )
        )
      }

    }

    "asked to update the user's group membership" should {
      "ADD user to group" in {
        appActor ! UserGroupMembershipsGetRequestADM(normalUser.id, rootUser)
        val membershipsBeforeUpdate = expectMsgType[UserGroupMembershipsGetResponseADM](timeout)
        membershipsBeforeUpdate.groups should equal(Seq())

        appActor ! UserGroupMembershipAddRequestADM(
          normalUser.id,
          imagesReviewerGroup.id,
          rootUser,
          UUID.randomUUID()
        )
        expectMsgType[UserOperationResponseADM](timeout)

        appActor ! UserGroupMembershipsGetRequestADM(normalUser.id, rootUser)
        val membershipsAfterUpdate = expectMsgType[UserGroupMembershipsGetResponseADM](timeout)
        membershipsAfterUpdate.groups.map(_.id) should equal(Seq(imagesReviewerGroup.id))

        appActor ! GroupMembersGetRequestADM(
          groupIri = imagesReviewerGroup.id,
          requestingUser = rootUser
        )
        val received: GroupMembersGetResponseADM = expectMsgType[GroupMembersGetResponseADM](timeout)

        received.members.map(_.id) should contain(normalUser.id)
      }

      "DELETE user from group" in {
        appActor ! UserGroupMembershipsGetRequestADM(normalUser.id, rootUser)
        val membershipsBeforeUpdate = expectMsgType[UserGroupMembershipsGetResponseADM](timeout)
        membershipsBeforeUpdate.groups.map(_.id) should equal(Seq(imagesReviewerGroup.id))

        appActor ! UserGroupMembershipRemoveRequestADM(
          normalUser.id,
          imagesReviewerGroup.id,
          rootUser,
          UUID.randomUUID()
        )
        expectMsgType[UserOperationResponseADM](timeout)

        appActor ! UserGroupMembershipsGetRequestADM(normalUser.id, rootUser)
        val membershipsAfterUpdate = expectMsgType[UserGroupMembershipsGetResponseADM](timeout)
        membershipsAfterUpdate.groups should equal(Seq())

        appActor ! GroupMembersGetRequestADM(
          groupIri = imagesReviewerGroup.id,
          requestingUser = rootUser
        )
        val received: GroupMembersGetResponseADM = expectMsgType[GroupMembersGetResponseADM](timeout)

        received.members.map(_.id) should not contain normalUser.id
      }

      "return a 'ForbiddenException' if the user requesting update is not the project or system admin" in {
        /* User is added to a project by a normal user */
        appActor ! UserGroupMembershipAddRequestADM(
          normalUser.id,
          imagesReviewerGroup.id,
          normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException("User's group membership can only be changed by a project or system administrator")
          )
        )

        /* User is removed from a project by a normal user */
        appActor ! UserGroupMembershipRemoveRequestADM(
          normalUser.id,
          imagesReviewerGroup.id,
          normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException("User's group membership can only be changed by a project or system administrator")
          )
        )
      }
    }
  }
}
