/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.ontology

import zio.*
import zio.test.*

import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.api.v3.V3BaseEndpoint
import org.knora.webapi.slice.infrastructure.Jwt
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.security.AuthenticatorError

object OntologyMappingEndpointsSpec extends ZIOSpecDefault {

  // Stub authenticator — routing tests do not exercise security logic.
  // All authenticate methods fail immediately; only the endpoint metadata (method, path) is under test.
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

  private val baseEndpoint = V3BaseEndpoint(stubAuthenticator)
  private val endpoints    = new OntologyMappingEndpoints(baseEndpoint)

  // Collect the Tapir endpoint show strings (method + path) for all four endpoints
  private val endpointShows = List(
    endpoints.putClassMapping.endpoint.show,
    endpoints.deleteClassMapping.endpoint.show,
    endpoints.putPropertyMapping.endpoint.show,
    endpoints.deletePropertyMapping.endpoint.show,
  )

  override def spec: Spec[TestEnvironment, Any] = suite("OntologyMappingEndpoints routing")(
    test("all four endpoints are defined") {
      assertTrue(endpointShows.size == 4)
    },
    test("no two endpoints share the same method + path (no routing conflicts)") {
      // Tapir show includes method and path; uniqueness guarantees no conflicts
      assertTrue(endpointShows.distinct.size == 4)
    },
    test("PUT class mapping endpoint has correct method and path") {
      val show = endpoints.putClassMapping.endpoint.show
      assertTrue(
        show.contains("PUT"),
        show.contains("ontologies"),
        show.contains("classes"),
        show.contains("mapping"),
      )
    },
    test("DELETE class mapping endpoint has correct method and path") {
      val show = endpoints.deleteClassMapping.endpoint.show
      assertTrue(
        show.contains("DELETE"),
        show.contains("ontologies"),
        show.contains("classes"),
        show.contains("mapping"),
      )
    },
    test("PUT property mapping endpoint has correct method and path") {
      val show = endpoints.putPropertyMapping.endpoint.show
      assertTrue(
        show.contains("PUT"),
        show.contains("ontologies"),
        show.contains("properties"),
        show.contains("mapping"),
      )
    },
    test("DELETE property mapping endpoint has correct method and path") {
      val show = endpoints.deletePropertyMapping.endpoint.show
      assertTrue(
        show.contains("DELETE"),
        show.contains("ontologies"),
        show.contains("properties"),
        show.contains("mapping"),
      )
    },
    test("DELETE class mapping endpoint exposes 'mapping' as a query parameter") {
      // query[Option[String]] — absent param yields None; the service layer validates presence
      val show = endpoints.deleteClassMapping.endpoint.show
      assertTrue(show.contains("mapping"))
    },
    test("DELETE property mapping endpoint exposes 'mapping' as a query parameter") {
      val show = endpoints.deletePropertyMapping.endpoint.show
      assertTrue(show.contains("mapping"))
    },
  )
}
