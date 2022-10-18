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
import dsp.valueobjects.User._
import dsp.valueobjects.LanguageCode
import dsp.valueobjects.Iri
import zio.prelude.Validation

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
        userCreatePayload <- req.bodyAsString.map(_.fromJson[UserCreatePayload])

        r <- userCreatePayload match {
               case Left(e) => {
                 ZIO
                   .debug(s"Failed to parse the input: $e")
                   .as(
                     Response.text(e).setStatus(Status.BadRequest)
                   )
               }

               case Right(u) => {

                 val id         = Iri.UserIri.make(u.id)
                 val givenName  = GivenName.make(u.givenName)
                 val familyName = FamilyName.make(u.familyName)
                 val username   = Username.make(u.username)
                 val email      = Email.make(u.email)
                 val password   = PasswordHash.make(u.password, PasswordStrength(12))
                 val language   = LanguageCode.make(u.language)
                 val status     = UserStatus.make(u.status)

                 for {
                   validationResult <-
                     Validation.validate(id, givenName, familyName, username, email, password, language, status).toZIO
                   (id, givenName, familyName, username, email, password, language, status) = validationResult

                   userId <- userHandler.createUser(
                               username,
                               email,
                               givenName,
                               familyName,
                               password,
                               language,
                               status
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

final case class UserCreatePayload private (
  id: String,
  givenName: String,
  familyName: String,
  username: String,
  email: String,
  password: String,
  language: String,
  status: Boolean
) {}

object UserCreatePayload {
  implicit val decoder: JsonDecoder[UserCreatePayload] = DeriveJsonDecoder.gen[UserCreatePayload]
}
