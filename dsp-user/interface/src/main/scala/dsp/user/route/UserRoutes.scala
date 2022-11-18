/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.route

import zhttp.http._
import zio._

import dsp.config.AppConfig
import dsp.errors.ValidationException
import dsp.user.handler.UserHandler

/**
 * The UserRoutes case class which needs an instance of a userHandler
 */
final case class UserRoutes(userHandler: UserHandler) {

  /**
   * The user related routes which need AppConfig in the environment
   */
  val routes: HttpApp[AppConfig, ValidationException] = Http.collectZIO[Request] {
    // POST /admin/users
    case req @ (Method.POST -> !! / "admin" / "users") =>
      CreateUser
        .route(req, userHandler)
        .catchAll { e =>
          ZIO.succeed(Response.text(e.getMessage).setStatus(Status.BadRequest))
        }

    // POST /admin/users/migration
    case req @ (Method.POST -> !! / "admin" / "users" / "migration") =>
      MigrateUser
        .route(req, userHandler)
        .catchAll { e =>
          ZIO.succeed(Response.text(e.getMessage).setStatus(Status.BadRequest))
        }

    // GET /admin/users/:uuid
    case Method.GET -> !! / "admin" / "users" / uuid =>
      GetUser.route(uuid, userHandler).catchAll { e =>
        ZIO.succeed(Response.text(e.getMessage).setStatus(Status.BadRequest))
      }

  }
}

object UserRoutes {
  val layer: ZLayer[UserHandler, Nothing, UserRoutes] =
    ZLayer.fromFunction(UserRoutes.apply _)
}
