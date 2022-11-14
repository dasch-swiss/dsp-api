package dsp.user.route

import zhttp.http.Request
import zhttp.http._
import zio._
import zio.json._
import zio.prelude.Validation

import dsp.errors.ValidationException
import dsp.user.handler.UserHandler
import dsp.valueobjects.LanguageCode
import dsp.valueobjects.User._

object CreateUser {

  /**
   * The route to create a user
   *
   * @param req
   * @param userHandler
   * @return a response with the user as json
   */
  def route(req: Request, userHandler: UserHandler): ZIO[Any, ValidationException, Response] =
    for {
      userCreatePayload <-
        req.body.asString.map(_.fromJson[CreateUserPayload]).orElseFail(ValidationException("Couldn't parse input"))

      response <-
        userCreatePayload match {
          case Left(e) => {
            ZIO.fail(ValidationException(s"Payload invalid: $e"))
          }

          case Right(u) => {
            val givenName  = GivenName.make(u.givenName)
            val familyName = FamilyName.make(u.familyName)
            val username   = Username.make(u.username)
            val email      = Email.make(u.email)
            val password =
              PasswordHash.make(
                u.password,
                PasswordStrength(12)
              ) // TODO use password strength from config instead
            val language = LanguageCode.make(u.language)
            val status   = UserStatus.make(u.status)

            (for {
              userId <-
                Validation
                  .validateWith(username, email, givenName, familyName, password, language, status)(
                    userHandler.createUser(_, _, _, _, _, _, _).mapError(e => ValidationException(e.getMessage()))
                  )
                  // in case of errors, all errors are collected and returned in a list
                  .fold(e => ZIO.fail(ValidationException(e.map(err => err.getMessage()).toCons.toString)), v => v)
              user <- userHandler.getUserById(userId).orDie
            } yield Response.json(user.toJson))
          }
        }
    } yield response

  /**
   * The payload needed to create a user.
   *
   * @param givenName
   * @param familyName
   * @param username
   * @param email
   * @param password
   * @param language
   * @param status
   */
  final case class CreateUserPayload private (
    givenName: String,
    familyName: String,
    username: String,
    email: String,
    password: String,
    language: String,
    status: Boolean
  )

  object CreateUserPayload {
    implicit val decoder: JsonDecoder[CreateUserPayload] = DeriveJsonDecoder.gen[CreateUserPayload]
  }

}
