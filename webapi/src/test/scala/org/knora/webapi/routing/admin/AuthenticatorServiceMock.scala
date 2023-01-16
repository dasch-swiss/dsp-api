/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import zhttp.http.Request
import zio._

import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM

/**
 * Test instance of the AuthenticatorService which stores the created [[UserADM]], so that
 * it can be queried / returned in tests
 */
final case class AuthenticatorServiceMock(knownUser: Ref[UserADM]) extends AuthenticatorService {

  /**
   * Sets the given user that can be queried later
   *
   * @param user a [[UserADM]] that should be set as known user
   */
  def setUser(user: UserADM) = knownUser.set(user)

  /**
   * Returns a [[UserADM]]
   */
  override def getUser(request: Request): Task[UserADM] =
    for {
      user <- knownUser.get
    } yield user

}
object AuthenticatorServiceMock {

  val layer: ULayer[AuthenticatorServiceMock] =
    ZLayer {
      val user = UserADM(
        id = "id",
        username = "username",
        email = "email@example.com",
        password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
        token = None,
        givenName = "Given Name",
        familyName = "Family Name",
        status = true,
        lang = "en",
        groups = Seq.empty[GroupADM],
        projects = Seq.empty[ProjectADM],
        sessionId = None,
        permissions = PermissionsDataADM()
      )
      for {
        exampleUser <- Ref.make(user)
      } yield AuthenticatorServiceMock(knownUser = exampleUser)
    }

}
