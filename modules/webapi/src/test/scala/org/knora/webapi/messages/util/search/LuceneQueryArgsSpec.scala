/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class LuceneQueryArgsSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment, Any] = suite("LuceneQueryArgs.toSparql")(
    test("renders a plain term with the limit") {
      assertTrue(LuceneQueryArgs("ir14*", 1000000).toSparql == "(\"ir14*\" 1000000)")
    },
    test("escapes a quote, a backslash, LF and CR in the term") {
      val term = "a\"b\\c\nd\re"
      assertTrue(LuceneQueryArgs(term, 5).toSparql == """("a\"b\\c\nd\re" 5)""")
    },
    test("escapes the backslash before the characters it escapes") {
      assertTrue(LuceneQueryArgs("\\\"", 5).toSparql == """("\\\"" 5)""")
    },
  )
}
