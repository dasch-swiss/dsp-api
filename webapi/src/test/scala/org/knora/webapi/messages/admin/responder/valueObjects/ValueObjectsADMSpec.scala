/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.admin.responder.valueObjects

import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.{IRI, UnitSpec}
import org.scalatest.enablers.Messaging.messagingNatureOfThrowable

object ValueObjectsADMSpec {
  val config: Config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * This spec is used to test the creation of value objects of the [[ValueObject]] trait.
  */
class ValueObjectsADMSpec extends UnitSpec(ValueObjectsADMSpec.config) {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

  /**
   * Convenience method returning the UserEntity from the [[CreateUserApiRequestADM]] object
   *
   * @param createUserApiRequestADM the [[CreateUserApiRequestADM]] object
   * @return                        a [[UserEntity]]
   */
  private def createUserEntity(createUserApiRequestADM: CreateUserApiRequestADM): UserEntity = {
    UserEntity(
      id = stringFormatter.validateOptionalUserIri(createUserApiRequestADM.id,
                                                   throw BadRequestException(s"Invalid user IRI")),
      username = Username.create(createUserApiRequestADM.username).fold(error => throw error, value => value),
      email = Email.create(createUserApiRequestADM.email).fold(error => throw error, value => value),
      givenName = GivenName.create(createUserApiRequestADM.givenName).fold(error => throw error, value => value),
      familyName = FamilyName.create(createUserApiRequestADM.familyName).fold(error => throw error, value => value),
      password = Password.create(createUserApiRequestADM.password).fold(error => throw error, value => value),
      status = Status.create(createUserApiRequestADM.status).fold(error => throw error, value => value),
      lang = LanguageCode.create(createUserApiRequestADM.lang).fold(error => throw error, value => value),
      systemAdmin = SystemAdmin.create(createUserApiRequestADM.systemAdmin).fold(error => throw error, value => value)
    )
  }

  /**
   * Convenience method returning the [[CreateUserApiRequestADM]] object
   *
   * @param id          the optional IRI of the user to be created (unique).
   * @param username    the username of the user to be created (unique).
   * @param email       the email of the user to be created (unique).
   * @param givenName   the given name of the user to be created.
   * @param familyName  the family name of the user to be created
   * @param password    the password of the user to be created.
   * @param status      the status of the user to be created (active = true, inactive = false).
   * @param lang        the default language of the user to be created.
   * @param systemAdmin the system admin membership.
   * @return            a [[UserEntity]]
   */
  private def createUserApiRequestADM(
      id: Option[IRI] = None,
      username: String = "donald.duck",
      email: String = "donald.duck@example.com",
      givenName: String = "Donald",
      familyName: String = "Duck",
      password: String = "test",
      status: Boolean = true,
      lang: String = "en",
      systemAdmin: Boolean = false
  ): CreateUserApiRequestADM = {
    CreateUserApiRequestADM(
      id = id,
      username = username,
      email = email,
      givenName = givenName,
      familyName = familyName,
      password = password,
      status = status,
      lang = lang,
      systemAdmin = systemAdmin
    )
  }

