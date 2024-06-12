package org.knora.webapi.slice.security

import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.PasswordService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.infrastructure.InvalidTokenCache
import org.knora.webapi.slice.infrastructure.JwtService

object SecurityModule {

  type Dependencies =
    // format: off
    AppConfig &
    InvalidTokenCache &
    JwtService &
    KnoraProjectService &
    PasswordService &
    UserService
    // format: on

  type Provided =
    // format: off
    ScopeResolver &
    Authenticator
    // format: on

  val layer = ZLayer.makeSome[Dependencies, Provided](
    ScopeResolver.layer,
    AuthenticatorLive.layer,
  )
}
