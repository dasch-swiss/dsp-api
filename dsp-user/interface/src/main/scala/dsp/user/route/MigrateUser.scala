package dsp.user.route

import zhttp.http.Request
import zhttp.http._
import zio._
import zio.json._
import zio.prelude.Validation
import zio.prelude.ZValidation.Failure
import zio.prelude.ZValidation.Success

import dsp.config.AppConfig
import dsp.errors.ValidationException
import dsp.user.handler.UserHandler
import dsp.valueobjects.Id
import dsp.valueobjects.Iri
import dsp.valueobjects.LanguageCode
import dsp.valueobjects.User._

object MigrateUser {

  /**
   * The route to migrate an existing user
   *
   * @param req         the request that was sent
   * @param userHandler the userHandler that handles user actions
   * @return            a response with the user as json
   */
  def route(req: Request, userHandler: UserHandler): RIO[AppConfig, Response] =
    for {
      // get the appConfig from the environment
      appConfig <- ZIO.service[AppConfig]

      userMigratePayload <-
        req.body.asString.map(_.fromJson[MigrateUserPayload]).orElseFail(ValidationException("Couldn't parse payload"))

      response <-
        userMigratePayload match {
          case Left(e) => {
            ZIO.fail(ValidationException(s"Invalid payload: $e"))
          }

          case Right(u) => {
            val iri = Iri.UserIri.make(u.iri)
            val id = iri match {
              case Failure(_, errors) => Validation.failNonEmptyChunk(errors)
              case Success(_, value)  => Id.UserId.fromIri(value)
            }
            val username   = Username.make(u.username)
            val email      = Email.make(u.email)
            val givenName  = GivenName.make(u.givenName)
            val familyName = FamilyName.make(u.familyName)
            val password = PasswordHash.make(
              u.password,
              // at this point in time the config has already been checked, so it is OK to use unsafeMake()
              PasswordStrength.unsafeMake(
                appConfig.bcryptPasswordStrength
              )
            )
            val language = LanguageCode.make(u.language)
            val status   = UserStatus.make(u.status)

            for {
              userId <-
                Validation
                  .validateWith(id, username, email, givenName, familyName, password, language, status)(
                    userHandler.migrateUser _
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
   * The payload needed to migrate a user. It is the same as [[CreateUserPayload]] but with the existing IRI.
   *
   * @param iri
   * @param username
   * @param email
   * @param givenName
   * @param familyName
   * @param password
   * @param language
   * @param status
   */
  final case class MigrateUserPayload private (
    iri: String,
    username: String,
    email: String,
    givenName: String,
    familyName: String,
    password: String,
    language: String,
    status: Boolean
  )

  object MigrateUserPayload {
    implicit val decoder: JsonDecoder[MigrateUserPayload] = DeriveJsonDecoder.gen[MigrateUserPayload]
  }

}
