/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.store.triplestoremessages

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json._

import dsp.errors.BadRequestException
import org.knora.webapi.messages.admin.responder.listsmessages._

/**
 * This spec is used to test 'ListAdminMessages'.
 */
class TriplestoreMessagesSpec extends AnyWordSpecLike with Matchers with ListADMJsonProtocol {

  "Conversion from case class to JSON and back" should {

    "work for a 'StringLiteralV2' without language tag" in {

      val string = StringLiteralV2("stringwithoutlang", None)
      val json   = string.toJson.compactPrint

      json should be("{\"value\":\"stringwithoutlang\"}")

      val converted: StringLiteralV2 = json.parseJson.convertTo[StringLiteralV2]

      converted should be(string)
    }

    "work for a 'StringLiteralV2' with language tag" in {

      val string = StringLiteralV2("stringwithlang", Some("de"))
      val json   = string.toJson.compactPrint

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
