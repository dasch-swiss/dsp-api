/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.util

import org.knora.webapi.util.standoff._
import org.scalatest.{Matchers, WordSpec}
import org.xmlunit.builder.{DiffBuilder, Input}

/**
  * Tests [[StandoffUtil]].
  */
class StandoffUtilSpec extends WordSpec with Matchers {

    val standoffUtil = new StandoffUtil

    "The standoff utility" should {

        "convert a simple XML document to text with standoff, then back to an equivalent XML document" in {

            // Convert the XML document to standoff.
            val textWithStandoff: TextWithStandoff = standoffUtil.xml2Standoff(StandoffUtilSpec.simpleXmlDoc)

            // Convert the text with standoff back to XML. The resulting XML is intentionally different in insignificant
            // ways (e.g. order of attributes).
            val backToXml = standoffUtil.standoff2Xml(textWithStandoff)

            // Compare the original XML with the regenerated XML, ignoring insignificant differences.
            val diff = DiffBuilder.compare(Input.fromString(StandoffUtilSpec.simpleXmlDoc)).withTest(Input.fromString(backToXml)).build()

            diff.hasDifferences should be(false)

        }

    }
}

object StandoffUtilSpec {
    val simpleXmlDoc =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<article>
          |    <title>Special Relativity</title>
          |
          |    <paragraph>
          |        In physics, special relativity is the generally accepted and experimentally well confirmed physical
          |        theory regarding the relationship between space and time. In <person id="6789">Albert Einstein</person>'s
          |        original pedagogical treatment, it is based on two postulates:
          |
          |        <orderedList>
          |            <listItem>that the laws of physics are invariant (i.e. identical) in all inertial systems
          |                (non-accelerating frames of reference).</listItem>
          |            <listItem>that the speed of light in a vacuum is the same for all observers, regardless of the
          |                motion of the light source.</listItem>
          |        </orderedList>
          |    </paragraph>
          |
          |    <paragraph>
          |        <person id="6789">Einstein</person> originally proposed it in
          |        <date value="1905" calendar="gregorian">1905</date> in <citation
          |        id="einstein_1905a"/>.
          |
          |        Here is a sentence with a sequence of empty tags: <foo /><bar /><baz />.
          |    </paragraph>
          |</article>""".stripMargin
}
