package org.knora.webapi.messages.util.rdf

import zio._
import zio.test.Assertion.assertion
import zio.test.Assertion.equalTo
import zio.test.Assertion.fails
import zio.test.Assertion.failsCause
import zio.test.Assertion.failsWithA
import zio.test._
import scala.util.Try

import dsp.errors.BadRequestException
import org.knora.webapi.messages.util.rdf.JsonLDObjectSpec.test

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
      test("maybeString should return None") {
        assertTrue(empty.maybeString(someKey).isEmpty)
      },
      test("getString should return None") {
        for {
          actual <- empty.getString(someKey)
        } yield assertTrue(actual.isEmpty)
      },
      test("requireString should Fail") {
        assertTrue(Try(empty.requireString(someKey)).isFailure)
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
