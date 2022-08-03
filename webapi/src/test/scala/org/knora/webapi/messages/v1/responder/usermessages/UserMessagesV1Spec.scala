/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v1.responder.usermessages

import org.knora.webapi.messages.admin.responder.permissionsmessages
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionProfileType
import org.knora.webapi.sharedtestdata.SharedTestDataV1
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder

/**
 * This spec is used to test subclasses of the [[UsersResponderRequestV1]] class.
 */
class UserMessagesV1Spec extends AnyWordSpecLike with Matchers {

  private val lang           = SharedTestDataV1.rootUser.userData.lang
  private val user_id        = SharedTestDataV1.rootUser.userData.user_id
  private val token          = SharedTestDataV1.rootUser.userData.token
  private val firstname      = SharedTestDataV1.rootUser.userData.firstname
  private val lastname       = SharedTestDataV1.rootUser.userData.lastname
  private val email          = SharedTestDataV1.rootUser.userData.email
  private val password       = SharedTestDataV1.rootUser.userData.password
  private val groups         = SharedTestDataV1.rootUser.groups
  private val projects_info  = SharedTestDataV1.rootUser.projects_info
  private val permissionData = SharedTestDataV1.rootUser.permissionData
  private val sessionId      = SharedTestDataV1.rootUser.sessionId

  "The UserProfileV1 case class " should {
    "return a safe UserProfileV1 when requested " in {
      val rootUserProfileV1 = UserProfileV1(
        UserDataV1(
          user_id = user_id,
          email = email,
          firstname = firstname,
          lastname = lastname,
          password = password,
          token = token,
          lang = lang
        ),
        groups = groups,
        projects_info = projects_info,
        permissionData = permissionData,
        sessionId = sessionId
      )
      val rootUserProfileV1Safe = UserProfileV1(
        UserDataV1(
          user_id = user_id,
          email = email,
          firstname = firstname,
          lastname = lastname,
          password = None,
          token = None,
          lang = lang
        ),
        groups = groups,
        projects_info = projects_info,
        permissionData = permissionData.ofType(PermissionProfileType.Restricted),
        sessionId = sessionId
      )

      assert(rootUserProfileV1.ofType(UserProfileTypeV1.RESTRICTED) === rootUserProfileV1Safe)
    }

    "allow checking SCrypt passwords" in {
      // hashedPassword =  encoder.encode(createRequest.password);
      val encoder = new SCryptPasswordEncoder
      val hp      = encoder.encode("123456")
      val up = UserProfileV1(
        userData = UserDataV1(
          password = Some(hp),
          lang = lang
        ),
        permissionData = permissionsmessages.PermissionsDataADM()
      )

      // test SCrypt
      assert(encoder.matches("123456", encoder.encode("123456")))

      // test UserProfileV1 SCrypt usage
      assert(up.passwordMatch("123456"))
    }

    "allow checking BCrypt passwords" in {
      // hashedPassword =  encoder.encode(createRequest.password);
      val encoder = new BCryptPasswordEncoder
      val hp      = encoder.encode("123456")
      val up = UserProfileV1(
        userData = UserDataV1(
          password = Some(hp),
          lang = lang
        ),
        permissionData = permissionsmessages.PermissionsDataADM()
      )

      // test BCrypt
      assert(encoder.matches("123456", encoder.encode("123456")))

      // test UserProfileV1 BCrypt usage
      assert(up.passwordMatch("123456"))
    }

    "allow checking the password of root" in {
      SharedTestDataV1.rootUser.passwordMatch("test") should equal(true)
    }
  }
}
