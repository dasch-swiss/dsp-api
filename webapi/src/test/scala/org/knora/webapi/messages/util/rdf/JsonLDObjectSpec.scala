package org.knora.webapi.messages.util.rdf

import zio._
import zio.test.Assertion.assertion
import zio.test.Assertion.equalTo
import zio.test.Assertion.fails
import zio.test.Assertion.failsCause
import zio.test.Assertion.failsWithA
import zio.test._

import dsp.errors.BadRequestException

object JsonLDObjectSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("JsonLDObject")(givenAnEmptyObject)

  private val givenAnEmptyObject = {
    val empty   = JsonLDObject(Map.empty)
    val someKey = "someKey"
    suite("when given an empty map")(
      test("getObjectIri should return None") {
        for {
          actual <- empty.getObjectIri(someKey)
        } yield assertTrue(actual.isEmpty)
      },
      test("maybeObject should return None") {
        assertTrue(empty.maybeObject(someKey).isEmpty)
      },
      test("getObject should return None") {
        for {
          actual <- empty.getObject(someKey)
        } yield assertTrue(actual.isEmpty)
      },
      test("getString should return None") {
        for {
          actual <- empty.getString(someKey)
        } yield assertTrue(actual.isEmpty)
      },
      test("getRequiredString should fail") {
        for {
          actual <- empty.getRequiredString(someKey).exit
        } yield assertTrue(actual == Exit.fail("No someKey provided"))
      },
      test("getRequiredObject should fail") {
        for {
          actual <- empty.getRequiredObject(someKey).exit
        } yield assertTrue(actual == Exit.fail("No someKey provided"))
      }
    )
  }

}
