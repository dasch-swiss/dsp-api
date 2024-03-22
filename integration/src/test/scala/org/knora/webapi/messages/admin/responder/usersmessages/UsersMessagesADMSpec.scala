/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.usersmessages

import org.knora.webapi._
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
        permissions = permissions,
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
        permissions = permissions.ofType(PermissionProfileType.Restricted),
      )

      assert(rootUser.ofType(UserInformationType.Restricted) === rootUserRestricted)
    }

    "return true if user is ProjectAdmin in any project " in {
      assert(
        SharedTestDataADM.anythingAdminUser.permissions.isProjectAdminInAnyProject() === true,
        "user is not ProjectAdmin in any of his projects",
      )
    }

    "return false if user is not ProjectAdmin in any project " in {
      assert(
        SharedTestDataADM.anythingUser1.permissions.isProjectAdminInAnyProject() === false,
        "user is ProjectAdmin in one of his projects",
      )
    }
  }
}
