/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin.service

import zio.*
import zio.config.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.TestAppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.repo.KnoraProjectRepoInMemory
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.admin.domain.service.PasswordService
import org.knora.webapi.slice.admin.repo.LicenseRepo
import org.knora.webapi.slice.admin.repo.service.KnoraGroupRepoInMemory
import org.knora.webapi.slice.admin.repo.service.KnoraUserRepoLive
import org.knora.webapi.slice.api.admin.SparqlPassthroughEndpoints
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.infrastructure.Jwt
import org.knora.webapi.slice.infrastructure.OtelSetup
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoInMemory
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.security.AuthenticatorError
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

/**
 * The layer stack the SPARQL passthrough specs run against: an in-memory triplestore, in-memory repositories, and an
 * `AppConfig` read from the real `application.conf` with the passthrough flag flipped on, so a spec can vary one
 * guardrail at a time without hand-building a config value.
 */
object SparqlPassthroughTestEnv {

  /**
   * Fails every authentication attempt. These specs exercise authorization and guardrails, which sit in the server
   * logic, downstream of the security logic; nothing here goes through authentication.
   */
  private val stubAuthenticator: Authenticator = new Authenticator {
    def calculateCookieName(): String                                                         = "stub"
    def invalidateToken(jwt: String): IO[AuthenticatorError, Unit]                            = ZIO.fail(AuthenticatorError.BadCredentials)
    def parseToken(jwt: String): IO[AuthenticatorError, Jwt]                                  = ZIO.fail(AuthenticatorError.BadCredentials)
    def authenticate(userIri: UserIri, password: String): IO[AuthenticatorError, (User, Jwt)] =
      ZIO.fail(AuthenticatorError.BadCredentials)
    def authenticate(username: Username, password: String): IO[AuthenticatorError, (User, Jwt)] =
      ZIO.fail(AuthenticatorError.BadCredentials)
    def authenticate(email: Email, password: String): IO[AuthenticatorError, (User, Jwt)] =
      ZIO.fail(AuthenticatorError.BadCredentials)
    def authenticate(jwtToken: String): IO[AuthenticatorError, User] = ZIO.fail(AuthenticatorError.BadCredentials)
  }

  def appConfigLayer(overrides: (String, Any)*): ULayer[AppConfig] =
    ZLayer.fromZIO(
      read(AppConfig.config from TestAppConfig.provider(("app.allow-sparql-passthrough" -> true) +: overrides*)).orDie,
    )

  def layer(
    overrides: (String, Any)*,
  ): ULayer[AppConfig & SparqlPassthroughRestService & SparqlPassthroughEndpoints] =
    ZLayer.make[AppConfig & SparqlPassthroughRestService & SparqlPassthroughEndpoints](
      appConfigLayer(overrides*),
      AuthorizationRestService.layer,
      BaseEndpoints.layer,
      CacheManager.layer,
      IriConverter.layer,
      IriService.layer,
      KnoraGroupRepoInMemory.layer,
      KnoraGroupService.layer,
      KnoraProjectRepoInMemory.layer,
      KnoraProjectService.layer,
      KnoraUserRepoLive.layer,
      KnoraUserService.layer,
      LicenseRepo.layer,
      OntologyRepoInMemory.emptyLayer,
      OtelSetup.stdOut,
      PasswordService.layer,
      SparqlPassthroughEndpoints.layer,
      SparqlPassthroughRestService.layer,
      StringFormatter.test,
      TriplestoreServiceInMemory.emptyLayer,
      ZLayer.succeed(stubAuthenticator),
    )
}
