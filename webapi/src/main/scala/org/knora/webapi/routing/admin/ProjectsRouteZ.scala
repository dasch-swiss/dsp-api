package org.knora.webapi.routing.admin

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import zhttp.http._
import zio.Task
import zio.ZIO
import zio.ZLayer
import dsp.errors.InternalServerException
import dsp.errors.RequestRejectedException

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.AppRouter
import org.knora.webapi.http.handler.ExceptionHandlerZ
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.routing.RouteUtilZ
import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM

trait ProjectService {
  def getProjectByIri(iri: IRI, user: UserADM): Task[ProjectGetResponseADM]
}

case class ProjectServiceLive(router: AppRouter, appConfig: AppConfig) extends ProjectService {
  implicit val sender: ActorRef = router.ref
  implicit val timeout: Timeout = appConfig.defaultTimeoutAsDuration
  override def getProjectByIri(
    iri: IRI,
    user: UserADM
  ): Task[ProjectGetResponseADM] =
    for {
      iriValue <- ProjectIdentifierADM.IriIdentifier.fromString(iri).toZIO
      message   = ProjectGetRequestADM(identifier = iriValue, requestingUser = user)
      response <- ZIO.fromFuture(_ => router.ref.ask(message)).map(_.asInstanceOf[ProjectGetResponseADM]).orDie
    } yield response
}
object ProjectService {
  val live = ZLayer.fromFunction(ProjectServiceLive.apply _)
}

final case class ProjectsRouteZ(
  appConfig: AppConfig,
  authenticatorService: AuthenticatorService,
  projectService: ProjectService
) {

  def getProjectByIri(iri: String, request: Request): Task[Response] =
    for {
      user       <- authenticatorService.getUser(request)
      iriDecoded <- RouteUtilZ.decodeUrl(iri)
      response   <- projectService.getProjectByIri(iriDecoded, user)
    } yield Response.json(response.toJsValue.toString())

  val route: HttpApp[Any, Nothing] =
    (Http
      .collectZIO[Request] {
        // TODO : tests

        // Returns a single project identified through the IRI.
        case request @ Method.GET -> !! / "admin" / "projects" / "iri" / iri =>
          getProjectByIri(iri, request)
      })
      .catchAll {
        case RequestRejectedException(e) =>
          ExceptionHandlerZ.exceptionToJsonHttpResponseZ(e, appConfig)
        case InternalServerException(e) =>
          ExceptionHandlerZ.exceptionToJsonHttpResponseZ(e, appConfig)
      }
}

object ProjectsRouteZ {
  val layer: ZLayer[AppConfig with AuthenticatorService with ProjectService, Nothing, ProjectsRouteZ] =
    ZLayer.fromFunction(ProjectsRouteZ(_, _, _))
}
