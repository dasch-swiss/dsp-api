/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.usersmessages

import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages.{PermissionProfileType, PermissionsDataADM}
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder

object UsersMessagesADMSpec {
  val config: Config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * This spec is used to test the [[UserADM]] and [[UserIdentifierADM]] classes.
 */
class UsersMessagesADMSpec extends CoreSpec(UsersMessagesADMSpec.config) {

  private val id = SharedTestDataADM.rootUser.id
  private val username = SharedTestDataADM.rootUser.username
  private val email = SharedTestDataADM.rootUser.email
  private val password = SharedTestDataADM.rootUser.password
  private val token = SharedTestDataADM.rootUser.token
  private val givenName = SharedTestDataADM.rootUser.givenName
  private val familyName = SharedTestDataADM.rootUser.familyName
  private val status = SharedTestDataADM.rootUser.status
  private val lang = SharedTestDataADM.rootUser.lang
  private val groups = SharedTestDataADM.rootUser.groups
  private val projects = SharedTestDataADM.rootUser.projects
  private val sessionId = SharedTestDataADM.rootUser.sessionId
  private val permissions = SharedTestDataADM.rootUser.permissions

  private implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

  "The UserADM case class" should {
    "return a RESTRICTED UserADM when requested " in {
      val rootUser = UserADM(
        id = id,
        username = username,
        email = email,
        password = password,
        token = token,
        givenName = givenName,
        familyName = familyName,
        status = status,
        lang = lang,
        groups = groups,
        projects = projects,
        sessionId = sessionId,
        permissions = permissions
      )
      val rootUserRestricted = UserADM(
        id = id,
        username = username,
        email = email,
        password = None,
        token = None,
        givenName = givenName,
        familyName = familyName,
        status = status,
        lang = lang,
        groups = groups,
        projects = projects,
        sessionId = sessionId,
        permissions = permissions.ofType(PermissionProfileType.Restricted)
      )

      assert(rootUser.ofType(UserInformationTypeADM.Restricted) === rootUserRestricted)
    }

    "return true if user is ProjectAdmin in any project " in {
      assert(
        SharedTestDataADM.anythingAdminUser.permissions.isProjectAdminInAnyProject() === true,
        "user is not ProjectAdmin in any of his projects"
      )
    }

    "return false if user is not ProjectAdmin in any project " in {
      assert(
        SharedTestDataADM.anythingUser1.permissions.isProjectAdminInAnyProject() === false,
        "user is ProjectAdmin in one of his projects"
      )
    }

    "allow checking the SCrypt passwords" in {
      val encoder = new SCryptPasswordEncoder()
      val hp = encoder.encode("123456")
      val up = UserADM(
        id = "something",
        username = "something",
        email = "something",
        password = Some(hp),
        token = None,
        givenName = "something",
        familyName = "something",
        status = status,
        lang = lang,
        groups = groups,
        projects = projects,
        sessionId = sessionId,
        permissions = PermissionsDataADM()
      )

      // test SCrypt
      assert(encoder.matches("123456", encoder.encode("123456")))

      // test UserADM SCrypt usage
      assert(up.passwordMatch("123456"))
    }

    "allow checking the BCrypt passwords" in {
      val encoder = new BCryptPasswordEncoder()
      val hp = encoder.encode("123456")
      val up = UserADM(
        id = "something",
        username = "something",
        email = "something",
        password = Some(hp),
        token = None,
        givenName = "something",
        familyName = "something",
        status = status,
        lang = lang,
        groups = groups,
        projects = projects,
        sessionId = sessionId,
        permissions = PermissionsDataADM()
      )

      // test BCrypt
      assert(encoder.matches("123456", encoder.encode("123456")))

      // test UserADM BCrypt usage
      assert(up.passwordMatch("123456"))
    }

    "allow checking the password (2)" in {
      SharedTestDataADM.rootUser.passwordMatch("test") should equal(true)
    }

    "return isSelf for IRI" in {
      SharedTestDataADM.rootUser.isSelf(UserIdentifierADM(maybeIri = Some(SharedTestDataADM.rootUser.id)))
    }

    "return isSelf for email" in {
      SharedTestDataADM.rootUser.isSelf(UserIdentifierADM(maybeEmail = Some(SharedTestDataADM.rootUser.email)))
    }

    "return isSelf for username" in {
      SharedTestDataADM.rootUser.isSelf(UserIdentifierADM(maybeUsername = Some(SharedTestDataADM.rootUser.username)))
    }
  }

