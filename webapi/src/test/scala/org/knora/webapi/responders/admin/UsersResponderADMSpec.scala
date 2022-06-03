/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import akka.actor.Status.Failure
import akka.testkit.ImplicitSender
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import dsp.valueobjects.User._
import dsp.valueobjects.V2
import org.knora.webapi._
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.exceptions.DuplicateValueException
import org.knora.webapi.exceptions.ForbiddenException
import org.knora.webapi.exceptions.NotFoundException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupMembersGetRequestADM
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupMembersGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraPasswordCredentialsV2
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.sharedtestdata.SharedTestDataADM

import java.util.UUID
import scala.concurrent.duration._

object UsersResponderADMSpec {
  val config: Config = ConfigFactory.parseString("""
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
         app.use-redis-cache = true
        """.stripMargin)
}

/**
 * This spec is used to test the messages received by the [[UsersResponderADM]] actor.
 */
class UsersResponderADMSpec extends CoreSpec(UsersResponderADMSpec.config) with ImplicitSender with Authenticator {

  private val timeout: FiniteDuration = 8.seconds

  private val rootUser          = SharedTestDataADM.rootUser
  private val anythingAdminUser = SharedTestDataADM.anythingAdminUser
  private val normalUser        = SharedTestDataADM.normalUser

  private val incunabulaUser = SharedTestDataADM.incunabulaProjectAdminUser

