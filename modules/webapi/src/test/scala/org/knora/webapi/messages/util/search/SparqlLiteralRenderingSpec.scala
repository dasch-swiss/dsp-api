/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search

import org.junit.runner.RunWith
import zio.Runtime
import zio.Unsafe
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter

@RunWith(classOf[DspZTestJUnitRunner])
class SparqlLiteralRenderingSpec extends ZIOSpecDefault {
  private implicit val stringFormatter: StringFormatter = {
    val config = Unsafe.unsafe(implicit u => Runtime.default.unsafe.run(AppConfig.parseConfig).getOrThrowFiberFailure())
    StringFormatter.init(config)
    StringFormatter.getGeneralInstance
  }

  private val xsdString = OntologyConstants.Xsd.String.toSmartIri
  private val textVar   = QueryVariable("text")

  override def spec: Spec[TestEnvironment, Any] = suite("SPARQL literal rendering")(
    suite("XsdLiteral.toSparql")(
      test("renders a plain value unchanged") {
        assertTrue(XsdLiteral("Tiere", xsdString).toSparql == "\"Tiere\"^^<http://www.w3.org/2001/XMLSchema#string>")
      },
      test("escapes a quote, a backslash, LF and CR in the value") {
        assertTrue(
          XsdLiteral("a\"b\\c\nd\re", xsdString).toSparql ==
            """"a\"b\\c\nd\re"^^<http://www.w3.org/2001/XMLSchema#string>""",
        )
      },
    ),
    suite("RegexFunction.toSparql")(
      test("escapes a backslash in the pattern, so a regex escape reaches the triplestore intact") {
        assertTrue(RegexFunction(textVar, "\\d+", None).toSparql == """regex(?text, "\\d+")""")
      },
      test("escapes a quote in the pattern and the flags") {
        assertTrue(
          RegexFunction(textVar, "say \"hi\"", Some("i\"")).toSparql == """regex(?text, "say \"hi\"", "i\"")""",
        )
      },
    ),
  )
}
