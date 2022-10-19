/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.route

import zhttp.http._
import zio._

import dsp.errors.ValidationException
import dsp.user.handler.UserHandler

/**
 * An http app that:
 *   - Accepts a `Request` and returns a `Response`
 *   - May fail with type of `Throwable`
 *   - Uses a `UserHandler` as the environment
 */
final case class UserRoutes(userHandler: UserHandler) {
  val routes: Http[Any, ValidationException, Request, Response] = Http.collectZIO[Request] {

    // POST /admin/users
    case req @ (Method.POST -> !! / "admin" / "users") =>
      CreateUser
        .route(req, userHandler)
        .catchSome {
          case ValidationException(
                msg,
                _
              ) => // TODO: what else can go wrong that we can treat besides validation of input?
            ZIO.succeed(Response.text(msg).setStatus(Status.BadRequest))
        }

    // POST /admin/users/migration
    case req @ (Method.POST -> !! / "admin" / "users" / "migration") =>
      MigrateUser
        .route(req, userHandler)
        .catchSome {
          case ValidationException(
                msg,
                _
              ) => // TODO: what else can go wrong that we can treat besides validation of input?
            ZIO.succeed(Response.text(msg).setStatus(Status.BadRequest))
        }

    // GET /admin/users/:id
    case Method.GET -> !! / "admin" / "users" / id =>
      ZIO.succeed(Response.text(s"hallo $id"))
    // create value object UserId from id
    // userHandler.getUserById(id).map {
    //   case Some(user) =>
    //     Response.json(user.toJson)
    //   case None =>
    //     Response.status(Status.NotFound)
    // }

  }
}

/**
 * Companion object providing the layer with an initialized implementation of the UserHandler
 */
object UserRoutes {
  val layer: ZLayer[UserHandler, Nothing, UserRoutes] =
    ZLayer.fromFunction(userHandler => UserRoutes.apply(userHandler))
}
