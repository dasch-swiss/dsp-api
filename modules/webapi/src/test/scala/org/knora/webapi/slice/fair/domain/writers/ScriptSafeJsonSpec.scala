/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain.writers

import org.junit.runner.RunWith
import zio.Chunk
import zio.json.*
import zio.json.ast.Json
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class ScriptSafeJsonSpec extends ZIOSpecDefault {

  private val lineSeparator      = 0x2028.toChar.toString
  private val paragraphSeparator = 0x2029.toChar.toString
  private val forbidden          = List("<", ">", "&", lineSeparator, paragraphSeparator)

  private def hasNoForbidden(out: String): Boolean = forbidden.forall(c => !out.contains(c))

  private val hostile =
    s"</script><script>alert(1)</script> <!-- \"quoted\" \n line\n $lineSeparator$paragraphSeparator & Zürich 😀"

  override def spec: Spec[Any, Any] = suite("ScriptSafeJson")(
    test("hostile label and description are neutralised and decode to the same Json") {
      val json = Json.Obj(
        Chunk(
          "name"        -> Json.Str(hostile),
          "description" -> Json.Arr(Json.Str(hostile)),
        ),
      )
      val out = ScriptSafeJson.render(json)
      assertTrue(hasNoForbidden(out), out.fromJson[Json] == Right(json))
    },
    test("a script end tag becomes escapes") {
      val out = ScriptSafeJson.render(Json.Str("</script><!--"))
      assertTrue(out == "\"\\u003c/script\\u003e\\u003c!--\"")
    },
    test("object keys are escaped as well") {
      val json = Json.Obj(Chunk("a<b&c" -> Json.Num(1)))
      val out  = ScriptSafeJson.render(json)
      assertTrue(hasNoForbidden(out), out.fromJson[Json] == Right(json))
    },
    test("any string is escaped and decodes back") {
      val special = Gen.elements('<', '>', '&', 0x2028.toChar, 0x2029.toChar, '"', '\\', '\n')
      val strings = Gen.oneOf(
        Gen.string,
        Gen.listOf(Gen.oneOf(Gen.unicodeChar, special)).map(_.mkString),
      )
      check(strings) { s =>
        val out = ScriptSafeJson.render(Json.Str(s))
        assertTrue(hasNoForbidden(out), out.fromJson[Json] == Right(Json.Str(s)))
      }
    },
  )
}
