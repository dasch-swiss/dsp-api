/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.proto

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class PersonProtoSpec extends AnyWordSpecLike with Matchers {
  "The generated proto" should {
    "allow creating a person" in {
      val testZip = Some(proto.zip_code.ZipCode(code = "4123"))

      val testAddress = Some(
        proto.address.Address(
          city = "Allschwil",
          zipCode = testZip
        ))

      val person = proto.person.Person(
        name = "anyname",
        id = 123456,
        email = "any@example.org",
        address = testAddress,
        favoriteThing = "blue thing"
      )

      val serialisedPerson: Array[Byte] = person.toByteArray
      val parsedPerson: proto.person.Person = proto.person.Person.parseFrom(serialisedPerson)

      parsedPerson.name should be("anyname")
      parsedPerson.id should be(123456)
      parsedPerson.email should be("any@example.org")
      parsedPerson.address should be(testAddress)
      parsedPerson.favoriteThing should be("blue thing")
    }
  }
}
