/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.usersmessages

import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionProfileType
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.domain.model.User

/**
 * This spec is used to test the [[User]] and [[UserIdentifierADM]] classes.
 */
object UsersMessagesADMSpec extends ZIOSpecDefault {

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

  val spec: Spec[Any, Nothing] = suite("The UserADM case class")(
    test("return a RESTRICTED UserADM when requested") {
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

      assertTrue(rootUser.ofType(UserInformationType.Restricted) == rootUserRestricted)
    },
    test("return true if user is ProjectAdmin in any project") {
      assertTrue(SharedTestDataADM.anythingAdminUser.permissions.isProjectAdminInAnyProject())
    },
    test("return false if user is not ProjectAdmin in any project") {
      assertTrue(!SharedTestDataADM.anythingUser1.permissions.isProjectAdminInAnyProject())
    },
  )
}
