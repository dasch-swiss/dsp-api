/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security

import zio.URLayer
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.URModule
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.PasswordService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.infrastructure.InvalidTokenCache
import org.knora.webapi.slice.infrastructure.JwtService

object SecurityModule
    extends URModule[
      // format: off
      AppConfig &
      InvalidTokenCache &
      JwtService &
      KnoraProjectService &
      PasswordService &
      UserService
      ,
      ScopeResolver &
      Authenticator
      // format: on
    ] { self =>
  inline def layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      ScopeResolver.layer,
      AuthenticatorLive.layer,
    )
}