  "When the UserEntity case class is created it" should {
    "create a valid UserEntity" in {

      val request = createUserApiRequestADM()

      val userEntity = createUserEntity(request)

      userEntity.id should equal(request.id)
      userEntity.username.value should equal(request.username)
      userEntity.email.value should equal(request.email)
      userEntity.password.value should equal(request.password)
      userEntity.givenName.value should equal(request.givenName)
      userEntity.familyName.value should equal(request.familyName)
      userEntity.status.value should equal(request.status)
      userEntity.lang.value should equal(request.lang)
      userEntity.systemAdmin.value should equal(request.systemAdmin)

      val otherRequest = createUserApiRequestADM(
        id = Some("http://rdfh.ch/users/notdonald"),
        username = "not.donald.duck",
        email = "not.donald.duck@example.com",
        givenName = "NotDonald",
        familyName = "NotDuck",
        password = "notDonaldDuckTest",
        status = false,
        lang = "de",
        systemAdmin = true
      )

      val otherUserEntity = createUserEntity(otherRequest)

      otherUserEntity.id should equal(otherRequest.id)
      otherUserEntity.username.value should equal(otherRequest.username)
      otherUserEntity.email.value should equal(otherRequest.email)
      otherUserEntity.password.value should equal(otherRequest.password)
      otherUserEntity.givenName.value should equal(otherRequest.givenName)
      otherUserEntity.familyName.value should equal(otherRequest.familyName)
      otherUserEntity.status.value should equal(otherRequest.status)
      otherUserEntity.lang.value should equal(otherRequest.lang)
      otherUserEntity.systemAdmin.value should equal(otherRequest.systemAdmin)

      otherUserEntity.id should not equal request.id
      otherUserEntity.username.value should not equal request.username
      otherUserEntity.email.value should not equal request.email
      otherUserEntity.password.value should not equal request.password
      otherUserEntity.givenName.value should not equal request.givenName
      otherUserEntity.familyName.value should not equal request.familyName
      otherUserEntity.status.value should not equal request.status
      otherUserEntity.lang.value should not equal request.lang
      otherUserEntity.systemAdmin.value should not equal request.systemAdmin
    }

    "throw 'BadRequestException' if 'username' is missing" in {
      val request = createUserApiRequestADM(username = "")

      the[BadRequestException] thrownBy createUserEntity(request) should have message "Missing username"
    }

    "throw 'BadRequestException' if 'email' is missing" in {
      val request = createUserApiRequestADM(email = "")

      the[BadRequestException] thrownBy createUserEntity(request) should have message "Missing email"
    }

    "throw 'BadRequestException' if 'password' is missing" in {
      val request = createUserApiRequestADM(password = "")

      the[BadRequestException] thrownBy createUserEntity(request) should have message "Missing password"
    }

    "throw 'BadRequestException' if 'givenName' is missing" in {
      val request = createUserApiRequestADM(givenName = "")

      the[BadRequestException] thrownBy createUserEntity(request) should have message "Missing given name"
    }

    "throw 'BadRequestException' if 'familyName' is missing" in {
      val request = createUserApiRequestADM(familyName = "")

      the[BadRequestException] thrownBy createUserEntity(request) should have message "Missing family name"
    }

    "throw 'BadRequestException' if 'lang' is missing" in {
      val request = createUserApiRequestADM(lang = "")

      the[BadRequestException] thrownBy createUserEntity(request) should have message "Missing language code"
    }

    "throw 'BadRequestException' if the supplied 'id' is not a valid IRI" in {
      val request = createUserApiRequestADM(id = Some("invalid-iri"))

      the[BadRequestException] thrownBy createUserEntity(request) should have message "Invalid user IRI"

    }

    "throw 'BadRequestException' if 'username' is invalid" in {
      Set(
        createUserApiRequestADM(username = "don"), // too short
        createUserApiRequestADM(username = "asdfoiasdfasdnlasdkjflasdjfaskdjflaskdjfaddssdskdfjs"), // too long
        createUserApiRequestADM(username = "_donald"), // starts with _
        createUserApiRequestADM(username = ".donald"), // starts with .
        createUserApiRequestADM(username = "donald_"), // ends with _
        createUserApiRequestADM(username = "donald."), // ends with .
        createUserApiRequestADM(username = "donald__duck"), // contains multiple _
        createUserApiRequestADM(username = "donald..duck"), // contains multiple .
        createUserApiRequestADM(username = "donald#duck"), // contains not only alphanumeric characters
        createUserApiRequestADM(username = "dönälddück") // contains umlaut characters
      ).map(request =>
        the[BadRequestException] thrownBy createUserEntity(request) should have message "Invalid username")
    }

    "throw 'BadRequestException' if 'email' is invalid" in {
      Set(
        createUserApiRequestADM(email = "don"), // does not contain @
        createUserApiRequestADM(email = "don@"), // ends with @
        createUserApiRequestADM(email = "@don"), // starts with @
      ).map(request =>
        the[BadRequestException] thrownBy createUserEntity(request) should have message "Invalid email")
    }

    "throw 'BadRequestException' if 'lang' is invalid" in {
      val request = createUserApiRequestADM(lang = "xy")

      the[BadRequestException] thrownBy createUserEntity(request) should have message "Invalid language code"
    }

  }

}
