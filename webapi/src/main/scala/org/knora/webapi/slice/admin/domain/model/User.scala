/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.RefinedTypeOps
import eu.timepit.refined.string.MatchesRegex
import spray.json.JsValue
import zio.json.JsonDecoder
import zio.json.JsonEncoder
import zio.prelude.Validation

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri.isUserIri
import dsp.valueobjects.Iri.validateAndEscapeUserIri
import dsp.valueobjects.IriErrorMessages
import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserInformationTypeADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol

/**
 * Represents a user's profile.
 *
 * @param id          The user's IRI.
 * @param username    The user's username (unique).
 * @param email       The user's email address.
 * @param password    The user's hashed password.
 * @param token       The API token. Can be used instead of email/password for authentication.
 * @param givenName   The user's given name.
 * @param familyName  The user's surname.
 * @param status      The user's status.
 * @param lang        The ISO 639-1 code of the user's preferred language.
 * @param groups      The groups that the user belongs to.
 * @param projects    The projects that the user belongs to.
 * @param permissions The user's permissions.
 */
final case class User(
  id: String,
  username: String,
  email: String,
  givenName: String,
  familyName: String,
  status: Boolean,
  lang: String,
  password: Option[String] = None,
  token: Option[String] = None,
  groups: Seq[GroupADM] = Vector.empty[GroupADM],
  projects: Seq[ProjectADM] = Seq.empty[ProjectADM],
  permissions: PermissionsDataADM = PermissionsDataADM()
) extends Ordered[User] { self =>

  /**
   * Allows to sort collections of UserADM. Sorting is done by the id.
   */
  def compare(that: User): Int = this.id.compareTo(that.id)

  /**
   * Check password (in clear text) using SCrypt. The password supplied in clear text is hashed and
   * compared against the stored hash.
   *
   * @param password the password to check.
   * @return true if password matches and false if password doesn't match.
   */
  def passwordMatch(password: String): Boolean =
    this.password.exists { hashedPassword =>
      // check which type of hash we have
      if (hashedPassword.startsWith("$e0801$")) {
        // SCrypt
        import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder
        val encoder = new SCryptPasswordEncoder(16384, 8, 1, 32, 64)
        encoder.matches(password, hashedPassword)
      } else if (hashedPassword.startsWith("$2a$")) {
        // BCrypt
        import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
        val encoder = new BCryptPasswordEncoder()
        encoder.matches(password, hashedPassword)
      } else {
        // SHA-1
        val md = java.security.MessageDigest.getInstance("SHA-1")
        md.digest(password.getBytes("UTF-8")).map("%02x".format(_)).mkString.equals(hashedPassword)
      }
    }

  /**
   * Creating a [[User]] of the requested type.
   *
   * @return a [[User]]
   */
  def ofType(userTemplateType: UserInformationTypeADM): User =
    userTemplateType match {
      case UserInformationTypeADM.Public =>
        self.copy(
          username = "",
          email = "",
          status = false,
          lang = "",
          password = None,
          token = None,
          groups = Seq.empty[GroupADM],
          projects = Seq.empty[ProjectADM],
          permissions = PermissionsDataADM()
        )
      case UserInformationTypeADM.Short =>
        self.copy(
          password = None,
          token = None,
          groups = Seq.empty[GroupADM],
          projects = Seq.empty[ProjectADM],
          permissions = PermissionsDataADM()
        )
      case UserInformationTypeADM.Restricted =>
        self.copy(password = None, token = None)
      case UserInformationTypeADM.Full =>
        self
      case _ => throw BadRequestException(s"The requested userTemplateType: $userTemplateType is invalid.")
    }

  /**
   * Given an identifier, returns true if it is the same user, and false if not.
   */
  def isSelf(identifier: UserIdentifierADM): Boolean = {

    val iriEquals      = identifier.toIriOption.contains(id)
    val emailEquals    = identifier.toEmailOption.contains(email)
    val usernameEquals = identifier.toUsernameOption.contains(username)

    iriEquals || emailEquals || usernameEquals
  }

  /**
   * Is the user a member of the SystemAdmin group
   */
  def isSystemAdmin: Boolean =
    permissions.groupsPerProject
      .getOrElse(OntologyConstants.KnoraAdmin.SystemProject, List.empty[IRI])
      .contains(OntologyConstants.KnoraAdmin.SystemAdmin)

  def isSystemUser: Boolean = id.equalsIgnoreCase(OntologyConstants.KnoraAdmin.SystemUser)

  def isActive: Boolean =
    status

  def toJsValue: JsValue = UsersADMJsonProtocol.userADMFormat.write(this)

  def isAnonymousUser: Boolean = id.equalsIgnoreCase(OntologyConstants.KnoraAdmin.AnonymousUser)
}

object Username {

  /**
   * Username validated by regex:
   * - 4 - 50 characters long
   * - Only contains alphanumeric characters, underscore and dot.
   * - Underscore and dot can't be at the end or start of a username
   * - Underscore or dot can't be used multiple times in a row
   */
  type Username = String Refined MatchesRegex["^(?=.{4,50}$)(?![_.])(?!.*[_.]{2})[a-zA-Z0-9._]+(?<![_.])$"]

  object Username extends RefinedTypeOps[Username, String] {
    //  implicit val codec: JsonCodec[Username] = JsonCodec[String].transformOrFail(Username.from, _.toString)
  }
}

object Email {

  type Email = String Refined MatchesRegex["^.+@.+$"]
  object Email extends RefinedTypeOps[Email, String] {
    //  implicit val codec: JsonCodec[Email] = JsonCodec[String].transformOrFail(Email.from, _.toString)
  }

}

final case class UserIri private (value: String) extends AnyVal
object UserIri {
  implicit val decoder: JsonDecoder[UserIri] =
    JsonDecoder[String].mapOrFail(value => UserIri.make(value).toEitherWith(e => e.head.getMessage))
  implicit val encoder: JsonEncoder[UserIri] = JsonEncoder[String].contramap((userIri: UserIri) => userIri.value)

  def make(value: String): Validation[Throwable, UserIri] =
    if (value.isEmpty) Validation.fail(BadRequestException(UserErrorMessages.UserIriMissing))
    else {
      val isUuid: Boolean = UuidUtil.hasValidLength(value.split("/").last)

      if (!isUserIri(value))
        Validation.fail(BadRequestException(UserErrorMessages.UserIriInvalid(value)))
      else if (isUuid && !UuidUtil.hasSupportedVersion(value))
        Validation.fail(BadRequestException(IriErrorMessages.UuidVersionInvalid))
      else
        Validation
          .fromOption(validateAndEscapeUserIri(value))
          .mapError(_ => BadRequestException(UserErrorMessages.UserIriInvalid(value)))
          .map(UserIri(_))
    }
}

object UserErrorMessages {
  val UserIriMissing: String           = "User IRI cannot be empty."
  val UserIriInvalid: String => String = (iri: String) => s"User IRI: $iri is invalid."
}
