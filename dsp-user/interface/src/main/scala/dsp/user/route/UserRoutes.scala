/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.route

import zhttp.http._
import zio._
import zio.json._
import dsp.user.domain.User
import dsp.user.handler.UserHandler

/**
 * An http app that:
 *   - Accepts a `Request` and returns a `Response`
 *   - May fail with type of `Throwable`
 *   - Uses a `UserHandler` as the environment
 */
final case class UserRoutes(userHandler: UserHandler) {
  val routes: Http[Any, Throwable, Request, Response] = Http.collectZIO[Request] {
    case req @ (Method.POST -> !! / "admin" / "users") => {
      for {
        user <- req.bodyAsString.map(_.fromJson[User])

        r <- user match {
               case Left(e) => {
                 ZIO
                   .debug(s"Failed to parse the input: $e")
                   .as(
                     Response.text(e).setStatus(Status.BadRequest)
                   )
               }

               case Right(u) => {
                 for {
                   _ <- ZIO.unit

                   userId <- userHandler.createUser(
                               u.username,
                               u.email,
                               u.givenName,
                               u.familyName,
                               u.password,
                               u.language,
                               u.status
                             )
                   user <- userHandler.getUserById(userId)
                 } yield (Response.json(user.toJson))
               }
             }
      } yield r
    }

    // GET /users/:id
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
