package dsp.user.route

import zhttp.http.Request
import zhttp.http._
import zio._
import zio.json._
import zio.prelude.Validation
import zio.prelude.ZValidation.Failure
import zio.prelude.ZValidation.Success

import dsp.errors.ValidationException
import dsp.user.handler.UserHandler
import dsp.valueobjects.Id
import dsp.valueobjects.Iri
import dsp.valueobjects.LanguageCode
import dsp.valueobjects.User._

object MigrateUser {

  /**
   * The route to create a user
   *
   * @param req
   * @param userHandler
   * @return the user as json
   */
  def route(req: Request, userHandler: UserHandler): ZIO[Any, ValidationException, Response] =
    for {
      userMigratePayload <-
        req.bodyAsString.map(_.fromJson[MigrateUserPayload]).orElseFail(ValidationException("Couldn't parse input"))

      r <- userMigratePayload match {
             case Left(e) => {
               ZIO.fail(ValidationException(s"Payload invalid: $e"))
             }

             case Right(u) => {
               val iri = Iri.UserIri.make(u.iri)
               val id = iri match {
                 case Failure(_, errors) => Validation.failNonEmptyChunk(errors)
                 case Success(_, value)  => Id.UserId.fromIri(value)
               }
               val givenName  = GivenName.make(u.givenName)
               val familyName = FamilyName.make(u.familyName)
               val username   = Username.make(u.username)
               val email      = Email.make(u.email)
               val password   = PasswordHash.make(u.password, PasswordStrength(12))
               val language   = LanguageCode.make(u.language)
               val status     = UserStatus.make(u.status)

               (for {
                 validationResult <-
                   Validation
                     .validate(id, givenName, familyName, username, email, password, language, status)
                     .toZIO
                     .mapError(e => ValidationException(e.getMessage()))

                 (id, givenName, familyName, username, email, password, language, status) = validationResult

                 userId <- userHandler
                             .migrateUser(
                               id,
                               username,
                               email,
                               givenName,
                               familyName,
                               password,
                               language,
                               status
                             )
                             .mapError(e => ValidationException(e.getMessage()))
                 user <- userHandler.getUserById(userId).orDie
               } yield Response.json(user.toJson))
             }
           }
    } yield r

  /**
   * The payload needed to migrate a user. It is the same as [[CreateUserPayload]] but with the existing IRI.
   *
   * @param iri
   * @param givenName
   * @param familyName
   * @param username
   * @param email
   * @param password
   * @param language
   * @param status
   */
  final case class MigrateUserPayload private (
    iri: String,
    givenName: String,
    familyName: String,
    username: String,
    email: String,
    password: String,
    language: String,
    status: Boolean
  ) {}

  object MigrateUserPayload {
    implicit val decoder: JsonDecoder[MigrateUserPayload] = DeriveJsonDecoder.gen[MigrateUserPayload]
  }

}
