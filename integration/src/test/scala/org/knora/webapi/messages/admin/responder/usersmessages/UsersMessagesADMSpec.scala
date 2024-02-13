/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.usersmessages

import dsp.errors.BadRequestException
import org.knora.webapi.*
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionProfileType
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.domain.model.User

/**
 * This spec is used to test the [[User]] and [[UserIdentifierADM]] classes.
 */
class UsersMessagesADMSpec extends CoreSpec {

  private val id          = SharedTestDataADM.rootUser.id
  private val username    = SharedTestDataADM.rootUser.username
  private val email       = SharedTestDataADM.rootUser.email
  private val password    = SharedTestDataADM.rootUser.password
  private val givenName   = SharedTestDataADM.rootUser.givenName
  private val familyName  = SharedTestDataADM.rootUser.familyName
  private val status      = SharedTestDataADM.rootUser.status
  private val lang        = SharedTestDataADM.rootUser.lang
  private val groups      = SharedTestDataADM.rootUser.groups
  private val projects    = SharedTestDataADM.rootUser.projects
  private val permissions = SharedTestDataADM.rootUser.permissions

  "The UserADM case class" should {
    "return a RESTRICTED UserADM when requested " in {
      val rootUser = User(
        id = id,
        username = username,
        email = email,
        givenName = givenName,
        familyName = familyName,
        status = status,
        lang = lang,
        password = password,
        groups = groups,
        projects = projects,
        permissions = permissions
      )
      val rootUserRestricted = User(
        id = id,
        username = username,
        email = email,
        givenName = givenName,
        familyName = familyName,
        status = status,
        lang = lang,
        password = None,
        groups = groups,
        projects = projects,
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
