/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin.service

import zio.*
import zio.config.*
import zio.nio.file.Path as NioPath
import zio.stream.ZStream
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.telemetry.opentelemetry.tracing.Tracing

import java.nio.file.Path

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.TestAppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.SparqlConstructResponse
import org.knora.webapi.messages.util.rdf.QuadFormat
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
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
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.infrastructure.Jwt
import org.knora.webapi.slice.infrastructure.OtelSetup
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoInMemory
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.security.AuthenticatorError
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.RawSparqlRequest
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.RawSparqlResponse
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus
import org.knora.webapi.store.triplestore.errors.SparqlPassthroughException
import org.knora.webapi.store.triplestore.upgrade.GraphsForMigration

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

  /**
   * A `TriplestoreService` whose `rawQuery` answers with whatever a spec programmed, so the passthrough can be driven
   * to each of its outcomes -- including the ones a working store never produces -- without a container.
   *
   * Every other member dies: nothing on this surface calls them, and a defect is louder than a plausible stub value
   * if that ever stops being true.
   */
  def stubStore(
    answer: RawSparqlRequest => IO[SparqlPassthroughException, RawSparqlResponse],
  ): ULayer[TriplestoreService] = ZLayer.succeed(new TriplestoreService {
    private def unused = ZIO.die(new UnsupportedOperationException("not used by the SPARQL passthrough specs"))

    override def rawQuery(request: RawSparqlRequest): IO[SparqlPassthroughException, RawSparqlResponse] =
      answer(request)
    override def uploadNQuads(stream: ZStream[Any, Throwable, Byte]): Task[Unit]                  = unused
    override def query(sparql: Ask): Task[Boolean]                                                = unused
    override def query(sparql: Construct): Task[SparqlConstructResponse]                          = unused
    override def queryRdf(sparql: Construct): Task[String]                                        = unused
    override def query(sparql: Select): Task[SparqlSelectResult]                                  = unused
    override def query(sparql: Update): Task[Unit]                                                = unused
    override def queryToFile(s: Construct, g: InternalIri, o: NioPath, f: QuadFormat): Task[Unit] = unused
    override def downloadGraph(g: InternalIri, out: NioPath, f: QuadFormat): Task[Unit]           = unused
    override def resetTripleStoreContent(r: List[RdfDataObject], prepend: Boolean): Task[Unit]    = unused
    override def dropDataGraphByGraph(): Task[Unit]                                               = unused
    override def insertDataIntoTriplestore(r: List[RdfDataObject], prepend: Boolean): Task[Unit]  = unused
    override def checkTriplestore(): Task[TriplestoreStatus]                                      = unused
    override def downloadRepository(out: Path, graphs: GraphsForMigration): Task[Unit]            = unused
    override def uploadRepository(inputFile: Path): Task[Unit]                                    = unused
    override def dropGraph(graphName: String): Task[Unit]                                         = unused
    override def compact(): Task[Boolean]                                                         = unused
  })

  /**
   * An authenticator whose token authentication is whatever a spec programs, and whose other methods refuse.
   *
   * [[stubAuthenticator]] is right for a spec that drives the rest service directly, since nothing there
   * authenticates. A spec that goes through the endpoint has to be able to get *past* the security logic to reach
   * anything behind it -- body decoding, in particular -- or to make it fail in a specific way, and needs this.
   */
  def authenticatorAnswering(answer: String => IO[AuthenticatorError, User]): Authenticator = new Authenticator {
    def calculateCookieName(): String                                                         = "stub"
    def invalidateToken(jwt: String): IO[AuthenticatorError, Unit]                            = ZIO.fail(AuthenticatorError.BadCredentials)
    def parseToken(jwt: String): IO[AuthenticatorError, Jwt]                                  = ZIO.fail(AuthenticatorError.BadCredentials)
    def authenticate(userIri: UserIri, password: String): IO[AuthenticatorError, (User, Jwt)] =
      ZIO.fail(AuthenticatorError.BadCredentials)
    def authenticate(username: Username, password: String): IO[AuthenticatorError, (User, Jwt)] =
      ZIO.fail(AuthenticatorError.BadCredentials)
    def authenticate(email: Email, password: String): IO[AuthenticatorError, (User, Jwt)] =
      ZIO.fail(AuthenticatorError.BadCredentials)
    def authenticate(jwtToken: String): IO[AuthenticatorError, User] = answer(jwtToken)
  }

  /** Accepts exactly one bearer token, as one user, and refuses everything else. */
  def tokenAuthenticator(token: String, user: User): Authenticator =
    authenticatorAnswering(jwt => if (jwt == token) ZIO.succeed(user) else ZIO.fail(AuthenticatorError.BadCredentials))

  /**
   * What [[make]] hands out.
   *
   * `Tracing` and `ContextStorage` are exported rather than kept internal because a spec that drives the endpoint
   * through the server's interceptor chain needs *the same* pair the services behind it got. Appending a second
   * `OpenTelemetry.contextZIO` alongside this layer instead gives the interceptor a different `ContextStorage` from
   * the one `Tracing` writes to, so `updateSpanMetadata` silently renames nothing and the spec passes without
   * covering anything.
   */
  type Env = AppConfig & SparqlPassthroughRestService & SparqlPassthroughEndpoints & Tracing & ContextStorage

  /** [[layer]] with the in-memory triplestore replaced by a programmed [[stubStore]]. */
  def layerWithStore(store: ULayer[TriplestoreService], overrides: (String, Any)*): ULayer[Env] =
    make(store, stubAuthenticator, overrides*)

  /** [[layerWithStore]] with an authenticator that can succeed, for specs that go through the endpoint. */
  def layerWithAuthenticator(
    store: ULayer[TriplestoreService],
    authenticator: Authenticator,
    overrides: (String, Any)*,
  ): ULayer[Env] =
    make(store, authenticator, overrides*)

  def layer(overrides: (String, Any)*): ULayer[Env] =
    make(
      TriplestoreServiceInMemory.emptyLayer.map(env => ZEnvironment[TriplestoreService](env.get)),
      stubAuthenticator,
      overrides*,
    )

  private def make(
    store: ZLayer[StringFormatter, Nothing, TriplestoreService],
    authenticator: Authenticator,
    overrides: (String, Any)*,
  ): ULayer[Env] =
    ZLayer.make[Env](
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
      store,
      ZLayer.succeed(authenticator),
    )
}
