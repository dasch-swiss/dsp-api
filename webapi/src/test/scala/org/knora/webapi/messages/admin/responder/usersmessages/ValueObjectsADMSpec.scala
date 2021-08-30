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

package org.knora.webapi.messages.admin.responder.usersmessages

import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter

object ValueObjectsADMSpec {
  val config: Config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * This spec is used to test the creation of value objects of the [[ValueObject]] trait.
  */
class ValueObjectsADMSpec extends CoreSpec(ValueObjectsADMSpec.config) {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

  "The CreateUserApiRequestADM case class" should {
    "throw 'BadRequestException' if 'username'is missing" in {

      val createUserApiRequestADM: CreateUserApiRequestADM = CreateUserApiRequestADM(
        username = "",
        email = "ddd@example.com",
        givenName = "Donald",
        familyName = "Duck",
        password = "test",
        status = true,
        lang = "en",
        systemAdmin = false
      )

      assertThrows[BadRequestException](
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
          systemAdmin =
            SystemAdmin.create(createUserApiRequestADM.systemAdmin).fold(error => throw error, value => value)
        )
      )
    }

    "throw 'BadRequestException' if 'email' is missing" in {

      val createUserApiRequestADM: CreateUserApiRequestADM =
        CreateUserApiRequestADM(
          username = "ddd",
          email = "",
          givenName = "Donald",
          familyName = "Duck",
          password = "test",
          status = true,
          lang = "en",
          systemAdmin = false
        )

      assertThrows[BadRequestException](
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
          systemAdmin =
            SystemAdmin.create(createUserApiRequestADM.systemAdmin).fold(error => throw error, value => value)
        )
      )
    }

    "throw 'BadRequestException' if 'password' is missing" in {

      val createUserApiRequestADM: CreateUserApiRequestADM =
        CreateUserApiRequestADM(
          username = "donald.duck",
          email = "donald.duck@example.com",
          givenName = "Donald",
          familyName = "Duck",
          password = "",
          status = true,
          lang = "en",
          systemAdmin = false
        )

      assertThrows[BadRequestException](
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
          systemAdmin =
            SystemAdmin.create(createUserApiRequestADM.systemAdmin).fold(error => throw error, value => value)
        )
      )
    }

    "throw 'BadRequestException' if 'givenName' is missing" in {

      val createUserApiRequestADM: CreateUserApiRequestADM =
        CreateUserApiRequestADM(
          username = "donald.duck",
          email = "donald.duck@example.com",
          givenName = "",
          familyName = "Duck",
          password = "test",
          status = true,
          lang = "en",
          systemAdmin = false
        )

      assertThrows[BadRequestException](
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
          systemAdmin =
            SystemAdmin.create(createUserApiRequestADM.systemAdmin).fold(error => throw error, value => value)
        )
      )
    }

    "throw 'BadRequestException' if 'familyName' is missing" in {

      val createUserApiRequestADM: CreateUserApiRequestADM =
        CreateUserApiRequestADM(
          username = "donald.duck",
          email = "donald.duck@example.com",
          givenName = "Donald",
          familyName = "",
          password = "test",
          status = true,
          lang = "en",
          systemAdmin = false
        )

      assertThrows[BadRequestException](
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
          systemAdmin =
            SystemAdmin.create(createUserApiRequestADM.systemAdmin).fold(error => throw error, value => value)
        )
      )
    }

    "return 'BadRequest' if the supplied 'id' is not a valid IRI" in {
      val createUserApiRequestADM: CreateUserApiRequestADM =
        CreateUserApiRequestADM(
          id = Some("invalid-user-IRI"),
          username = "userWithInvalidCustomIri",
          email = "userWithInvalidCustomIri@example.org",
          givenName = "a user",
          familyName = "with an invalid custom Iri",
          password = "test",
          status = true,
          lang = "en",
          systemAdmin = false
        )

      assertThrows[BadRequestException](
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
          systemAdmin =
            SystemAdmin.create(createUserApiRequestADM.systemAdmin).fold(error => throw error, value => value)
        )
      )

    }

  }

}