  "The UserIdentifierADM case class" should {

    "return the identifier type" in {

      val iriIdentifier = UserIdentifierADM(maybeIri = Some("http://rdfh.ch/users/root"))
      iriIdentifier.hasType should be(UserIdentifierType.Iri)

      val emailIdentifier = UserIdentifierADM(maybeEmail = Some("root@example.com"))
      emailIdentifier.hasType should be(UserIdentifierType.Email)

      val usernameIdentifier = UserIdentifierADM(maybeUsername = Some("root"))
      usernameIdentifier.hasType should be(UserIdentifierType.Username)
    }

    "check whether a user identified by email is the same as a user identified by username" in {
      val userEmail = "user@example.org"
      val username = "user"

      val user = UserADM(
        id = "http://rdfh.ch/users/example",
        username = username,
        email = userEmail,
        givenName = "Foo",
        familyName = "Bar",
        status = true,
        lang = "en"
      )

      val emailID = UserIdentifierADM(maybeEmail = Some(userEmail))
      val usernameID = UserIdentifierADM(maybeUsername = Some(username))

      assert(user.isSelf(emailID))
      assert(user.isSelf(usernameID))
    }

    "throw a BadRequestException for an empty identifier" in {
      assertThrows[BadRequestException](
        UserIdentifierADM()
      )
    }

    "throw a BadRequestException for an invalid user IRI" in {
      assertThrows[BadRequestException](
        UserIdentifierADM(maybeIri = Some("http://example.org/not/our/user/iri/structure"))
      )
    }

    "throw a BadRequestException for an invalid email" in {
      assertThrows[BadRequestException](
        UserIdentifierADM(maybeEmail = Some("invalidemail"))
      )
    }

    "throw a BadRequestException for an invalid username" in {
      assertThrows[BadRequestException](
        // we allow max 50 characters in username
        UserIdentifierADM(maybeEmail = Some("_username"))
      )
    }

  }

  "The ChangeUserApiRequestADM case class" should {

    "throw a BadRequestException if number of parameters is wrong" in {

      // all parameters are None
      assertThrows[BadRequestException](
        ChangeUserApiRequestADM()
      )

      val errorNoParameters = the[BadRequestException] thrownBy ChangeUserApiRequestADM()
      errorNoParameters.getMessage should equal("No data sent in API request.")

      // more than one parameter for status update
      assertThrows[BadRequestException](
        ChangeUserApiRequestADM(status = Some(true), systemAdmin = Some(true))
      )

      val errorTooManyParametersStatusUpdate =
        the[BadRequestException] thrownBy ChangeUserApiRequestADM(status = Some(true), systemAdmin = Some(true))
      errorTooManyParametersStatusUpdate.getMessage should equal("Too many parameters sent for change request.")

      // more than one parameter for systemAdmin update
      assertThrows[BadRequestException](
        ChangeUserApiRequestADM(systemAdmin = Some(true), status = Some(true))
      )

      val errorTooManyParametersSystemAdminUpdate =
        the[BadRequestException] thrownBy ChangeUserApiRequestADM(systemAdmin = Some(true), status = Some(true))
      errorTooManyParametersSystemAdminUpdate.getMessage should equal("Too many parameters sent for change request.")

      // more than 5 parameters for basic user information update
      assertThrows[BadRequestException](
        ChangeUserApiRequestADM(
          username = Some("newUsername"),
          email = Some("newEmail@email.com"),
          givenName = Some("newGivenName"),
          familyName = Some("familyName"),
          lang = Some("en"),
          status = Some(true),
          systemAdmin = Some(false)
        )
      )

      val errorTooManyParametersBasicInformationUpdate = the[BadRequestException] thrownBy ChangeUserApiRequestADM(
        username = Some("newUsername"),
        email = Some("newEmail@email.com"),
        givenName = Some("newGivenName"),
        familyName = Some("familyName"),
        lang = Some("en"),
        status = Some(true),
        systemAdmin = Some(false)
      )
      errorTooManyParametersBasicInformationUpdate.getMessage should equal(
        "Too many parameters sent for change request."
      )
    }
  }
}
