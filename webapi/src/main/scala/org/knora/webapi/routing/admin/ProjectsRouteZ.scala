package org.knora.webapi.routing.admin

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.AppRouter
import org.knora.webapi.messages.admin.responder.projectsmessages.{
  ProjectGetRequestADM,
  ProjectGetResponseADM,
  ProjectIdentifierADM
}
import org.knora.webapi.messages.util.KnoraSystemInstances
import zhttp.http._
import zio.{ZIO, ZLayer}

import java.net.URLDecoder

final case class ProjectsRouteZ(router: AppRouter, appConfig: AppConfig) {
  implicit val sender: ActorRef = router.ref
  implicit val timeout: Timeout = appConfig.defaultTimeoutAsDuration

  def route(): HttpApp[Any, Nothing] =
    Http.collectZIO[Request] {

      // TODO : Add user authentication and error handling
      // Returns a single project identified through the IRI.
      case Method.GET -> !! / "admin" / "projects" / "iri" / iri =>
        for {
          user       <- ZIO.succeed(KnoraSystemInstances.Users.SystemUser)
          iriDecoded <- ZIO.attempt(URLDecoder.decode(iri, "utf-8")).orDie
          iriValue <- ProjectIdentifierADM.IriIdentifier
                        .fromString(iriDecoded)
                        .toZIO
                        .orDie
          message = ProjectGetRequestADM(
                      identifier = iriValue,
                      requestingUser = user
                    )
          response <- ZIO.fromFuture(_ => router.ref.ask(message)).map(_.asInstanceOf[ProjectGetResponseADM]).orDie
        } yield Response.json(response.toJsValue.toString())

    }
}

object ProjectsRouteZ {
  val layer: ZLayer[AppRouter with AppConfig, Nothing, ProjectsRouteZ] = ZLayer.fromFunction { (router, config) =>
    ProjectsRouteZ(router, config)
  }
}
