/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util.standoff

import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.Input
import org.xmlunit.diff.Diff

import java.nio.file.Paths

import dsp.errors.StandoffConversionException
import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.util.standoff.XMLUtil
import org.knora.webapi.util.FileUtil

/**
 * Tests [[org.knora.webapi.messages.util.standoff.XMLToStandoffUtil]].
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
      val xmlDiff: Diff =
        DiffBuilder.compare(Input.fromString(expected)).withTest(Input.fromString(transformed)).build()

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
        val _: String = XMLUtil.applyXSLTransformation(xml, xsltInvalid)
      }

    }

    "demonstrate how to handle resources that may or may not be embedded" in {
      val xmlWithNestedResource =
        FileUtil.readTextFile(Paths.get("..", "test_data/test_route/texts/beol/xml-with-nested-resources.xml"))
      val xmlWithNonNestedResource =
        FileUtil.readTextFile(Paths.get("..", "test_data/test_route/texts/beol/xml-with-non-nested-resources.xml"))
      val xslt = FileUtil.readTextFile(Paths.get("..", "test_data/test_route/texts/beol/header.xsl"))

      val transformedXmlWithNestedResource: String    = XMLUtil.applyXSLTransformation(xmlWithNestedResource, xslt)
      val transformedXmlWithNonNestedResource: String = XMLUtil.applyXSLTransformation(xmlWithNonNestedResource, xslt)

      val xmlDiff: Diff = DiffBuilder
        .compare(Input.fromString(transformedXmlWithNestedResource))
        .withTest(Input.fromString(transformedXmlWithNonNestedResource))
        .build()
      xmlDiff.hasDifferences should be(false)
    }
  }
}
