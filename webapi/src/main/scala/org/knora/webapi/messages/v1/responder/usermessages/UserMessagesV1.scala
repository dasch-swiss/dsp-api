/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v1.responder.usermessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

import dsp.errors.BadRequestException
import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsADMJsonProtocol
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectInfoV1
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectV1JsonProtocol
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileTypeV1.UserProfileType

/**
 * Represents a user's profile.
 *
 * @param userData       basic information about the user.
 * @param groups         the groups that the user belongs to.
 * @param projects_info  the projects that the user belongs to.
 * @param sessionId      the sessionId,.
 * @param permissionData the user's permission data.
 */
case class UserProfileV1(
  userData: UserDataV1 = UserDataV1(lang = "en"),
  groups: Seq[IRI] = Seq.empty[IRI],
  projects_info: Map[IRI, ProjectInfoV1] = Map.empty[IRI, ProjectInfoV1],
  sessionId: Option[String] = None,
  isSystemUser: Boolean = false,
  permissionData: PermissionsDataADM = PermissionsDataADM()
) {

  /**
   * Check password using either SHA-1 or SCrypt.
   * The SCrypt password always starts with '$e0801$' (spring.framework implementation)
   *
   * @param password the password to check.
   * @return true if password matches and false if password doesn't match.
   */
  def passwordMatch(password: String): Boolean =
    userData.password.exists { hashedPassword =>
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
   * Creating a [[UserProfileV1]] of the requested type.
   *
   * @return a [[UserProfileV1]]
   */
  def ofType(userProfileType: UserProfileType): UserProfileV1 =
    userProfileType match {
      case UserProfileTypeV1.SHORT =>
        val oldUserData = userData
        val newUserData = UserDataV1(
          user_id = oldUserData.user_id,
          token = None, // remove token
          firstname = oldUserData.firstname,
          lastname = oldUserData.lastname,
          email = oldUserData.email,
          password = None, // remove password
          status = oldUserData.status,
          lang = oldUserData.lang
        )

        UserProfileV1(
          userData = newUserData,
          groups = Vector.empty[IRI],                    // removed groups
          projects_info = Map.empty[IRI, ProjectInfoV1], // removed projects
          permissionData = PermissionsDataADM(),         // remove permissions
          sessionId = None                               // removed sessionId
        )
      case UserProfileTypeV1.RESTRICTED =>
        val oldUserData = userData
        val newUserData = UserDataV1(
          lang = oldUserData.lang,
          user_id = oldUserData.user_id,
          token = None, // remove token
          firstname = oldUserData.firstname,
          lastname = oldUserData.lastname,
          email = oldUserData.email,
          password = None, // remove password
          status = oldUserData.status
        )

        UserProfileV1(
          userData = newUserData,
          groups = groups,
          projects_info = projects_info,
          permissionData = permissionData,
          sessionId = None // removed sessionId
        )
      case UserProfileTypeV1.FULL =>
        UserProfileV1(
          userData = userData,
          groups = groups,
          projects_info = projects_info,
          permissionData = permissionData,
          sessionId = sessionId
        )
      case _ => throw BadRequestException(s"The requested userProfileType: $userProfileType is invalid.")
    }

  def isAnonymousUser: Boolean =
    userData.user_id.isEmpty

  def isActive: Boolean =
    userData.status.getOrElse(false)

  def toJsValue: JsValue = UserV1JsonProtocol.userProfileV1Format.write(this)

}

/**
 * Represents basic information about a user.
 *
 * @param user_id   The user's IRI.
 * @param email     The user's email address.
 * @param password  The user's hashed password.
 * @param token     The API token. Can be used instead of email/password for authentication.
 * @param firstname The user's given name.
 * @param lastname  The user's surname.
 * @param status    The user's status.
 * @param lang      The ISO 639-1 code of the user's preferred language.
 */
case class UserDataV1(
  user_id: Option[IRI] = None,
  email: Option[String] = None,
  password: Option[String] = None,
  token: Option[String] = None,
  firstname: Option[String] = None,
  lastname: Option[String] = None,
  status: Option[Boolean] = Some(true),
  lang: String
) {
  def toJsValue: JsValue = UserV1JsonProtocol.userDataV1Format.write(this)
}

/**
 * UserProfile types:
 * restricted: everything without sensitive information, i.e. token, password.
 * full: everything.
 *
 * Mainly used in combination with the 'ofType' method, to make sure that a request receiving this information
 * also returns the user profile of the correct type. Should be used in cases where we don't want to expose
 * sensitive information to the outside world. Since in API V1 [[UserDataV1]] is returned with some responses,
 * we use 'restricted' in those cases.
 */
object UserProfileTypeV1 extends Enumeration {
  /* TODO: Extend to incorporate user privacy wishes */

  type UserProfileType = Value

  val SHORT: UserProfileTypeV1.Value      = Value(0, "short")      // only userdata
  val RESTRICTED: UserProfileTypeV1.Value = Value(1, "restricted") // without sensitive information
  val FULL: UserProfileTypeV1.Value       = Value(2, "full")       // everything, including sensitive information

  val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

  /**
   * Given the name of a value in this enumeration, returns the value. If the value is not found, throws an
   * [[InconsistentRepositoryDataException]].
   *
   * @param name the name of the value.
   * @return the requested value.
   */
  def lookup(name: String): Value =
    valueMap.get(name) match {
      case Some(value) => value
      case None        => throw InconsistentRepositoryDataException(s"User profile type not supported: $name")
    }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
 * A spray-json protocol for formatting objects as JSON.
 */
object UserV1JsonProtocol
    extends SprayJsonSupport
    with DefaultJsonProtocol
    with NullOptions
    with ProjectV1JsonProtocol
    with PermissionsADMJsonProtocol {

  implicit val userDataV1Format: JsonFormat[UserDataV1]       = lazyFormat(jsonFormat8(UserDataV1))
  implicit val userProfileV1Format: JsonFormat[UserProfileV1] = jsonFormat6(UserProfileV1)
}
