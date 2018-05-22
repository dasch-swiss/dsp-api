/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

import java.io.{StringReader, StringWriter}

import javax.xml.transform.stream.StreamSource
import org.knora.webapi.StandoffConversionException

object XMLUtil {

    /**
      * Applies an XSL transformation to the given XML and returns the result.
      *
      * @param xml the xml to be transformed.
      * @param xslt the XSL transformation to be applied.
      * @return the transformation's result.
      */
    def applyXSLTransformation(xml: String, xslt: String): String = {

        // apply the XSL transformation to xml
        val proc = new net.sf.saxon.s9api.Processor(false)
        val comp = proc.newXsltCompiler()

        val exp = comp.compile(new StreamSource(new StringReader(xslt)))

        val source = try {
            proc.newDocumentBuilder().build(new StreamSource(new StringReader(xml)))
        } catch {
            case e: Exception => throw StandoffConversionException(s"The provided XML could not be parsed: ${e.getMessage}")
        }

        val xmlTransformedStr: StringWriter = new StringWriter()
        val out = proc.newSerializer(xmlTransformedStr)

        val trans = exp.load()
        trans.setInitialContextNode(source)
        trans.setDestination(out)
        trans.transform()

        xmlTransformedStr.toString

    }

}