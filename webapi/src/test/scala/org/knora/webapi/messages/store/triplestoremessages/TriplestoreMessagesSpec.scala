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

package org.knora.webapi.messages.store.triplestoremessages

import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json._

/**
 * This spec is used to test 'ListAdminMessages'.
 */
class TriplestoreMessagesSpec extends AnyWordSpecLike with Matchers with ListADMJsonProtocol {

  "Conversion from case class to JSON and back" should {

    "work for a 'StringLiteralV2' without language tag" in {

      val string = StringLiteralV2("stringwithoutlang", None)
      val json = string.toJson.compactPrint

      json should be("{\"value\":\"stringwithoutlang\"}")

      val converted: StringLiteralV2 = json.parseJson.convertTo[StringLiteralV2]

      converted should be(string)
    }

    "work for a 'StringLiteralV2' with language tag" in {

      val string = StringLiteralV2("stringwithlang", Some("de"))
      val json = string.toJson.compactPrint

      json should be("{\"value\":\"stringwithlang\",\"language\":\"de\"}")

      val converted = json.parseJson.convertTo[StringLiteralV2]

      converted should be(string)
    }
  }

  "Creating a `StringLiteralV2`" should {
    "fail when language tag is given but value is missing" in {
      val caught = intercept[BadRequestException](
        StringLiteralV2("", Some("de"))
      )
      assert(caught.getMessage === "String value is missing.")
    }
  }
}
