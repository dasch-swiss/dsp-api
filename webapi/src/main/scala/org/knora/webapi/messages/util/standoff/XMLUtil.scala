/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.standoff

import java.io.{StringReader, StringWriter}

import javax.xml.transform.stream.StreamSource
import net.sf.saxon.s9api.XsltExecutable
import org.knora.webapi.exceptions.StandoffConversionException

object XMLUtil {

  /**
   * Applies an XSL transformation to the given XML and returns the result.
   *
   * @param xml  the xml to be transformed.
   * @param xslt the XSL transformation to be applied.
   * @return the transformation's result.
   */
  def applyXSLTransformation(xml: String, xslt: String): String = {

    // apply the XSL transformation to xml
    val proc = new net.sf.saxon.s9api.Processor(false)
    val comp = proc.newXsltCompiler()

    val exp: XsltExecutable =
      try {
        comp.compile(new StreamSource(new StringReader(xslt)))
      } catch {
        case e: Exception =>
          throw StandoffConversionException(s"The provided XSLT could not be parsed: ${e.getMessage}")
      }

    val source =
      try {
        proc.newDocumentBuilder().build(new StreamSource(new StringReader(xml)))
      } catch {
        case e: Exception => throw StandoffConversionException(s"The provided XML could not be parsed: ${e.getMessage}")
      }

    val xmlTransformedStr: StringWriter = new StringWriter()
    val out = proc.newSerializer(xmlTransformedStr)

    val trans = exp.load()
    trans.setInitialContextNode(source)
    trans.setDestination(out)

    try {
      trans.transform()
    } catch {
      case e: Exception =>
        throw StandoffConversionException(s"The provided XSLT could not be applied correctly: ${e.getMessage}")
    }

    xmlTransformedStr.toString

  }

}
