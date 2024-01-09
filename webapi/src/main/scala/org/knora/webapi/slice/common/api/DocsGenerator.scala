package org.knora.webapi.slice.common.api

import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.server.RequestContext
import sttp.apispec.openapi.Server
import sttp.apispec.openapi.circe.yaml.RichOpenAPI
import sttp.tapir.AnyEndpoint
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import zio.Chunk
import zio.Task
import zio.ZIO
import zio.ZIOAppArgs
import zio.ZIOAppDefault
import zio.ZLayer
import zio.nio.file.Files
import zio.nio.file.Path

import org.knora.webapi.http.version.BuildInfo
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.slice.admin.api.AdminApiEndpoints
import org.knora.webapi.slice.admin.api.MaintenanceEndpoints
import org.knora.webapi.slice.admin.api.PermissionsEndpoints
import org.knora.webapi.slice.admin.api.ProjectsEndpoints
import org.knora.webapi.slice.admin.api.UsersEndpoints
import org.knora.webapi.slice.admin.domain.model.User

final case class DocsNoopAuthenticator() extends Authenticator {
  override def getUserADM(requestContext: RequestContext): Task[User]                                    = ???
  override def calculateCookieName(): String                                                             = "foo"
  override def getUserADMThroughCredentialsV2(credentials: Option[KnoraCredentialsV2]): Task[User]       = ???
  override def doLogoutV2(requestContext: RequestContext): Task[HttpResponse]                            = ???
  override def doLoginV2(credentials: KnoraCredentialsV2.KnoraPasswordCredentialsV2): Task[HttpResponse] = ???
  override def doAuthenticateV2(requestContext: RequestContext): Task[HttpResponse]                      = ???
  override def presentLoginFormV2(requestContext: RequestContext): Task[HttpResponse]                    = ???
  override def authenticateCredentialsV2(credentials: Option[KnoraCredentialsV2]): Task[Boolean]         = ???
  override def getUserByIdentifier(identifier: UserIdentifierADM): Task[User]                            = ???
}
object DocsNoopAuthenticator {
  val layer = ZLayer.succeed(DocsNoopAuthenticator())
}

object DocsGenerator extends ZIOAppDefault {

  private val interp: OpenAPIDocsInterpreter = OpenAPIDocsInterpreter()
  override def run: ZIO[ZIOAppArgs, java.io.IOException, Int] = {
    for {
      _              <- ZIO.logInfo("Generating OpenAPI docs")
      args           <- getArgs
      adminEndpoints <- ZIO.serviceWith[AdminApiEndpoints](_.endpoints)
      path            = Path(args.headOption.getOrElse("/tmp"))
      fileWritten    <- writeToFile(adminEndpoints, path, "maintenance")
      _              <- ZIO.logInfo(s"Wrote to $fileWritten")
    } yield 0
  }.provideSome[ZIOAppArgs](
    AdminApiEndpoints.layer,
    BaseEndpoints.layer,
    DocsNoopAuthenticator.layer,
    MaintenanceEndpoints.layer,
    PermissionsEndpoints.layer,
    ProjectsEndpoints.layer,
    UsersEndpoints.layer
    //        ZLayer.Debug.mermaid ,
  )

  private def writeToFile(endpoints: Seq[AnyEndpoint], path: Path, name: String) = {
    val content = interp
      .toOpenAPI(endpoints, s"${BuildInfo.name}-$name", BuildInfo.version)
      .servers(
        List(
          Server(url = "http://localhost:3333", description = Some("Local development server")),
          Server(url = "https://api.dasch.swiss", description = Some("Production server"))
        )
      )
    for {
      _     <- ZIO.logInfo(s"Writing to $path")
      target = path / s"openapi-$name.yml"
      _     <- Files.deleteIfExists(target) *> Files.createFile(target)
      _     <- Files.writeBytes(target, Chunk.fromArray(content.toYaml.getBytes))
    } yield target
  }
}
