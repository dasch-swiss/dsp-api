package dsp.user.route

import zhttp.http.Request
import zhttp.http._
import zio._
import zio.json._
import zio.prelude.Validation

import dsp.config.AppConfig
import dsp.errors.ValidationException
import dsp.user.handler.UserHandler
import dsp.valueobjects.LanguageCode
import dsp.valueobjects.User._

object CreateUser {

  /**
   * The route to create a user
   *
   * @param req         the request that was sent
   * @param userHandler the userHandler that handles user actions
   * @return            a response with the user as json
   */
  def route(req: Request, userHandler: UserHandler): RIO[AppConfig, Response] =
    for {
      // get the appConfig from the environment
      appConfig <- ZIO.service[AppConfig]

      userCreatePayload <-
        req.body.asString.map(_.fromJson[CreateUserPayload]).orElseFail(ValidationException("Couldn't parse payload"))

      response <-
        userCreatePayload match {
          case Left(e) => {
            ZIO.fail(ValidationException(s"Invalid payload: $e"))
          }

          case Right(u) => {
            val username   = Username.make(u.username)
            val email      = Email.make(u.email)
            val givenName  = GivenName.make(u.givenName)
            val familyName = FamilyName.make(u.familyName)
            val password = PasswordHash.make(
              u.password,
              PasswordStrength.unsafeMake(appConfig.bcryptPasswordStrength)
            )
            val language = LanguageCode.make(u.language)
            val status   = UserStatus.make(u.status)

            for {
              userId <-
                Validation
                  .validateWith(username, email, givenName, familyName, password, language, status)(
                    userHandler.createUser _
                  )
                  // in case of errors, all errors are collected and returned in a list
                  // TODO what should be the type of the exception?
                  .fold(e => ZIO.fail(ValidationException(e.map(err => err.getMessage()).toCons.toString)), v => v)
              user <- userHandler.getUserById(userId).orDie
            } yield Response.json(user.toJson)
          }
        }
    } yield response

  /**
   * The payload needed to create a user.
   *
   * @param username
   * @param email
   * @param givenName
   * @param familyName
   * @param password
   * @param language
   * @param status
   */
  final case class CreateUserPayload private (
    username: String,
    email: String,
    givenName: String,
    familyName: String,
    password: String,
    language: String,
    status: Boolean
  )

  object CreateUserPayload {
    implicit val decoder: JsonDecoder[CreateUserPayload] = DeriveJsonDecoder.gen[CreateUserPayload]
  }

}
