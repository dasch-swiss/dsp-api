/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.util.standoff

import org.knora.webapi.{CoreSpec, StandoffConversionException}
import org.xmlunit.builder.{DiffBuilder, Input}
import org.xmlunit.diff.Diff

/**
  * Tests [[XMLToStandoffUtil]].
  */
class XMLUtilSpec extends CoreSpec {

    "The XML to standoff utility" should {

        "transform an XML document to HTML" in {

            val xml =
                """<?xml version="1.0"?>
                  |<text><i>test</i></text>
                """.stripMargin

            val xslt =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |
                  |<xsl:transform xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
                  |
                  |    <xsl:output method="html" encoding="utf-8" indent="yes"/>
                  |
                  |    <xsl:template match="text"><div><xsl:apply-templates/></div></xsl:template>
                  |
                  |    <xsl:template match="i"><em><xsl:apply-templates/></em></xsl:template>
                  |</xsl:transform>
                """.stripMargin

            val expected =
                """<div><em>test</em></div>
                """.stripMargin

            val transformed: String = XMLUtil.applyXSLTransformation(xml, xslt)

            // Compare the generated XML with the expected XML.
            val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(expected)).withTest(Input.fromString(transformed)).build()

            xmlDiff.hasDifferences should be(false)

        }

        "attempt transform an XML document with an invalid XSL transformation" in {

            val xml =
                """<?xml version="1.0"?>
                  |<text><i>test</i></text>
                """.stripMargin

            // closing root tag is invalid
            val xsltInvalid =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |
                  |<xsl:transform xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
                  |
                  |    <xsl:output method="html" encoding="utf-8" indent="yes"/>
                  |
                  |    <xsl:template match="text"><div><xsl:apply-templates/></div></xsl:template>
                  |
                  |    <xsl:template match="i"><em><xsl:apply-templates/></em></xsl:template>
                  |</xsl:transform
                """.stripMargin

            assertThrows[StandoffConversionException] {
                val transformed: String = XMLUtil.applyXSLTransformation(xml, xsltInvalid)
            }

        }
    }
}