/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.knora.webapi.config.AppConfig
import org.knora.webapi.routing.HealthRouteWithZIOHttp
import zhttp.http._
import zhttp.service.Server
import zio.json.{DeriveJsonEncoder, EncoderOps}
import zio.{ZLayer, _}
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetRequestADM

case class HelloZio(hello: String)

object HelloZio {
  implicit val encoder = DeriveJsonEncoder.gen[HelloZio]
}

object HelloZioApp {

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
  def apply(): HttpApp[State, Nothing] =
    Http.collectZIO[Request] {

      // GET admin/projects/iri/{iri}
      case Method.GET -> !! / "admin" / "projects" / "iri" / iri =>
        for {
          user <- ZIO.succeed(KnoraSystemInstances.Users.SystemUser)
          iriValue <- ProjectIdentifierADM.IriIdentifier
                        .fromString(iri)
                        .toZIO
                        .orDie
          message = ProjectGetRequestADM(
                      identifier = iriValue,
                      requestingUser = user
                    )

        } yield Response.json(HelloZio(iri).toJson)

    }
}

object HttpServerWithZIOHttp {

  val routes = HealthRouteWithZIOHttp() ++ HelloZioApp()

  val layer: ZLayer[AppConfig & State, Nothing, Unit] =
    ZLayer {
      for {
        appConfig <- ZIO.service[AppConfig]
        port       = appConfig.knoraApi.externalZioPort
        _         <- Server.start(port, routes).forkDaemon
        _         <- ZIO.logInfo(">>> Acquire ZIO HTTP Server <<<")
      } yield ()
    }
}
