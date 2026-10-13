/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain.writers

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class HtmlSpec extends ZIOSpecDefault {

  private val Pattern =
    java.util.regex.Pattern.compile("""^<meta name="([^"]*)" content="([^"]*)">$""", java.util.regex.Pattern.DOTALL)

  private def unescape(s: String): String =
    s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")

  private val hostile = List(
    "\"><script>alert(1)</script>",
    "' onmouseover='x",
    "&amp;",
    "line one\nline two",
    "a b",
    "Über — 日本語 😀",
  )

  override def spec: Spec[Any, Any] = suite("Html")(
    test("escapeAttribute escapes & first and the other four characters") {
      assertTrue(
        Html.escapeAttribute("&<>\"'") == "&amp;&lt;&gt;&quot;&#39;",
        Html.escapeAttribute("&amp;") == "&amp;amp;",
      )
    },
    test("metaElement keeps hostile content inside one well-formed element") {
      val checks = hostile.map { content =>
        val html = Html.metaElement(MetaTag("DC.title", content))
        val m    = Pattern.matcher(html)
        m.matches() &&
        m.group(1) == "DC.title" &&
        unescape(m.group(2)) == content &&
        html.count(_ == '<') == 1 &&
        html.count(_ == '>') == 1
      }
      assertTrue(checks.forall(identity))
    },
    test("metaElement escapes the name as well") {
      assertTrue(Html.metaElement(MetaTag("a\"b", "c")) == """<meta name="a&quot;b" content="c">""")
    },
  )
}
