/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security

import zio.URLayer
import zio.ZLayer

import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.PasswordService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.infrastructure.InvalidTokenCache
import org.knora.webapi.slice.infrastructure.JwtService

object SecurityModule { self =>
  type Dependencies =
      // format: off
      InvalidTokenCache &
      JwtService &
      KnoraProjectService &
      PasswordService &
      UserService
      // format: on

  type Provided = ScopeResolver & Authenticator

  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](ScopeResolver.layer, AuthenticatorLive.layer)
}