  private val imagesProject       = SharedTestDataADM.imagesProject
  private val imagesReviewerGroup = SharedTestDataADM.imagesReviewerGroup

  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  "The UsersResponder " when {
    "asked about all users" should {
      "return a list if asked by SystemAdmin" in {
        responderManager ! UsersGetRequestADM(
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = rootUser
        )
        val response = expectMsgType[UsersGetResponseADM](timeout)
        response.users.nonEmpty should be(true)
        response.users.size should be(18)
      }

      "return a list if asked by ProjectAdmin" in {
        responderManager ! UsersGetRequestADM(
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = anythingAdminUser
        )
        val response = expectMsgType[UsersGetResponseADM](timeout)
        response.users.nonEmpty should be(true)
        response.users.size should be(18)
      }

      "return 'ForbiddenException' if asked by normal user'" in {
        responderManager ! UsersGetRequestADM(
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = normalUser
        )
        expectMsg(timeout, Failure(ForbiddenException("ProjectAdmin or SystemAdmin permissions are required.")))
      }

      "not return the system and anonymous users" in {
        responderManager ! UsersGetRequestADM(
          featureFactoryConfig = defaultFeatureFactoryConfig,
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
        responderManager ! UserGetADM(
          identifier = UserIdentifierADM(maybeIri = Some(rootUser.id)),
          userInformationTypeADM = UserInformationTypeADM.Full,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Some(rootUser.ofType(UserInformationTypeADM.Full)))
      }

      "return a profile if the user (incunabula user) is known" in {
        responderManager ! UserGetADM(
          identifier = UserIdentifierADM(maybeIri = Some(incunabulaUser.id)),
          userInformationTypeADM = UserInformationTypeADM.Full,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Some(incunabulaUser.ofType(UserInformationTypeADM.Full)))
      }

      "return 'NotFoundException' when the user is unknown" in {
        responderManager ! UserGetRequestADM(
          identifier = UserIdentifierADM(maybeIri = Some("http://rdfh.ch/users/notexisting")),
          userInformationTypeADM = UserInformationTypeADM.Full,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Failure(NotFoundException(s"User 'http://rdfh.ch/users/notexisting' not found")))
      }

      "return 'None' when the user is unknown" in {
        responderManager ! UserGetADM(
          identifier = UserIdentifierADM(maybeIri = Some("http://rdfh.ch/users/notexisting")),
          userInformationTypeADM = UserInformationTypeADM.Full,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(None)
      }
    }

    "asked about an user identified by 'email'" should {
      "return a profile if the user (root user) is known" in {
        responderManager ! UserGetADM(
          identifier = UserIdentifierADM(maybeEmail = Some(rootUser.email)),
          userInformationTypeADM = UserInformationTypeADM.Full,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Some(rootUser.ofType(UserInformationTypeADM.Full)))
      }

      "return a profile if the user (incunabula user) is known" in {
        responderManager ! UserGetADM(
          identifier = UserIdentifierADM(maybeEmail = Some(incunabulaUser.email)),
          userInformationTypeADM = UserInformationTypeADM.Full,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Some(incunabulaUser.ofType(UserInformationTypeADM.Full)))
      }

      "return 'NotFoundException' when the user is unknown" in {
        responderManager ! UserGetRequestADM(
          identifier = UserIdentifierADM(maybeEmail = Some("userwrong@example.com")),
          userInformationTypeADM = UserInformationTypeADM.Full,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Failure(NotFoundException(s"User 'userwrong@example.com' not found")))
      }

      "return 'None' when the user is unknown" in {
        responderManager ! UserGetADM(
          identifier = UserIdentifierADM(maybeEmail = Some("userwrong@example.com")),
          userInformationTypeADM = UserInformationTypeADM.Full,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(None)
      }
    }

    "asked about an user identified by 'username'" should {
      "return a profile if the user (root user) is known" in {
        responderManager ! UserGetADM(
          identifier = UserIdentifierADM(maybeUsername = Some(rootUser.username)),
          userInformationTypeADM = UserInformationTypeADM.Full,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Some(rootUser.ofType(UserInformationTypeADM.Full)))
      }

      "return a profile if the user (incunabula user) is known" in {
        responderManager ! UserGetADM(
          identifier = UserIdentifierADM(maybeUsername = Some(incunabulaUser.username)),
          userInformationTypeADM = UserInformationTypeADM.Full,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Some(incunabulaUser.ofType(UserInformationTypeADM.Full)))
      }

      "return 'NotFoundException' when the user is unknown" in {
        responderManager ! UserGetRequestADM(
          identifier = UserIdentifierADM(maybeUsername = Some("userwrong")),
          userInformationTypeADM = UserInformationTypeADM.Full,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Failure(NotFoundException(s"User 'userwrong' not found")))
      }

      "return 'None' when the user is unknown" in {
        responderManager ! UserGetADM(
          identifier = UserIdentifierADM(maybeUsername = Some("userwrong")),
          userInformationTypeADM = UserInformationTypeADM.Full,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(None)
      }
    }

    "asked to create a new user" should {
      "CREATE the user and return it's profile if the supplied email is unique " in {
        responderManager ! UserCreateRequestADM(
          userCreatePayloadADM = UserCreatePayloadADM(
            username = Username.make("donald.duck").fold(e => throw e.head, v => v),
            email = Email.make("donald.duck@example.com").fold(e => throw e.head, v => v),
            givenName = GivenName.make("Donald").fold(e => throw e.head, v => v),
            familyName = FamilyName.make("Duck").fold(e => throw e.head, v => v),
            password = Password.make("test").fold(e => throw e.head, v => v),
            status = UserStatus.make(true).fold(error => throw error.head, value => value),
            lang = LanguageCode.make("en").fold(e => throw e.head, v => v),
            systemAdmin = SystemAdmin.make(false).fold(e => throw e.head, v => v)
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
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
        responderManager ! UserCreateRequestADM(
          userCreatePayloadADM = UserCreatePayloadADM(
            username = Username.make("root").fold(e => throw e.head, v => v),
            email = Email.make("root2@example.com").fold(e => throw e.head, v => v),
            givenName = GivenName.make("Donald").fold(e => throw e.head, v => v),
            familyName = FamilyName.make("Duck").fold(e => throw e.head, v => v),
            password = Password.make("test").fold(e => throw e.head, v => v),
            status = UserStatus.make(true).fold(error => throw error.head, value => value),
            lang = LanguageCode.make("en").fold(e => throw e.head, v => v),
            systemAdmin = SystemAdmin.make(false).fold(e => throw e.head, v => v)
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          SharedTestDataADM.anonymousUser,
          UUID.randomUUID
        )
        expectMsg(Failure(DuplicateValueException(s"User with the username 'root' already exists")))
      }

      "return a 'DuplicateValueException' if the supplied 'email' is not unique" in {
        responderManager ! UserCreateRequestADM(
          userCreatePayloadADM = UserCreatePayloadADM(
            username = Username.make("root2").fold(e => throw e.head, v => v),
            email = Email.make("root@example.com").fold(e => throw e.head, v => v),
            givenName = GivenName.make("Donald").fold(e => throw e.head, v => v),
            familyName = FamilyName.make("Duck").fold(e => throw e.head, v => v),
            password = Password.make("test").fold(e => throw e.head, v => v),
            status = UserStatus.make(true).fold(error => throw error.head, value => value),
            lang = LanguageCode.make("en").fold(e => throw e.head, v => v),
            systemAdmin = SystemAdmin.make(false).fold(e => throw e.head, v => v)
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          SharedTestDataADM.anonymousUser,
          UUID.randomUUID
        )
        expectMsg(Failure(DuplicateValueException(s"User with the email 'root@example.com' already exists")))
      }
    }

    "asked to update a user" should {
      "UPDATE the user's basic information" in {
        /* User information is updated by the user */
        responderManager ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            givenName = Some(GivenName.make("Donald").fold(e => throw e.head, v => v))
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.normalUser,
          apiRequestID = UUID.randomUUID
        )

        val response1 = expectMsgType[UserOperationResponseADM](timeout)
        response1.user.givenName should equal("Donald")

        /* User information is updated by a system admin */
        responderManager ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            familyName = Some(FamilyName.make("Duck").fold(e => throw e.head, v => v))
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.superUser,
          apiRequestID = UUID.randomUUID
        )

        val response2 = expectMsgType[UserOperationResponseADM](timeout)
        response2.user.familyName should equal("Duck")

        /* User information is updated by a system admin */
        responderManager ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            givenName = Some(GivenName.make(SharedTestDataADM.normalUser.givenName).fold(e => throw e.head, v => v)),
            familyName = Some(FamilyName.make(SharedTestDataADM.normalUser.familyName).fold(e => throw e.head, v => v))
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
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
        responderManager ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            username = duplicateUsername
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
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
        responderManager ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            email = duplicateEmail
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
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
        responderManager ! UserChangePasswordRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdatePasswordPayload = UserUpdatePasswordPayloadADM(
            requesterPassword = requesterPassword,
            newPassword = newPassword
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.normalUser,
          apiRequestID = UUID.randomUUID()
        )

        expectMsgType[UserOperationResponseADM](timeout)

        // need to be able to authenticate credentials with new password
        val resF = Authenticator.authenticateCredentialsV2(
          credentials =
            Some(KnoraPasswordCredentialsV2(UserIdentifierADM(maybeEmail = Some(normalUser.email)), "test123456")),
          featureFactoryConfig = defaultFeatureFactoryConfig
        )(system, responderManager, executionContext)

        resF map { res =>
          assert(res)
        }
      }

      "UPDATE the user's password (by a system admin)" in {
        val requesterPassword = Password.make("test").fold(e => throw e.head, v => v)
        val newPassword       = Password.make("test654321").fold(e => throw e.head, v => v)

        responderManager ! UserChangePasswordRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdatePasswordPayload = UserUpdatePasswordPayloadADM(
            requesterPassword = requesterPassword,
            newPassword = newPassword
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.rootUser,
          apiRequestID = UUID.randomUUID()
        )

        expectMsgType[UserOperationResponseADM](timeout)

        // need to be able to authenticate credentials with new password
        val resF = Authenticator.authenticateCredentialsV2(
          credentials =
            Some(KnoraPasswordCredentialsV2(UserIdentifierADM(maybeEmail = Some(normalUser.email)), "test654321")),
          featureFactoryConfig = defaultFeatureFactoryConfig
        )(system, responderManager, executionContext)

        resF map { res =>
          assert(res)
        }
      }

      "UPDATE the user's status, (deleting) making him inactive " in {
        responderManager ! UserChangeStatusRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          status = UserStatus.make(false).fold(error => throw error.head, value => value),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        val response1 = expectMsgType[UserOperationResponseADM](timeout)
        response1.user.status should equal(false)

        responderManager ! UserChangeStatusRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          status = UserStatus.make(true).fold(error => throw error.head, value => value),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        val response2 = expectMsgType[UserOperationResponseADM](timeout)
        response2.user.status should equal(true)
      }

      "UPDATE the user's system admin membership" in {
        responderManager ! UserChangeSystemAdminMembershipStatusRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          systemAdmin = SystemAdmin.make(true).fold(e => throw e.head, v => v),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        val response1 = expectMsgType[UserOperationResponseADM](timeout)
        response1.user.isSystemAdmin should equal(true)

        responderManager ! UserChangeSystemAdminMembershipStatusRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          systemAdmin = SystemAdmin.make(false).fold(e => throw e.head, v => v),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        val response2 = expectMsgType[UserOperationResponseADM](timeout)
        response2.user.permissions.isSystemAdmin should equal(false)
      }

      "return a 'ForbiddenException' if the user requesting update is not the user itself or system admin" in {
        /* User information is updated by other normal user */
        responderManager ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.superUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            email = None,
            givenName = Some(GivenName.make("Donald").fold(e => throw e.head, v => v)),
            familyName = None,
            lang = None
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
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
        responderManager ! UserChangePasswordRequestADM(
          userIri = SharedTestDataADM.superUser.id,
          userUpdatePasswordPayload = UserUpdatePasswordPayloadADM(
            requesterPassword = Password.make("test").fold(e => throw e.head, v => v),
            newPassword = Password.make("test123456").fold(e => throw e.head, v => v)
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
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
        responderManager ! UserChangeStatusRequestADM(
          userIri = SharedTestDataADM.superUser.id,
          status = UserStatus.make(false).fold(error => throw error.head, value => value),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.normalUser,
          UUID.randomUUID
        )
        expectMsg(
          timeout,
          Failure(ForbiddenException("User's status can only be changed by the user itself or a system administrator"))
        )

        /* System admin group membership */
        responderManager ! UserChangeSystemAdminMembershipStatusRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          systemAdmin = SystemAdmin.make(true).fold(e => throw e.head, v => v),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(ForbiddenException("User's system admin membership can only be changed by a system administrator"))
        )
      }

      "return 'BadRequest' if system user is requested to change" in {
        responderManager ! UserChangeStatusRequestADM(
          userIri = KnoraSystemInstances.Users.SystemUser.id,
          status = UserStatus.make(false).fold(error => throw error.head, value => value),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        expectMsg(timeout, Failure(BadRequestException("Changes to built-in users are not allowed.")))
      }

      "return 'BadRequest' if anonymous user is requested to change" in {
        responderManager ! UserChangeStatusRequestADM(
          userIri = KnoraSystemInstances.Users.AnonymousUser.id,
          status = UserStatus.make(false).fold(error => throw error.head, value => value),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        expectMsg(timeout, Failure(BadRequestException("Changes to built-in users are not allowed.")))
      }
    }

    "asked to update the user's project membership" should {
      "ADD user to project" in {

        // get current project memberships
        responderManager ! UserProjectMembershipsGetRequestADM(normalUser.id, defaultFeatureFactoryConfig, rootUser)
        val membershipsBeforeUpdate = expectMsgType[UserProjectMembershipsGetResponseADM](timeout)
        membershipsBeforeUpdate.projects should equal(Seq())

        // add user to images project (00FF)
        responderManager ! UserProjectMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          defaultFeatureFactoryConfig,
          rootUser,
          UUID.randomUUID()
        )
        val membershipUpdateResponse = expectMsgType[UserOperationResponseADM](timeout)

        responderManager ! UserProjectMembershipsGetRequestADM(normalUser.id, defaultFeatureFactoryConfig, rootUser)
        val membershipsAfterUpdate = expectMsgType[UserProjectMembershipsGetResponseADM](timeout)
        membershipsAfterUpdate.projects should equal(Seq(imagesProject))

        responderManager ! ProjectMembersGetRequestADM(
          ProjectIdentifierADM(maybeIri = Some(imagesProject.id)),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        val received: ProjectMembersGetResponseADM = expectMsgType[ProjectMembersGetResponseADM](timeout)

        received.members.map(_.id) should contain(normalUser.id)
      }

      "DELETE user from project" in {
        responderManager ! UserProjectMembershipsGetRequestADM(normalUser.id, defaultFeatureFactoryConfig, rootUser)
        val membershipsBeforeUpdate = expectMsgType[UserProjectMembershipsGetResponseADM](timeout)
        membershipsBeforeUpdate.projects should equal(Seq(imagesProject))

        responderManager ! UserProjectMembershipRemoveRequestADM(
          normalUser.id,
          imagesProject.id,
          defaultFeatureFactoryConfig,
          rootUser,
          UUID.randomUUID()
        )
        expectMsgType[UserOperationResponseADM](timeout)

        responderManager ! UserProjectMembershipsGetRequestADM(normalUser.id, defaultFeatureFactoryConfig, rootUser)
        val membershipsAfterUpdate = expectMsgType[UserProjectMembershipsGetResponseADM](timeout)
        membershipsAfterUpdate.projects should equal(Seq())

        responderManager ! ProjectMembersGetRequestADM(
          ProjectIdentifierADM(maybeIri = Some(imagesProject.id)),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = rootUser
        )
        val received: ProjectMembersGetResponseADM = expectMsgType[ProjectMembersGetResponseADM](timeout)

        received.members should not contain normalUser.ofType(UserInformationTypeADM.Restricted)
      }

      "return a 'ForbiddenException' if the user requesting update is not the project or system admin" in {
        /* User is added to a project by a normal user */
        responderManager ! UserProjectMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          defaultFeatureFactoryConfig,
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
        responderManager ! UserProjectMembershipRemoveRequestADM(
          normalUser.id,
          imagesProject.id,
          defaultFeatureFactoryConfig,
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
      "ADD user to project admin group" in {
        responderManager ! UserProjectAdminMembershipsGetRequestADM(
          normalUser.id,
          defaultFeatureFactoryConfig,
          rootUser,
          UUID.randomUUID()
        )
        val membershipsBeforeUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseADM](timeout)
        membershipsBeforeUpdate.projects should equal(Seq())

        responderManager ! UserProjectAdminMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          defaultFeatureFactoryConfig,
          rootUser,
          UUID.randomUUID()
        )
        expectMsgType[UserOperationResponseADM](timeout)

        responderManager ! UserProjectAdminMembershipsGetRequestADM(
          normalUser.id,
          defaultFeatureFactoryConfig,
          rootUser,
          UUID.randomUUID()
        )
        val membershipsAfterUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseADM](timeout)
        membershipsAfterUpdate.projects should equal(Seq(imagesProject))

        responderManager ! ProjectAdminMembersGetRequestADM(
          ProjectIdentifierADM(maybeIri = Some(imagesProject.id)),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = rootUser
        )
        val received: ProjectAdminMembersGetResponseADM = expectMsgType[ProjectAdminMembersGetResponseADM](timeout)

        received.members should contain(normalUser.ofType(UserInformationTypeADM.Restricted))
      }

      "DELETE user from project admin group" in {
        responderManager ! UserProjectAdminMembershipsGetRequestADM(
          normalUser.id,
          defaultFeatureFactoryConfig,
          rootUser,
          UUID.randomUUID()
        )
        val membershipsBeforeUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseADM](timeout)
        membershipsBeforeUpdate.projects should equal(Seq(imagesProject))

        responderManager ! UserProjectAdminMembershipRemoveRequestADM(
          normalUser.id,
          imagesProject.id,
          defaultFeatureFactoryConfig,
          rootUser,
          UUID.randomUUID()
        )
        expectMsgType[UserOperationResponseADM](timeout)

        responderManager ! UserProjectAdminMembershipsGetRequestADM(
          normalUser.id,
          defaultFeatureFactoryConfig,
          rootUser,
          UUID.randomUUID()
        )
        val membershipsAfterUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseADM](timeout)
        membershipsAfterUpdate.projects should equal(Seq())

        responderManager ! ProjectAdminMembersGetRequestADM(
          ProjectIdentifierADM(maybeIri = Some(imagesProject.id)),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = rootUser
        )
        val received: ProjectAdminMembersGetResponseADM = expectMsgType[ProjectAdminMembersGetResponseADM](timeout)

        received.members should not contain normalUser.ofType(UserInformationTypeADM.Restricted)
      }

      "return a 'ForbiddenException' if the user requesting update is not the project or system admin" in {
        /* User is added to a project by a normal user */
        responderManager ! UserProjectAdminMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          defaultFeatureFactoryConfig,
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
        responderManager ! UserProjectAdminMembershipRemoveRequestADM(
          normalUser.id,
          imagesProject.id,
          defaultFeatureFactoryConfig,
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
        responderManager ! UserGroupMembershipsGetRequestADM(normalUser.id, defaultFeatureFactoryConfig, rootUser)
        val membershipsBeforeUpdate = expectMsgType[UserGroupMembershipsGetResponseADM](timeout)
        membershipsBeforeUpdate.groups should equal(Seq())

        responderManager ! UserGroupMembershipAddRequestADM(
          normalUser.id,
          imagesReviewerGroup.id,
          defaultFeatureFactoryConfig,
          rootUser,
          UUID.randomUUID()
        )
        expectMsgType[UserOperationResponseADM](timeout)

        responderManager ! UserGroupMembershipsGetRequestADM(normalUser.id, defaultFeatureFactoryConfig, rootUser)
        val membershipsAfterUpdate = expectMsgType[UserGroupMembershipsGetResponseADM](timeout)
        membershipsAfterUpdate.groups.map(_.id) should equal(Seq(imagesReviewerGroup.id))

        responderManager ! GroupMembersGetRequestADM(
          groupIri = imagesReviewerGroup.id,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = rootUser
        )
        val received: GroupMembersGetResponseADM = expectMsgType[GroupMembersGetResponseADM](timeout)

        received.members.map(_.id) should contain(normalUser.id)
      }

      "DELETE user from group" in {
        responderManager ! UserGroupMembershipsGetRequestADM(normalUser.id, defaultFeatureFactoryConfig, rootUser)
        val membershipsBeforeUpdate = expectMsgType[UserGroupMembershipsGetResponseADM](timeout)
        membershipsBeforeUpdate.groups.map(_.id) should equal(Seq(imagesReviewerGroup.id))

        responderManager ! UserGroupMembershipRemoveRequestADM(
          normalUser.id,
          imagesReviewerGroup.id,
          defaultFeatureFactoryConfig,
          rootUser,
          UUID.randomUUID()
        )
        expectMsgType[UserOperationResponseADM](timeout)

        responderManager ! UserGroupMembershipsGetRequestADM(normalUser.id, defaultFeatureFactoryConfig, rootUser)
        val membershipsAfterUpdate = expectMsgType[UserGroupMembershipsGetResponseADM](timeout)
        membershipsAfterUpdate.groups should equal(Seq())

        responderManager ! GroupMembersGetRequestADM(
          groupIri = imagesReviewerGroup.id,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = rootUser
        )
        val received: GroupMembersGetResponseADM = expectMsgType[GroupMembersGetResponseADM](timeout)

        received.members.map(_.id) should not contain normalUser.id
      }

      "return a 'ForbiddenException' if the user requesting update is not the project or system admin" in {
        /* User is added to a project by a normal user */
        responderManager ! UserGroupMembershipAddRequestADM(
          normalUser.id,
          imagesReviewerGroup.id,
          defaultFeatureFactoryConfig,
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
        responderManager ! UserGroupMembershipRemoveRequestADM(
          normalUser.id,
          imagesReviewerGroup.id,
          defaultFeatureFactoryConfig,
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
