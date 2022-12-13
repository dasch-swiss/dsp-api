/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.admin.responder.projectsmessages.{
  ProjectGetRequestADM,
  ProjectIdentifierADM,
  ProjectsGetResponseADM
}
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.routing.HealthRouteWithZIOHttp
import zhttp.http._
import zhttp.service.Server
import zio.json.DeriveJsonEncoder
import zio.{ZLayer, _}
import java.net.URLDecoder
// import java.net.URLDecoder
// import dsp.config.AppConfig

case class HelloZio(hello: String)

object HelloZio {
  implicit val encoder = DeriveJsonEncoder.gen[HelloZio]
}

final case class HelloZioApp(router: AppRouter, appConfig: AppConfig) {
  implicit val sender: ActorRef = router.ref
  implicit val timeout: Timeout = appConfig.defaultTimeoutAsDuration

  /**
   * Returns a single project identified through the IRI.
   * //
   */
//  private def getProjectByIri(): Route =
//    path(projectsBasePath / "iri" / Segment) { value =>
//      get { requestContext =>
//        val requestMessage: Future[ProjectGetRequestADM] = for {
//          requestingUser <- getUserADM(
//            requestContext = requestContext,
//            routeData.appConfig
//          )
//
//        } yield ProjectGetRequestADM(
//          identifier = IriIdentifier
//            .fromString(value)
//            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
//          requestingUser = requestingUser
//        )
//
//        RouteUtilADM.runJsonRoute(
//          requestMessageF = requestMessage,
//          requestContext = requestContext,
//          appActor = appActor,
//          log = log
//        )
//      }
//    }
  def route(): HttpApp[Any, Nothing] =
    Http.collectZIO[Request] {

      // GET admin/projects/iri/{iri}
      case Method.GET -> !! / "admin" / "projects" / "iri" / iri =>
        for {
          user       <- ZIO.succeed(KnoraSystemInstances.Users.SystemUser)
          iriDecoded <- ZIO.attempt(URLDecoder.decode(iri, "utf-8")).orDie
          iriValue <- ProjectIdentifierADM.IriIdentifier
                        .fromString(iri)
                        .toZIO
                        .orDie
          message = ProjectGetRequestADM(
                      identifier = iriValue,
                      requestingUser = user
                    )
          response <- ZIO.fromFuture(_ => router.ref.ask(message)).map(_.asInstanceOf[ProjectsGetResponseADM]).orDie
        } yield Response.json(response.toJsValue.toString())

    }
}
object HelloZioApp {
  val layer: ZLayer[AppRouter with AppConfig, Nothing, HelloZioApp] = ZLayer.fromFunction { (router, config) =>
    HelloZioApp(router, config)
  }
}

object HttpServerWithZIOHttp {

  val routes = HealthRouteWithZIOHttp()

  val layer: ZLayer[AppRouter & AppConfig & State & HelloZioApp, Nothing, Unit] =
    ZLayer {
      for {
        appConfig   <- ZIO.service[AppConfig]
        helloZioApp <- ZIO.service[HelloZioApp]
        r            = routes ++ helloZioApp.route()
        port         = appConfig.knoraApi.externalZioPort
        _           <- Server.start(port, r).forkDaemon
        _           <- ZIO.logInfo(">>> Acquire ZIO HTTP Server <<<")
      } yield ()
    }
}
