/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.search

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.infrastructure.Jwt
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.security.AuthenticatorError

@RunWith(classOf[DspZTestJUnitRunner])
class SearchEndpointsSpec extends ZIOSpecDefault {

  // Stub authenticator -- these are contract tests over endpoint metadata, not security logic.
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

  // Authorization is likewise never invoked here -- these render endpoint metadata via `.show`, so a bare instance
  // (its service dependencies are never dereferenced) is enough to satisfy the BaseEndpoints constructor.
  private val stubAuthorization: AuthorizationRestService = new AuthorizationRestService(null, null)

  private val endpoints = new SearchEndpoints(BaseEndpoints(stubAuthenticator, stubAuthorization))

  // Tapir's `.show` renders each endpoint's error outputs, including the fixed status codes of its oneOf variants.
  private val fullTextSearch      = endpoints.getFullTextSearch.endpoint.show
  private val fullTextSearchCount = endpoints.getFullTextSearchCount.endpoint.show
  private val searchByLabel       = endpoints.getSearchByLabel.endpoint.show
  private val gravsearch          = endpoints.getGravsearch.endpoint.show

  // HONEST-TIMEOUT (DEV-6864): the two fulltext routes translate a triplestore timeout into a 503, attached via
  // errorOutVariantsPrepend on those endpoints only. These tests are the regression net for that being scoped to
  // the fulltext routes and for the prepend not dropping the shared client-error variants.
  override def spec: Spec[TestEnvironment, Any] = suite("SearchEndpoints HONEST-TIMEOUT contract (DEV-6864)")(
    test("the fulltext search and count endpoints advertise a 503") {
      assertTrue(
        fullTextSearch.contains("503"),
        fullTextSearchCount.contains("503"),
      )
    },
    test("the 503 is attached only to the fulltext endpoints, not search-by-label or gravsearch") {
      assertTrue(
        !searchByLabel.contains("503"),
        !gravsearch.contains("503"),
      )
    },
    test("the fulltext endpoints keep their documented client-error variants (nothing dropped by prepending)") {
      assertTrue(
        fullTextSearch.contains("400"),
        fullTextSearch.contains("404"),
        fullTextSearchCount.contains("400"),
      )
    },
  )
}
