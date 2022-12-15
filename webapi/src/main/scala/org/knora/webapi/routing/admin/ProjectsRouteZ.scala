package org.knora.webapi.routing.admin

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import zhttp.http._
import zio.ZIO
import zio.ZLayer

import java.net.URLDecoder

import dsp.errors.BadRequestException
import dsp.errors.InternalServerException
import dsp.errors.KnoraException
import dsp.errors.RequestRejectedException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.AppRouter
import org.knora.webapi.http.handler.ExceptionHandlerZ
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.util.KnoraSystemInstances

final case class ProjectsRouteZ(router: AppRouter, appConfig: AppConfig) {
  implicit val sender: ActorRef = router.ref
  implicit val timeout: Timeout = appConfig.defaultTimeoutAsDuration

  def getProjectByIri(iri: String): ZIO[Any, KnoraException, Response] =
    for {
      user <- ZIO.succeed(KnoraSystemInstances.Users.SystemUser)
      iriDecoded <-
        ZIO
          .attempt(URLDecoder.decode(iri, "utf-8"))
          .orElseFail(BadRequestException(s"Failed to decode IRI $iri"))
      iriValue <- ProjectIdentifierADM.IriIdentifier
                    .fromString(iriDecoded)
                    .toZIO
      message   = ProjectGetRequestADM(identifier = iriValue, requestingUser = user)
      response <- ZIO.fromFuture(_ => router.ref.ask(message)).map(_.asInstanceOf[ProjectGetResponseADM]).orDie
    } yield Response.json(response.toJsValue.toString())

  val route: HttpApp[Any, Nothing] =
    (Http
      .collectZIO[Request] {
        // TODO : Add user authentication, make tests run with the new route
        // Returns a single project identified through the IRI.
        case Method.GET -> !! / "admin" / "projects" / "iri" / iri =>
          getProjectByIri(iri)
      })
      .catchAll {
        case RequestRejectedException(e) =>
          ExceptionHandlerZ.exceptionToJsonHttpResponseZ(e, appConfig)
        case InternalServerException(e) =>
          ExceptionHandlerZ.exceptionToJsonHttpResponseZ(e, appConfig)
      }
}

object ProjectsRouteZ {
  val layer: ZLayer[AppRouter with AppConfig, Nothing, ProjectsRouteZ] = ZLayer.fromFunction { (router, config) =>
    ProjectsRouteZ(router, config)
  }
}
