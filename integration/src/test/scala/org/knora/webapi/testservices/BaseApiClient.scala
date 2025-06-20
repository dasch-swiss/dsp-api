package org.knora.webapi.testservices
import sttp.capabilities.zio.ZioStreams
import sttp.client4.StreamBackend
import sttp.client4.wrappers.ResolveRelativeUrisBackend
import sttp.model.Uri
import zio.*

import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.security.ScopeResolver

abstract class BaseApiClient(
  private val authenticator: Authenticator,
  private val be: StreamBackend[Task, ZioStreams],
  private val jwtService: JwtService,
  private val scopeResolver: ScopeResolver,
) {
  protected def baseUrl: Uri

  protected val backend: StreamBackend[Task, ZioStreams] = ResolveRelativeUrisBackend(be, baseUrl)
  protected val authCookieName: String                   = authenticator.calculateCookieName()

  def jwtFor(user: User): UIO[String] =
    scopeResolver.resolve(user).flatMap(jwtService.createJwt(user.userIri, _)).map(_.jwtString)

  def jwtForRoot(): UIO[String] = jwtFor(rootUser)
}
